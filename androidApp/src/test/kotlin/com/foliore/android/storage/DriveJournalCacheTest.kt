package com.foliore.android.storage

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class DriveJournalCacheTest {
    private fun metadata(text: String) = DriveFileMetadata("record-id", "foliore-v4-record-test.json", "application/json",
        sizeText = text.toByteArray().size.toString(), version = "1",
        sha256Checksum = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) })

    @Test fun reusesPersistedBytesOnlyForTheSameAccountAndVersion() = temporary { root ->
        val file = metadata("record")
        assertEquals("record", DriveJournalCache(root, "account-a").read(file) { "record" })
        assertEquals("record", DriveJournalCache(root, "account-a").read(file) { error("Unexpected network read") })
        var downloads = 0
        DriveJournalCache(root, "account-a").read(file.copy(version = "2")) { downloads++; "record" }
        DriveJournalCache(root, "account-b").read(file) { downloads++; "record" }
        assertEquals(2, downloads)
    }

    @Test fun damagedCacheIsRefetchedAndDamagedRemoteCannotBeCached() = temporary { root ->
        val file = metadata("record")
        val cache = DriveJournalCache(root, "a")
        cache.read(file) { "record" }
        root.walkTopDown().filter { it.isFile }.forEach { it.writeText("broken") }
        var downloads = 0
        assertEquals("record", cache.read(file) { downloads++; "record" })
        assertEquals(1, downloads)
        assertFailsWith<IllegalStateException> { cache.read(file.copy(version = "2")) { "broken" } }
        assertEquals("record", cache.read(file.copy(version = "2")) { "record" })
    }

    @Test fun absentVersionOrChecksumAndDeletionMarkersAlwaysReadTheServer() = temporary { root ->
        val cache = DriveJournalCache(root, "a")
        val file = metadata("record")
        val uncached = listOf(file.copy(version = null), file.copy(sha256Checksum = null), file.copy(name = "library.deleted.json"))
        var downloads = 0
        repeat(2) { uncached.forEach { cache.read(it) { downloads++; "record" } } }
        assertEquals(6, downloads)
    }

    @Test fun freshListingEvictsDeletedRecordsAndOlderVersions() = temporary { root ->
        val cache = DriveJournalCache(root, "a")
        val first = metadata("one")
        val second = metadata("two").copy(id = "second")
        cache.read(first) { "one" }; cache.read(second) { "two" }
        cache.retain(listOf(second))
        assertEquals(1, root.walkTopDown().count { it.isFile })
        cache.read(second) { error("Unexpected download") }
        cache.retain(emptyList())
        assertEquals(0, root.walkTopDown().count { it.isFile })
    }

    @Test fun serverChecksumRequiresExactSizeAndHash() {
        val file = metadata("record")
        assertTrue(file.verifiesUpload(6, file.sha256Checksum!!))
        assertFalse(file.copy(sha256Checksum = null).verifiesUpload(6, file.sha256Checksum))
        assertFailsWith<IllegalStateException> { file.verifiesUpload(5, file.sha256Checksum) }
        assertFailsWith<IllegalStateException> { file.verifiesUpload(6, "0".repeat(64)) }
    }

    private fun temporary(test: (File) -> Unit) {
        val root = createTempDirectory().toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }
}
