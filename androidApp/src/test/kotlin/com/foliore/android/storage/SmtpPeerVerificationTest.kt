package com.foliore.android.storage

import java.io.File
import java.security.KeyStore
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.test.*

class SmtpPeerVerificationTest {
    @Test fun trustedCertificateRequiresMatchingHost() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val keyFile = File(dir, "server.p12")
            val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
            val process = ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA",
                "-keystore", keyFile.path, "-storetype", "PKCS12", "-storepass", "fixture-password",
                "-keypass", "fixture-password", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "2")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            val keys = KeyStore.getInstance("PKCS12").apply { keyFile.inputStream().use { load(it, "fixture-password".toCharArray()) } }
            val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, "fixture-password".toCharArray()) }
            val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
            val context = SSLContext.getInstance("TLS").apply { init(km.keyManagers, tm.trustManagers, null) }
            fun connect(host: String): Boolean {
                val server = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
                val worker = thread { runCatching { server.accept().use { (it as SSLSocket).startHandshake() } } }
                return try {
                    runCatching {
                        (context.socketFactory.createSocket(host, server.localPort) as SSLSocket).use {
                            it.soTimeout = 3000
                            it.verifySmtpPeer()
                        }
                    }.isSuccess
                } finally { server.close(); worker.join(4000) }
            }
            assertTrue(connect("localhost"), "Valid trusted peer must work")
            assertFalse(connect("127.0.0.1"), "Trusted certificate for a different identity must fail")
        } finally { dir.deleteRecursively() }
    }
}
