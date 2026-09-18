package com.foliore.android.storage

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class KindleBatchSenderTest {
    private fun stagedFile(size: Long = 100): File = File.createTempFile("batch-test", ".epub").apply {
        RandomAccessFile(this, "rw").use { it.setLength(size) }
    }

    @Test fun sixtyBooksUseThreeMessagesWithNoDuplicatesAndBoundedStaging() = runBlocking {
        val sizes = mutableListOf<Int>()
        val files = mutableListOf<File>()
        var peak = 0
        val ids = (1..60).map(Int::toString)
        val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(ids + "1",
            prepare = { stagedFile().also { files += it; peak = maxOf(peak, files.count(File::exists)) } },
            deliver = { sizes += it.size; KindleSendResult.SENT })
        assertEquals(listOf(25, 25, 10), sizes)
        assertEquals(60, report.sentCount)
        assertEquals(3, report.emailCount)
        assertEquals(ids, report.items.map { it.bookId })
        assertEquals(26, peak)
        assertTrue(files.none(File::exists))
        assertTrue(report.retryableIds.isEmpty())
    }

    @Test fun splitsByCombinedSizeBeforeCountLimit() = runBlocking {
        val sizes = mutableListOf<Int>()
        val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(listOf("a", "b", "c"),
            prepare = { stagedFile(12_000_000) }, deliver = { sizes += it.size; KindleSendResult.SENT })
        assertEquals(listOf(2, 1), sizes)
        assertEquals(3, report.sentCount)
    }

    @Test fun smtpSplitsByEncodedMessageSize() = runBlocking {
        val sizes = mutableListOf<Int>()
        KindleBatchSender(KindleMessageLimits.SMTP_BATCH).send(listOf("a", "b"),
            prepare = { stagedFile(9_000_000) }, deliver = { sizes += it.size; KindleSendResult.SENT })
        assertEquals(listOf(1, 1), sizes)
    }

    @Test fun largeAndBrokenBooksDoNotPreventValidBooksFromSending() = runBlocking {
        var calls = 0
        val files = mutableListOf<File>()
        val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(listOf("broken", "large", "good"),
            prepare = { when (it) {
                "broken" -> error("Bad EPUB")
                "large" -> stagedFile(24_000_001).also { f -> files += f }
                else -> stagedFile().also { f -> files += f }
            } }, deliver = { calls++; assertEquals(1, it.size); KindleSendResult.SENT })
        assertEquals(listOf(KindleBookDelivery.PREPARATION_FAILED, KindleBookDelivery.TOO_LARGE, KindleBookDelivery.SENT), report.items.map { it.delivery })
        assertEquals(setOf("broken", "large"), report.retryableIds)
        assertEquals(1, calls)
        assertTrue(files.none(File::exists))
    }

    @Test fun lostSecondResponseStopsBatchAndDoesNotSelectUncertainBooksForRetry() = runBlocking {
        var calls = 0
        val files = mutableListOf<File>()
        val ids = (1..60).map(Int::toString)
        val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(ids,
            prepare = { stagedFile().also { f -> files += f } },
            deliver = { calls++; if (calls == 1) KindleSendResult.SENT else KindleSendResult.UNKNOWN })
        assertEquals(2, calls)
        assertEquals(25, report.sentCount)
        assertEquals(25, report.items.count { it.delivery == KindleBookDelivery.UNKNOWN })
        assertEquals(ids.drop(50).toSet(), report.retryableIds)
        assertEquals(1, report.emailCount)
        assertEquals(KindleSendResult.UNKNOWN, report.failure)
        assertTrue(files.none(File::exists))
    }

    @Test fun rejectionStopsFollowingMessagesAndKeepsOnlyUnsentSelected() = runBlocking {
        for (failure in listOf(KindleSendResult.RATE_LIMITED, KindleSendResult.AUTH_REQUIRED, KindleSendResult.CONFIGURATION_REQUIRED, KindleSendResult.FAILED)) {
            var calls = 0
            val ids = (1..60).map(Int::toString)
            val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(ids,
                prepare = { stagedFile() }, deliver = { calls++; if (calls == 1) KindleSendResult.SENT else failure })
            assertEquals(2, calls)
            assertEquals(25, report.sentCount)
            assertEquals(ids.drop(25).toSet(), report.retryableIds)
            assertEquals(failure, report.failure)
        }
    }

    @Test fun thrownDeliveryExceptionIsUnknownAndNotRetried() = runBlocking {
        var calls = 0
        val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(listOf("a", "b"),
            prepare = { stagedFile() }, deliver = { calls++; error("Lost connection") })
        assertEquals(1, calls)
        assertEquals(KindleSendResult.UNKNOWN, report.failure)
        assertTrue(report.retryableIds.isEmpty())
    }

    @Test fun cancellationCleansPendingAttachmentsAndNeverContinuesSending() = runBlocking {
        val files = mutableListOf<File>()
        var calls = 0
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send((1..60).map(Int::toString),
                prepare = { stagedFile().also { f -> files += f } },
                deliver = { calls++; throw kotlinx.coroutines.CancellationException() })
        }
        assertEquals(1, calls)
        assertTrue(files.none(File::exists))
    }

    @Test fun emptySelectionDoesNotOpenConnection() = runBlocking {
        val report = KindleBatchSender(KindleMessageLimits.GMAIL_BATCH).send(emptyList(),
            prepare = { error("No preparation") }, deliver = { error("No sending") })
        assertTrue(report.items.isEmpty())
        assertEquals(0, report.emailCount)
    }
}
