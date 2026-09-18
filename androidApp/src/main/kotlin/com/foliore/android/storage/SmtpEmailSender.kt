package com.foliore.android.storage

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.io.OutputStream
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SmtpEmailSender {
    fun sendEpub(credentials: KindleEmailCredentials, subject: String, body: String, attachment: File): Boolean =
        sendEpubs(credentials, subject, body, listOf(attachment)) == KindleSendResult.SENT

    fun sendEpubs(credentials: KindleEmailCredentials, subject: String, body: String, attachments: List<File>): KindleSendResult {
        if (!KindleMimeMessage.validAddress(credentials.senderEmail) || !KindleMimeMessage.validAddress(credentials.kindleEmail) ||
            attachments.isEmpty() || attachments.any { !it.isFile }) return KindleSendResult.FAILED
        if (!KindleMessageLimits.SMTP_BATCH.accepts(attachments)) return KindleSendResult.TOO_LARGE
        var uploading = false
        var accepted = false
        return try {
            SmtpConnection(credentials.smtpHost, credentials.smtpPort, credentials.smtpPort == IMPLICIT_TLS_PORT).use { smtp ->
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
                uploading = true
                smtp.writeData { output ->
                    KindleMimeMessage.write(output, credentials.senderEmail, credentials.kindleEmail, subject, body, attachments)
                }
                smtp.expect(250)
                accepted = true
                // A failure while closing an accepted message must not invite a duplicate resend.
                runCatching { smtp.command("QUIT", 221) }
            }
            KindleSendResult.SENT
        } catch (_: SmtpRejected) {
            if (accepted) KindleSendResult.SENT else KindleSendResult.FAILED
        } catch (_: Exception) {
            if (accepted) KindleSendResult.SENT else if (uploading) KindleSendResult.UNKNOWN else KindleSendResult.FAILED
        }
    }

    private class SmtpRejected : Exception()

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

        fun writeData(writeMessage: (OutputStream) -> Unit) {
            writer.flush()
            // MIME body and subject use base64. Dot stuffing also protects any future text parts.
            val output = SmtpDataOutputStream(socket.getOutputStream())
            writeMessage(output)
            output.flush()
            writer.write(".\r\n")
            writer.flush()
        }

        fun expect(vararg expectedCodes: Int): SmtpResponse {
            val response = readResponse()
            if (response.code !in expectedCodes) {
                throw SmtpRejected()
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
            val plain = Socket()
            try {
                plain.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                plain.soTimeout = TIMEOUT_MS
                return if (useImplicitTls) {
                    ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, host, port, true) as SSLSocket)
                        .apply { soTimeout = TIMEOUT_MS; verifySmtpPeer() }
                } else plain
            } catch (error: Exception) { plain.close(); throw error }
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

/** Does not close the socket; DATA ends only after the MIME writer completes successfully. */
internal class SmtpDataOutputStream(private val target: OutputStream) : OutputStream() {
    private var lineStart = true
    override fun write(value: Int) {
        if (lineStart && value == '.'.code) target.write('.'.code)
        target.write(value)
        lineStart = value == '\n'.code
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        var start = offset
        for (index in offset until offset + length) {
            if (lineStart && bytes[index] == '.'.code.toByte()) {
                target.write(bytes, start, index - start)
                target.write('.'.code)
                start = index
            }
            lineStart = bytes[index] == '\n'.code.toByte()
        }
        target.write(bytes, start, offset + length - start)
    }
    override fun flush() = target.flush()
}
