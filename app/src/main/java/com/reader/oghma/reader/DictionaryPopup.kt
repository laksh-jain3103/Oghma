package com.reader.oghma.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * The dictionary card shown over the reader when you tap "Dictionary" on selected text.
 *
 * It sits in the half of the screen that does NOT hold the selected word, so the line you are
 * reading stays visible. It shows pronunciation (with a button to hear it), meanings by part of
 * speech with examples, synonyms and antonyms, the base form of inflected words, and words built
 * on the word. Tap any of those words to look it up in turn.
 */
class DictionaryPopup(private val context: Context) {

    interface Listener {

        fun onDismissed()
    }

    // Matches the app's dark theme.
    private val cardColor = Color.parseColor("#313244")
    private val strokeColor = Color.parseColor("#45475A")
    private val textPrimary = Color.parseColor("#CDD6F4")
    private val textSecondary = Color.parseColor("#A6ADC8")
    private val accent = Color.parseColor("#CBA6F7")
    private val link = Color.parseColor("#89B4FA")

    private val card = LinearLayout(context)

    private val titleView = TextView(context)

    private val phoneticView = TextView(context)

    private val backButton = TextView(context)

    private val speakerButton = ImageView(context)

    private val scroll = MaxHeightScrollView(context)

    private val content = LinearLayout(context)

    private val relatedHolder = LinearLayout(context)

    private var parent: FrameLayout? = null

    private var listener: Listener? = null

    private var request: DictionaryRequest? = null

    private var current: DictionaryResult? = null

    private val history = ArrayList<String>()

    private var currentQuery = ""

    private var dismissed = false

    private var player: MediaPlayer? = null

    private var tts: TextToSpeech? = null

    private var ttsReady = false

    private var wordToSpeak: String? = null


    // ---------------------------------------------------------------------
    // Showing and hiding
    // ---------------------------------------------------------------------

    /**
     * @param anchorTop top of the selected text, in pixels inside [parent] (NaN if unknown)
     * @param anchorBottom bottom of the selected text, in the same pixels
     */
    fun show(
        parent: FrameLayout,
        query: String,
        anchorTop: Float,
        anchorBottom: Float,
        listener: Listener
    ) {

        this.parent = parent
        this.listener = listener

        buildCard()

        place(parent, anchorTop, anchorBottom)

        lookup(query)
    }

    fun dismiss() {

        if (dismissed) return

        dismissed = true

        request?.cancel()

        stopAudio()

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


    /** Puts the card on the side of the selected word that has more room. */
    private fun place(parent: FrameLayout, anchorTop: Float, anchorBottom: Float) {

        val placement = PopupPlacement.compute(
            parent.height.toFloat(),
            anchorTop,
            anchorBottom,
            context.resources.displayMetrics.density
        )

        // The scrolling part gets what is left after the header and footer.
        scroll.maxHeightPx = (placement.maxCardPx - dp(112)).toInt().coerceAtLeast(dp(80))

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            if (placement.atBottom) Gravity.BOTTOM else Gravity.TOP
        )

        params.setMargins(dp(8), dp(8), dp(8), dp(8))

        parent.addView(card, params)
    }


    // ---------------------------------------------------------------------
    // Building the card
    // ---------------------------------------------------------------------

