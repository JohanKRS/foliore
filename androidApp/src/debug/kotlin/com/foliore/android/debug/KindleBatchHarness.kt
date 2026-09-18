package com.foliore.android.debug

import android.content.Context
import com.foliore.android.di.AppContainer
import com.foliore.android.storage.AndroidPreparedEpubService
import com.foliore.android.storage.DeviceIdProvider
import com.foliore.android.storage.GmailEmailSender
import com.foliore.android.storage.KindleBookDelivery
import com.foliore.android.storage.sha256
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking

/** Opt-in, debug-only integration check. Captures MIME locally; never connects to an email service. */
internal object KindleBatchHarness {
    fun run(context: Context) = runBlocking {
        val app = AppContainer(context)
        val books = app.libraryRepository.getBooks()
        require(books.size in 2..25) { "Use a disposable emulator with 2–25 imported fixtures" }
        val originals = books.associate { it.book.originalFilePath to File(it.book.originalFilePath).sha256() }
        val out = File(context.cacheDir, "kindle-batch-qa").apply { mkdirs() }
        var calls = 0
        val service = AndroidPreparedEpubService(context, app.libraryRepository, DeviceIdProvider(context),
            gmailEmailSender = GmailEmailSender {
                calls++
                object : HttpURLConnection(URL("https://example.invalid/")) {
                    private val output = ByteArrayOutputStream()
                    override fun getOutputStream() = output
                    override fun getResponseCode() = 200
                    override fun getInputStream() = "{\"id\":\"local-qa-message\"}".byteInputStream()
                    override fun disconnect() { File(out, "message.eml").writeBytes(output.toByteArray()) }
                    override fun usingProxy() = false
                    override fun connect() = Unit
                }
            })
        val report = service.sendBatchToKindle(books.map { it.book.id }, account = "sender@example.invalid",
            recipient = "reader@example.invalid", token = "local-test", onProgress = { _, _, _ -> })
        check(calls == 1 && report.emailCount == 1)
        File(out, "report.txt").writeText(report.toString())
        check(report.sentCount >= 2)
        check(report.items.all { it.delivery == KindleBookDelivery.SENT || it.delivery == KindleBookDelivery.TOO_LARGE })
        check(originals.all { (path, hash) -> File(path).sha256() == hash })
        check(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("kindle-send-") })
        File(out, "result.txt").writeText("PASS: ${report.sentCount} prepared EPUBs, ${report.items.count { it.delivery == KindleBookDelivery.TOO_LARGE }} oversized skipped, one captured email, original hashes unchanged, staging removed. No network.\n")
    }
}
