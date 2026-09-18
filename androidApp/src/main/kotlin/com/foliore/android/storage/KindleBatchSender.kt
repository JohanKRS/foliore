package com.foliore.android.storage

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class KindleSendResult { SENT, AUTH_REQUIRED, CONFIGURATION_REQUIRED, TOO_LARGE, RATE_LIMITED, FAILED, UNKNOWN }
enum class KindleBookDelivery { SENT, PREPARATION_FAILED, TOO_LARGE, FAILED, UNKNOWN, NOT_SENT }
data class KindleBatchItem(val bookId: String, val delivery: KindleBookDelivery)
data class KindleBatchReport(val items: List<KindleBatchItem>, val emailCount: Int, val failure: KindleSendResult? = null) {
    val sentCount: Int get() = items.count { it.delivery == KindleBookDelivery.SENT }
    // An uncertain delivery must be checked in Sent before the user deliberately selects it again.
    val retryableIds: Set<String> get() = items.filter {
        it.delivery != KindleBookDelivery.SENT && it.delivery != KindleBookDelivery.UNKNOWN
    }.map { it.bookId }.toSet()
}

/** Bounds include MIME/base64 overhead without reading whole attachments into memory. */
internal data class KindleMessageLimits(val attachmentBytes: Long, val messageBytes: Long) {
    fun accepts(files: List<File>): Boolean = files.isNotEmpty() && files.size <= 25 &&
        files.all { it.length() in 1..attachmentBytes } &&
        files.sumOf { it.length() } <= attachmentBytes && estimatedBytes(files) <= messageBytes

    companion object {
        val GMAIL = KindleMessageLimits(25_000_000, 35_000_000)
        // SMTP providers vary. Keep the encoded message below the common 25 MB ceiling.
        val SMTP_BATCH = KindleMessageLimits(24_000_000, 24_000_000)
        val GMAIL_BATCH = KindleMessageLimits(24_000_000, 34_000_000)
        fun estimatedBytes(files: List<File>): Long = 65_536L + files.sumOf {
            val base64 = ((it.length() + 2) / 3) * 4
            base64 + ((base64 + 75) / 76) * 2 + 8_192L
        }
    }
}

/** Holds at most one message plus the next attachment on disk; stops on the first send failure. */
internal class KindleBatchSender(private val limits: KindleMessageLimits) {
    suspend fun send(
        bookIds: List<String>,
        prepare: (String) -> File?,
        deliver: (List<File>) -> KindleSendResult,
        onProgress: (preparing: Boolean, completed: Int, total: Int) -> Unit = { _, _, _ -> },
    ): KindleBatchReport {
        val ids = bookIds.distinct()
        val outcomes = linkedMapOf<String, KindleBookDelivery>()
        val pending = mutableListOf<Pair<String, File>>()
        var emailCount = 0
        var failure: KindleSendResult? = null
        var nextFile: File? = null
        suspend fun flush(): Boolean {
            if (pending.isEmpty()) return true
            currentCoroutineContext().ensureActive()
            onProgress(false, outcomes.size, ids.size)
            // An unexpected exception can happen after delivery. Never silently retry it.
            val result = try { deliver(pending.map { it.second }) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { KindleSendResult.UNKNOWN }
            val status = when (result) {
                KindleSendResult.SENT -> KindleBookDelivery.SENT
                KindleSendResult.UNKNOWN -> KindleBookDelivery.UNKNOWN
                KindleSendResult.TOO_LARGE -> KindleBookDelivery.TOO_LARGE
                else -> KindleBookDelivery.FAILED
            }
            pending.forEach { (id, file) -> outcomes[id] = status; file.delete() }
            pending.clear()
            if (result == KindleSendResult.SENT) emailCount++ else failure = result
            onProgress(false, outcomes.size, ids.size)
            return result == KindleSendResult.SENT
        }
        try {
            for (id in ids) {
                currentCoroutineContext().ensureActive()
                onProgress(true, outcomes.size + pending.size, ids.size)
                val file = try { prepare(id) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                nextFile = file
                if (file == null || !file.isFile || file.length() == 0L) {
                    outcomes[id] = KindleBookDelivery.PREPARATION_FAILED
                    file?.delete(); nextFile = null
                    continue
                }
                if (!limits.accepts(listOf(file))) {
                    outcomes[id] = KindleBookDelivery.TOO_LARGE
                    file.delete(); nextFile = null
                    continue
                }
                if (pending.isNotEmpty() && !limits.accepts(pending.map { it.second } + file)) {
                    if (!flush()) break
                }
                pending += id to file
                nextFile = null
            }
            if (failure == null) flush()
            return KindleBatchReport(ids.map { KindleBatchItem(it, outcomes[it] ?: KindleBookDelivery.NOT_SENT) }, emailCount, failure)
        } finally {
            nextFile?.delete()
            pending.forEach { it.second.delete() }
        }
    }
}
