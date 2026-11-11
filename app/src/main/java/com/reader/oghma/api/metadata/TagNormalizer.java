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

    private static final List<String> PRIMARY_GENRES_ORDER = Arrays.asList(
            "Science Fiction", "Fantasy", "Dystopia", "Space Opera", "Cyberpunk",
            "Horror", "Mystery", "Thriller", "Romance", "Historical Fiction",
            "Literary Fiction", "Young Adult", "Adventure", "Humor", "Biography",
            "History", "Science", "Philosophy", "Psychology", "Self-Help", "Business",
            "Computers", "Poetry", "Graphic Novels", "Religion", "Politics", "Award Winner"
    );

    private static final Map<String, String> ALIAS_MAP = new HashMap<>();
    private static final int MAX_TAGS = 12;
    private static final Map<String, String> PARENT = new HashMap<>();
    private static final Set<String> CANONICAL_VALUES = new HashSet<>();

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

        // ---- Subgenres and themes. Each also adds its parent genre. ----
        addSub("epic fantasy", "Epic Fantasy", "Fantasy");
        addSub("high fantasy", "High Fantasy", "Fantasy");
        addSub("urban fantasy", "Urban Fantasy", "Fantasy");
        addSub("dark fantasy", "Dark Fantasy", "Fantasy");
        addSub("sword and sorcery", "Sword and Sorcery", "Fantasy");
        addSub("fairy tales", "Fairy Tales", "Fantasy");
        addSub("dragons", "Dragons", "Fantasy");
        addSub("wizards", "Wizards", "Fantasy");
        addSub("witches", "Witches", "Fantasy");
        addSub("vampires", "Vampires", "Horror");
        addSub("paranormal", "Paranormal", "Fantasy");
        addSub("magic", "Magic", "Fantasy");
        addSub("mythology", "Mythology", null);

        addSub("military science fiction", "Military Science Fiction", "Science Fiction");
        addSub("hard science fiction", "Hard Science Fiction", "Science Fiction");
        addSub("space warfare", "Space Warfare", "Science Fiction");
        addSub("space colonies", "Space Colonization", "Science Fiction");
        addSub("space colonization", "Space Colonization", "Science Fiction");
        addSub("time travel", "Time Travel", "Science Fiction");
        addSub("post-apocalyptic", "Post-Apocalyptic", "Science Fiction");
        addSub("apocalyptic", "Post-Apocalyptic", "Science Fiction");
        addSub("alternative history", "Alternate History", "Science Fiction");
        addSub("alternate history", "Alternate History", "Science Fiction");
        addSub("artificial intelligence", "Artificial Intelligence", "Science Fiction");
        addSub("extraterrestrial", "Aliens", "Science Fiction");
        addSub("aliens", "Aliens", "Science Fiction");
        addSub("robots", "Robots", "Science Fiction");
        addSub("steampunk", "Steampunk", "Science Fiction");
        addSub("genetic engineering", "Genetic Engineering", "Science Fiction");

        addSub("cozy mystery", "Cozy Mystery", "Mystery");
        addSub("police procedural", "Police Procedural", "Mystery");
        addSub("true crime", "True Crime", "Nonfiction");
        addSub("espionage", "Espionage", "Thriller");
        addSub("political fiction", "Political Fiction", "Fiction");
        addSub("magical realism", "Magical Realism", "Literary Fiction");
        addSub("satire", "Satire", "Humor");
        addSub("coming of age", "Coming of Age", null);
        addSub("rebellion", "Rebellion", null);
        addSub("social classes", "Class Conflict", null);
        addSub("revenge", "Revenge", null);
        addSub("survival", "Survival", null);
        addSub("warfare", "War", null);
        addSub("military", "Military", null);
        addSub("classics", "Classics", null);
        addSub("short stories", "Short Stories", "Fiction");
        addSub("children's", "Children's", null);
        addSub("love stories", "Romance", null);

        CANONICAL_VALUES.addAll(ALIAS_MAP.values());
        CANONICAL_VALUES.addAll(PARENT.keySet());
    }

    /** Subgenre or theme: maps to its own tag, and also adds a parent genre if given. */
    private static void addSub(String key, String canonical, String parent) {
        addAlias(key, canonical);
        if (parent != null) {
            PARENT.put(canonical, parent);
        }
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

            String lower = trimmed.toLowerCase(Locale.ROOT);

            // Award handling: anything containing the word "award" (any case) -> "Award Winner"
            if (lower.matches(".*\\baward\\b.*") || lower.contains("award")) {
                result.add("Award Winner");
                continue;
            }

            String key = normalizeKey(trimmed);
            if (isNoise(key)) continue;

            // Direct map lookup
            if (ALIAS_MAP.containsKey(key)) {
                addWithParent(result, ALIAS_MAP.get(key));
                continue;
            }

            // Keyword matches: a raw tag like "Epic fantasy, American" can hit several aliases.
            Set<String> matches = findKeywordMatches(key);
            if (!matches.isEmpty()) {
                for (String m : matches) addWithParent(result, m);
                continue;
            }

            // Unmapped categories: keep if valid according to rules
            if (isValidUnmappedTag(trimmed, key)) {
                result.add(toTitleCase(trimmed));
            }
        }

        // Sort: specific genres before "Fiction" and "Nonfiction"
        List<String> sorted = new ArrayList<>(result);
        sorted.sort((a, b) -> {
            int rankA = getTagRank(a);
            int rankB = getTagRank(b);
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
            return a.compareToIgnoreCase(b);
        });

        // Cap at 12
        if (sorted.size() > MAX_TAGS) {
            return new ArrayList<>(sorted.subList(0, MAX_TAGS));
        }

        return sorted;
    }

    private static boolean isValidUnmappedTag(String trimmed, String key) {
        if (isNoise(key)) return false;
        if (trimmed.matches(".*[:=0-9].*")) return false;

        String[] words = trimmed.split("\\s+");
        if (words.length > 3) return false;

        if (words.length >= 3) {
            String lower = key;
            if (lower.contains(" in ") || lower.contains(" of ")) {
                return false;
            }
        }

        return true;
    }

    private static int getTagRank(String tag) {
        if ("Fiction".equalsIgnoreCase(tag) || "Nonfiction".equalsIgnoreCase(tag)) {
            return 300;
        }
        int primaryIdx = -1;
        for (int i = 0; i < PRIMARY_GENRES_ORDER.size(); i++) {
            if (PRIMARY_GENRES_ORDER.get(i).equalsIgnoreCase(tag)) {
                primaryIdx = i;
                break;
            }
        }
        if (primaryIdx != -1) {
            return primaryIdx;
        }
        if (CANONICAL_VALUES.contains(tag) || PARENT.containsKey(tag) || PARENT.containsValue(tag)) {
            return 100; // subgenres/themes (alias table values)
        }
        return 200; // unmapped kept tags
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

    private static void addWithParent(Set<String> out, String canonical) {
        out.add(canonical);
        String parent = PARENT.get(canonical);
        if (parent != null) out.add(parent);
    }

    /**
     * Every alias contained in the key, except aliases that are part of a longer matching
     * alias ("history" inside "alternative history", "fantasy" inside "epic fantasy").
     */
    private static Set<String> findKeywordMatches(String key) {
        java.util.TreeSet<String> hits = new java.util.TreeSet<>();
        for (String alias : ALIAS_MAP.keySet()) {
            if (alias.length() > 3 && key.contains(alias)) {
                hits.add(alias);
            }
        }
        Set<String> out = new LinkedHashSet<>();
        for (String a : hits) {
            boolean covered = false;
            for (String other : hits) {
                if (!other.equals(a) && other.contains(a)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) out.add(ALIAS_MAP.get(a));
        }
        return out;
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
