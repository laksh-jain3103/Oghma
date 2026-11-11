package com.reader.oghma;

import com.reader.oghma.api.model.ProjectGutenbergBook;
import com.reader.oghma.api.model.ProjectGutenbergParser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProjectGutenbergParserTest {

    @Test
    public void parsesPublicDomainEpubAndRejectsCopyrightedEntry() throws Exception {
        String feed =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry>"
                + "<title>Alice's Adventures in Wonderland</title>"
                + "<id>https://www.gutenberg.org/ebooks/11.opds</id>"
                + "<author><name>Carroll, Lewis</name></author>"
                + "<content type=\"xhtml\"><div xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<p>Rights: Public domain in the USA.</p></div></content>"
                + "<link rel=\"http://opds-spec.org/acquisition/open-access\" "
                + "type=\"application/epub+zip\" href=\"/ebooks/11.epub.images\"/>"
                + "<link rel=\"http://opds-spec.org/image/thumbnail\" "
                + "type=\"image/jpeg\" href=\"/cache/epub/11/pg11.cover.small.jpg\"/>"
                + "</entry>"
                + "<entry>"
                + "<title>Restricted Work</title>"
                + "<id>https://www.gutenberg.org/ebooks/99999.opds</id>"
                + "<author><name>Example Author</name></author>"
                + "<content type=\"xhtml\"><div xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<p>Rights: Copyrighted in the USA.</p></div></content>"
                + "<link rel=\"http://opds-spec.org/acquisition/open-access\" "
                + "type=\"application/epub+zip\" href=\"/ebooks/99999.epub.images\"/>"
                + "</entry>"
                + "</feed>";

        List<ProjectGutenbergBook> books = ProjectGutenbergParser.parse(
                new ByteArrayInputStream(feed.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(1, books.size());
        assertEquals(11, books.get(0).id);
        assertEquals("Alice's Adventures in Wonderland", books.get(0).title);
        assertEquals("Carroll, Lewis", books.get(0).authors.get(0));
        assertEquals("https://www.gutenberg.org/ebooks/11.epub.images",
                books.get(0).epubUrl);
        assertEquals("https://www.gutenberg.org/cache/epub/11/pg11.cover.small.jpg",
                books.get(0).coverUrl);
        assertTrue(books.get(0).publicDomainInUs);
    }
}
