package com.reader.oghma.reader

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Renders a PDF with PDF.js inside a WebView.
 *
 * Readium's PDFium navigator only draws bitmaps, so PDF text could not be selected or
 * highlighted. PDF.js gives us a real text layer, which makes selection work and lets us
 * draw highlights. See assets/pdfjs/viewer.js for the page side of the bridge.
 *
 * Nothing is loaded from the network: the viewer assets and the PDF itself are served
 * from inside the app through [WebViewClient.shouldInterceptRequest], and every other
 * request is blocked.
 */
@SuppressLint("SetJavaScriptEnabled")
class PdfReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onReady(pageCount: Int)

        /** [page] is 1-based, [pageFraction] is 0..1 inside that page. */
        fun onProgress(page: Int, pageFraction: Float, pageCount: Int)

        /**
         * The user highlighted text. [locatorJson] is stored as-is in Bookmark.locatorJson.
         *
         * If [withNote] is false the viewer already drew the highlight in [colorHex]; just save it.
         * If [withNote] is true nothing is drawn yet: ask the user for colour + note, save, then
         * call [addHighlight].
         */
        fun onHighlight(
            locatorJson: String,
            text: String,
            page: Int,
            colorHex: String,
            withNote: Boolean
        )

        fun onError(message: String)

        /** Read-aloud state: [active] while the reader is on, [playing] while it is speaking. */
        fun onTtsState(active: Boolean, playing: Boolean)

        fun onTtsError(message: String)

        /** "Play" was tapped in the selection bar: start reading aloud from the selected text. */
        fun onTtsRequested()

        /**
         * "Dictionary" was tapped. [topFraction] and [bottomFraction] say where the selected text
         * is, as fractions (0..1) of the view's height, so the popup can stay clear of it.
         */
        fun onDefineRequested(text: String, topFraction: Double, bottomFraction: Double)

        /** "Translate" was tapped. The fractions work as for [onDefineRequested]. */
        fun onTranslateRequested(text: String, topFraction: Double, bottomFraction: Double)
    }

    private val webView = WebView(context)

    private var listener: Listener? = null
    private var pdfFile: File? = null
    private var highlightsJson: String = "[]"
    private var destroyed = false

    // Read aloud (text-to-speech). The page decides what to read, this speaks it.
    private var ttsEngine: TtsEngineHelper? = null

    private var ttsActive = false

    private var ttsSpeed = 1.0

    private val ttsCallbacks = object : TtsEngineHelper.Callbacks {

        override fun onUtteranceStart(id: String) = runJs("oghma.ttsOnStart(${JSONObject.quote(id)})")

        override fun onUtteranceDone(id: String) = runJs("oghma.ttsOnDone(${JSONObject.quote(id)})")

        override fun onUtteranceError(id: String) = runJs("oghma.ttsOnError(${JSONObject.quote(id)})")
    }

    init {
        addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        webView.setBackgroundColor(Color.parseColor("#181825"))

        webView.settings.apply {
            javaScriptEnabled = true
            // localStorage: the viewer remembers Text/Page view and the text size.
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = false
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {

                val uri = request.url

                if (uri.scheme != "https" || uri.host != HOST) {
                    return blocked()
                }

                return serve(uri.path ?: "", request.requestHeaders)
            }

            // Never let the viewer navigate away from our own pages.
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = request.url.host != HOST
        }

        webView.addJavascriptInterface(JsBridge(), BRIDGE_NAME)
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * @param startPage 1-based page to open at
     * @param startFraction 0..1 position inside that page
     * @param highlightLocators saved PDF highlight locators (see viewer.js for the format)
     */
    fun open(
        file: File,
        startPage: Int,
        startFraction: Float,
        highlightLocators: List<String>,
        listener: Listener
    ) {
        this.listener = listener
        this.pdfFile = file

        highlightsJson = highlightLocators
            .filter { it.isNotBlank() }
            .joinToString(prefix = "[", postfix = "]", separator = ",")

        // Same palette as the EPUB reader and the Notes list.
        val colors = HighlightColors.ALL.joinToString(",") {
            String.format("%06X", it and 0xFFFFFF)
        }

        val url = "https://$HOST/pdfjs/viewer.html" +
                "?file=%2Fbook.pdf" +
                "&page=${startPage.coerceAtLeast(1)}" +
                "&y=${startFraction.coerceIn(0f, 1f)}" +
                "&bg=181825" +
                "&colors=$colors"

        webView.loadUrl(url)
    }

    /** Draws a highlight that was just saved (used after the note dialog). */
    fun addHighlight(locatorJson: String, colorHex: String) {
        if (destroyed) return
        webView.evaluateJavascript(
            "window.oghma && window.oghma.addHighlight(" +
                    locatorJson + "," + JSONObject.quote(colorHex) + ");",
            null
        )
    }

    // ---------------------------------------------------------------------
    // Read aloud
    // ---------------------------------------------------------------------

    /** Starts reading from the top of the screen. Switches to Text view if needed. */
    fun startTts(speed: Double, voiceName: String?) {

        ttsSpeed = speed

        val helper = ttsEngine
            ?: TtsEngineHelper(context, ttsCallbacks).also { ttsEngine = it }

        helper.voiceName = voiceName

        helper.setRate(speed.toFloat())

        helper.prepare { ok, message ->

            if (destroyed) return@prepare

            if (!ok) {
                listener?.onTtsError(message ?: "Text-to-speech isn't available.")
                listener?.onTtsState(false, false)
                return@prepare
            }

            runJs("oghma.ttsStart()")
        }
    }

    /** Removes the mark shown on the word that was looked up (dictionary or translation). */
    fun clearLookup() = runJs("oghma.clearLookup()")

    /** Jumps to a point in the document, 0.0 (start) .. 1.0 (end). Reading follows if it is on. */
    fun seekTo(fraction: Double) {
        runJs("oghma.seekFraction(${fraction.coerceIn(0.0, 1.0)})")
    }

    fun pauseTts() = runJs("oghma.ttsPause()")

    fun resumeTts() = runJs("oghma.ttsResume()")

    fun nextTts() = runJs("oghma.ttsNext()")

    fun previousTts() = runJs("oghma.ttsPrevious()")

    fun stopTts() = runJs("oghma.ttsStop()")

    /** Changes the voice while reading; the current sentence restarts with it. */
    fun setTtsVoice(voiceName: String?) {

        ttsEngine?.setVoice(voiceName)

        runJs("oghma.ttsRateChanged()")
    }

    fun setTtsSpeed(speed: Double) {

        ttsSpeed = speed

        ttsEngine?.setRate(speed.toFloat())

        runJs("oghma.ttsRateChanged()")
    }

    private fun runJs(code: String) {

        if (destroyed) return

        webView.evaluateJavascript("window.oghma && $code;", null)
    }

    /** Ask the page to report the current position right now (call from onPause). */
    fun flushProgress() {
        if (destroyed) return
        webView.evaluateJavascript(
            "window.oghma && window.oghma.flushProgress && window.oghma.flushProgress();",
            null
        )
    }

    fun onPause() {
        flushProgress()
        // A paused web view stops running page scripts; keep it going while reading aloud.
        if (!ttsActive) {
            webView.onPause()
        }
    }

    fun onResume() {
        webView.onResume()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        ttsEngine?.shutdown()
        ttsEngine = null
        listener = null
        pdfFile = null
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        removeView(webView)
        webView.destroy()
    }

    // ---------------------------------------------------------------------
    // JS -> Android
    // ---------------------------------------------------------------------

    /** Methods here run on a WebView binder thread, so hop to the UI thread. */
    private inner class JsBridge {

        @JavascriptInterface
        fun getHighlights(): String = highlightsJson

        @JavascriptInterface
        fun onReady(pageCount: Int) {
            post { listener?.onReady(pageCount) }
        }

        @JavascriptInterface
        fun onProgress(page: Int, fraction: Double, pageCount: Int) {
            post { listener?.onProgress(page, fraction.toFloat(), pageCount) }
        }

        @JavascriptInterface
        fun onHighlight(
            locatorJson: String,
            text: String,
            page: Int,
            colorHex: String,
            withNote: Boolean
        ) {
            post { listener?.onHighlight(locatorJson, text, page, colorHex, withNote) }
        }

        @JavascriptInterface
        fun onCopy(text: String) {
            post {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Oghma", text))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            post { listener?.onError(message) }
        }

        // ---- read aloud ----

        @JavascriptInterface
        fun ttsSpeak(text: String, id: String) {
            post { ttsEngine?.speak(text, id) }
        }

        @JavascriptInterface
        fun ttsHalt() {
            post { ttsEngine?.halt() }
        }

        @JavascriptInterface
        fun onTtsState(active: Boolean, playing: Boolean) {
            post {
                ttsActive = active
                listener?.onTtsState(active, playing)
            }
        }

        @JavascriptInterface
        fun onTtsError(message: String) {
            post { listener?.onTtsError(message) }
        }

        @JavascriptInterface
        fun onTtsRequested() {
            post { listener?.onTtsRequested() }
        }

        @JavascriptInterface
        fun onDefine(text: String, topFraction: Double, bottomFraction: Double) {
            post { listener?.onDefineRequested(text, topFraction, bottomFraction) }
        }

        @JavascriptInterface
        fun onTranslate(text: String, topFraction: Double, bottomFraction: Double) {
            post { listener?.onTranslateRequested(text, topFraction, bottomFraction) }
        }
    }

    // ---------------------------------------------------------------------
    // Local "server": viewer assets + the PDF file
    // ---------------------------------------------------------------------

    private fun serve(path: String, headers: Map<String, String>): WebResourceResponse {

        if (path.contains("..")) return blocked()

        if (path == "/book.pdf") {
            val file = pdfFile
            return if (file != null && file.exists()) {
                servePdf(file, headers)
            } else {
                notFound()
            }
        }

        if (path.startsWith("/pdfjs/")) {
            return try {
                val stream = context.assets.open(path.removePrefix("/"))
                val mime = mimeFor(path)
                val encoding = if (mime.startsWith("text/") || mime.endsWith("javascript") ||
                    mime.endsWith("json")) "utf-8" else null
                WebResourceResponse(mime, encoding, stream)
            } catch (e: IOException) {
                notFound()
            }
        }

        return notFound()
    }

    /**
     * Serves the PDF, honouring HTTP Range requests so PDF.js can fetch only the parts
     * it needs instead of reading the whole file into memory.
     */
    private fun servePdf(file: File, headers: Map<String, String>): WebResourceResponse {

        val length = file.length()
        val rangeHeader = headers.entries
            .firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value

        val baseHeaders = mutableMapOf(
            "Accept-Ranges" to "bytes",
            "Cache-Control" to "no-store"
        )

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {

            val spec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
            val dash = spec.indexOf('-')

            if (dash >= 0) {
                var start = spec.substring(0, dash).toLongOrNull()
                var end = spec.substring(dash + 1).toLongOrNull()

                if (start == null && end != null) {
                    // Suffix range: the last N bytes.
                    start = (length - end).coerceAtLeast(0)
                    end = length - 1
                } else {
                    if (start == null) start = 0
                    if (end == null || end >= length) end = length - 1
                }

                if (start >= length || start > end) {
                    baseHeaders["Content-Range"] = "bytes */$length"
                    return WebResourceResponse(
                        "application/pdf", null, 416, "Range Not Satisfiable",
                        baseHeaders, ByteArrayInputStream(ByteArray(0))
                    )
                }

                val count = end - start + 1
                val input = FileInputStream(file)
                input.channel.position(start)

                baseHeaders["Content-Range"] = "bytes $start-$end/$length"
                baseHeaders["Content-Length"] = count.toString()

                return WebResourceResponse(
                    "application/pdf", null, 206, "Partial Content",
                    baseHeaders, LimitedInputStream(input, count)
                )
            }
        }

        baseHeaders["Content-Length"] = length.toString()

        return WebResourceResponse(
            "application/pdf", null, 200, "OK",
            baseHeaders, FileInputStream(file)
        )
    }

    private fun blocked() = WebResourceResponse(
        "text/plain", "utf-8", 403, "Forbidden",
        emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    private fun notFound() = WebResourceResponse(
        "text/plain", "utf-8", 404, "Not Found",
        emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    private fun mimeFor(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream" // .bcmap, .pfb, .ttf ...
        }

    /** Stops reading after [remaining] bytes (needed for 206 responses). */
    private class LimitedInputStream(
        input: InputStream,
        private var remaining: Long
    ) : FilterInputStream(input) {

        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = super.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(minOf(n, remaining))
            if (skipped > 0) remaining -= skipped
            return skipped
        }

        override fun available(): Int =
            minOf(super.available().toLong(), remaining).toInt()

        override fun markSupported(): Boolean = false
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val BRIDGE_NAME = "OghmaPdf"
    }
}
