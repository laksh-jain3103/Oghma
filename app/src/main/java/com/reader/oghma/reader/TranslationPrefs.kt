package com.reader.oghma.reader

import android.content.Context

/** What the reader chose in Settings for translation. */
object TranslationPrefs {

    private const val PREFS = "oghma_translation_prefs"

    private const val KEY_TARGETS = "target_languages"

    private const val KEY_LAST_TARGET = "last_target"

    private const val KEY_SOURCE_FALLBACK = "source_fallback"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The languages to translate into, in the order they were chosen. */
    @JvmStatic
    fun getTargets(context: Context): List<String> =
        prefs(context)
            .getString(KEY_TARGETS, "")
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    @JvmStatic
    fun setTargets(context: Context, targets: List<String>) {

        prefs(context).edit().putString(KEY_TARGETS, targets.distinct().joinToString(",")).apply()
    }

    /** False until the reader has picked at least one language in Settings. */
    @JvmStatic
    fun hasTargets(context: Context): Boolean = getTargets(context).isNotEmpty()

    @JvmStatic
    fun getLastTarget(context: Context): String? =
        prefs(context).getString(KEY_LAST_TARGET, null)

    @JvmStatic
    fun setLastTarget(context: Context, code: String) {

        prefs(context).edit().putString(KEY_LAST_TARGET, code).apply()
    }

    /** The language the reader's books are in, used when it cannot be detected. Null = automatic. */
    @JvmStatic
    fun getSourceFallback(context: Context): String? =
        prefs(context).getString(KEY_SOURCE_FALLBACK, null)

    @JvmStatic
    fun setSourceFallback(context: Context, code: String?) {

        prefs(context).edit().putString(KEY_SOURCE_FALLBACK, code).apply()
    }
}
