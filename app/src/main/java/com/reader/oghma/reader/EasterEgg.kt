package com.reader.oghma.reader

/**
 * An easter egg for this project: trying to download the Hebrew language pack shows a message
 * instead of downloading.
 *
 * To turn it off, set [ENABLED] to false. Nothing else needs to change.
 */
object EasterEgg {

    const val ENABLED = true

    const val BLOCKED_LANGUAGE = "he"

    const val MESSAGE = "Free Palestine"

    fun blocks(language: String): Boolean = ENABLED && language == BLOCKED_LANGUAGE

    fun blocksAny(languages: Collection<String>): Boolean = languages.any { blocks(it) }
}
