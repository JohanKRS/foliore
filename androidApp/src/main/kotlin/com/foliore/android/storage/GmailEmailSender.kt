package com.foliore.android.storage

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GmailEmailSender internal constructor(
    private val openConnection: () -> HttpURLConnection,
) {
    constructor() : this({ URL("https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send?uploadType=media").openConnection() as HttpURLConnection })

    fun send(token: String, from: String, to: String, subject: String, body: String, attachment: File): KindleSendResult =
        send(token, from, to, subject, body, listOf(attachment))

    fun send(token: String, from: String, to: String, subject: String, body: String, attachments: List<File>): KindleSendResult {
        if (attachments.isEmpty() || attachments.any { !it.isFile }) return KindleSendResult.FAILED
        if (!KindleMessageLimits.GMAIL.accepts(attachments)) return KindleSendResult.TOO_LARGE
        if (!KindleMimeMessage.validAddress(from) || !KindleMimeMessage.validAddress(to) || token.isBlank() || token.any { it == '\r' || it == '\n' }) return KindleSendResult.FAILED
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
                KindleMimeMessage.write(output, from, to, subject, body, attachments)
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
            if (uploading) KindleSendResult.UNKNOWN else KindleSendResult.FAILED
        } finally { connection?.disconnect() }
    }

    internal fun classify(status: Int, response: String): KindleSendResult = when {
        status in 200..299 -> if (runCatching { Json.parseToJsonElement(response).jsonObject["id"]?.jsonPrimitive?.content?.isNotBlank() == true }.getOrDefault(false)) KindleSendResult.SENT else KindleSendResult.UNKNOWN
        status == 401 -> KindleSendResult.AUTH_REQUIRED
        status == 413 -> KindleSendResult.TOO_LARGE
        status == 429 || status == 403 && ("rateLimitExceeded" in response || "userRateLimitExceeded" in response || "dailyLimitExceeded" in response || "quotaExceeded" in response) -> KindleSendResult.RATE_LIMITED
        status == 403 && ("insufficientPermissions" in response || "ACCESS_TOKEN_SCOPE_INSUFFICIENT" in response) -> KindleSendResult.AUTH_REQUIRED
        status == 403 && ("accessNotConfigured" in response || "SERVICE_DISABLED" in response) -> KindleSendResult.CONFIGURATION_REQUIRED
        status >= 500 -> KindleSendResult.UNKNOWN
        else -> KindleSendResult.FAILED
    }
}
