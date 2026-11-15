package com.reader.oghma.reader

import org.json.JSONArray
import org.json.JSONObject

/** One definition of a word, with an optional example sentence. */
data class Definition(
    val text: String,
    val example: String?
)

/** One part of speech (noun, verb, ...) with its definitions. */
data class DictionaryEntry(
    val partOfSpeech: String,
    val definitions: List<Definition>,
    val synonyms: List<String>,
    val antonyms: List<String>
)

/** Everything the dictionary popup shows for one word. */
data class DictionaryResult(
    /** What the reader selected. */
    val searched: String,
    /** The headword that was found (can differ from [searched], for example "run" for "running"). */
    val word: String,
    /** Pronunciation as text, for example "həˈləʊ". */
    val phonetic: String?,
    val audioUrl: String?,
    val entries: List<DictionaryEntry>,
    /** For an inflected word ("running", "geese"): the word it comes from. */
    val baseForm: String?,
    val origin: String?,
    val license: String?,
    /** Words built on this one (filled in a moment later). */
    val related: List<String> = emptyList(),
    /** Where the entry came from, so the popup can credit it. */
    val source: String = DictionaryParser.SOURCE_FREE_DICTIONARY
)

/**
 * The thinking behind the dictionary popup, with no Android code so it can be tested on its own:
 * cleaning up what the reader selected, guessing base forms, and reading the services' JSON.
 */
object DictionaryParser {

    // ---------------------------------------------------------------------
    // What to look up
    // ---------------------------------------------------------------------

    private val EDGES = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

    /**
     * The words to try, in order. The first one the dictionary knows wins.
     *
     * "Running," -> running, run. "ice cream" -> ice cream, ice. "Alice's" -> alice.
     */
    fun candidates(raw: String): List<String> {

        val cleaned = raw
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u00AD', ' ')
            .trim()

        val tokens = cleaned
            .split(Regex("\\s+"))
            .map { it.replace(EDGES, "") }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return emptyList()

        val out = LinkedHashSet<String>()

        // A short phrase may be an entry of its own ("ice cream").
        if (tokens.size in 2..4) {
            out.add(tokens.joinToString(" ").lowercase())
        }

        var first = tokens[0].lowercase()

        // Possessives: "alice's" -> "alice", "dogs'" -> "dogs".
        if (first.endsWith("'s")) first = first.removeSuffix("'s")
        first = first.trimEnd('\'')

        if (first.isNotEmpty()) {
            out.add(first)
            out.addAll(baseGuesses(first))
        }

        // "well-known": also try the parts.
        if (first.contains('-')) {
            first.split('-').filter { it.length > 1 }.forEach { out.add(it) }
        }

        return out.toList()
    }

    private val IRREGULAR = mapOf(
        "am" to "be", "is" to "be", "are" to "be", "was" to "be", "were" to "be", "been" to "be",
        "had" to "have", "has" to "have", "did" to "do", "does" to "do", "done" to "do",
        "went" to "go", "gone" to "go", "goes" to "go", "said" to "say", "made" to "make",
        "took" to "take", "taken" to "take", "came" to "come", "saw" to "see", "seen" to "see",
        "knew" to "know", "known" to "know", "got" to "get", "gave" to "give", "given" to "give",
        "found" to "find", "thought" to "think", "told" to "tell", "became" to "become",
        "left" to "leave", "felt" to "feel", "brought" to "bring", "began" to "begin",
        "begun" to "begin", "kept" to "keep", "held" to "hold", "wrote" to "write",
        "written" to "write", "stood" to "stand", "heard" to "hear", "meant" to "mean",
        "met" to "meet", "ran" to "run", "paid" to "pay", "sat" to "sit", "spoke" to "speak",
        "spoken" to "speak", "brought" to "bring", "bought" to "buy", "caught" to "catch",
        "taught" to "teach", "sold" to "sell", "built" to "build", "lost" to "lose",
        "won" to "win", "sent" to "send", "spent" to "spend", "fell" to "fall", "fallen" to "fall",
        "rose" to "rise", "risen" to "rise", "drew" to "draw", "drawn" to "draw",
        "flew" to "fly", "flown" to "fly", "grew" to "grow", "grown" to "grow",
        "threw" to "throw", "thrown" to "throw", "broke" to "break", "broken" to "break",
        "chose" to "choose", "chosen" to "choose", "drove" to "drive", "driven" to "drive",
        "ate" to "eat", "eaten" to "eat", "sang" to "sing", "sung" to "sing",
        "men" to "man", "women" to "woman", "children" to "child", "feet" to "foot",
        "teeth" to "tooth", "mice" to "mouse", "geese" to "goose", "wives" to "wife",
        "lives" to "life", "knives" to "knife", "leaves" to "leaf", "wolves" to "wolf",
        "better" to "good", "best" to "good", "worse" to "bad", "worst" to "bad"
    )

