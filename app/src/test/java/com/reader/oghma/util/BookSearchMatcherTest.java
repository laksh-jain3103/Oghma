package com.reader.oghma.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookSearchMatcherTest {

    @Test
    public void testBlankQueryMatchesEverything() {
        assertTrue(BookSearchMatcher.matches("Dune", "Frank Herbert", "epub", Collections.singletonList("Sci-Fi"), ""));
        assertTrue(BookSearchMatcher.matches("Dune", "Frank Herbert", "epub", Collections.singletonList("Sci-Fi"), null));
    }

    @Test
    public void testMultiWordQuery() {
        assertTrue(BookSearchMatcher.matches("The Great Gatsby", "F. Scott Fitzgerald", "epub", Collections.singletonList("Classic"), "great fitzgerald"));
        assertFalse(BookSearchMatcher.matches("The Great Gatsby", "F. Scott Fitzgerald", "epub", Collections.singletonList("Classic"), "great hemingway"));
    }

    @Test
    public void testTagNameMatch() {
        assertTrue(BookSearchMatcher.matches("1984", "George Orwell", "epub", Arrays.asList("Dystopia", "Political"), "dystopia"));
    }

    @Test
    public void testAuthorMatch() {
        assertTrue(BookSearchMatcher.matches("Red Rising", "Pierce Brown", "epub", Collections.singletonList("Sci-Fi"), "pierce"));
    }

    @Test
    public void testNoMatch() {
        assertFalse(BookSearchMatcher.matches("Dune", "Frank Herbert", "epub", Collections.singletonList("Sci-Fi"), "tolkien"));
    }
}
