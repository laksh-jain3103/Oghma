package com.reader.oghma.reader

import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * The Android side of the dictionary: real HTTP requests and real threads. The actual plan (which
 * service to ask, what to do when one fails) lives in [DictionaryService].
 *
 * Needs no account or API key. Everything happens on background threads; the callbacks arrive on
 * the main thread.
 */
object DictionaryClient {

    private val main = Handler(Looper.getMainLooper())

    private val daemonThreads = ThreadFactory { task ->

        Thread(task, "oghma-dictionary").apply { isDaemon = true }
    }

    private val service = DictionaryService(
        http = UrlFetcher,
        // Lookups and related words have their own, growing pools: a slow related-words request
        // can never hold up the next lookup.
        lookupPool = Executors.newCachedThreadPool(daemonThreads),
        relatedPool = Executors.newCachedThreadPool(daemonThreads),
        deliver = { action -> main.post(action) }
    )

    fun lookup(raw: String, callback: DictionaryCallback): DictionaryRequest =
        service.lookup(raw, callback)


    private object UrlFetcher : HttpFetcher {

        @Throws(IOException::class)
        override fun get(url: String, timeoutMs: Int): HttpResult {

            val connection = URL(url).openConnection() as HttpURLConnection

            try {

                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.requestMethod = "GET"
                connection.useCaches = false
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "OghmaReader/1.0 (Android)")
                // A fresh connection each time: no chance of reusing one the server has dropped.
                connection.setRequestProperty("Connection", "close")

                val code = connection.responseCode

                val stream = if (code in 200..299) connection.inputStream else connection.errorStream

                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                return HttpResult(code, body)

            } finally {

                connection.disconnect()
            }
        }
    }
}
