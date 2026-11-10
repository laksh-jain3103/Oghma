package com.reader.oghma.api.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Digs a title and author out of a file name such as
 * "Pierce_Brown_-_Red_Rising_(z-lib.org).epub".
 *
 * Pure Java, no Android dependencies, so it can be unit tested.
 */
public final class FilenameParser {

    private FilenameParser() {}

    public static final class Candidate {
        public final String title;
        /** Null when no author guess is available. */
        public final List<String> authors;

        public Candidate(String title, List<String> authors) {
            this.title = title;
            this.authors = authors;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Candidate)) return false;
            Candidate c = (Candidate) o;
            return title.equalsIgnoreCase(c.title)
                    && String.valueOf(authors).equalsIgnoreCase(String.valueOf(c.authors));
        }

        @Override
        public int hashCode() {
            return title.toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public String toString() {
            return "title=\"" + title + "\" authors=" + authors;
        }
    }

    private static final Pattern KNOWN_EXTENSION =
            Pattern.compile("(?i)\\.(epub|pdf|txt|mobi|azw3?|fb2|djvu|rtf)$");

    private static final Pattern BRACKETS =
            Pattern.compile("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}");

    private static final Pattern SITE_JUNK = Pattern.compile(
            "(?i)\\b(z[- ]?lib(rary)?([. ]org)?|libgen([. ]\\w+)?|anna'?s[- ]archive|"
                    + "oceanofpdf([. ]com)?|epub|mobi|azw3|retail|ebook|ocr)\\b");

    /** Digits (optionally hyphen/space separated) that may be an ISBN-10 or ISBN-13. */
    private static final Pattern ISBN_LIKE =
            Pattern.compile("(?<![\\dA-Za-z])(?:\\d[- ]?){9,12}[\\dXx](?![\\dA-Za-z])");

    private static final Pattern DASH_SEPARATOR = Pattern.compile("\\s+[-\\u2013\\u2014]\\s+");

    private static final Pattern BY_SEPARATOR = Pattern.compile("(?i)\\s+by\\s+");

    /** Readable title for display: extension, junk and bracketed noise removed. */
    public static String cleanTitle(String fileName) {
        String cleaned = clean(fileName);
        if (cleaned.isEmpty()) {
            return fileName == null ? "" : stripExtension(fileName).trim();
        }
        return cleaned;
    }

    /**
     * Guesses of (title, author), best guess first. Always ends with the whole cleaned
     * name as a title with no author.
     */
    public static List<Candidate> candidates(String fileName) {
        List<Candidate> out = new ArrayList<>();
        String cleaned = clean(fileName);
        if (cleaned.isEmpty()) {
            return out;
        }

        List<String> parts = splitParts(cleaned);

        if (parts.size() == 2) {
            String a = parts.get(0);
            String b = parts.get(1);
            boolean aName = looksLikeName(a);
            boolean bName = looksLikeName(b);
            if (aName || !bName) {
                add(out, b, a);   // "Author - Title"
                add(out, a, b);   // "Title - Author"
            } else {
                add(out, a, b);
                add(out, b, a);
            }
        } else if (parts.size() > 2) {
            String first = parts.get(0);
            String last = parts.get(parts.size() - 1);
            add(out, join(parts, 1, parts.size()), first);       // "Author - Series - Title"
            add(out, parts.get(parts.size() - 1), first);        // "Author - ... - Title"
            add(out, join(parts, 0, parts.size() - 1), last);    // "Title - Series - Author"
        }

        Candidate whole = new Candidate(cleaned, null);

        if (parts.size() <= 1) {
            // No separator ("Red Rising Pierce Brown"): try the whole name as a title
            // first, then guess that the first or last two words are the author.
            if (!out.contains(whole)) out.add(whole);
            String[] w = cleaned.split(" ");
            if (w.length >= 3 && w.length <= 8) {
                add(out, join(Arrays.asList(w), 0, w.length - 2), w[w.length - 2] + " " + w[w.length - 1]);
                add(out, join(Arrays.asList(w), 2, w.length), w[0] + " " + w[1]);
            }
            return out;
        }

        if (!out.contains(whole)) {
            out.add(whole);
        }
        return out;
    }

    /** An ISBN-10 or ISBN-13 found in the file name, digits only, or null. */
    public static String extractIsbn(String fileName) {
        if (fileName == null) return null;
        java.util.regex.Matcher m = ISBN_LIKE.matcher(stripExtension(fileName.replace('_', ' ')));
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9Xx]", "");
            if (isIsbnLength(digits)) return digits.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean isIsbnLength(String digits) {
        if (digits.length() == 13) return digits.startsWith("978") || digits.startsWith("979");
        return digits.length() == 10;
    }

    private static String removeIsbns(String s) {
        java.util.regex.Matcher m = ISBN_LIKE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9Xx]", "");
            m.appendReplacement(sb, isIsbnLength(digits) ? " " : java.util.regex.Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---------------------------------------------------------------------

    private static String clean(String fileName) {
        if (fileName == null) return "";

        String s = stripExtension(fileName.trim());

        s = s.replace('_', ' ');

        // "Red.Rising.Pierce.Brown" style: dots used as spaces.
        if (!s.contains(" ") && s.contains(".")) {
            s = s.replace('.', ' ');
        }

        String withoutBrackets = BRACKETS.matcher(s).replaceAll(" ");
        if (!withoutBrackets.trim().isEmpty()) {
            s = withoutBrackets;
        }

        s = SITE_JUNK.matcher(s).replaceAll(" ");
        s = removeIsbns(s);
        s = s.replaceAll("[()\\[\\]{}]", " ");
        s = s.replaceAll("\\s+", " ").trim();

        // Dangling separators left behind by the removals above.
        s = s.replaceAll("^[\\s\\-\\u2013\\u2014,.]+", "").replaceAll("[\\s\\-\\u2013\\u2014,.]+$", "");

        return s.replaceAll("\\s+", " ").trim();
    }

    private static String stripExtension(String name) {
        return KNOWN_EXTENSION.matcher(name).replaceFirst("");
    }

    private static List<String> splitParts(String cleaned) {
        List<String> parts = new ArrayList<>();

        String[] byParts = BY_SEPARATOR.split(cleaned, 2);
        if (byParts.length == 2 && !DASH_SEPARATOR.matcher(cleaned).find()) {
            // "Red Rising by Pierce Brown": title first, author second.
            parts.add(byParts[0].trim());
            parts.add(byParts[1].trim());
            return parts;
        }

        for (String p : DASH_SEPARATOR.split(cleaned)) {
            String t = p.trim();
            if (!t.isEmpty()) parts.add(t);
        }
        return parts;
    }

    /** 1 to 4 words, each starting with a capital letter, no digits. "Pierce Brown", "Brown, Pierce". */
    private static boolean looksLikeName(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.matches(".*\\d.*")) return false;
        String[] words = t.replace(",", " ").trim().split("\\s+");
        if (words.length < 2 || words.length > 4) return false;
        for (String w : words) {
            if (w.isEmpty()) continue;
            char c = w.charAt(0);
            // Allow short lower-case particles: "de", "van", "von", "la".
            if (!Character.isUpperCase(c) && w.length() > 3) return false;
        }
        return true;
    }

    /** "Brown, Pierce" becomes "Pierce Brown". Anything else is returned unchanged. */
    private static String flipLastFirst(String name) {
        String[] bits = name.split(",");
        if (bits.length == 2 && !bits[0].trim().isEmpty() && !bits[1].trim().isEmpty()
                && bits[0].trim().split("\\s+").length <= 2) {
            return bits[1].trim() + " " + bits[0].trim();
        }
        return name.trim();
    }

    private static void add(List<Candidate> list, String title, String author) {
        if (title == null || title.trim().isEmpty() || author == null || author.trim().isEmpty()) {
            return;
        }
        Candidate c = new Candidate(
                title.trim(),
                Collections.singletonList(flipLastFirst(author)));
        if (!list.contains(c)) {
            list.add(c);
        }
    }

    private static String join(List<String> parts, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
