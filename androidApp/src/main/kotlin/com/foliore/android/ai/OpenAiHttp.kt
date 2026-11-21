package com.foliore.android.ai

import com.foliore.android.storage.readBounded
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTPS helper shared by the OpenAI API key and ChatGPT sign-in paths. */
internal object OpenAiHttp {
    private const val MAX_BODY_BYTES = 4L * 1024 * 1024
    private const val USER_AGENT = "Foliore (Android; open source)"

    data class Response(val status: Int, val body: String, val requestId: String?) {
        val isSuccess: Boolean get() = status in 200..299
    }

    fun get(url: String, bearer: String? = null): Response = request(url, "GET", bearer, null, null)

    fun postForm(url: String, form: Map<String, String>): Response =
        request(url, "POST", null, "application/x-www-form-urlencoded", ChatGptOAuth.formBody(form))

    fun postJson(url: String, bearer: String, body: String): Response =
        request(url, "POST", bearer, "application/json", body)

    /**
     * Posts [body] and, on success, hands each server-sent event's data to [onEvent] until it
     * returns true. Non-2xx responses are returned with their body instead.
     */
    fun postJsonStream(
        url: String,
        bearer: String,
        body: String,
        maxStreamBytes: Long,
        onEvent: (data: String) -> Boolean,
    ): Response {
        val connection = open(url, "POST", bearer, "application/json", readTimeoutMillis = 300_000)
        try {
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val requestId = connection.getHeaderField("x-request-id")
            if (status !in 200..299) return Response(status, errorBody(connection), requestId)
            var consumed = 0L
            val data = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    consumed += line.length + 1
                    if (consumed > maxStreamBytes) throw IOException("Response stream too large")
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                val payload = data.toString()
                                data.setLength(0)
                                if (payload != "[DONE]" && onEvent(payload)) return Response(status, "", requestId)
                            }
                        }
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").removePrefix(" "))
                        }
                    }
                }
            }
            if (data.isNotEmpty()) onEvent(data.toString())
            return Response(status, "", requestId)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadBytes(url: String, maxBytes: Long, accept: String): Pair<String?, ByteArray>? {
        val connection = open(url, "GET", null, null, readTimeoutMillis = 20_000)
        try {
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", accept)
            if (connection.responseCode !in 200..299) return null
            if (!connection.url.protocol.equals("https", ignoreCase = true)) return null
            val declared = connection.contentLengthLong
            if (declared > maxBytes) return null
            val bytes = connection.inputStream.use { it.readBounded(maxBytes) }
            return connection.contentType to bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun request(url: String, method: String, bearer: String?, contentType: String?, body: String?): Response {
        val connection = open(url, method, bearer, contentType, readTimeoutMillis = 180_000)
        try {
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = if (status in 200..299) {
                connection.inputStream.use { it.readBounded(MAX_BODY_BYTES) }.toString(Charsets.UTF_8)
            } else {
                errorBody(connection)
            }
            return Response(status, text, connection.getHeaderField("x-request-id"))
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        url: String,
        method: String,
        bearer: String?,
        contentType: String?,
        readTimeoutMillis: Int,
    ): HttpURLConnection {
        require(url.startsWith("https://")) { "HTTPS is required" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = readTimeoutMillis
            setRequestProperty("User-Agent", USER_AGENT)
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (contentType != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
    }

    private fun errorBody(connection: HttpURLConnection): String =
        runCatching { connection.errorStream?.use { it.readBounded(MAX_BODY_BYTES) }?.toString(Charsets.UTF_8) }
            .getOrNull()
            .orEmpty()
}
