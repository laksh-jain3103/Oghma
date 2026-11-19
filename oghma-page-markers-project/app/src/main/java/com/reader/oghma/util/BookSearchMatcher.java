package com.reader.oghma.util;

import java.util.List;
import java.util.Locale;

public class BookSearchMatcher {

    public static boolean matches(String title, String author, String format, List<String> tagNames, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (words.length == 0) return true;

        String t = title != null ? title.toLowerCase(Locale.ROOT) : "";
        String a = author != null ? author.toLowerCase(Locale.ROOT) : "";
        String f = format != null ? format.toLowerCase(Locale.ROOT) : "";

        for (String word : words) {
            boolean wordMatched = false;
            if (t.contains(word) || a.contains(word) || f.contains(word)) {
                wordMatched = true;
            } else if (tagNames != null) {
                for (String tag : tagNames) {
                    if (tag != null && tag.toLowerCase(Locale.ROOT).contains(word)) {
                        wordMatched = true;
                        break;
                    }
                }
            }
            if (!wordMatched) {
                return false;
            }
        }
        return true;
    }
}
