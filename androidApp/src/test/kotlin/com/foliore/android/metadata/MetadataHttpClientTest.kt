package com.foliore.android.metadata

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class MetadataHttpClientTest {
    @Test fun cachesSuccessButRetriesTransientErrorsAndNeverCachesFailure() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/lookup") { exchange ->
            val number = calls.incrementAndGet()
            val text = if (number == 1) "unavailable" else "{\"docs\":[]}"
            exchange.sendResponseHeaders(if (number == 1) 503 else 200, text.length.toLong())
            exchange.responseBody.use { it.write(text.toByteArray()) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/lookup"
            assertEquals("{\"docs\":[]}", MetadataHttpClient(4000).getText(url))
            assertEquals("{\"docs\":[]}", MetadataHttpClient(4000).getText(url))
            assertEquals(2, calls.get())
        } finally { server.stop(0) }
    }
    @Test fun respectsLongRetryAfterAndRejectsOversizedResponse() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/limited") { exchange ->
            calls.incrementAndGet(); exchange.responseHeaders.add("Retry-After", "120")
            exchange.sendResponseHeaders(429, -1); exchange.close()
        }
        server.createContext("/large") { exchange ->
            val bytes = ByteArray(2 * 1024 * 1024 + 1)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            runCatching { exchange.responseBody.use { it.write(bytes) } }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(429, assertFailsWith<MetadataHttpException> { MetadataHttpClient().getText("$base/limited") }.status)
            assertEquals(1, calls.get())
            assertFailsWith<IllegalArgumentException> { MetadataHttpClient().getText("$base/large") }
        } finally { server.stop(0) }
    }
}
