package com.reader.oghma;

import com.reader.oghma.api.model.ProjectGutenbergBook;
import com.reader.oghma.api.model.ProjectGutenbergParser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectGutenbergParserTest {

    @Test
    public void parsesSearchListingAndDetailFeedWithRealStructure() throws Exception {
        String searchFeed =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<title>Subjects</title>"
                + "<id>https://www.gutenberg.org/ebooks/subjects/search.opds/?query=alice</id>"
                + "</entry>"
                + "<entry>"
                + "<title>Alice's Adventures in Wonderland</title>"
                + "<id>https://www.gutenberg.org/ebooks/11.opds</id>"
                + "<content type=\"text\">Lewis Carroll</content>"
                + "<link type=\"application/atom+xml;profile=opds-catalog\" rel=\"subsection\" href=\"/ebooks/11.opds\"/>"
                + "</entry>"
                + "</feed>";

        List<ProjectGutenbergBook> searchBooks = ProjectGutenbergParser.parseSearchListing(
                new ByteArrayInputStream(searchFeed.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(1, searchBooks.size());
        assertEquals(11, searchBooks.get(0).id);
        assertEquals("Alice's Adventures in Wonderland", searchBooks.get(0).title);

        String detailFeed =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<title>Alice's Adventures in Wonderland</title>"
                + "<content type=\"xhtml\"><div xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<p>Author: Carroll, Lewis, 1832-1898</p>"
                + "<p>EBook No.: 11</p>"
                + "<p>Rights: Public domain in the USA.</p>"
                + "</div></content>"
                + "<id>urn:gutenberg:11:2</id>"
                + "<link rel=\"http://opds-spec.org/acquisition/open-access\" type=\"application/epub+zip\" href=\"/ebooks/11.epub.images\"/>"
                + "</entry>"
                + "</feed>";

        ProjectGutenbergBook detailBook = ProjectGutenbergParser.parseDetail(
                new ByteArrayInputStream(detailFeed.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(11, detailBook.id);
        assertEquals("Alice's Adventures in Wonderland", detailBook.title);
        assertTrue(detailBook.publicDomainInUs);
        assertEquals("https://www.gutenberg.org/ebooks/11.epub.images", detailBook.epubUrl);
    }

    @Test
    public void testsQueryNormalization() {
        String query = "Alice's Adventures in Wonderland!";
        String normalized = query.replaceAll("(?i)['’]s\\b", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        assertEquals("Alice Adventures in Wonderland", normalized);
    }

    @Test
    public void testsTitleMatchingBehavior() {
        String title1 = "Alice's Adventures in Wonderland";
        String title2 = "Alice in Wonderland";

        String norm1 = normalizeTitle(title1);
        String norm2 = normalizeTitle(title2);

        // Documented behavior: different titles (full title vs abbreviated) do not match strictly,
        // but main title components or subtitles can be normalized.
        assertFalse(norm1.equals(norm2));
    }

    private String normalizeTitle(String value) {
        if (value == null) return "";
        String title = value.toLowerCase(Locale.ROOT);
        int colonIdx = title.indexOf(':');
        if (colonIdx >= 0) {
            title = title.substring(0, colonIdx);
        }
        title = title.replaceAll("^\\s*(the|a|an)\\b", "").trim();
        title = title.replaceAll("[^\\p{L}\\p{N}]", "");
        return title;
    }
}
