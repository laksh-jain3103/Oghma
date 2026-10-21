package com.reader.oghma.reader

/**
 * The steps of translating a piece of text, as a small state machine with no Android code:
 *
 *   detect the language -> make sure the language packs are there (ask first) -> translate.
 *
 * It reports each step through [listener] so the popup can show it. Stale answers (for example
 * from a language you have since switched away from) are ignored.
 *
 * @param targets the languages the reader chose in Settings (never empty)
 * @param preferredTarget the one used last time, tried first
 * @param assumedSource used when the text's language cannot be detected
 */
class TranslationFlow(
    private val engine: TranslationEngine,
    private val targets: List<String>,
    private val preferredTarget: String?,
    private val assumedSource: String,
    private val listener: (State) -> Unit
) {

    sealed class State {

        object Detecting : State()

        /** The packs in [missing] are not on the device. Waits for [confirmDownload]. */
        class NeedsDownload(val source: String, val target: String, val missing: List<String>) : State()

        class Downloading(val missing: List<String>) : State()

        class Translating(val source: String, val target: String) : State()

        class Done(
            val source: String,
            val target: String,
            /** true when the source language is a guess rather than detected. */
            val sourceAssumed: Boolean,
            val original: String,
            val translated: String
        ) : State()

        /** The text is already in the only language chosen. */
        class SameLanguage(val language: String) : State()

        class Failed(val message: String, val details: String) : State()
    }

    private var text = ""

    private var source = ""

    private var sourceAssumed = false

    private var target = ""

    private var missing: List<String> = emptyList()

    private var generation = 0

    private var disposed = false

    private val supported: List<String> by lazy { engine.supportedLanguages() }

    val currentTarget: String
        get() = target

    val currentSource: String
        get() = source


    fun start(selectedText: String) {

        text = selectedText.trim()

        val gen = ++generation

        report(State.Detecting)

        engine.detectLanguage(text) { detected ->

            if (stale(gen)) return@detectLanguage

            val code = TranslationLanguages.normalizeDetected(detected, supported)

            source = code ?: assumedSource

            sourceAssumed = code == null

            target = chooseTarget(preferredTarget)

            proceed(gen)
        }
    }

    /** The reader tapped another of their languages. */
    fun changeTarget(newTarget: String) {

        if (text.isEmpty() || newTarget !in targets) return

        target = newTarget

        proceed(++generation)
    }

    /** The reader says what language the text is really in. */
    fun changeSource(newSource: String) {

        if (text.isEmpty()) return

        source = newSource

        sourceAssumed = false

        target = chooseTarget(target.ifEmpty { preferredTarget })

        proceed(++generation)
    }

    /** The reader agreed to download the missing packs. */
    fun confirmDownload() {

        val toGet = missing

        if (toGet.isEmpty()) return

        val gen = ++generation

        report(State.Downloading(toGet))

        downloadNext(toGet, 0, gen)
    }

    fun retry() {

        if (text.isEmpty()) return

        if (source.isEmpty()) start(text) else proceed(++generation)
    }

    fun dispose() {

        disposed = true

        generation++
    }


    // ---------------------------------------------------------------------

    /** The preferred target, but never the language the text is already in if there is a choice. */
    private fun chooseTarget(preferred: String?): String {

        val first = preferred?.takeIf { it in targets } ?: targets.first()

        if (first != source) return first

        return targets.firstOrNull { it != source } ?: first
    }

    private fun proceed(gen: Int) {

        if (target == source) {

            report(State.SameLanguage(source))

            return
        }

        engine.downloadedLanguages { downloaded ->

            if (stale(gen)) return@downloadedLanguages

            // English is always treated as present; the engine fetches it itself when needed.
            val needed = listOf(source, target).filter { it != "en" }.distinct()

            // If the list of packs could not be read, just try: the engine downloads what it needs.
            missing =
                if (downloaded == null) emptyList() else needed.filter { it !in downloaded }

            if (missing.isNotEmpty()) {

                report(State.NeedsDownload(source, target, missing))

            } else {

                translateNow(gen)
            }
        }
    }

    private fun downloadNext(list: List<String>, index: Int, gen: Int) {

        if (index >= list.size) {

            missing = emptyList()

            translateNow(gen)

            return
        }

        engine.download(list[index]) { ok, error ->

            if (stale(gen)) return@download

            if (!ok) {

                report(
                    State.Failed(
                        "Couldn't download the ${TranslationLanguages.displayName(list[index])} language pack.",
                        error ?: "Check your internet connection and try again."
                    )
                )

                return@download
            }

            downloadNext(list, index + 1, gen)
        }
    }

    private fun translateNow(gen: Int) {

        val from = source

        val to = target

        report(State.Translating(from, to))

        engine.translate(text, from, to) { result, error ->

            if (stale(gen)) return@translate

            if (result == null) {

                report(
                    State.Failed(
                        "Couldn't translate that.",
                        error ?: "Unknown problem."
                    )
                )

            } else {

                report(State.Done(from, to, sourceAssumed, text, result))
            }
        }
    }

    private fun stale(gen: Int) = disposed || gen != generation

    private fun report(state: State) {

        if (!disposed) listener(state)
    }
}