    private val VOWELS = "aeiou"

    /** A removed ending can leave a doubled letter: "runn" -> "run". */
    private fun undouble(stem: String): String {

        if (stem.length >= 3) {

            val a = stem[stem.length - 1]
            val b = stem[stem.length - 2]

            if (a == b && a !in VOWELS && a != 'l' && a != 's' && a != 'z') {
                return stem.dropLast(1)
            }
        }

        return stem
    }

    /**
     * Likely base forms of an inflected word. Guesses only: a wrong guess just isn't found in
     * the dictionary and the next one is tried.
     */
    fun baseGuesses(word: String): List<String> {

        val w = word.lowercase()

        val out = LinkedHashSet<String>()

        IRREGULAR[w]?.let { out.add(it) }

        fun add(stem: String) {
            if (stem.length >= 2 && stem != w) out.add(stem)
        }

        when {

            w.endsWith("ies") && w.length > 4 -> {
                add(w.dropLast(3) + "y")
            }

            w.endsWith("ied") && w.length > 4 -> {
                add(w.dropLast(3) + "y")
            }

            w.endsWith("ing") && w.length > 4 -> {
                val stem = w.dropLast(3)
                add(undouble(stem))
                add(stem)
                add(stem + "e")
            }

            w.endsWith("ed") && w.length > 3 -> {
                val stem = w.dropLast(2)
                add(undouble(stem))
                add(stem)
                add(stem + "e")
            }

            w.endsWith("iest") && w.length > 5 -> {
                add(w.dropLast(4) + "y")
            }

            w.endsWith("est") && w.length > 4 -> {
                val stem = w.dropLast(3)
                add(undouble(stem))
                add(stem)
                add(stem + "e")
            }

            w.endsWith("ier") && w.length > 4 -> {
                add(w.dropLast(3) + "y")
            }

            w.endsWith("er") && w.length > 3 -> {
                val stem = w.dropLast(2)
                add(undouble(stem))
                add(stem)
                add(stem + "e")
            }

            w.endsWith("ily") && w.length > 4 -> {
                add(w.dropLast(3) + "y")
            }

            w.endsWith("ly") && w.length > 3 -> {
                add(w.dropLast(2))
            }

            w.endsWith("es") && w.length > 3 -> {
                add(w.dropLast(2))
                add(w.dropLast(1))
            }

            w.endsWith("s") && !w.endsWith("ss") && w.length > 2 -> {
                add(w.dropLast(1))
            }
        }

        return out.toList()
    }

    // ---------------------------------------------------------------------
    // Reading the Free Dictionary API (dictionaryapi.dev, v2)
    // ---------------------------------------------------------------------

