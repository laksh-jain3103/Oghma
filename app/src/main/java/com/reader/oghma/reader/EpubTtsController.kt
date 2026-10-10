package com.reader.oghma.reader

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language

/**
 * Reads an EPUB aloud using Readium's text-to-speech navigator.
 *
 * Readium walks the book's text sentence by sentence and speaks it with Android's TTS engine.
 * While it speaks, this class highlights the current sentence on the page and keeps the page
 * turned to it.
 *
 * Lifecycle: [start] once, [stop] when done. After [stop] the instance cannot be reused.
 */
@OptIn(ExperimentalReadiumApi::class)
class EpubTtsController(
    private val application: Application,
    private val publication: Publication,
    private val visualNavigator: EpubNavigatorFragment
) {

    interface Listener {

        /** Playback started or paused. */
        fun onPlayingChanged(playing: Boolean)

        /** Reading ended (finished, stopped, or failed). The controls should be hidden. */
        fun onStopped()

        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var navigator: AndroidTtsNavigator? = null

    private var listener: Listener? = null

    private var startJob: Job? = null

    private var closed = false

    // Readium replaces all preferences on every change, so the current set is kept here.
    private var preferences = AndroidTtsPreferences()


    /**
     * @param speed 1.0 = normal speech rate.
     * @param voiceName the voice the user picked, or null for automatic.
     * @param voiceLanguage the picked voice's language tag, for example "en-US".
     */
    fun start(
        speed: Double,
        voiceName: String?,
        voiceLanguage: String?,
        listener: Listener
    ) {

        if (startJob != null || closed) return

        this.listener = listener

        preferences = AndroidTtsPreferences(
            speed = speed,
            voices = voicesFor(voiceName, voiceLanguage)
        )

        startJob = scope.launch {

            val factory = TtsNavigatorFactory(application, publication)

            if (factory == null) {
                fail("This book can't be read aloud.")
                return@launch
            }

            // Start from what is on screen right now.
            val initialLocator: Locator? =
                try {
                    visualNavigator.firstVisibleElementLocator()
                } catch (e: Exception) {
                    null
                }

            val created = factory.createNavigator(
                listener = object : TtsNavigator.Listener {

                    override fun onStopRequested() {
                        stop()
                    }
                },
                initialLocator = initialLocator,
                initialPreferences = preferences
            )

            val tts = created.getOrNull()

            if (tts == null) {
                fail("Text-to-speech couldn't start. Check that a voice is installed.")
                return@launch
            }

            navigator = tts

            observe(tts)

            tts.play()
        }
    }


    private fun observe(tts: AndroidTtsNavigator) {

        // Play / pause state, end of book, and errors.
        tts.playback
            .onEach { playback ->

                listener?.onPlayingChanged(playback.playWhenReady)

                when (val state = playback.state as? TtsNavigator.State) {

                    is TtsNavigator.State.Ended -> stop()

                    is TtsNavigator.State.Failure -> onFailure(state.error)

                    else -> Unit
                }
            }
            .launchIn(scope)

        // The sentence being spoken: highlight it and keep the page on it.
        tts.location
            .map { it.utteranceLocator }
            .distinctUntilChanged()
            .onEach { locator ->

                showHighlight(locator)

                try {
                    visualNavigator.go(locator, animated = false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .launchIn(scope)
    }


    private fun onFailure(error: TtsNavigator.Error) {

        val engineError =
            (error as? TtsNavigator.Error.EngineError<*>)?.cause

        if (engineError is AndroidTtsEngine.Error.LanguageMissingData) {

            listener?.onError("The voice for this book's language isn't installed.")

            AndroidTtsEngine.requestInstallVoice(application)

        } else {

            listener?.onError(error.message)
        }

        stop()
    }


    private suspend fun showHighlight(locator: Locator?) {

        val decorations =
            if (locator == null) {
                emptyList()
            } else {
                listOf(
                    Decoration(
                        id = "tts-utterance",
                        locator = locator,
                        style = Decoration.Style.Highlight(
                            tint = SPOKEN_COLOR
                        )
                    )
                )
            }

        try {
            visualNavigator.applyDecorations(decorations, GROUP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // ---------------------------------------------------------------------
    // Controls
    // ---------------------------------------------------------------------

    val isPlaying: Boolean
        get() = navigator?.playback?.value?.playWhenReady == true


    fun togglePlayPause() {

        val tts = navigator ?: return

        if (tts.playback.value.playWhenReady) {
            tts.pause()
        } else {
            tts.play()
        }
    }


    fun next() {

        val tts = navigator ?: return

        if (tts.hasNextUtterance()) {
            tts.skipToNextUtterance()
        }
    }


    fun previous() {

        val tts = navigator ?: return

        if (tts.hasPreviousUtterance()) {
            tts.skipToPreviousUtterance()
        }
    }


    fun setSpeed(speed: Double) {

        preferences = preferences.copy(speed = speed)

        navigator?.submitPreferences(preferences)
    }


    /** Changes the voice (null = automatic). Takes effect from the next sentence. */
    fun setVoice(voiceName: String?, voiceLanguage: String?) {

        preferences = preferences.copy(
            voices = voicesFor(voiceName, voiceLanguage)
        )

        navigator?.submitPreferences(preferences)
    }


    // Readium looks a voice up by the book's language, first with the region (en-GB) and
    // then without (en). Registering both means the voice is used for any variant.
    private fun voicesFor(
        voiceName: String?,
        voiceLanguage: String?
    ): Map<Language, AndroidTtsEngine.Voice.Id>? {

        if (voiceName == null || voiceLanguage == null) return null

        val id = AndroidTtsEngine.Voice.Id(voiceName)

        val language = Language(voiceLanguage)

        return mapOf(
            language to id,
            language.removeRegion() to id
        )
    }


    fun stop() {

        if (closed) return

        closed = true

        val tts = navigator
        val stoppedListener = listener

        navigator = null
        listener = null

        // Remove the highlight with a standalone scope, because [scope] is cancelled below.
        CoroutineScope(Dispatchers.Main).launch {
            showHighlight(null)
        }

        scope.cancel()

        try {
            tts?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stoppedListener?.onStopped()
    }


    private fun fail(message: String) {

        listener?.onError(message)

        stop()
    }


    private companion object {

        const val GROUP = "tts"

        // Soft blue, different from the yellow user highlights.
        const val SPOKEN_COLOR = 0xFF89B4FA.toInt()
    }
}
