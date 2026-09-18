package com.foliore.android.storage

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class GmailSendResult { SENT, AUTH_REQUIRED, CONFIGURATION_REQUIRED, TOO_LARGE, RATE_LIMITED, FAILED, UNKNOWN }

class GmailEmailSender internal constructor(
    private val openConnection: () -> HttpURLConnection,
) {
    constructor() : this({ URL("https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send?uploadType=media").openConnection() as HttpURLConnection })

    fun send(token: String, from: String, to: String, subject: String, body: String, attachment: File): GmailSendResult {
        if (!attachment.isFile) return GmailSendResult.FAILED
        if (attachment.length() > GmailMimeMessage.MAX_ATTACHMENT_BYTES) return GmailSendResult.TOO_LARGE
        if (!GmailMimeMessage.validAddress(from) || !GmailMimeMessage.validAddress(to) || token.isBlank() || token.any { it == '\r' || it == '\n' }) return GmailSendResult.FAILED
        var connection: HttpURLConnection? = null
        var uploading = false
        return try {
            connection = openConnection().apply {
                requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 60_000
                instanceFollowRedirects = false; useCaches = false; doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "message/rfc822")
                setChunkedStreamingMode(16 * 1024)
            }
            connection.outputStream.use { output ->
                uploading = true
                GmailMimeMessage.write(output, from, to, subject, body, attachment)
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { input ->
                    val bytes = ByteArray(64 * 1024); var size = 0
                    while (size < bytes.size) { val count = input.read(bytes, size, bytes.size - size); if (count < 0) break; size += count }
                    String(bytes, 0, size, Charsets.UTF_8)
                }.orEmpty()
            classify(status, response)
        } catch (_: Exception) {
            // A connection lost after upload may already have delivered the message. Never retry automatically.
            if (uploading) GmailSendResult.UNKNOWN else GmailSendResult.FAILED
        } finally { connection?.disconnect() }
    }

    internal fun classify(status: Int, response: String): GmailSendResult = when {
        status in 200..299 -> if (runCatching { Json.parseToJsonElement(response).jsonObject["id"]?.jsonPrimitive?.content?.isNotBlank() == true }.getOrDefault(false)) GmailSendResult.SENT else GmailSendResult.UNKNOWN
        status == 401 -> GmailSendResult.AUTH_REQUIRED
        status == 413 -> GmailSendResult.TOO_LARGE
        status == 429 || status == 403 && ("rateLimitExceeded" in response || "userRateLimitExceeded" in response || "dailyLimitExceeded" in response || "quotaExceeded" in response) -> GmailSendResult.RATE_LIMITED
        status == 403 && ("insufficientPermissions" in response || "ACCESS_TOKEN_SCOPE_INSUFFICIENT" in response) -> GmailSendResult.AUTH_REQUIRED
        status == 403 && ("accessNotConfigured" in response || "SERVICE_DISABLED" in response) -> GmailSendResult.CONFIGURATION_REQUIRED
        status >= 500 -> GmailSendResult.UNKNOWN
        else -> GmailSendResult.FAILED
    }
}

/** Stream the attachment directly: no copies of a large EPUB in JSON/base64 strings. */
internal object GmailMimeMessage {
    const val MAX_ATTACHMENT_BYTES = 25_000_000L
    fun validAddress(value: String) = value.length in 3..254 &&
        value.count { it == '@' } == 1 && !value.startsWith('@') && !value.endsWith('@') &&
        value.none { it.isWhitespace() || it.isISOControl() || it in "<>,;:\"\\" }

    fun write(output: OutputStream, from: String, to: String, subject: String, body: String, attachment: File) {
        require(validAddress(from) && validAddress(to))
        require(attachment.length() <= MAX_ATTACHMENT_BYTES)
        val boundary = "foliore-${UUID.randomUUID()}"
        val name = attachment.name.replace(Regex("[\\r\\n\"\\\\]"), "_")
        val ascii = name.map { if (it.code in 32..126) it else '_' }.joinToString("")
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        fun line(value: String = "") { output.write((value + "\r\n").toByteArray(Charsets.UTF_8)) }
        fun base64(write: (OutputStream) -> Unit) {
            val keepOpen = object : FilterOutputStream(output) {
                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                override fun close() = flush()
            }
            Base64.getMimeEncoder(76, "\r\n".toByteArray()).wrap(keepOpen).use(write)
            line()
        }
        line("From: <$from>"); line("To: <$to>")
        val cleanSubject = subject.replace(Regex("[\\r\\n]"), " ")
        val subjectChunks = cleanSubject.codePoints().toArray().take(250).chunked(10).map { String(it.toIntArray(), 0, it.size) }
        subjectChunks.forEachIndexed { index, chunk ->
            line((if (index == 0) "Subject: " else " ") + "=?UTF-8?B?${Base64.getEncoder().encodeToString(chunk.toByteArray(Charsets.UTF_8))}?=")
        }
        line("MIME-Version: 1.0"); line("Content-Type: multipart/mixed; boundary=\"$boundary\""); line()
        line("--$boundary"); line("Content-Type: text/plain; charset=UTF-8"); line("Content-Transfer-Encoding: base64"); line()
        base64 { it.write(body.toByteArray(Charsets.UTF_8)) }
        line("--$boundary"); line("Content-Type: application/epub+zip")
        line("Content-Disposition: attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded")
        line("Content-Transfer-Encoding: base64"); line()
        base64 { target -> attachment.inputStream().use { source ->
            var total = 0L; val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = source.read(buffer); if (count < 0) break
                total += count; require(total <= MAX_ATTACHMENT_BYTES); target.write(buffer, 0, count)
            }
        } }
        line("--$boundary--")
    }
}
