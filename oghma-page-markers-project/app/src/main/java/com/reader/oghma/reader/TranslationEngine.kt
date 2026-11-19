package com.reader.oghma.reader

/**
 * What the translation feature needs from a translation library. The real one is
 * [MlKitTranslationEngine] (Google ML Kit, on-device); tests use a fake.
 *
 * Language codes are short tags such as "en", "es", "zh". Every callback arrives on the main thread.
 */
interface TranslationEngine {

    /** Languages that can be translated to and from. */
    fun supportedLanguages(): List<String>

    /** Which language [text] is in, or null if it cannot tell (short text, names, mixed text). */
    fun detectLanguage(text: String, callback: (String?) -> Unit)

    /** The language packs already on the device, or null if that could not be read. */
    fun downloadedLanguages(callback: (Set<String>?) -> Unit)

    /** Downloads a language pack. [callback] gets (success, errorMessage). */
    fun download(language: String, callback: (Boolean, String?) -> Unit)

    fun delete(language: String, callback: (Boolean) -> Unit)

    /**
     * Translates [text]. Language packs that are still missing are downloaded first.
     * [callback] gets (translation, errorMessage): exactly one of them is null.
     */
    fun translate(text: String, source: String, target: String, callback: (String?, String?) -> Unit)

    /** Releases anything held (language detector, ...). Call when the screen using this goes away. */
    fun close() {}
}
