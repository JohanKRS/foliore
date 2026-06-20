package com.foliore.android.storage

import java.io.File
import java.net.ServerSocket
import java.net.URLEncoder
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmtpEmailSenderTest {
    @Test
    fun mimeMessageUsesStableLineBreaksAndUtf8Headers() {
        val attachment = File.createTempFile("Acao espacial", ".epub").apply {
            writeBytes("epub".toByteArray(Charsets.UTF_8))
        }

        try {
            val message = SmtpEmailSender().buildMimeMessage(
                from = "sender@example.com",
                to = "reader@kindle.com",
                subject = "Enviar para Kindle: Acao espacial",
                body = "Preparado pelo Foliore.\r\n.Line that needs dot stuffing later.",
                attachment = attachment,
                boundary = "test-boundary",
            )
            val encodedFileName = URLEncoder.encode(attachment.name, Charsets.UTF_8.name())
                .replace("+", "%20")

            assertFalse(message.contains('\r'), "SMTP data writer owns CRLF conversion")
            assertTrue(message.contains("Subject: Enviar para Kindle: Acao espacial"))
            assertTrue(message.contains("Content-Type: multipart/mixed; boundary=\"test-boundary\""))
            assertTrue(message.contains("Content-Type: application/epub+zip; name=\"${attachment.name}\""))
            assertTrue(message.contains("filename*=UTF-8''$encodedFileName"))
            assertTrue(message.contains("ZXB1Yg=="))
            assertTrue(message.endsWith("--test-boundary--\n"))
        } finally {
            attachment.delete()
        }
    }

    @Test
    fun mimeMessageEncodesNonAsciiSubject() {
        val attachment = File.createTempFile("foliore", ".epub").apply {
            writeBytes("epub".toByteArray(Charsets.UTF_8))
        }

        try {
            val message = SmtpEmailSender().buildMimeMessage(
                from = "sender@example.com",
                to = "reader@kindle.com",
                subject = "Enviar para Kindle: Ação espacial",
                body = "Preparado pelo Foliore.",
                attachment = attachment,
                boundary = "test-boundary",
            )

            assertTrue(message.contains("Subject: =?UTF-8?B?"))
            assertFalse(message.contains("Ação"))
        } finally {
            attachment.delete()
        }
    }

    @Test
    fun sendEpubDeliversMessageToPlainLocalSmtpServer() {
        val attachment = File.createTempFile("foliore-send", ".epub").apply {
            writeBytes("epub".toByteArray(Charsets.UTF_8))
        }
        val smtpServer = PlainSmtpTestServer()
        val serverThread = thread(start = true) { smtpServer.run() }

        try {
            val sent = SmtpEmailSender().sendEpub(
                credentials = KindleEmailCredentials(
                    kindleEmail = "reader@kindle.com",
                    senderEmail = "sender@example.com",
                    smtpHost = "127.0.0.1",
                    smtpPort = smtpServer.port,
                    smtpUsername = "sender@example.com",
                    smtpPassword = "secret",
                ),
                subject = "Enviar para Kindle: Teste",
                body = "EPUB preparado pelo Foliore.",
                attachment = attachment,
            )

            serverThread.join(3_000)

            assertTrue(sent)
            assertTrue(smtpServer.commands.any { it == "MAIL FROM:<sender@example.com>" })
            assertTrue(smtpServer.commands.any { it == "RCPT TO:<reader@kindle.com>" })
            assertTrue(smtpServer.commands.any { it == "AUTH LOGIN" })
            assertTrue(smtpServer.message.contains("To: <reader@kindle.com>"))
            assertTrue(smtpServer.message.contains("Content-Type: application/epub+zip"))
            assertTrue(smtpServer.message.contains(Base64.getEncoder().encodeToString("epub".toByteArray())))
        } finally {
            smtpServer.close()
            serverThread.join(1_000)
            attachment.delete()
        }
    }

    private class PlainSmtpTestServer : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        val commands = mutableListOf<String>()
        var message: String = ""

        fun run() {
            server.use { socket ->
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
                    val writer = client.getOutputStream().bufferedWriter(Charsets.UTF_8)

                    fun send(line: String) {
                        writer.write(line)
                        writer.write("\r\n")
                        writer.flush()
                    }

                    send("220 foliore-test ESMTP")
                    var inData = false
                    val messageLines = mutableListOf<String>()

                    while (true) {
                        val line = reader.readLine() ?: break
                        if (inData) {
                            if (line == ".") {
                                message = messageLines.joinToString("\n")
                                send("250 accepted")
                                inData = false
                                continue
                            }
                            messageLines += line.removePrefix(".")
                            continue
                        }

                        commands += line
                        when {
                            line.startsWith("EHLO", ignoreCase = true) -> {
                                send("250-foliore-test")
                                send("250 AUTH LOGIN")
                            }
                            line == "AUTH LOGIN" -> send("334 VXNlcm5hbWU6")
                            commands.takeLast(2).firstOrNull() == "AUTH LOGIN" -> send("334 UGFzc3dvcmQ6")
                            commands.takeLast(3).firstOrNull() == "AUTH LOGIN" -> send("235 authenticated")
                            line.startsWith("MAIL FROM", ignoreCase = true) -> send("250 ok")
                            line.startsWith("RCPT TO", ignoreCase = true) -> send("250 ok")
                            line == "DATA" -> {
                                send("354 end with dot")
                                inData = true
                            }
                            line == "QUIT" -> {
                                send("221 bye")
                                break
                            }
                            else -> send("250 ok")
                        }
                    }
                }
            }
        }

        override fun close() {
            runCatching { server.close() }
        }
    }
}
