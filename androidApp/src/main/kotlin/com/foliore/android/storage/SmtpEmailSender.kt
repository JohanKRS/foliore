package com.foliore.android.storage

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.URLEncoder
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SmtpEmailSender {
    fun sendEpub(
        credentials: KindleEmailCredentials,
        subject: String,
        body: String,
        attachment: File,
    ): Boolean {
        return runCatching {
            val smtp = SmtpConnection(
                host = credentials.smtpHost,
                port = credentials.smtpPort,
                useImplicitTls = credentials.smtpPort == IMPLICIT_TLS_PORT,
            )
            smtp.use {
                smtp.expect(220)
                smtp.ehlo()
                if (!smtp.isTlsActive && smtp.supportsStartTls) {
                    smtp.startTls()
                    smtp.ehlo()
                } else if (!smtp.isTlsActive) {
                    error("SMTP server does not advertise STARTTLS")
                }
                smtp.authLogin(credentials.smtpUsername, credentials.smtpPassword)
                smtp.command("MAIL FROM:<${credentials.senderEmail}>", 250)
                smtp.command("RCPT TO:<${credentials.kindleEmail}>", 250, 251)
                smtp.command("DATA", 354)
                smtp.writeData(
                    buildMimeMessage(
                        from = credentials.senderEmail,
                        to = credentials.kindleEmail,
                        subject = subject,
                        body = body,
                        attachment = attachment,
                    ),
                )
                smtp.expect(250)
                smtp.command("QUIT", 221)
            }
            true
        }.getOrDefault(false)
    }

    internal fun buildMimeMessage(
        from: String,
        to: String,
        subject: String,
        body: String,
        attachment: File,
        boundary: String = "foliore-${System.currentTimeMillis()}",
    ): String {
        val safeFileName = attachment.name.sanitizeHeaderValue()
        val asciiFileName = safeFileName.asciiFallback()
        val encodedFileName = safeFileName.rfc5987Value()
        val encodedAttachment = Base64.getMimeEncoder(76, "\n".toByteArray())
            .encodeToString(attachment.readBytes())
        val normalizedBody = body.normalizedLines()

        return buildString {
            appendLine("From: <$from>")
            appendLine("To: <$to>")
            appendLine("Subject: ${subject.encodedHeaderValue()}")
            appendLine("MIME-Version: 1.0")
            appendLine("Content-Type: multipart/mixed; boundary=\"$boundary\"")
            appendLine()
            appendLine("--$boundary")
            appendLine("Content-Type: text/plain; charset=UTF-8")
            appendLine("Content-Transfer-Encoding: 8bit")
            appendLine()
            appendLine(normalizedBody)
            appendLine()
            appendLine("--$boundary")
            appendLine("Content-Type: application/epub+zip; name=\"$asciiFileName\"")
            appendLine("Content-Transfer-Encoding: base64")
            appendLine("Content-Disposition: attachment; filename=\"$asciiFileName\"; filename*=UTF-8''$encodedFileName")
            appendLine()
            appendLine(encodedAttachment)
            appendLine("--$boundary--")
        }.normalizedLines()
    }

    private fun String.sanitizeHeaderValue(): String {
        return replace(Regex("[\\r\\n\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.normalizedLines(): String {
        return replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun String.asciiFallback(): String {
        return map { char ->
            if (char.code in 32..126) char else '_'
        }.joinToString("")
    }

    private fun String.rfc5987Value(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
    }

    private fun String.encodedHeaderValue(): String {
        val clean = sanitizeHeaderValue()
        if (clean.all { it.code in 32..126 }) return clean
        val encoded = Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8))
        return "=?UTF-8?B?$encoded?="
    }

    private class SmtpConnection(
        private val host: String,
        private val port: Int,
        private val useImplicitTls: Boolean,
    ) : AutoCloseable {
        private var tlsActive = useImplicitTls
        private var socket: Socket = createSocket()
        private var reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private var writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        var supportsStartTls: Boolean = false
            private set

        val isTlsActive: Boolean
            get() = tlsActive

        fun ehlo() {
            supportsStartTls = command("EHLO foliore.local", 250)
                .text
                .contains("STARTTLS", ignoreCase = true)
        }

        fun startTls() {
            command("STARTTLS", 220)
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, host, port, true) as SSLSocket
            sslSocket.soTimeout = TIMEOUT_MS
            sslSocket.verifySmtpPeer()
            socket = sslSocket
            tlsActive = true
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        }

        fun authLogin(username: String, password: String) {
            check(tlsActive) { "Authentication requires verified TLS" }
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
        }

        fun command(command: String, vararg expectedCodes: Int): SmtpResponse {
            writer.write(command)
            writer.write("\r\n")
            writer.flush()
            return expect(*expectedCodes)
        }

        fun writeData(message: String) {
            message
                .lineSequence()
                .forEach { line ->
                    writer.write(if (line.startsWith(".")) ".$line" else line)
                    writer.write("\r\n")
                }
            writer.write(".\r\n")
            writer.flush()
        }

        fun expect(vararg expectedCodes: Int): SmtpResponse {
            val response = readResponse()
            if (response.code !in expectedCodes) {
                error("Unexpected SMTP response ${response.code}: ${response.text}")
            }
            return response
        }

        private fun readResponse(): SmtpResponse {
            var firstCode: Int? = null
            val lines = mutableListOf<String>()
            while (true) {
                val line = reader.readLine() ?: error("SMTP server closed the connection")
                lines += line
                val code = line.take(3).toIntOrNull()
                if (firstCode == null && code != null) {
                    firstCode = code
                }
                if (line.length < 4 || line[3] != '-') {
                    return SmtpResponse(
                        code = code ?: firstCode ?: 0,
                        text = lines.joinToString("\n"),
                    )
                }
            }
        }

        private fun createSocket(): Socket {
            return if (useImplicitTls) {
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(host, port) as SSLSocket)
                    .apply {
                        soTimeout = TIMEOUT_MS
                        verifySmtpPeer()
                    }
            } else {
                Socket(host, port).apply {
                    soTimeout = TIMEOUT_MS
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }

        private companion object {
            const val TIMEOUT_MS = 30_000
        }
    }

    private data class SmtpResponse(
        val code: Int,
        val text: String,
    )

    private companion object {
        const val IMPLICIT_TLS_PORT = 465
        const val STARTTLS_PORT = 587
    }
}

internal fun SSLSocket.verifySmtpPeer() {
    sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
    try { startHandshake() } catch (error: Throwable) { close(); throw error }
}