    /**
     * @return null if the response holds no entries (the service answers an unknown word
     * with a JSON object, not an array, so that case also gives null).
     */
    fun parseDictionary(json: String, searched: String): DictionaryResult? {

        val array =
            try {
                JSONArray(json)
            } catch (e: Exception) {
                return null
            }

        if (array.length() == 0) return null

        var word = ""
        var phonetic: String? = null
        var audio: String? = null
        var origin: String? = null
        var license: String? = null

        val entries = ArrayList<DictionaryEntry>()

        for (i in 0 until array.length()) {

            val obj = array.optJSONObject(i) ?: continue

            if (word.isEmpty()) word = obj.optString("word", "")

            if (phonetic == null) phonetic = blankToNull(obj.optString("phonetic", ""))

            val phonetics = obj.optJSONArray("phonetics")

            if (phonetics != null) {

                for (p in 0 until phonetics.length()) {

                    val item = phonetics.optJSONObject(p) ?: continue

                    if (phonetic == null) phonetic = blankToNull(item.optString("text", ""))

                    if (audio == null) audio = normalizeAudioUrl(item.optString("audio", ""))
                }
            }

            if (origin == null) origin = blankToNull(obj.optString("origin", ""))

            if (license == null) {
                license = blankToNull(obj.optJSONObject("license")?.optString("name", "") ?: "")
            }

            val meanings = obj.optJSONArray("meanings") ?: continue

            for (m in 0 until meanings.length()) {

                val meaning = meanings.optJSONObject(m) ?: continue

                val definitions = ArrayList<Definition>()

                val synonyms = LinkedHashSet<String>()
                val antonyms = LinkedHashSet<String>()

                collectStrings(meaning.optJSONArray("synonyms"), synonyms)
                collectStrings(meaning.optJSONArray("antonyms"), antonyms)

                val defs = meaning.optJSONArray("definitions")

                if (defs != null) {

                    for (d in 0 until defs.length()) {

                        val def = defs.optJSONObject(d) ?: continue

                        val text = def.optString("definition", "").trim()

                        if (text.isEmpty()) continue

                        definitions.add(
                            Definition(
                                text = text,
                                example = blankToNull(def.optString("example", ""))
                            )
                        )

                        collectStrings(def.optJSONArray("synonyms"), synonyms)
                        collectStrings(def.optJSONArray("antonyms"), antonyms)
                    }
                }

                if (definitions.isEmpty()) continue

                entries.add(
                    DictionaryEntry(
                        partOfSpeech = meaning.optString("partOfSpeech", "").trim(),
                        definitions = definitions,
                        synonyms = synonyms.take(8),
                        antonyms = antonyms.take(8)
                    )
                )
            }
        }

        if (entries.isEmpty() || word.isEmpty()) return null

        return DictionaryResult(
            searched = searched,
            word = word,
            phonetic = phonetic,
            audioUrl = audio,
            entries = entries,
            baseForm = baseFormFrom(word, entries),
            origin = origin,
            license = license
        )
    }

    private fun collectStrings(array: JSONArray?, into: MutableSet<String>) {

        if (array == null) return

        for (i in 0 until array.length()) {

            val s = array.optString(i, "").trim()

            if (s.isNotEmpty()) into.add(s)
        }
    }

    private fun blankToNull(s: String): String? =
        s.trim().ifEmpty { null }

    /** The service sometimes gives "//host/file.mp3" or plain http. */
    fun normalizeAudioUrl(url: String?): String? {

        val u = url?.trim().orEmpty()

        if (u.isEmpty()) return null

        return when {
            u.startsWith("//") -> "https:$u"
            u.startsWith("http://") -> "https://" + u.removePrefix("http://")
            else -> u
        }
    }

    // "present participle of run." -> run
    private val FORM_OF = Regex(
        "(?:plural|past tense|past participle|present participle|gerund|" +
            "third-person singular(?: simple present(?: indicative)?)?|" +
            "simple past(?: and past participle)?|comparative form|superlative form|" +
            "comparative|superlative|inflection|alternative (?:form|spelling)|" +
            "(?:plural|past|present)[a-z ,-]*)\\s+(?:of|form of)\\s+" +
            "([\\p{L}][\\p{L}'-]*)",
        RegexOption.IGNORE_CASE
    )

    /** If the first definition just says "<inflection> of X", X is the base form. */
    private fun baseFormFrom(word: String, entries: List<DictionaryEntry>): String? {

        for (entry in entries) {

            val text = entry.definitions.firstOrNull()?.text ?: continue

            val match = FORM_OF.find(text) ?: continue

            val base = match.groupValues[1].lowercase().trimEnd('.', ',', ';')

            if (base.isNotEmpty() && base != word.lowercase()) return base
        }

        return null
    }

