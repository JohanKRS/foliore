package com.foliore.android.storage

import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmtpEmailSenderTest {
    @Test
    fun dotStuffingHandlesChunkBoundariesAndLeavesStreamOpen() {
        val output = java.io.ByteArrayOutputStream()
        val data = SmtpDataOutputStream(output)
        data.write("first\r\n".toByteArray())
        data.write('.'.code)
        data.write("line\r\n..second\r\nend\r\n".toByteArray())
        data.flush()
        kotlin.test.assertEquals("first\r\n..line\r\n...second\r\nend\r\n", output.toString("UTF-8"))
    }

    @Test
    fun sendEpubRejectsPlainServerOnCustomPortBeforeCredentials() {
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

            assertFalse(sent)
            assertTrue(smtpServer.commands.any { it.startsWith("EHLO") })
            assertFalse(smtpServer.commands.any { it.startsWith("AUTH") || it.startsWith("MAIL") || it == "DATA" })
            assertTrue(smtpServer.message.isEmpty())
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
