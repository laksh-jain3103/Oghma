package com.reader.oghma.util;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class AuthorUtils {

    public static String cleanAuthor(String rawAuthor) {
        if (rawAuthor == null || rawAuthor.trim().isEmpty()) {
            return "Unknown author";
        }
        String trimmed = rawAuthor.trim();
        if (trimmed.equalsIgnoreCase("Unknown author") || trimmed.isEmpty()) {
            return "Unknown author";
        }

        String[] parts = trimmed.split("[,&]");
        Set<String> unique = new LinkedHashSet<>();
        Set<String> seenLower = new HashSet<>();
        for (String part : parts) {
            String pTrim = part.trim();
            if (!pTrim.isEmpty()) {
                String lower = pTrim.toLowerCase(Locale.ROOT);
                if (!seenLower.contains(lower)) {
                    seenLower.add(lower);
                    unique.add(pTrim);
                }
            }
        }

        if (unique.isEmpty()) {
            return "Unknown author";
        }

        return String.join(", ", unique);
    }

    public static boolean isUnknownOrEmpty(String author) {
        if (author == null || author.trim().isEmpty()) return true;
        return author.trim().equalsIgnoreCase("unknown author");
    }
}
