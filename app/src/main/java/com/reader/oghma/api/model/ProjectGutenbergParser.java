package com.reader.oghma.api.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Parses the Atom XML returned by Project Gutenberg's official OPDS search feed and detail feeds.
 *
 * A result is marked downloadable only when the detail feed explicitly states that
 * the work is public domain in the USA and supplies an EPUB acquisition link.
 */
public final class ProjectGutenbergParser {

    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final String BASE_URL = "https://www.gutenberg.org/";
    private static final Pattern BOOK_ID_PATTERN =
            Pattern.compile("/ebooks/(\\d+)(?:\\.opds)?");

    private ProjectGutenbergParser() {
    }

    public static List<ProjectGutenbergBook> parseSearchListing(InputStream inputStream) throws Exception {
        List<ProjectGutenbergBook> books = new ArrayList<>();
        if (inputStream == null) {
            return books;
        }

        Document document = parseXmlDocument(inputStream);
        NodeList entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");
        if (entries.getLength() == 0) {
            entries = document.getElementsByTagName("entry");
        }

        for (int i = 0; i < entries.getLength(); i++) {
            Node node = entries.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element entry = (Element) node;
            String title = textOf(firstDirectChild(entry, "title"));
            String entryId = textOf(firstDirectChild(entry, "id"));

            if ("Subjects".equalsIgnoreCase(title)) {
                continue;
            }

            ProjectGutenbergBook book = new ProjectGutenbergBook();
            book.title = title;
            book.id = extractBookId(entryId);

            List<Element> authors = directChildren(entry, "author");
            for (Element authorElement : authors) {
                String authorName = textOf(firstDirectChild(authorElement, "name"));
                if (!isBlank(authorName)) {
                    book.authors.add(authorName);
                }
            }

            Element content = firstDirectChild(entry, "content");
            if (content != null) {
                String contentText = content.getTextContent();
                if (!isBlank(contentText) && book.authors.isEmpty() && contentText.length() < 100) {
                    book.authors.add(contentText.trim());
                }
            }

            if (book.id <= 0) {
                List<Element> links = directChildren(entry, "link");
                for (Element link : links) {
                    String href = link.getAttribute("href");
                    int id = extractBookId(href);
                    if (id > 0) {
                        book.id = id;
                        break;
                    }
                }
            }

            if (book.id > 0 && !isBlank(book.title)) {
                book.bookPageUrl = BASE_URL + "ebooks/" + book.id;
                books.add(book);
            }
        }

        return books;
    }

    public static ProjectGutenbergBook parseDetail(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return null;
        }

        Document document = parseXmlDocument(inputStream);
        NodeList entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");
        if (entries.getLength() == 0) {
            entries = document.getElementsByTagName("entry");
        }
        if (entries.getLength() == 0) {
            return null;
        }

        Element entry = (Element) entries.item(0);
        ProjectGutenbergBook book = parseEntry(entry);
        if (book != null && book.id > 0 && !isBlank(book.title) && !isBlank(book.epubUrl) && book.publicDomainInUs) {
            book.bookPageUrl = BASE_URL + "ebooks/" + book.id;
            return book;
        }
        return book;
    }

    public static List<ProjectGutenbergBook> parse(InputStream inputStream)
            throws Exception {
        List<ProjectGutenbergBook> books = new ArrayList<>();
        if (inputStream == null) {
            return books;
        }

        Document document = parseXmlDocument(inputStream);
        NodeList entries = document.getElementsByTagNameNS(ATOM_NAMESPACE, "entry");

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

    private static Document parseXmlDocument(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
        }
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) ->
                new InputSource(new StringReader(""))
        );
        return builder.parse(inputStream);
    }

    private static ProjectGutenbergBook parseEntry(Element entry) {
        ProjectGutenbergBook book = new ProjectGutenbergBook();

        book.title = textOf(firstDirectChild(entry, "title"));
        String entryId = textOf(firstDirectChild(entry, "id"));
        book.publishedDate = textOf(firstDirectChild(entry, "published"));

        List<Element> authors = directChildren(entry, "author");
        for (Element authorElement : authors) {
            String authorName = textOf(
                    firstDirectChild(authorElement, "name")
            );
            if (!isBlank(authorName)) {
                book.authors.add(authorName);
            }
        }

        StringBuilder rightsText = new StringBuilder();
        appendText(rightsText, textOf(firstDirectChild(entry, "rights")));

        Element content = firstDirectChild(entry, "content");
        if (content != null) {
            appendText(rightsText, content.getTextContent());
            if (book.authors.isEmpty()) {
                String contentText = content.getTextContent();
                Matcher authorMatcher = Pattern.compile("Author:\\s*([^\n<]+)", Pattern.CASE_INSENSITIVE).matcher(contentText);
                if (authorMatcher.find() && authorMatcher.group(1) != null) {
                    String auth = authorMatcher.group(1).trim();
                    if (!isBlank(auth)) {
                        book.authors.add(auth);
                    }
                }
            }
        }

        book.rights = rightsText.toString().trim();
        String normalizedRights = book.rights.toLowerCase(Locale.ROOT);
        book.publicDomainInUs =
                normalizedRights.contains("public domain in the usa");

        List<Element> links = directChildren(entry, "link");
        for (Element link : links) {
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

        if (isBlank(book.epubUrl)) {
            for (Element link : links) {
                String href = link.getAttribute("href");
                String type = link.getAttribute("type").toLowerCase(Locale.ROOT);
                if (!isBlank(href) && (type.contains("epub") || href.contains(".epub"))) {
                    book.epubUrl = resolveUrl(href);
                    break;
                }
            }
        }
        if (isBlank(book.epubUrl) && book.id > 0) {
            book.epubUrl = BASE_URL + "ebooks/" + book.id + ".epub.images";
        }

        book.id = extractBookId(entryId);
        if (book.id <= 0) {
            book.id = extractBookId(book.epubUrl);
        }
        if (book.id <= 0) {
            if (content != null) {
                Matcher idMatcher = Pattern.compile("EBook No\\.:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(content.getTextContent());
                if (idMatcher.find() && idMatcher.group(1) != null) {
                    try {
                        book.id = Integer.parseInt(idMatcher.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return book;
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> matches = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element
                    && localName.equals(localNameOf(child))) {
                matches.add((Element) child);
            }
            child = child.getNextSibling();
        }
        return matches;
    }

    private static Element firstDirectChild(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        return children.isEmpty() ? null : children.get(0);
    }

    private static void trySetFeature(
            DocumentBuilderFactory factory,
            String feature,
            boolean enabled
    ) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
        }
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
        return matcher.find() && matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
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
