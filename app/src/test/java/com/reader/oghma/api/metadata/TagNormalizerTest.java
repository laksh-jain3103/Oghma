package com.reader.oghma.api.metadata;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TagNormalizerTest {

    @Test
    public void testAliasMapping() {
        List<String> raw = Arrays.asList("sci-fi", "dystopian fiction", "epic fantasy");
        List<String> mapped = TagNormalizer.map(raw);
        assertEquals(4, mapped.size());
        assertEquals("Science Fiction", mapped.get(0));
        assertEquals("Fantasy", mapped.get(1));
        assertEquals("Dystopia", mapped.get(2));
        assertEquals("Epic Fantasy", mapped.get(3));
    }

    @Test
    public void testNoiseFiltering() {
        List<String> raw = Arrays.asList("Accessible book", "In library", "protected daisy", "sci-fi");
        List<String> mapped = TagNormalizer.map(raw);
        assertEquals(1, mapped.size());
        assertEquals("Science Fiction", mapped.get(0));
    }

    @Test
    public void testDeduplicationAndCap() {
        List<String> raw = Arrays.asList(
                "sci-fi", "science fiction", "fantasy", "horror", "mystery",
                "thriller", "romance", "adventure", "history", "poetry", "fiction",
                "biography", "science", "philosophy"
        );
        List<String> mapped = TagNormalizer.map(raw);
        assertTrue(mapped.size() <= 12);
        long sciFiCount = mapped.stream().filter(t -> t.equals("Science Fiction")).count();
        assertEquals(1, sciFiCount);
    }

    @Test
    public void testAwardHandling() {
        List<String> raw = Arrays.asList("sci-fi", "Award:nebula_award=2015", "Hugo Award Winner");
        List<String> mapped = TagNormalizer.map(raw);
        assertTrue(mapped.contains("Award Winner"));
        assertTrue(mapped.contains("Science Fiction"));
        long awardWinnerCount = mapped.stream().filter(t -> t.equals("Award Winner")).count();
        assertEquals(1, awardWinnerCount);
    }

    @Test
    public void testUnmappedSubjectHeadingsAndJunkDropped() {
        List<String> raw = Arrays.asList("Discoveries In Geography", "History of the World", "sci-fi", "Valid Tag");
        List<String> mapped = TagNormalizer.map(raw);
        assertFalse(mapped.contains("Discoveries In Geography"));
        assertFalse(mapped.contains("History of the World"));
        assertTrue(mapped.contains("Valid Tag"));
        assertTrue(mapped.contains("Science Fiction"));
    }

    @Test
    public void testOrderingPriority() {
        List<String> raw = Arrays.asList("Fiction", "Custom Tag", "Cyberpunk", "Science Fiction");
        List<String> mapped = TagNormalizer.map(raw);
        assertEquals("Science Fiction", mapped.get(0));
        assertEquals("Cyberpunk", mapped.get(1));
        assertEquals("Custom Tag", mapped.get(2));
        assertEquals("Fiction", mapped.get(3));
    }

    @Test
    public void testAliasRankingAndRebellion() {
        List<String> raw = Arrays.asList(
                "rebellion", "social classes", "sci-fi", "fantasy", "horror",
                "mystery", "thriller", "romance", "adventure", "history", "poetry", "biography"
        );
        List<String> mapped = TagNormalizer.map(raw);
        assertTrue(mapped.contains("Rebellion"));
        assertTrue(mapped.contains("Class Conflict"));
    }

    @Test
    public void testNormalizeUserInput() {
        assertEquals("Science Fiction", TagNormalizer.normalizeUserInput("sci-fi"));
        assertEquals("Custom Category", TagNormalizer.normalizeUserInput("  custom category  "));
        assertEquals("Already Capitalized", TagNormalizer.normalizeUserInput("Already Capitalized"));
    }
}
