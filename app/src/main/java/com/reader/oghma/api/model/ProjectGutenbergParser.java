package com.reader.oghma.api.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Parses the Atom XML returned by Project Gutenberg's official OPDS search feed.
 *
 * A result is marked downloadable only when the feed explicitly states that
 * the work is public domain in the USA and supplies an EPUB acquisition link.
 */
public final class ProjectGutenbergParser {

    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final String BASE_URL = "https://www.gutenberg.org/";
    private static final Pattern BOOK_ID_PATTERN =
            Pattern.compile("/ebooks/(\\d+)(?:\\.opds)?");

    private ProjectGutenbergParser() {
    }

    public static List<ProjectGutenbergBook> parse(InputStream inputStream)
            throws Exception {
        List<ProjectGutenbergBook> books = new ArrayList<>();
        if (inputStream == null) {
            return books;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(inputStream);
        NodeList entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");

        // Be tolerant of feeds that omit the Atom namespace declaration.
        if (entries.getLength() == 0) {
            entries = document.getElementsByTagName("entry");
        }

        for (int i = 0; i < entries.getLength(); i++) {
            Node node = entries.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            ProjectGutenbergBook book = parseEntry((Element) node);
            if (book != null
                    && book.id > 0
                    && !isBlank(book.title)
                    && !isBlank(book.epubUrl)
                    && book.publicDomainInUs) {
                book.bookPageUrl = BASE_URL + "ebooks/" + book.id;
                books.add(book);
            }
        }

        return books;
    }

    private static ProjectGutenbergBook parseEntry(Element entry) {
        ProjectGutenbergBook book = new ProjectGutenbergBook();

        book.title = textOf(firstDirectChild(entry, "title"));
        String entryId = textOf(firstDirectChild(entry, "id"));
        book.publishedDate = textOf(firstDirectChild(entry, "published"));

        NodeList authors = directChildren(entry, "author");
        for (int i = 0; i < authors.getLength(); i++) {
            Node authorNode = authors.item(i);
            if (authorNode instanceof Element) {
                String authorName = textOf(
                        firstDirectChild((Element) authorNode, "name")
                );
                if (!isBlank(authorName)) {
                    book.authors.add(authorName);
                }
            }
        }

        StringBuilder rightsText = new StringBuilder();
        appendText(rightsText, textOf(firstDirectChild(entry, "rights")));

        Element content = firstDirectChild(entry, "content");
        if (content != null) {
            appendText(rightsText, content.getTextContent());
        }

        book.rights = rightsText.toString().trim();
        String normalizedRights = book.rights.toLowerCase(Locale.ROOT);
        book.publicDomainInUs =
                normalizedRights.contains("public domain in the usa");

        NodeList links = directChildren(entry, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Node linkNode = links.item(i);
            if (!(linkNode instanceof Element)) {
                continue;
            }

            Element link = (Element) linkNode;
            String rel = link.getAttribute("rel");
            String type = link.getAttribute("type").toLowerCase(Locale.ROOT);
            String href = link.getAttribute("href");

            if (isBlank(href)) {
                continue;
            }

            String resolvedUrl = resolveUrl(href);

            if (type.startsWith("application/epub+zip")
                    && (rel.isEmpty()
                    || rel.startsWith("http://opds-spec.org/acquisition")
                    || rel.startsWith("https://opds-spec.org/acquisition"))) {
                book.epubUrl = resolvedUrl;
            } else if (rel.contains("image/thumbnail")
                    || rel.contains("image")) {
                if (book.coverUrl == null) {
                    book.coverUrl = resolvedUrl;
                }
            }
        }

        book.id = extractBookId(entryId);
        if (book.id <= 0) {
            book.id = extractBookId(book.epubUrl);
        }

        return book;
    }

    private static NodeList directChildren(Element parent, String localName) {
        return new NodeList() {
            private final List<Node> matches = collect();

            private List<Node> collect() {
                List<Node> result = new ArrayList<>();
                Node child = parent.getFirstChild();
                while (child != null) {
                    if (child instanceof Element
                            && localName.equals(localNameOf(child))) {
                        result.add(child);
                    }
                    child = child.getNextSibling();
                }
                return result;
            }

            @Override
            public Node item(int index) {
                return index >= 0 && index < matches.size()
                        ? matches.get(index) : null;
            }

            @Override
            public int getLength() {
                return matches.size();
            }
        };
    }

    private static Element firstDirectChild(Element parent, String localName) {
        NodeList children = directChildren(parent, localName);
        return children.getLength() == 0
                ? null : (Element) children.item(0);
    }

    private static String localNameOf(Node node) {
        String localName = node.getLocalName();
        if (localName != null) {
            return localName;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String textOf(Element element) {
        return element == null || element.getTextContent() == null
                ? "" : element.getTextContent().trim();
    }

    private static void appendText(StringBuilder target, String value) {
        if (!isBlank(value)) {
            if (target.length() > 0) {
                target.append(' ');
            }
            target.append(value.trim());
        }
    }

    private static int extractBookId(String value) {
        if (isBlank(value)) {
            return 0;
        }
        Matcher matcher = BOOK_ID_PATTERN.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static String resolveUrl(String href) {
        try {
            return new URL(new URL(BASE_URL), href).toString();
        } catch (Exception ignored) {
            return href;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
