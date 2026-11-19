package com.reader.oghma.reader

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/** What the popup hears back. All methods are called on the main thread. */
interface DictionaryCallback {

    /** The definitions. Related words may follow a moment later in [onRelated]. */
    fun onResult(result: DictionaryResult)

    /** Words built on the headword (running, runner, runway ...). */
    fun onRelated(word: String, related: List<String>)

    fun onNotFound(query: String)

    /** [details] says exactly what failed, for example "dictionaryapi.dev: HTTP 503". */
    fun onError(message: String, details: String)
}

/** Lets the caller stop waiting (for example when the popup is closed). */
class DictionaryRequest {

    @Volatile
    internal var cancelled = false

    fun cancel() {
        cancelled = true
    }

    val isCancelled: Boolean
        get() = cancelled
}

class HttpResult(val code: Int, val body: String)

/** Does one HTTP GET. Throws [IOException] if the server cannot be reached. */
fun interface HttpFetcher {

    @Throws(IOException::class)
    fun get(url: String, timeoutMs: Int): HttpResult
}

/**
 * Looks words up online, with a plan B.
 *
 *  1. The Free Dictionary API (dictionaryapi.dev): definitions, examples, synonyms, audio.
 *  2. If that does not answer, Datamuse: definitions and pronunciation. It is a different
 *     service run by different people, so one being down does not stop the dictionary.
 *
 * Good manners towards free services: at most four words are tried, a service that just failed is
 * left alone for a while, and related words are fetched on their own threads so they can never hold
 * up the next lookup.
 *
 * No Android code in here, so it can be tested on a computer. [deliver] hands results to the main
 * thread.
 */
