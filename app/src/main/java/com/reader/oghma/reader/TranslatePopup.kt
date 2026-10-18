package com.reader.oghma.reader

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * The translation card shown over the reader when you tap "Translate" on selected text.
 *
 * Like the dictionary card it sits on the side of the screen away from the selected text, so the
 * line you are reading stays visible. It shows the translation, lets you switch between the
 * languages you chose in Settings, copy the translation, and hear it read out. If a language pack
 * is missing it asks before downloading.
 */
class TranslatePopup(private val context: Context) {

    interface Listener {

        fun onDismissed()

        /** The reader wants to change languages or manage packs. */
        fun onOpenSettings()
    }

    // Matches the app's dark theme.
    private val cardColor = Color.parseColor("#313244")
    private val strokeColor = Color.parseColor("#45475A")
    private val chipColor = Color.parseColor("#45475A")
    private val textPrimary = Color.parseColor("#CDD6F4")
    private val textSecondary = Color.parseColor("#A6ADC8")
    private val accent = Color.parseColor("#CBA6F7")
    private val onAccent = Color.parseColor("#181825")
    private val link = Color.parseColor("#89B4FA")

    private val card = LinearLayout(context)

    private val subtitleView = TextView(context)

    private val scroll = MaxHeightScrollView(context)

    private val content = LinearLayout(context)

    private var parent: FrameLayout? = null

    private var listener: Listener? = null

    private var engine: TranslationEngine? = null

    private var flow: TranslationFlow? = null

    private var targets: List<String> = emptyList()

    private var dismissed = false

    private var tts: TextToSpeech? = null

    private var ttsReady = false

    private var pendingSpeech: Pair<String, String>? = null


    // ---------------------------------------------------------------------
    // Showing and hiding
    // ---------------------------------------------------------------------

    /**
     * @param targets the languages chosen in Settings (must not be empty)
     * @param anchorTop top of the selected text in pixels inside [parent] (NaN if unknown)
     * @param anchorBottom bottom of the selected text, same pixels
     */
    fun show(
        parent: FrameLayout,
        text: String,
        anchorTop: Float,
        anchorBottom: Float,
        engine: TranslationEngine,
        targets: List<String>,
        listener: Listener
    ) {

        this.parent = parent
        this.listener = listener
        this.engine = engine
        this.targets = targets

        buildCard()

        place(parent, anchorTop, anchorBottom)

        val assumed =
            TranslationPrefs.getSourceFallback(context)
                ?: TranslationLanguages.defaultSource(
                    Locale.getDefault().language,
                    engine.supportedLanguages()
                )

        flow = TranslationFlow(
            engine,
            targets,
            TranslationPrefs.getLastTarget(context),
            assumed
        ) { state -> if (!dismissed) render(state) }

        flow?.start(text)
    }

    fun dismiss() {

        if (dismissed) return

        dismissed = true

        flow?.dispose()

        try {
            engine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tts = null

        parent?.removeView(card)

        listener?.onDismissed()

        listener = null
    }


    private fun place(parent: FrameLayout, anchorTop: Float, anchorBottom: Float) {

        val placement = PopupPlacement.compute(
            parent.height.toFloat(),
            anchorTop,
            anchorBottom,
            context.resources.displayMetrics.density
        )

        scroll.maxHeightPx = (placement.maxCardPx - dp(64)).toInt().coerceAtLeast(dp(80))

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (placement.atBottom) Gravity.BOTTOM else Gravity.TOP
        )

        params.setMargins(dp(8), dp(8), dp(8), dp(8))

        parent.addView(card, params)
    }


    // ---------------------------------------------------------------------
    // The card
    // ---------------------------------------------------------------------

