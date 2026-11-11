package com.reader.oghma.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AuthorUtilsTest {

    @Test
    public void testCleanAuthor() {
        assertEquals("Lindsay Ellis", AuthorUtils.cleanAuthor("Lindsay Ellis, Lindsay Ellis"));
        assertEquals("A, B", AuthorUtils.cleanAuthor("A & B"));
        assertEquals("Unknown author", AuthorUtils.cleanAuthor(null));
        assertEquals("Unknown author", AuthorUtils.cleanAuthor("   "));
    }
}