class DictionaryService(
    private val http: HttpFetcher,
    private val lookupPool: Executor,
    private val relatedPool: Executor,
    private val deliver: (Runnable) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val retryDelayMs: Long = 500
) {

    private val primaryDownUntil = AtomicLong(0)

    // Recently looked-up words, so tapping the same word again is instant.
    private val cache = object : LinkedHashMap<String, DictionaryResult>(32, 0.75f, true) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DictionaryResult>?): Boolean =
            size > 60
    }


    fun lookup(raw: String, callback: DictionaryCallback): DictionaryRequest {

        val request = DictionaryRequest()

        lookupPool.execute {

            try {

                performLookup(raw, request, callback)

            } catch (e: Exception) {

                post(request) {
                    callback.onError(
                        "Something went wrong looking that up.",
                        e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
                    )
                }
            }
        }

        return request
    }


    // ---------------------------------------------------------------------

    private sealed class Outcome {

        class Found(val result: DictionaryResult) : Outcome()

        object NotFound : Outcome()

        class Failed(val detail: String) : Outcome()
    }


    private fun performLookup(raw: String, request: DictionaryRequest, callback: DictionaryCallback) {

        // A handful of tries is plenty, and keeps us gentle on free servers.
        val candidates = DictionaryParser.candidates(raw).take(MAX_CANDIDATES)

        if (candidates.isEmpty()) {

            post(request) { callback.onNotFound(raw.trim()) }

            return
        }

        val problems = ArrayList<String>()

        var found: DictionaryResult? = null

        // ---- Plan A: the Free Dictionary API ----
        if (clock() >= primaryDownUntil.get()) {

            for (candidate in candidates) {

                if (request.isCancelled) return

                when (val outcome = fetchPrimary(candidate, raw)) {

                    is Outcome.Found -> {

                        found = outcome.result

                        break
                    }

                    is Outcome.NotFound -> Unit      // not a word we know: try the next spelling

                    is Outcome.Failed -> {

                        problems.add(outcome.detail)

                        // Leave this service alone for a bit, so the next lookups go straight to plan B.
                        primaryDownUntil.set(clock() + COOL_DOWN_MS)

                        break
                    }
                }
            }

        } else {

            problems.add("dictionaryapi.dev was skipped (it failed a moment ago)")
        }

        // ---- Plan B: Datamuse ----
        if (found == null) {

            for (candidate in candidates.take(2)) {

                if (request.isCancelled) return

                when (val outcome = fetchDatamuse(candidate, raw)) {

                    is Outcome.Found -> {

                        found = outcome.result

                        break
                    }

                    is Outcome.NotFound -> Unit

                    is Outcome.Failed -> {

                        problems.add(outcome.detail)

                        break
                    }
                }
            }
        }

        if (request.isCancelled) return

        val result = found

        when {

            result != null -> {

                val shown = result.copy(searched = raw.trim())

                post(request) { callback.onResult(shown) }

                relatedPool.execute { loadRelated(shown, request, callback) }
            }

            // Nothing found and at least one service did not answer: do not claim the word is
            // missing, say what went wrong.
            problems.isNotEmpty() -> {

                post(request) {
                    callback.onError(
                        "Couldn't reach the dictionary right now.",
                        problems.joinToString("\n")
                    )
                }
            }

            else -> post(request) { callback.onNotFound(candidates.first()) }
        }
    }


    private fun fetchPrimary(word: String, searched: String): Outcome {

        val key = "p:$word"

        synchronized(cache) { cache[key] }?.let { return Outcome.Found(it) }

        val url = PRIMARY_URL + encode(word)

        var attempt = 0

        while (true) {

            attempt++

            val response =
                try {
                    http.get(url, PRIMARY_TIMEOUT_MS)
                } catch (e: IOException) {
                    return Outcome.Failed("dictionaryapi.dev: ${describe(e)}")
                }

            when {

                response.code == 404 -> return Outcome.NotFound

                // Busy or hiccupping: wait a moment and try once more.
                (response.code == 429 || response.code in 500..599) && attempt < 2 -> {

                    sleep(retryDelayMs)

                    continue
                }

                response.code !in 200..299 ->
                    return Outcome.Failed("dictionaryapi.dev: HTTP ${response.code}")

                else -> {

                    val parsed = DictionaryParser.parseDictionary(response.body, searched)
                        ?: return Outcome.NotFound

                    synchronized(cache) { cache[key] = parsed }

                    primaryDownUntil.set(0)

                    return Outcome.Found(parsed)
                }
            }
        }
    }


    private fun fetchDatamuse(word: String, searched: String): Outcome {

        val key = "d:$word"

        synchronized(cache) { cache[key] }?.let { return Outcome.Found(it) }

        val url = DATAMUSE_URL + "words?sp=" + encode(word) + "&qe=sp&md=dr&ipa=1&max=1"

        val response =
            try {
                http.get(url, FALLBACK_TIMEOUT_MS)
            } catch (e: IOException) {
                return Outcome.Failed("datamuse.com: ${describe(e)}")
            }

        if (response.code !in 200..299) return Outcome.Failed("datamuse.com: HTTP ${response.code}")

        val parsed = DictionaryParser.parseDatamuseDefinitions(response.body, searched, word)
            ?: return Outcome.NotFound

        synchronized(cache) { cache[key] = parsed }

        return Outcome.Found(parsed)
    }


    /** Related words are a bonus: if this fails, nothing is shown and nothing breaks. */
    private fun loadRelated(result: DictionaryResult, request: DictionaryRequest, callback: DictionaryCallback) {

        try {

            val base = result.baseForm ?: result.word

            if (base.length < 3 || base.contains(' ') || request.isCancelled) return

            val url = DATAMUSE_URL + "words?max=40&sp=" +
                encode(DictionaryParser.relatedPrefix(base) + "*")

            val response = http.get(url, RELATED_TIMEOUT_MS)

            if (response.code !in 200..299) return

            val related = DictionaryParser.parseRelated(response.body, base, result.word)

            if (related.isNotEmpty()) {

                post(request) { callback.onRelated(result.word, related) }
            }

        } catch (e: Exception) {
            // ignore: related words are optional
        }
    }


    private fun describe(e: IOException): String =
        e.javaClass.simpleName.ifEmpty { "network error" }

    private fun encode(text: String): String =
        URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun post(request: DictionaryRequest, action: () -> Unit) {

        deliver(Runnable { if (!request.isCancelled) action() })
    }


    companion object {

        const val PRIMARY_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/"

        const val DATAMUSE_URL = "https://api.datamuse.com/"

        private const val MAX_CANDIDATES = 4

        private const val PRIMARY_TIMEOUT_MS = 7000

        private const val FALLBACK_TIMEOUT_MS = 7000

        private const val RELATED_TIMEOUT_MS = 4000

        /** After a failure, skip the main service for this long. */
        private const val COOL_DOWN_MS = 45_000L
    }
}
