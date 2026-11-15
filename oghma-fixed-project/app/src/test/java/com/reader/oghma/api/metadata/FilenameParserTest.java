package com.reader.oghma.api.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class FilenameParserTest {

    private static FilenameParser.Candidate first(String name) {
        List<FilenameParser.Candidate> c = FilenameParser.candidates(name);
        assertTrue(!c.isEmpty());
        return c.get(0);
    }

    @Test
    public void authorDashTitleWithSiteJunk() {
        FilenameParser.Candidate c = first("Pierce_Brown_-_Red_Rising_(z-lib.org).epub");
        assertEquals("Red Rising", c.title);
        assertEquals("Pierce Brown", c.authors.get(0));
    }

    @Test
    public void lastCommaFirstIsFlipped() {
        FilenameParser.Candidate c = first("Brown, Pierce - Red Rising (Red Rising Saga, #1) (2014).epub");
        assertEquals("Red Rising", c.title);
        assertEquals("Pierce Brown", c.authors.get(0));
    }

    @Test
    public void bothOrdersAreOffered() {
        List<FilenameParser.Candidate> c = FilenameParser.candidates("Red Rising - Pierce Brown.epub");
        boolean found = false;
        for (FilenameParser.Candidate x : c) {
            if (x.title.equals("Red Rising") && x.authors != null && x.authors.get(0).equals("Pierce Brown")) found = true;
        }
        assertTrue(found);
    }

    @Test
    public void titleOnly() {
        FilenameParser.Candidate c = first("Dune.epub");
        assertEquals("Dune", c.title);
        assertNull(c.authors);
    }

    @Test
    public void isbnFromName() {
        assertEquals("9780345539786", FilenameParser.extractIsbn("978-0-345-53978-6.epub"));
        assertNull(FilenameParser.extractIsbn("Red Rising.epub"));
    }

    @Test
    public void cleanTitleStripsNoise() {
        assertEquals("Pierce Brown - Red Rising", FilenameParser.cleanTitle("Pierce_Brown_-_Red_Rising_(z-lib.org).epub"));
    }
}
