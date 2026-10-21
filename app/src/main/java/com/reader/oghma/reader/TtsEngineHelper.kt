package com.reader.oghma.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * A thin wrapper around Android's [TextToSpeech] used by the PDF reader.
 *
 * The PDF viewer (JavaScript) decides what to read and sends one sentence at a time. This class
 * speaks them in order and reports back when each one starts and ends, so the page can
 * highlight the sentence being read.
 */
class TtsEngineHelper(
    context: Context,
    private val callbacks: Callbacks
) {

    interface Callbacks {

        fun onUtteranceStart(id: String)

        fun onUtteranceDone(id: String)

        fun onUtteranceError(id: String)
    }

    private val appContext = context.applicationContext

    private val main = Handler(Looper.getMainLooper())

    private var engine: TextToSpeech? = null

    private var ready = false

    private var rate = 1.0f

    /** The voice the user picked (null = automatic). Set before [prepare] or via [setVoice]. */
    var voiceName: String? = null


    /**
     * Starts the engine (once). [onResult] runs on the main thread with true when speech is
     * possible, or false and a message for the user.
     */
    fun prepare(onResult: (Boolean, String?) -> Unit) {

        if (ready) {
            onResult(true, null)
            return
        }

        engine = TextToSpeech(appContext) { status ->

            val tts = engine

            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(false, "Text-to-speech isn't available on this device.")
                return@TextToSpeech
            }

            // The voice the user picked, if it is still installed.
            val chosen = findVoice(tts, voiceName)

            if (chosen != null && tts.setVoice(chosen) == TextToSpeech.SUCCESS) {

                finishSetup(tts, onResult)

                return@TextToSpeech
            }

            // Otherwise prefer the phone's language, fall back to English.
            var result = tts.setLanguage(Locale.getDefault())

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                result = tts.setLanguage(Locale.US)
            }

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                onResult(false, "No text-to-speech voice is installed.")
                return@TextToSpeech
            }

            finishSetup(tts, onResult)
        }
    }


    private fun finishSetup(
        tts: TextToSpeech,
        onResult: (Boolean, String?) -> Unit
    ) {

        tts.setSpeechRate(rate)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                utteranceId?.let { id -> main.post { callbacks.onUtteranceStart(id) } }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { id -> main.post { callbacks.onUtteranceDone(id) } }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { id -> main.post { callbacks.onUtteranceError(id) } }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { id -> main.post { callbacks.onUtteranceError(id) } }
            }
        })

        ready = true

        onResult(true, null)
    }


    private fun findVoice(tts: TextToSpeech, name: String?): android.speech.tts.Voice? {

        if (name == null) return null

        return try {
            tts.voices?.firstOrNull { it.name == name }
        } catch (e: Exception) {
            null
        }
    }


    /** Switches voice while reading (null goes back to the engine's default). */
    fun setVoice(name: String?) {

        voiceName = name

        val tts = engine ?: return

        if (!ready) return

        try {

            val target =
                if (name != null) findVoice(tts, name) else tts.defaultVoice

            if (target != null) {
                tts.setVoice(target)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /** Adds a sentence to the end of the speaking queue. */
    fun speak(text: String, id: String) {

        engine?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }


    /** Stops speaking and empties the queue. */
    fun halt() {

        engine?.stop()
    }


    fun setRate(newRate: Float) {

        rate = newRate

        engine?.setSpeechRate(newRate)
    }


    fun shutdown() {

        try {
            engine?.stop()
            engine?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        engine = null

        ready = false
    }
}
