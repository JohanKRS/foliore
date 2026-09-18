package com.foliore.android.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.test.*

class GmailEmailSenderTest {
    private fun withAttachment(test: (File) -> Unit) {
        val file = File.createTempFile("Ação-日本-📚", ".epub")
        try { file.writeBytes(ByteArray(70_123) { (it % 251).toByte() }); test(file) }
        finally { file.delete() }
    }

    @Test fun uploadsExactAttachmentWithUnicodeHeadersAndBoundedLines() = withAttachment { file ->
        val connection = FakeConnection()
        val subject = "123456789📚 Ação e 日本 — envio"
        val body = "Preparado pelo Foliore.\nOlá! 日本 📚"
        val result = GmailEmailSender { connection }.send("test-token", "sender@example.com", "reader@kindle.com", subject, body, file)
        assertEquals(GmailSendResult.SENT, result)
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer test-token", connection.getRequestProperty("Authorization"))
        assertEquals("message/rfc822", connection.getRequestProperty("Content-Type"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertTrue(connection.connectTimeout > 0 && connection.readTimeout > 0)
        assertTrue(connection.chunkSize > 0)
        assertTrue(connection.closed)
        val mime = connection.output.toString("UTF-8")
        assertFalse(mime.replace("\r\n", "").contains('\n'))
        assertTrue(mime.split("\r\n").all { it.toByteArray().size <= 998 })
        val decodedSubject = Regex("=\\?UTF-8\\?B\\?([^?]+)\\?=").findAll(mime.substringBefore("MIME-Version"))
            .joinToString("") { String(Base64.getDecoder().decode(it.groupValues[1]), Charsets.UTF_8) }
        assertEquals(subject, decodedSubject)
        val boundary = Regex("boundary=\"([^\"]+)\"").find(mime)!!.groupValues[1]
        val parts = mime.split("--$boundary")
        assertEquals(body, String(Base64.getMimeDecoder().decode(parts[1].substringAfter("\r\n\r\n").trim()), Charsets.UTF_8))
        assertContentEquals(file.readBytes(), Base64.getMimeDecoder().decode(parts[2].substringAfter("\r\n\r\n").trim()))
        assertTrue(parts[2].contains("filename*=UTF-8''A%C3%A7%C3%A3o-"))
        assertTrue(mime.endsWith("--$boundary--\r\n"))
    }

    @Test fun rejectsHeaderInjectionBeforeOpeningConnection() = withAttachment { file ->
        var opens = 0
        val sender = GmailEmailSender { opens++; FakeConnection() }
        for (address in listOf("sender@example.com\r\nBcc:other@example.com", "a@b,c@d", "a@b\u0000")) {
            assertEquals(GmailSendResult.FAILED, sender.send("token", address, "reader@kindle.com", "Book", "", file))
            assertEquals(GmailSendResult.FAILED, sender.send("token", "sender@example.com", address, "Book", "", file))
        }
        assertEquals(GmailSendResult.FAILED, sender.send("token\r\nX:1", "sender@example.com", "reader@kindle.com", "Book", "", file))
        assertEquals(0, opens)
    }

    @Test fun subjectCannotAddRecipients() = withAttachment { file ->
        val connection = FakeConnection()
        GmailEmailSender { connection }.send("token", "sender@example.com", "reader@kindle.com", "Book\r\nBcc: other@example.com", "", file)
        assertFalse(connection.output.toString("UTF-8").contains("\r\nBcc:"))
    }

    @Test fun oversizedAttachmentRejectedWithoutNetwork() = withAttachment { file ->
        RandomAccessFile(file, "rw").use { it.setLength(GmailMimeMessage.MAX_ATTACHMENT_BYTES + 1) }
        val sender = GmailEmailSender { error("No request should be made") }
        assertEquals(GmailSendResult.TOO_LARGE, sender.send("token", "sender@example.com", "reader@kindle.com", "Book", "", file))
    }

    @Test fun mapsServerResponsesWithoutRetryingOrFollowingRedirects() = withAttachment { file ->
        val cases = listOf(
            Triple(200, "{}", GmailSendResult.UNKNOWN),
            Triple(200, "not-json", GmailSendResult.UNKNOWN),
            Triple(401, "", GmailSendResult.AUTH_REQUIRED),
            Triple(403, "{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}", GmailSendResult.AUTH_REQUIRED),
            Triple(403, "{\"error\":{\"status\":\"SERVICE_DISABLED\"}}", GmailSendResult.CONFIGURATION_REQUIRED),
            Triple(403, "{\"error\":{\"errors\":[{\"reason\":\"dailyLimitExceeded\"}]}}", GmailSendResult.RATE_LIMITED),
            Triple(429, "", GmailSendResult.RATE_LIMITED), Triple(413, "", GmailSendResult.TOO_LARGE),
            Triple(500, "", GmailSendResult.UNKNOWN), Triple(302, "", GmailSendResult.FAILED), Triple(400, "", GmailSendResult.FAILED),
        )
        for ((status, body, expected) in cases) {
            var opens = 0
            val connection = FakeConnection(status, body)
            val sender = GmailEmailSender { opens++; connection }
            assertEquals(expected, sender.send("token", "sender@example.com", "reader@kindle.com", "Book", "", file), "HTTP $status")
            assertEquals(1, opens)
            assertTrue(connection.closed)
            assertFalse(connection.instanceFollowRedirects)
        }
    }

    @Test fun lostResponseIsUnknownAndNeverAutomaticallyResent() = withAttachment { file ->
        var opens = 0
        val connection = object : FakeConnection() { override fun getResponseCode(): Int = throw IOException("Lost response") }
        val result = GmailEmailSender { opens++; connection }.send("token", "sender@example.com", "reader@kindle.com", "Book", "", file)
        assertEquals(GmailSendResult.UNKNOWN, result)
        assertEquals(1, opens)
        assertTrue(connection.output.size() > file.length())
        assertTrue(connection.closed)
    }

    @Test fun failureBeforeUploadIsNotReportedAsDelivered() = withAttachment { file ->
        assertEquals(GmailSendResult.FAILED, GmailEmailSender { throw IOException("Offline") }
            .send("token", "sender@example.com", "reader@kindle.com", "Book", "", file))
    }

    private open class FakeConnection(private val status: Int = 200, private val response: String = "{\"id\":\"message-id\"}") : HttpURLConnection(URL("https://example.invalid/")) {
        val output = ByteArrayOutputStream()
        var closed = false
        val chunkSize get() = chunkLength
        override fun getOutputStream() = output
        override fun getResponseCode() = status
        override fun getInputStream() = response.byteInputStream()
        override fun getErrorStream() = response.byteInputStream()
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }
}
