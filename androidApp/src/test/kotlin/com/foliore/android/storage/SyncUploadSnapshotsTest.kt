package com.foliore.android.storage

import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class SyncUploadSnapshotsTest {
    @Test fun nextSessionRemovesAbandonedSnapshotsButKeepsUnrelatedFiles() {
        val cache = createTempDirectory().toFile()
        try {
            val root = File(cache, "sync-upload").apply { mkdirs() }
            val abandoned = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            File(abandoned, "original.epub").writeText("abandoned snapshot")
            val unrelated = File(root, "other-cache").apply { mkdirs() }
            SyncUploadSnapshots.withDirectory(cache) {
                assertFalse(abandoned.exists())
                assertTrue(unrelated.isDirectory)
                assertTrue(it.isDirectory)
            }
        } finally { cache.deleteRecursively() }
    }

    @Test fun overlappingSessionCannotDeleteAnActiveSnapshot() {
        val cache = createTempDirectory().toFile()
        try {
            SyncUploadSnapshots.withDirectory(cache) { first ->
                val epub = File(first, "original.epub").apply { writeText("active") }
                SyncUploadSnapshots.withDirectory(cache) { second ->
                    assertNotEquals(first, second)
                    assertEquals("active", epub.readText())
                }
                assertEquals("active", epub.readText())
            }
            assertTrue(File(cache, "sync-upload").listFiles()!!.isEmpty())
        } finally { cache.deleteRecursively() }
    }

    @Test fun failedOperationReleasesItsSnapshot() {
        val cache = createTempDirectory().toFile()
        var snapshot: File? = null
        try {
            assertFailsWith<IllegalStateException> {
                SyncUploadSnapshots.withDirectory(cache) {
                    snapshot = it
                    File(it, "partial.epub").writeText("partial")
                    error("interrupted sync")
                }
            }
            assertFalse(snapshot!!.exists())
            SyncUploadSnapshots.withDirectory(cache) { assertTrue(it.isDirectory) }
        } finally { cache.deleteRecursively() }
    }
}