    const val SOURCE_FREE_DICTIONARY = "freedictionary"

    const val SOURCE_DATAMUSE = "datamuse"

    // ---------------------------------------------------------------------
    // Backup source: definitions from Datamuse (Wiktionary + WordNet)
    // ---------------------------------------------------------------------

    private val POS_NAMES = mapOf(
        "n" to "noun",
        "v" to "verb",
        "adj" to "adjective",
        "adv" to "adverb",
        "u" to ""
    )

    /**
     * Reads Datamuse's "/words?sp=<word>&qe=sp&md=dr&ipa=1&max=1" answer.
     * Each definition looks like "n<TAB>a plant cultivated for its blooms".
     *
     * @return null if Datamuse knows no definition for the word.
     */
    fun parseDatamuseDefinitions(json: String, searched: String, wanted: String): DictionaryResult? {

        val array =
            try {
                JSONArray(json)
            } catch (e: Exception) {
                return null
            }

        var item: JSONObject? = null

        for (i in 0 until array.length()) {

            val candidate = array.optJSONObject(i) ?: continue

            if (candidate.optString("word", "").equals(wanted, ignoreCase = true)) {
                item = candidate
                break
            }
        }

        if (item == null) return null

        val defs = item.optJSONArray("defs") ?: return null

        // Group by part of speech, keeping the order in which they first appear.
        val grouped = LinkedHashMap<String, MutableList<Definition>>()

        for (i in 0 until defs.length()) {

            val raw = defs.optString(i, "")

            val tab = raw.indexOf('\t')

            val pos = if (tab > 0) raw.substring(0, tab).trim() else ""

            val text = (if (tab >= 0) raw.substring(tab + 1) else raw).trim()

            if (text.isEmpty()) continue

            grouped.getOrPut(POS_NAMES[pos] ?: pos) { ArrayList() }.add(Definition(text, null))
        }

        if (grouped.isEmpty()) return null

        // Pronunciation: IPA if given, otherwise convert the ARPAbet guess.
        var phonetic: String? = null

        val tags = item.optJSONArray("tags")

        if (tags != null) {

            var arpabet: String? = null

            for (i in 0 until tags.length()) {

                val tag = tags.optString(i, "")

                when {
                    tag.startsWith("ipa_pron:") -> phonetic = blankToNull(tag.removePrefix("ipa_pron:"))
                    tag.startsWith("pron:") -> arpabet = blankToNull(tag.removePrefix("pron:"))
                }
            }

            if (phonetic == null && arpabet != null) phonetic = arpabetToIpa(arpabet)
        }

        val headword = blankToNull(item.optString("defHeadword", ""))

        val word = item.optString("word", wanted)

        return DictionaryResult(
            searched = searched,
            word = word,
            phonetic = phonetic,
            audioUrl = null,
            entries = grouped.map { (pos, list) ->
                DictionaryEntry(pos, list, emptyList(), emptyList())
            },
            baseForm = headword?.lowercase()?.takeIf { it != word.lowercase() },
            origin = null,
            license = null,
            source = SOURCE_DATAMUSE
        )
    }

    private val ARPABET = mapOf(
        "AA" to "\u0251", "AE" to "\u00E6", "AH" to "\u028C", "AO" to "\u0254",
        "AW" to "a\u028A", "AY" to "a\u026A", "B" to "b", "CH" to "t\u0283", "D" to "d",
        "DH" to "\u00F0", "EH" to "\u025B", "ER" to "\u025D", "EY" to "e\u026A", "F" to "f",
        "G" to "\u0261", "HH" to "h", "IH" to "\u026A", "IY" to "i", "JH" to "d\u0292",
        "K" to "k", "L" to "l", "M" to "m", "N" to "n", "NG" to "\u014B", "OW" to "o\u028A",
        "OY" to "\u0254\u026A", "P" to "p", "R" to "\u0279", "S" to "s", "SH" to "\u0283",
        "T" to "t", "TH" to "\u03B8", "UH" to "\u028A", "UW" to "u", "V" to "v", "W" to "w",
        "Y" to "j", "Z" to "z", "ZH" to "\u0292"
    )