    private fun buildCard() {

        card.orientation = LinearLayout.VERTICAL

        card.background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), strokeColor)
        }

        card.elevation = dp(16).toFloat()

        // The card swallows touches so scrolling inside it never scrolls the book.
        card.isClickable = true

        // ---- header ----
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(4), 0)
        }

        backButton.apply {
            text = "\u2190"
            setTextColor(link)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            contentDescription = "Back"
            setOnClickListener { goBack() }
        }

        header.addView(backButton, LinearLayout.LayoutParams(dp(40), dp(44)))

        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }

        titleView.apply {
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }

        phoneticView.apply {
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
        }

        titles.addView(titleView)
        titles.addView(phoneticView)

        header.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        speakerButton.apply {
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            setColorFilter(accent)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "Hear the pronunciation"
            visibility = View.INVISIBLE
            setOnClickListener { pronounce() }
        }

        header.addView(speakerButton, LinearLayout.LayoutParams(dp(46), dp(46)))

        val close = TextView(context).apply {
            text = "\u2715"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            contentDescription = "Close dictionary"
            setOnClickListener { dismiss() }
        }

        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))

        card.addView(header)

        // ---- scrolling content ----
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(4), dp(16), dp(8))

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
    // Looking things up
    // ---------------------------------------------------------------------

    private fun lookup(query: String) {

        request?.cancel()

        stopAudio()

        currentQuery = query

        current = null

        titleView.text = query.trim()

        phoneticView.text = ""

        speakerButton.visibility = View.INVISIBLE

        backButton.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE

        content.removeAllViews()

        content.addView(message("Looking up\u2026"))

        request = DictionaryClient.lookup(
            query,
            object : DictionaryCallback {

                override fun onResult(result: DictionaryResult) {

                    if (!dismissed) showResult(result)
                }

                override fun onRelated(word: String, related: List<String>) {

                    if (!dismissed && current?.word == word) showRelated(related)
                }

                override fun onNotFound(query: String) {

                    if (!dismissed) showNotFound(query)
                }

                override fun onError(message: String, details: String) {

                    if (!dismissed) showError(message, details)
                }
            }
        )
    }

    /** A tapped word inside the popup: look it up, and remember where we came from. */
    private fun lookupFromPopup(word: String) {

        if (currentQuery.isNotBlank()) history.add(currentQuery)

        lookup(word)
    }

    private fun goBack() {

        if (history.isEmpty()) return

        lookup(history.removeAt(history.size - 1))
    }


    // ---------------------------------------------------------------------
    // Showing results
    // ---------------------------------------------------------------------

    private fun showResult(result: DictionaryResult) {

        current = result

        titleView.text = result.word

        val phonetic = result.phonetic

        phoneticView.text =
            when {
                phonetic == null -> ""
                phonetic.startsWith("/") || phonetic.startsWith("[") -> phonetic
                else -> "/$phonetic/"
            }

        // Real recording if there is one, otherwise the phone's voice says the word.
        speakerButton.visibility = View.VISIBLE

        content.removeAllViews()

        val searched = result.searched.trim().lowercase()

        if (searched.isNotEmpty() && searched != result.word.lowercase() && !searched.contains(' ')) {

            content.addView(
                small("Showing \u201C${result.word}\u201D for \u201C${result.searched.trim()}\u201D")
            )
        }

        result.baseForm?.let { base ->

            content.addView(wordsLine("Base form", listOf(base)))
        }

        result.entries.forEach { entry ->

            if (entry.partOfSpeech.isNotEmpty()) {

                content.addView(
                    TextView(context).apply {
                        text = entry.partOfSpeech
                        setTextColor(accent)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTypeface(typeface, Typeface.BOLD_ITALIC)
                        setPadding(0, dp(12), 0, dp(2))
                    }
                )
            }

            entry.definitions.take(4).forEachIndexed { i, definition ->

                content.addView(definitionView(i + 1, definition))
            }

            if (entry.synonyms.isNotEmpty()) {

                content.addView(wordsLine("Synonyms", entry.synonyms.take(6)))
            }

            if (entry.antonyms.isNotEmpty()) {

                content.addView(wordsLine("Antonyms", entry.antonyms.take(6)))
            }
        }

        result.origin?.let { origin ->

            content.addView(
                TextView(context).apply {
                    text = "Origin  $origin"
                    setTextColor(textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(12), 0, 0)
                }
            )
        }

        // Filled in when the related words arrive.
        relatedHolder.removeAllViews()

        (relatedHolder.parent as? ViewGroup)?.removeView(relatedHolder)

        content.addView(relatedHolder)

        if (result.related.isNotEmpty()) showRelated(result.related)

        content.addView(footer(result))

        scroll.scrollTo(0, 0)
    }

    private fun showRelated(related: List<String>) {

        relatedHolder.removeAllViews()

        if (related.isEmpty()) return

        relatedHolder.addView(wordsLine("Related words", related))
    }

    private fun showNotFound(query: String) {

        content.removeAllViews()

        phoneticView.text = ""

        content.addView(message("No definition found for \u201C$query\u201D."))

        content.addView(
            actionLink("Search the web for it") {
                openUrl("https://www.google.com/search?q=" + Uri.encode("define $query"))
            }
        )
    }

    private fun showError(message: String, details: String) {

        content.removeAllViews()

        content.addView(message(message))

        // Exactly what failed, so a problem can be understood instead of guessed at.
        if (details.isNotBlank()) {

            content.addView(
                TextView(context).apply {
                    text = details
                    setTextColor(textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    alpha = 0.85f
                    setPadding(0, 0, 0, dp(6))
                }
            )
        }

        content.addView(actionLink("Try again") { lookup(currentQuery) })

        content.addView(
            actionLink("Look it up on Wiktionary \u2197") {
                openUrl("https://en.wiktionary.org/wiki/" + Uri.encode(currentQuery.trim().lowercase()))
            }
        )
    }


    // ---------------------------------------------------------------------
    // Small view helpers
    // ---------------------------------------------------------------------

    private fun definitionView(number: Int, definition: Definition): View {

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }

        box.addView(
            TextView(context).apply {
                text = "$number.  ${definition.text}"
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.1f)
            }
        )

        definition.example?.let { example ->

            box.addView(
                TextView(context).apply {
                    text = "\u201C$example\u201D"
                    setTextColor(textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(typeface, Typeface.ITALIC)
                    setPadding(dp(18), dp(2), 0, 0)
                }
            )
        }

        return box
    }

    /** "Synonyms  glad · joyful · cheerful" where every word can be tapped. */
    private fun wordsLine(label: String, words: List<String>): TextView {

        val text = SpannableStringBuilder("$label  ")

        text.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        text.setSpan(
            ForegroundColorSpan(textSecondary),
            0,
            label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        words.forEachIndexed { i, word ->

            val start = text.length

            text.append(word)

            text.setSpan(
                object : ClickableSpan() {

                    override fun onClick(widget: View) {
                        lookupFromPopup(word)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = link
                        ds.isUnderlineText = false
                    }
                },
                start,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            if (i < words.lastIndex) text.append("  \u00B7  ")
        }

        return TextView(context).apply {
            setText(text)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(8), 0, 0)
        }
    }

    private fun footer(result: DictionaryResult): View {

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(6))
        }

        box.addView(
            actionLink("Open the full entry \u2197") {
                openUrl("https://en.wiktionary.org/wiki/" + Uri.encode(result.word))
            }
        )

        val credit =
            if (result.source == DictionaryParser.SOURCE_DATAMUSE) {

                "Definitions and pronunciation: Datamuse (Wiktionary and WordNet). " +
                    "The main dictionary service did not answer, so this is the backup."

            } else {

                val license = result.license ?: "CC BY-SA 3.0"

                "Definitions: Wiktionary ($license), via the Free Dictionary API. " +
                    "Related words: Datamuse."
            }

        box.addView(
            TextView(context).apply {
                text = credit
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                alpha = 0.8f
                setPadding(0, dp(6), 0, 0)
            }
        )

        return box
    }

    private fun message(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(12), 0, dp(8))
        }

    private fun small(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(2))
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

    private fun openUrl(url: String) {

        try {

            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dp(value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)


    // ---------------------------------------------------------------------
    // Pronunciation
    // ---------------------------------------------------------------------

    private fun pronounce() {

        val result = current ?: return

        stopAudio()

        val url = result.audioUrl

        if (url == null) {

            speakWithPhone(result.word)

            return
        }

        try {

            val mp = MediaPlayer()

            player = mp

            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            mp.setDataSource(url)

            mp.setOnPreparedListener { it.start() }

            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }

            mp.setOnErrorListener { failed, _, _ ->

                failed.release()

                if (player === failed) player = null

                // The recording could not be played: fall back to the phone's own voice.
                speakWithPhone(result.word)

                true
            }

            mp.prepareAsync()

        } catch (e: Exception) {

            player = null

            speakWithPhone(result.word)
        }
    }

    private fun speakWithPhone(word: String) {

        wordToSpeak = word

        val engine = tts

        if (engine == null) {

            tts = TextToSpeech(context.applicationContext) { status ->

                if (status == TextToSpeech.SUCCESS) {

                    ttsReady = true

                    tts?.setLanguage(Locale.ENGLISH)

                    speakPending()
                }
            }

            return
        }

        if (ttsReady) speakPending()
    }

    private fun speakPending() {

        val word = wordToSpeak ?: return

        wordToSpeak = null

        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "dictionary-word")
    }

    private fun stopAudio() {

        try {
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        player = null
    }


    // ---------------------------------------------------------------------
}
