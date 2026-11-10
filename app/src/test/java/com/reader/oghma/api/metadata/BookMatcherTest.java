package com.reader.oghma.api.metadata;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookMatcherTest {

    @Test
    public void testIsbnMatchWins() {
        boolean match = BookMatcher.isMatch(
                "Red Rising", Collections.singletonList("Pierce Brown"), "978-0345539786",
                "Completely Different Title", Collections.singletonList("Other Author"), Collections.singletonList("9780345539786")
        );
        assertTrue(match);
    }

    @Test
    public void testTitleAndAuthorMatch() {
        boolean match = BookMatcher.isMatch(
                "Red Rising: Book 1 of the Red Rising Saga", Collections.singletonList("Pierce Brown"), null,
                "Red Rising", Collections.singletonList("Pierce Brown"), null
        );
        assertTrue(match);
    }

    @Test
    public void testWrongBookRejection() {
        boolean match = BookMatcher.isMatch(
                "Red Rising", Collections.singletonList("Pierce Brown"), null,
                "Harry Potter and the Sorcerer's Stone", Collections.singletonList("J.K. Rowling"), null
        );
        assertFalse(match);
    }

    @Test
    public void testSubtitleNormalization() {
        String normalized = BookMatcher.normalizeTitle("Dune: The Graphic Novel, Book 1");
        assertEquals("dune", normalized);
    }

    private void assertEquals(String expected, String actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }
}
