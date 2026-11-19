package com.reader.oghma.reader

import java.util.Locale

/** Names and small decisions about languages, with no Android code. */
object TranslationLanguages {

    /** "es" -> "Spanish" (in the phone's language). */
    fun displayName(code: String, inLocale: Locale = Locale.getDefault()): String {

        val name = Locale.forLanguageTag(code).getDisplayLanguage(inLocale)

        if (name.isBlank() || name.equals(code, ignoreCase = true)) return code

        return name.replaceFirstChar { it.titlecase(inLocale) }
    }

    /** "es" -> "Español", the language written in itself. */
    fun nativeName(code: String): String {

        val locale = Locale.forLanguageTag(code)

        val name = locale.getDisplayLanguage(locale)

        if (name.isBlank()) return displayName(code)

        return name.replaceFirstChar { it.titlecase(locale) }
    }

    /** "Spanish (Español)", or just "English" when both are the same. */
    fun label(code: String, inLocale: Locale = Locale.getDefault()): String {

        val name = displayName(code, inLocale)

        val own = nativeName(code)

        return if (own.equals(name, ignoreCase = true)) name else "$name ($own)"
    }

    fun sortedByName(codes: Collection<String>, inLocale: Locale = Locale.getDefault()): List<String> =
        codes.sortedBy { displayName(it, inLocale).lowercase(inLocale) }

    /**
     * The language to assume when the text's language cannot be detected: the phone's language if
     * it can be translated, otherwise English.
     */
    fun defaultSource(deviceLanguage: String, supported: Collection<String>): String {

        val code = deviceLanguage.substringBefore('-').lowercase()

        return if (code in supported) code else "en"
    }

    /**
     * Turns a detected tag ("en", "ja-Latn", "und") into a supported code, or null.
     * Script variants such as "ja-Latn" count as their base language.
     */
    fun normalizeDetected(tag: String?, supported: Collection<String>): String? {

        if (tag.isNullOrBlank() || tag == "und") return null

        val base = tag.substringBefore('-').lowercase()

        return base.takeIf { it in supported }
    }
}
