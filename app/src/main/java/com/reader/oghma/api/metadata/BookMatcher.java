package com.reader.oghma.api.metadata;

import java.util.List;
import java.util.Locale;

public class BookMatcher {

    /** Minimum confidence score required to auto-accept a candidate book match (0.0 to 1.0). */
    public static final double CONFIDENCE_THRESHOLD = 0.75;

    public static boolean isMatch(String targetTitle, List<String> targetAuthors, String targetIsbn,
                                  String candidateTitle, List<String> candidateAuthors, List<String> candidateIsbns) {
        return calculateScore(targetTitle, targetAuthors, targetIsbn, candidateTitle, candidateAuthors, candidateIsbns) >= CONFIDENCE_THRESHOLD;
    }

    public static double calculateScore(String targetTitle, List<String> targetAuthors, String targetIsbn,
                                        String candidateTitle, List<String> candidateAuthors, List<String> candidateIsbns) {
        // 1. ISBN match wins instantly (score = 1.0)
        if (targetIsbn != null && !targetIsbn.trim().isEmpty() && candidateIsbns != null) {
            String cleanTargetIsbn = cleanIsbn(targetIsbn);
            for (String candIsbn : candidateIsbns) {
                if (cleanTargetIsbn.equalsIgnoreCase(cleanIsbn(candIsbn))) {
                    return 1.0;
                }
            }
        }

        if (targetTitle == null || candidateTitle == null) {
            return 0.0;
        }

        // 2. Normalize titles (strip punctuation, subtitle after ':', leading articles)
        String normTargetTitle = normalizeTitle(targetTitle);
        String normCandTitle = normalizeTitle(candidateTitle);

        if (normTargetTitle.isEmpty() || normCandTitle.isEmpty()) {
            return 0.0;
        }

        double titleScore;
        if (normTargetTitle.equalsIgnoreCase(normCandTitle)) {
            titleScore = 1.0;
        } else if (normTargetTitle.contains(normCandTitle) || normCandTitle.contains(normTargetTitle)) {
            titleScore = 0.85;
        } else {
            titleScore = levenshteinSimilarity(normTargetTitle, normCandTitle);
        }

        // 3. Author surname overlap
        double authorScore = 0.0;
        if (targetAuthors != null && !targetAuthors.isEmpty() && candidateAuthors != null && !candidateAuthors.isEmpty()) {
            authorScore = calculateAuthorSimilarity(targetAuthors, candidateAuthors);
        } else {
            // If author missing, title must be extremely close (e.g. exact match)
            authorScore = titleScore >= 0.95 ? 0.75 : 0.0;
        }

        // Weighted total: 60% Title, 40% Author
        return (titleScore * 0.60) + (authorScore * 0.40);
    }

    public static String normalizeTitle(String title) {
        if (title == null) return "";
        // Take part before subtitle colon if present
        int colonIdx = title.indexOf(':');
        if (colonIdx != -1) {
            title = title.substring(0, colonIdx);
        }
        // Remove punctuation and extra spaces
        String clean = title.replaceAll("[^a-zA-Z0-9\\s]", " ").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        // Strip leading articles
        if (clean.startsWith("the ")) clean = clean.substring(4);
        else if (clean.startsWith("a ")) clean = clean.substring(2);
        else if (clean.startsWith("an ")) clean = clean.substring(3);

        return clean.trim();
    }

    private static String cleanIsbn(String isbn) {
        if (isbn == null) return "";
        return isbn.replaceAll("[^0-9X]", "");
    }

    private static double calculateAuthorSimilarity(List<String> targetAuthors, List<String> candidateAuthors) {
        for (String target : targetAuthors) {
            String targetSurname = extractSurname(target);
            for (String cand : candidateAuthors) {
                String candSurname = extractSurname(cand);
                if (!targetSurname.isEmpty() && targetSurname.equalsIgnoreCase(candSurname)) {
                    return 1.0;
                }
            }
        }
        return 0.0;
    }

    private static String extractSurname(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] parts = name.trim().split("\\s+");
        return parts[parts.length - 1].toLowerCase(Locale.ROOT);
    }

    private static double levenshteinSimilarity(String s1, String s2) {
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) distance / maxLen);
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
