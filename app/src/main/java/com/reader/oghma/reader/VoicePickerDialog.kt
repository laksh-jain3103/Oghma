package com.reader.oghma.reader

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/**
 * Lets the reader choose which text-to-speech voice reads aloud.
 *
 * Shows the voices installed for the phone's current speech engine, grouped by language.
 * Tapping a voice plays a short sample. The choice is handed back to the caller, which saves
 * it and applies it to the EPUB or PDF reader.
 *
 * Better voices come from the engine itself (Google, Samsung, BookFusion Voice, ...): the
 * "More voices" button opens Android's text-to-speech settings.
 */
object VoicePickerDialog {

    interface Listener {

        /**
         * [voiceName] is null for "Automatic" (the engine's default voice).
         * [languageTag] is the voice's language, for example "en-US".
         */
        fun onVoiceChosen(voiceName: String?, languageTag: String?)
    }

    private class Option(
        val name: String,
        val languageTag: String,
        val languageLabel: String,
        val quality: Int,
        val online: Boolean
    )

    @JvmStatic
    fun show(
        context: Context,
        selectedVoice: String?,
        listener: Listener
    ) {

        var engine: TextToSpeech? = null

        engine = TextToSpeech(context.applicationContext) { status ->

            val tts = engine

            if (status != TextToSpeech.SUCCESS || tts == null) {

                Toast.makeText(
                    context,
                    "Text-to-speech isn't available on this device.",
                    Toast.LENGTH_LONG
                ).show()

                tts?.shutdown()

                return@TextToSpeech
            }

            val options = loadOptions(tts)

            if (options.isEmpty()) {

                Toast.makeText(
                    context,
                    "No voices are installed yet. Opening voice settings...",
                    Toast.LENGTH_LONG
                ).show()

                tts.shutdown()

                openVoiceSettings(context)

                return@TextToSpeech
            }

            build(context, tts, options, selectedVoice, listener)
        }
    }


    private fun loadOptions(tts: TextToSpeech): List<Option> {

        val voices =
            try {
                tts.voices
            } catch (e: Exception) {
                null
            } ?: return emptyList()

        return voices
            .filter { voice ->
                // Voices that still have to be downloaded cannot be used yet.
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            .map { voice ->
                Option(
                    name = voice.name,
                    languageTag = voice.locale.toLanguageTag(),
                    languageLabel = voice.locale.displayName,
                    quality = voice.quality,
                    online = voice.isNetworkConnectionRequired
                )
            }
            .sortedWith(
                compareBy<Option>({ it.languageLabel }, { -it.quality }, { it.name })
            )
    }


    private fun build(
        context: Context,
        tts: TextToSpeech,
        options: List<Option>,
        selectedVoice: String?,
        listener: Listener
    ) {

        val density = context.resources.displayMetrics.density

        fun dp(v: Int) = Math.round(v * density)

        // Languages, with the phone's own language first.
        val phoneLanguage = Locale.getDefault().displayName

        val languages = options
            .map { it.languageLabel }
            .distinct()
            .sortedWith(compareBy({ it != phoneLanguage }, { it }))

        // Start on the saved voice's language, otherwise the phone's.
        val startLanguage =
            options.firstOrNull { it.name == selectedVoice }?.languageLabel
                ?: languages.first()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                languages
            )
            setSelection(languages.indexOf(startLanguage), false)
        }

        val hint = TextView(context).apply {
            text = "Tap a voice to hear it."
            textSize = 13f
            alpha = 0.7f
            setPadding(0, dp(8), 0, dp(4))
        }

        val list = ListView(context).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
        }

        root.addView(spinner)
        root.addView(hint)
        root.addView(
            list,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280)
            )
        )

        // Rows currently shown: index 0 is "Automatic", the rest map to [shown].
        var shown: List<Option> = emptyList()

        fun showLanguage(label: String) {

            shown = options.filter { it.languageLabel == label }

            val rows = ArrayList<String>()

            rows.add("Automatic (default voice)")

            shown.forEachIndexed { i, o ->
                rows.add(
                    "Voice ${i + 1}  \u00B7  ${qualityLabel(o.quality)}  \u00B7  " +
                        if (o.online) "needs internet" else "offline"
                )
            }

            list.adapter = ArrayAdapter(
                context,
                android.R.layout.simple_list_item_single_choice,
                rows
            )

            val selectedIndex =
                shown.indexOfFirst { it.name == selectedVoice }

            list.setItemChecked(
                if (selectedIndex >= 0) selectedIndex + 1 else 0,
                true
            )
        }

        showLanguage(startLanguage)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                showLanguage(languages[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Tapping a voice plays a sample with it.
        list.setOnItemClickListener { _, _, position, _ ->

            try {

                val androidVoice =
                    if (position == 0) {
                        tts.defaultVoice
                    } else {
                        val name = shown[position - 1].name
                        tts.voices?.firstOrNull { it.name == name }
                    }

                if (androidVoice != null) {
                    tts.setVoice(androidVoice)
                }

                tts.speak(
                    "This is how your book will sound.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "preview"
                )

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Voice")
            .setView(root)
            .setPositiveButton("Use this voice") { _, _ ->

                val position = list.checkedItemPosition

                if (position <= 0) {

                    listener.onVoiceChosen(null, null)

                } else {

                    val option = shown[position - 1]

                    listener.onVoiceChosen(option.name, option.languageTag)
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("More voices\u2026") { _, _ ->
                openVoiceSettings(context)
            }
            .setOnDismissListener {

                try {
                    tts.stop()
                    tts.shutdown()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .show()
    }


    private fun qualityLabel(quality: Int): String =
        when {
            quality >= 500 -> "Very high quality"
            quality >= 400 -> "High quality"
            quality >= 300 -> "Normal quality"
            quality >= 200 -> "Low quality"
            else -> "Basic quality"
        }


    /** Android's own text-to-speech settings: pick another engine or install voices. */
    private fun openVoiceSettings(context: Context) {

        val candidates = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in candidates) {

            try {

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(intent)

                return

            } catch (e: Exception) {
                // try the next one
            }
        }
    }
}
