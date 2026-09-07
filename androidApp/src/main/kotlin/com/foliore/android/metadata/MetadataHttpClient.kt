package com.foliore.android.metadata

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.util.LinkedHashMap
import com.foliore.android.storage.readBounded

/** Per-operation deadline; successful JSON responses are cached, errors are never cached. */
internal class MetadataHttpClient(timeoutMillis: Long = 18_000) {
    private val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    fun remainingMillis(): Long {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        return ((deadline - System.nanoTime()) / 1_000_000).also { if (it <= 0) throw SocketTimeoutException("Metadata lookup timed out") }
    }

    fun getText(url: String): String {
        remainingMillis()
        synchronized(cache) { cache[url]?.takeIf { it.expires > System.nanoTime() }?.let { return it.text } }
        var attempt = 0
        while (true) {
            remainingMillis()
            if (URL(url).host == "openlibrary.org") throttleOpenLibrary()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = minOf(6_000L, remainingMillis()).toInt()
                connection.readTimeout = minOf(10_000L, remainingMillis()).toInt()
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Foliore/0.1 (metadata lookup)")
                val code = connection.responseCode
                if (code !in 200..299) {
                    val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                    if (attempt++ == 0 && code in setOf(429, 502, 503, 504) && (retryAfter == null || retryAfter <= 2)) {
                        val delay = ((retryAfter ?: 1) * 1000).coerceAtLeast(1000)
                        if (remainingMillis() > delay + 1000) { connection.disconnect(); Thread.sleep(delay); continue }
                    }
                    throw MetadataHttpException(code)
                }
                val text = connection.inputStream.use { source ->
                    object : java.io.FilterInputStream(source) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            remainingMillis()
                            return super.read(buffer, offset, length).also { remainingMillis() }
                        }
                    }.readBounded(2L * 1024 * 1024).toString(Charsets.UTF_8)
                }
                remainingMillis()
                synchronized(cache) {
                    cache[url] = Cached(text, System.nanoTime() + 15L * 60 * 1_000_000_000)
                    while (cache.size > 24) cache.remove(cache.keys.first())
                }
                return text
            } finally { connection.disconnect() }
        }
    }

    private fun throttleOpenLibrary() {
        synchronized(rateLock) {
            val delay = (nextOpenLibraryRequest - System.nanoTime()) / 1_000_000
            if (delay > 0) { if (delay >= remainingMillis()) throw SocketTimeoutException(); Thread.sleep(delay) }
            remainingMillis()
            nextOpenLibraryRequest = System.nanoTime() + 1_050_000_000
        }
    }
    private data class Cached(val text: String, val expires: Long)
    companion object {
        private val cache = LinkedHashMap<String, Cached>(24, 0.75f, true)
        private val rateLock = Any()
        private var nextOpenLibraryRequest = 0L
    }
}
internal class MetadataHttpException(val status: Int) : IOException("Metadata provider returned HTTP $status")