    private fun buildCard() {

        card.orientation = LinearLayout.VERTICAL

        card.background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), strokeColor)
        }

        card.elevation = dp(16).toFloat()

        // Swallow touches so scrolling inside never scrolls the book.
        card.isClickable = true

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(4), 0)
        }

        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        titles.addView(
            TextView(context).apply {
                text = "Translate"
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        subtitleView.apply {
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
        }

        titles.addView(subtitleView)

        header.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        header.addView(
            TextView(context).apply {
                text = "\u2715"
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                contentDescription = "Close translation"
                setOnClickListener { dismiss() }
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )

        card.addView(header)

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(4), dp(16), dp(10))

        scroll.addView(content)

        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }


    // ---------------------------------------------------------------------
    // One screen per step of the flow
    // ---------------------------------------------------------------------

    private fun render(state: TranslationFlow.State) {

        content.removeAllViews()

        when (state) {

            is TranslationFlow.State.Detecting -> {

                subtitleView.text = "Detecting language\u2026"

                content.addView(message("Working out which language this is\u2026"))
            }

            is TranslationFlow.State.Translating -> {

                subtitleView.text = route(state.source, state.target)

                content.addView(message("Translating\u2026"))
            }

            is TranslationFlow.State.NeedsDownload -> showNeedsDownload(state)

            is TranslationFlow.State.Downloading -> {

                content.addView(
                    message("Downloading the ${names(state.missing)} language pack\u2026")
                )

                content.addView(small("This can take a minute. It only happens once."))
            }

            is TranslationFlow.State.Done -> showDone(state)

            is TranslationFlow.State.SameLanguage -> {

                subtitleView.text = TranslationLanguages.displayName(state.language)

                content.addView(
                    message(
                        "This text is already in ${TranslationLanguages.displayName(state.language)}. " +
                            "Add another language in Settings to translate it."
                    )
                )

                content.addView(settingsLink())
            }

            is TranslationFlow.State.Failed -> {

                content.addView(message(state.message))

                content.addView(small(state.details))

                content.addView(
                    actionLink("Try again") { flow?.retry() }
                )

                content.addView(settingsLink())
            }
        }
    }

    private fun showNeedsDownload(state: TranslationFlow.State.NeedsDownload) {

        subtitleView.text = route(state.source, state.target)

        // Easter egg: the Hebrew pack shows a message instead of offering a download.
        if (EasterEgg.blocksAny(state.missing)) {

            content.addView(message(EasterEgg.MESSAGE))

            content.addView(chipsRow(state.target))

            content.addView(settingsLink())

            return
        }

        val count = state.missing.size

        content.addView(
            message(
                "To translate offline, this needs the ${names(state.missing)} " +
                    "language pack${if (count > 1) "s" else ""} " +
                    "(about 30 MB each)."
            )
        )

        content.addView(small("Use Wi-Fi if you can. Once downloaded, translating works without internet."))

        content.addView(
            button("Download") { flow?.confirmDownload() }
        )

        content.addView(chipsRow(state.target))

        content.addView(settingsLink())
    }

    private fun showDone(state: TranslationFlow.State.Done) {

        subtitleView.text = route(state.source, state.target)

        TranslationPrefs.setLastTarget(context, state.target)

        // "From: Spanish (detected)" - tap to correct it.
        val fromNote = if (state.sourceAssumed) "assumed" else "detected"

        content.addView(
            TextView(context).apply {
                text = "From: ${TranslationLanguages.displayName(state.source)} ($fromNote)  \u25BE"
                setTextColor(link)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(4), 0, dp(6))
                setOnClickListener { pickSource(state.source) }
            }
        )

        content.addView(
            TextView(context).apply {
                text = state.original
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.ITALIC)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            }
        )

        content.addView(
            TextView(context).apply {
                text = state.translated
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setLineSpacing(0f, 1.15f)
                setTextIsSelectable(true)
                setPadding(0, dp(10), 0, dp(8))
            }
        )

        content.addView(chipsRow(state.target))

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }

        actions.addView(actionLink("Copy") { copy(state.translated) })

        actions.addView(
            actionLink("Listen") { speak(state.translated, state.target) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(20) }
        )

        actions.addView(
            settingsLink(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(20) }
        )

        content.addView(actions)
    }


    // ---------------------------------------------------------------------
    // Pieces
    // ---------------------------------------------------------------------

    /** The languages chosen in Settings, as tappable chips. The current one is filled in. */
    private fun chipsRow(current: String): View {

        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(2))
        }

        targets.forEach { code ->

            val selected = code == current

            strip.addView(
                TextView(context).apply {
                    text = TranslationLanguages.displayName(code)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (selected) onAccent else textPrimary)
                    setPadding(dp(14), dp(7), dp(14), dp(7))
                    background = GradientDrawable().apply {
                        setColor(if (selected) accent else chipColor)
                        cornerRadius = dp(18).toFloat()
                    }
                    setOnClickListener { if (!selected) flow?.changeTarget(code) }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            )
        }

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
    }

    private fun pickSource(current: String) {

        val all = TranslationLanguages.sortedByName(engine?.supportedLanguages() ?: emptyList())

        if (all.isEmpty()) return

        AlertDialog.Builder(context)
            .setTitle("This text is in\u2026")
            .setSingleChoiceItems(
                all.map { TranslationLanguages.label(it) }.toTypedArray(),
                all.indexOf(current)
            ) { dialog, which ->

                dialog.dismiss()

                flow?.changeSource(all[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copy(text: String) {

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

        clipboard?.setPrimaryClip(ClipData.newPlainText("Oghma", text))

        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    /** Reads the translation aloud in its own language, using the phone's voice. */
    private fun speak(text: String, language: String) {

        pendingSpeech = text to language

        if (tts == null) {

            tts = TextToSpeech(context.applicationContext) { status ->

                if (status == TextToSpeech.SUCCESS) {

                    ttsReady = true

                    speakPending()

                } else {

                    Toast.makeText(context, "Text-to-speech isn't available.", Toast.LENGTH_SHORT).show()
                }
            }

            return
        }

        if (ttsReady) speakPending()
    }

    private fun speakPending() {

        val (text, language) = pendingSpeech ?: return

        pendingSpeech = null

        val engine = tts ?: return

        val result = engine.setLanguage(Locale.forLanguageTag(language))

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {

            Toast.makeText(
                context,
                "No ${TranslationLanguages.displayName(language)} voice is installed.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation")
    }

    private fun route(source: String, target: String) =
        "${TranslationLanguages.displayName(source)} \u2192 ${TranslationLanguages.displayName(target)}"

    private fun names(codes: List<String>) =
        codes.joinToString(" and ") { TranslationLanguages.displayName(it) }

    private fun settingsLink(): TextView =
        actionLink("Languages\u2026") { listener?.onOpenSettings() }

    private fun message(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(10), 0, dp(6))
        }

    private fun small(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, dp(6))
        }

    private fun actionLink(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(link)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { onClick() }
        }

    private fun button(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dp(22).toFloat()
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(8) }
        }

    private fun dp(value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)
}
