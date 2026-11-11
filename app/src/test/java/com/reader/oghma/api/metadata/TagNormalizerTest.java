package com.reader.oghma.api.metadata;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
