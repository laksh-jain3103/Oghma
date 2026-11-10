package com.reader.oghma.api.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TagNormalizer {

    private static final Set<String> NOISE_PATTERNS = new HashSet<>(Arrays.asList(
            "accessible book",
            "protected daisy",
            "in library",
            "large type books",
            "general",
            "overdrive",
            "open library",
            "bestseller",
            "nyt",
            "new york times",
            "reading level",
            "fiction / general",
            "fiction/general"
    ));

    private static final Map<String, String> ALIAS_MAP = new HashMap<>();

    static {
        // Science Fiction
        addAlias("science fiction", "Science Fiction");
        addAlias("sci-fi", "Science Fiction");
        addAlias("scifi", "Science Fiction");
        addAlias("science-fiction", "Science Fiction");
        addAlias("science fiction & fantasy", "Science Fiction");
        addAlias("science fiction / fantasy", "Science Fiction");
        addAlias("speculative fiction", "Science Fiction");
        addAlias("sf", "Science Fiction");

        // Fantasy
        addAlias("fantasy", "Fantasy");
        addAlias("fantasy fiction", "Fantasy");
        addAlias("epic fantasy", "Fantasy");
        addAlias("high fantasy", "Fantasy");
        addAlias("urban fantasy", "Fantasy");

        // Dystopia
        addAlias("dystopia", "Dystopia");
        addAlias("dystopian", "Dystopia");
        addAlias("dystopian fiction", "Dystopia");
        addAlias("dystopian literature", "Dystopia");

        // Space Opera
        addAlias("space opera", "Space Opera");

        // Cyberpunk
        addAlias("cyberpunk", "Cyberpunk");

        // Horror
        addAlias("horror", "Horror");
        addAlias("horror fiction", "Horror");

        // Mystery
        addAlias("mystery", "Mystery");
        addAlias("mystery fiction", "Mystery");
        addAlias("detective", "Mystery");
        addAlias("crime", "Mystery");

        // Thriller
        addAlias("thriller", "Thriller");
        addAlias("suspense", "Thriller");
        addAlias("psychological thriller", "Thriller");

        // Romance
        addAlias("romance", "Romance");
        addAlias("romantic fiction", "Romance");

        // Historical Fiction
        addAlias("historical fiction", "Historical Fiction");
        addAlias("historical", "Historical Fiction");

        // Literary Fiction
        addAlias("literary fiction", "Literary Fiction");
        addAlias("literature", "Literary Fiction");

        // Young Adult
        addAlias("young adult", "Young Adult");
        addAlias("ya", "Young Adult");
        addAlias("young adult fiction", "Young Adult");

        // Adventure
        addAlias("adventure", "Adventure");
        addAlias("action & adventure", "Adventure");
        addAlias("action and adventure", "Adventure");

        // Humor
        addAlias("humor", "Humor");
        addAlias("humour", "Humor");
        addAlias("comedy", "Humor");

        // Fiction
        addAlias("fiction", "Fiction");

        // Nonfiction
        addAlias("nonfiction", "Nonfiction");
        addAlias("non-fiction", "Nonfiction");

        // Biography
        addAlias("biography", "Biography");
        addAlias("autobiography", "Biography");
        addAlias("memoir", "Biography");
        addAlias("biography & autobiography", "Biography");

        // History
        addAlias("history", "History");

        // Science
        addAlias("science", "Science");

        // Philosophy
        addAlias("philosophy", "Philosophy");

        // Psychology
        addAlias("psychology", "Psychology");

        // Self-Help
        addAlias("self-help", "Self-Help");
        addAlias("self help", "Self-Help");
        addAlias("personal development", "Self-Help");

        // Business
        addAlias("business", "Business");
        addAlias("economics", "Business");
        addAlias("business & economics", "Business");

        // Computers
        addAlias("computers", "Computers");
        addAlias("computer science", "Computers");
        addAlias("programming", "Computers");
        addAlias("software", "Computers");

        // Poetry
        addAlias("poetry", "Poetry");

        // Graphic Novels
        addAlias("graphic novels", "Graphic Novels");
        addAlias("comics", "Graphic Novels");
        addAlias("comic books", "Graphic Novels");

        // Religion
        addAlias("religion", "Religion");
        addAlias("spirituality", "Religion");

        // Politics
        addAlias("politics", "Politics");
        addAlias("political science", "Politics");
    }

    private static void addAlias(String key, String canonical) {
        ALIAS_MAP.put(normalizeKey(key), canonical);
    }

    public static String normalizeKey(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static List<String> map(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> result = new LinkedHashSet<>();

        for (String raw : rawTags) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            String key = normalizeKey(trimmed);
            if (isNoise(key)) continue;

            // Direct map lookup
            if (ALIAS_MAP.containsKey(key)) {
                result.add(ALIAS_MAP.get(key));
                continue;
            }

            // Keyword match in ALIAS_MAP keys
            String matchedCanonical = findKeywordMatch(key);
            if (matchedCanonical != null) {
                result.add(matchedCanonical);
                continue;
            }

            // Unmapped categories: keep if short (<= 30 chars) and not noise
            if (trimmed.length() <= 30 && !isNoise(key)) {
                result.add(toTitleCase(trimmed));
            }
        }

        // Sort: specific genres before "Fiction" and "Nonfiction"
        List<String> sorted = new ArrayList<>(result);
        sorted.sort((a, b) -> {
            boolean aGeneral = "Fiction".equalsIgnoreCase(a) || "Nonfiction".equalsIgnoreCase(a);
            boolean bGeneral = "Fiction".equalsIgnoreCase(b) || "Nonfiction".equalsIgnoreCase(b);
            if (aGeneral && !bGeneral) return 1;
            if (!aGeneral && bGeneral) return -1;
            return 0; // maintain insertion order otherwise
        });

        // Cap at 8
        if (sorted.size() > 8) {
            return new ArrayList<>(sorted.subList(0, 8));
        }

        return sorted;
    }

    public static String normalizeUserInput(String input) {
        if (input == null) return "";
        String trimmed = input.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) return "";

        String key = normalizeKey(trimmed);
        if (ALIAS_MAP.containsKey(key)) {
            return ALIAS_MAP.get(key);
        }

        // If user typed in all lowercase, convert to Title Case
        if (trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
            return toTitleCase(trimmed);
        }

        return trimmed;
    }

    private static boolean isNoise(String key) {
        if (key.isEmpty()) return true;
        for (String noise : NOISE_PATTERNS) {
            if (key.equalsIgnoreCase(noise) || key.contains(noise)) {
                return true;
            }
        }
        return false;
    }

    private static String findKeywordMatch(String key) {
        for (Map.Entry<String, String> entry : ALIAS_MAP.entrySet()) {
            String alias = entry.getKey();
            if (alias.length() > 3 && key.contains(alias)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String toTitleCase(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextTitle = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '/') {
                nextTitle = true;
                sb.append(c);
            } else if (nextTitle) {
                sb.append(Character.toTitleCase(c));
                nextTitle = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
