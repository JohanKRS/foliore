package com.foliore.android.ai

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One-shot HTTP listener on 127.0.0.1 that receives the OAuth redirect from the browser.
 * The docs require the loopback address (never "localhost") and the same port for the
 * authorization request and the code exchange.
 */
internal class ChatGptLoopbackServer private constructor(private val socket: ServerSocket) : Closeable {
    val port: Int get() = socket.localPort

    /**
     * Serves requests until [handle] returns a non-null result for one of them. Requests that
     * [handle] rejects get [CallbackPage.ignored] and do not end the wait.
     */
    suspend fun <T : Any> awaitCallback(
        timeoutMillis: Long,
        handle: (path: String, query: Map<String, String>) -> Pair<T, CallbackPage>?,
    ): T? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        socket.soTimeout = ACCEPT_POLL_MILLIS
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val client = try {
                socket.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (error: IOException) {
                if (socket.isClosed) return null else throw error
            }
            client.use { connection ->
                val target = runCatching { readRequestTarget(connection) }.getOrNull()
                if (target == null) {
                    respond(connection, 400, CallbackPage.ignored)
                    return@use
                }
                val path = target.substringBefore('?')
                val query = ChatGptOAuth.parseQuery(target.substringAfter('?', ""))
                val result = runCatching { handle(path, query) }.getOrNull()
                if (result == null) {
                    respond(connection, if (path == ChatGptOAuth.CALLBACK_PATH) 400 else 404, CallbackPage.ignored)
                } else {
                    respond(connection, 200, result.second)
                    return result.first
                }
            }
        }
        return null
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun readRequestTarget(connection: Socket): String? {
        connection.soTimeout = READ_TIMEOUT_MILLIS
        val input = BufferedInputStream(connection.getInputStream())
        val header = StringBuilder()
        while (header.length < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) break
            header.append(next.toChar())
            if (header.endsWith("\r\n\r\n") || header.endsWith("\n\n")) break
        }
        val requestLine = header.lineSequence().firstOrNull()?.trim().orEmpty()
        val parts = requestLine.split(' ')
        if (parts.size < 3 || parts[0] != "GET" || !parts[1].startsWith("/")) return null
        return parts[1]
    }

    private fun respond(connection: Socket, status: Int, page: CallbackPage) {
        val body = page.html().toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "Bad Request"
        }
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("Connection: close\r\n\r\n")
        }
        runCatching {
            connection.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(body)
                flush()
            }
        }
    }

    /** A minimal page shown in the browser after the redirect. Text is passed in already localized. */
    data class CallbackPage(val title: String, val message: String, val returnLabel: String?, val returnUrl: String?) {
        fun html(): String = buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>").append(escape(title)).append("</title>")
            append("<style>body{font-family:serif;max-width:32rem;margin:4rem auto;padding:0 1.5rem;")
            append("color:#2b2622;background:#f6f1ea}@media(prefers-color-scheme:dark){body{color:#ece5dc;")
            append("background:#1b1816}a{color:#e7c9a0}}h1{font-weight:500}a{color:#7a4e1d}</style></head><body>")
            append("<h1>").append(escape(title)).append("</h1><p>").append(escape(message)).append("</p>")
            if (returnLabel != null && returnUrl != null) {
                append("<p><a href=\"").append(escape(returnUrl)).append("\">").append(escape(returnLabel)).append("</a></p>")
            }
            append("</body></html>")
        }

        companion object {
            val ignored = CallbackPage("Foliore", "This link is not part of the current sign-in.", null, null)

            private fun escape(value: String): String = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
    }

    companion object {
        private const val ACCEPT_POLL_MILLIS = 500
        private const val READ_TIMEOUT_MILLIS = 5_000
        private const val MAX_HEADER_BYTES = 16 * 1024

        fun open(preferredPort: Int = ChatGptOAuth.PREFERRED_PORT): ChatGptLoopbackServer {
            val loopback = InetAddress.getByName("127.0.0.1")
            val socket = runCatching { ServerSocket(preferredPort, 8, loopback) }
                .getOrElse { ServerSocket(0, 8, loopback) }
            return ChatGptLoopbackServer(socket)
        }
    }
}