    private val ARPA_VOWELS = setOf(
        "AA", "AE", "AH", "AO", "AW", "AY", "EH", "ER", "EY", "IH", "IY", "OW", "OY", "UH", "UW"
    )

    /** "HH AH0 L OW1" -> "h\u0259\u02C8lo\u028A". Only used if Datamuse does not give IPA. */
    fun arpabetToIpa(arpabet: String): String? {

        val phones = arpabet.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (phones.isEmpty()) return null

        val out = ArrayList<String>()

        // index in `out` where each syllable's consonants begin, to place stress marks
        var syllableStart = 0

        for (phone in phones) {

            val base = phone.trimEnd('0', '1', '2')

            val stress = phone.lastOrNull()?.takeIf { it in "012" }

            val ipa = ARPABET[base] ?: return null

            if (base in ARPA_VOWELS) {

                var symbol = ipa

                // Unstressed AH is a schwa, unstressed ER is the r-coloured schwa.
                if (base == "AH" && stress == '0') symbol = "\u0259"
                if (base == "ER" && stress == '0') symbol = "\u025A"

                if (stress == '1') out.add(syllableStart, "\u02C8")
                if (stress == '2') out.add(syllableStart, "\u02CC")

                out.add(symbol)

                syllableStart = out.size

            } else {

                out.add(ipa)
            }
        }

        return out.joinToString("")
    }

    // ---------------------------------------------------------------------
    // Related words (Datamuse)
    // ---------------------------------------------------------------------

    private val PLAIN_WORD = Regex("^[A-Za-z][A-Za-z'-]*$")

    // Endings that make a word a form of, or derived from, another one.
    private val SUFFIXES = setOf(
        "s", "es", "ed", "d", "ing", "er", "ers", "est", "ly", "ness", "ment", "ments",
        "able", "ably", "ful", "fully", "less", "ish", "ion", "ions", "ity", "ive",
        "ize", "ise", "ized", "ised", "izing", "ising", "al", "ally", "ous", "y", "ie",
        "ies", "ist", "ists", "ism", "ings", "ance", "ence", "ant", "ent", "ory"
    )

    /**
     * The spelling to search for related words: "love" -> "lov", so "loving" and "lovely"
     * are found too. Datamuse is queried as "<this>*".
     */
    fun relatedPrefix(base: String): String {

        val b = base.lowercase()

        return if (b.endsWith("e") && b.length > 3) b.dropLast(1) else b
    }

    /**
     * Words built on [base] (running, runner, runway, ...): inflections, derived words and
     * compounds. [json] is a Datamuse "/words?sp=<relatedPrefix>*" response.
     *
     * Only genuine-looking extensions are kept, so "run" gives "runner" but not "rung".
     */
    fun parseRelated(json: String, base: String, exclude: String): List<String> {

        val array =
            try {
                JSONArray(json)
            } catch (e: Exception) {
                return emptyList()
            }

        val headword = base.lowercase()

        val prefix = relatedPrefix(base)

        val out = LinkedHashSet<String>()

        for (i in 0 until array.length()) {

            val w = (array.optJSONObject(i) ?: JSONObject()).optString("word", "").trim()

            if (w.isEmpty() || !PLAIN_WORD.matches(w)) continue

            // Capitalised entries are names (Runge), not related words.
            if (w[0].isUpperCase()) continue

            val lower = w.lowercase()

            if (lower == exclude.lowercase() || lower == headword) continue
            if (!lower.startsWith(prefix)) continue
            if (lower.length > prefix.length + 9) continue

            val rest = lower.removePrefix(prefix)

            val ok =
                rest in SUFFIXES ||
                    // running = run + n + ing
                    (rest.length > 1 && rest[0] == prefix.last() && rest.drop(1) in SUFFIXES) ||
                    // compounds and other derivations: runway, runaway, walkout
                    rest.length >= 3

            if (!ok) continue

            out.add(lower)

            if (out.size >= 12) break
        }

        return out.toList()
    }
}
