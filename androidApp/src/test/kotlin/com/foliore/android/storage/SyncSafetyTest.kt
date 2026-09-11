package com.foliore.android.storage

import java.io.File
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.json.*

class SyncSafetyTest {
    private fun book(id: String, revision: Int = 1, device: String = "a", deleted: Boolean = false) = buildJsonObject {
        put("bookId", id); put("revision", revision); put("updatedByDeviceId", device); put("deleted", deleted)
    }
    private fun manifest(vararg books: JsonObject) = buildJsonObject { put("updatedAt", "2026-09-07"); put("books", JsonArray(books.toList())) }
    private fun commit(id: String, parents: List<String>, vararg books: JsonObject) =
        RemoteManifestJournal.Commit(id, parents, manifest(*books))
    private fun List<RemoteManifestJournal.Commit>.mergedBooks() = RemoteManifestJournal.merge(this)["books"]!!.jsonArray

    @Test fun laterCausalEditWinsEvenWithSkewedClocksAndInputOrder() {
        val first = commit("1", emptyList(), book("book", 1))
        val later = commit("2", listOf("1"), book("book", 2, "b"))
        assertEquals(JsonArray(listOf(book("book", 2, "b"))), listOf(later, first).mergedBooks())
    }
    @Test fun independentOfflineEditsSurvive() {
        val commits = listOf(commit("1", emptyList(), book("one")), commit("2", emptyList(), book("two")))
        assertEquals(setOf("one", "two"), commits.mergedBooks().map { it.jsonObject["bookId"]!!.jsonPrimitive.content }.toSet())
    }
    @Test fun concurrentSameBookEditsProduceStableRecoveryCopy() {
        val commits = listOf(commit("1", emptyList(), book("book", 2, "a")), commit("2", emptyList(), book("book", 2, "b")))
        assertEquals(commits.mergedBooks(), commits.reversed().mergedBooks())
        assertEquals(2, commits.mergedBooks().size)
        assertEquals(1, commits.mergedBooks().count { "conflictOriginId" in it.jsonObject })
    }
    @Test fun deletionDoesNotDestroyConcurrentOfflineEdit() {
        val commits = listOf(commit("1", emptyList(), book("book", deleted = true)), commit("2", emptyList(), book("book", 2, "b")))
        assertEquals(2, commits.mergedBooks().size)
        assertTrue(commits.mergedBooks().any { it.jsonObject["deleted"]!!.jsonPrimitive.boolean })
        assertTrue(commits.mergedBooks().any { "conflictOriginId" in it.jsonObject })
    }
    @Test fun mergesIndependentMetadataFieldsButNotConflictingValues() {
        val base = buildJsonObject { put("title", "Original"); put("authors", "First"); put("revision", 1) }
        fun version(title: String, author: String) = JsonObject(book("book", 2) + mapOf(
            "mergeBase" to base, "metadataSnapshot" to JsonObject(base + mapOf("title" to JsonPrimitive(title), "authors" to JsonPrimitive(author))),
            "originalHashSha256" to JsonPrimitive("same"), "coverHash" to JsonPrimitive("same-cover")))
        val merged = RemoteManifestJournal.mergeIndependentMetadata(listOf(version("New title", "First"), version("Original", "New author")))!!
        assertEquals("New title", merged["metadataSnapshot"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("New author", merged["metadataSnapshot"]!!.jsonObject["authors"]!!.jsonPrimitive.content)
        assertNull(RemoteManifestJournal.mergeIndependentMetadata(listOf(version("One", "First"), version("Two", "First"))))
        assertNotNull(RemoteManifestJournal.mergeIndependentMetadata(listOf(
            JsonObject(version("New title", "First") - "coverHash"),
            JsonObject(version("Original", "New author") + ("coverHash" to JsonNull)))))
    }

    @Test fun explicitRecoveryEditsAndDeletesWinRegardlessOfListingOrder() {
        val first = commit("a", emptyList(), book("book", 2, "a"))
        val second = commit("b", emptyList(), book("book", 2, "b"))
        val recovery = listOf(first, second).mergedBooks().map { it.jsonObject }.single { "conflictOriginId" in it }
        val id = recovery.getValue("bookId").jsonPrimitive.content
        for (deleted in listOf(false, true)) {
            val updated = book(id, 3, "c", deleted)
            val third = commit("c", listOf("a", "b"), updated)
            for (order in listOf(listOf(first, second, third), listOf(third, second, first))) {
                assertEquals(updated, order.mergedBooks().single { it.jsonObject["bookId"]!!.jsonPrimitive.content == id })
            }
        }
    }

    @Test fun interruptedPublicationDoesNotHidePreviousCommitAndGcPreservesActiveData() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = TestStore(root)
            val first = RemoteManifestJournal(store)
            assertNull(first.read())
            val active = RemoteManifestJournal.DATA_PREFIX + "active"
            val payload = JsonObject(book("book") + ("originalFile" to JsonPrimitive("$active/original.epub")))
            first.publish(manifest(payload).toString())
            val old = System.currentTimeMillis() - RemoteManifestJournal.RETENTION_MILLIS - 10000
            File(root, active).apply { mkdirs(); setLastModified(old) }
            val abandoned = File(root, RemoteManifestJournal.DATA_PREFIX + "abandoned").apply { mkdirs(); setLastModified(old) }
            val retry = RemoteManifestJournal(store)
            assertNotNull(retry.read())
            store.interruptMarker = true
            assertFails { retry.publish(manifest(book("book", 2)).toString()) }
            val next = RemoteManifestJournal(store)
            val restored = Json.parseToJsonElement(next.read()!!).jsonObject
            assertEquals(payload, restored["books"]!!.jsonArray.single())
            next.collectGarbage()
            assertTrue(File(root, active).isDirectory)
            assertFalse(abandoned.exists())
            store.interruptMarker = false
            next.publish(manifest(book("book", 3)).toString())
            assertEquals(3, Json.parseToJsonElement(RemoteManifestJournal(store).read()!!).jsonObject["books"]!!.jsonArray.single().jsonObject["revision"]!!.jsonPrimitive.int)
        } finally { root.deleteRecursively() }
    }

    @Test fun deletingBackupInvalidatesLateWriterAndAllowsFreshBackup() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = TestStore(root)
            val first = RemoteManifestJournal(store).also { it.read() }
            first.publish(manifest(book("old")).toString())
            val staleSnapshot = RemoteManifestJournal(store).snapshot()
            assertNotNull(staleSnapshot.text)
            val stale = staleSnapshot.journal
            store.writeText("library.deleted.json", "new deletion epoch")
            assertFails { stale.publish(manifest(book("old", 2)).toString()) }
            assertNull(RemoteManifestJournal(store).read())
            val fresh = RemoteManifestJournal(store).also { it.read() }
            fresh.publish(manifest(book("new")).toString())
            val books = Json.parseToJsonElement(RemoteManifestJournal(store).read()!!).jsonObject["books"]!!.jsonArray
            assertEquals("new", books.single().jsonObject["bookId"]!!.jsonPrimitive.content)
        } finally { root.deleteRecursively() }
    }

    @Test fun preparedSnapshotKeepsConcurrentChangesOutsideItsObservedParents() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = TestStore(root)
            RemoteManifestJournal(store).snapshot().journal.publish(manifest(book("one")).toString())
            val prepared = RemoteManifestJournal(store).snapshot()
            val concurrent = RemoteManifestJournal(store).snapshot()
            concurrent.journal.publish(manifest(book("one", 2, "b")).toString())
            // Publishing the prepared view must not claim to supersede the unseen concurrent edit.
            prepared.journal.publish(manifest(book("one", 2, "a")).toString())
            val books = Json.parseToJsonElement(RemoteManifestJournal(store).read()!!).jsonObject["books"]!!.jsonArray
            assertEquals(2, books.size)
            assertEquals(1, books.count { "conflictOriginId" in it.jsonObject })
        } finally { root.deleteRecursively() }
    }

    @Test fun compressedOversizedEntryIsRejectedAndOrdinaryZipRemainsReadable() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            fun zip(name: String, size: Long): File = File(root, name).also { file ->
                java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("book.opf"))
                    val chunk = ByteArray(8192)
                    var remaining = size
                    while (remaining > 0) { val count = minOf(remaining, chunk.size.toLong()).toInt(); zip.write(chunk, 0, count); remaining -= count }
                    zip.closeEntry()
                }
            }
            val normal = zip("normal.epub", 100)
            AndroidZipEpubArchive(normal).use { assertEquals(100, it.readBytes("book.opf")!!.size) }
            val bomb = zip("oversized.epub", StorageLimits.ENTRY_BYTES + 1)
            assertTrue(bomb.length() < 100_000)
            assertFailsWith<IllegalArgumentException> { AndroidZipEpubArchive(bomb).close() }
        } finally { root.deleteRecursively() }
    }

    @Test fun garbageCollectionSkipsJournalDownloadsUntilOwnedObjectsExpire() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = TestStore(root)
            val journal = RemoteManifestJournal(store).also { it.read() }
            journal.publish(manifest(book("book")).toString())
            val now = System.currentTimeMillis()
            val recent = File(root, RemoteManifestJournal.DATA_PREFIX + "recent").apply { mkdirs() }
            File(root, "unrelated-old-directory").apply { mkdirs(); setLastModified(1) }
            store.textReads = 0
            journal.collectGarbage(now)
            assertEquals(0, store.textReads)
            assertTrue(recent.exists())
            journal.collectGarbage(now + RemoteManifestJournal.RETENTION_MILLIS + 10000)
            assertTrue(store.textReads > 0)
            assertFalse(recent.exists())
            assertTrue(File(root, "unrelated-old-directory").exists())
            assertNotNull(RemoteManifestJournal(store).read())
        } finally { root.deleteRecursively() }
    }

    @Test fun parallelJournalReadIsBoundedAndKeepsTheSameCausalResult() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val serial = TestStore(root)
            repeat(5) { revision ->
                RemoteManifestJournal(serial).also { it.read() }.publish(manifest(book("book", revision + 1)).toString())
            }
            val expected = RemoteManifestJournal(serial).read()
            val gate = java.util.concurrent.CountDownLatch(2)
            val active = java.util.concurrent.atomic.AtomicInteger()
            val peak = java.util.concurrent.atomic.AtomicInteger()
            val parallel = TestStore(root, maxConcurrentDownloads = 10, onRead = { path ->
                if (path.startsWith(RemoteManifestJournal.COMMIT_PREFIX)) {
                    val count = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, count) }
                    try {
                        gate.countDown()
                        check(gate.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Journal reads did not overlap" }
                    } finally { active.decrementAndGet() }
                }
            })
            assertEquals(expected, RemoteManifestJournal(parallel).read())
            assertTrue(peak.get() in 2..3)
        } finally { root.deleteRecursively() }
    }

    @Test fun parallelJournalReadRejectsAnyCorruptCommittedRecord() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val store = TestStore(root, maxConcurrentDownloads = 3)
            repeat(3) { revision ->
                RemoteManifestJournal(store).also { it.read() }.publish(manifest(book("book", revision + 1)).toString())
            }
            root.listFiles()!!.first { it.name.startsWith(RemoteManifestJournal.RECORD_PREFIX) }.appendText("corrupt")
            val failure = assertFails { RemoteManifestJournal(store).read() }
            assertTrue(generateSequence(failure) { it.cause }.any { it.message?.contains("checksum mismatch") == true })
        } finally { root.deleteRecursively() }
    }

    private class TestStore(
        private val root: File,
        override val maxConcurrentDownloads: Int = 1,
        private val onRead: (String) -> Unit = {},
    ) : RemoteLibraryStore {
        override val label = "test"
        var interruptMarker = false
        private val readCount = java.util.concurrent.atomic.AtomicInteger()
        var textReads: Int
            get() = readCount.get()
            set(value) { readCount.set(value) }
        override fun listRootEntries() = root.listFiles().orEmpty().map { RemoteRootEntry(it.name, it.isDirectory, it.lastModified()) }
        override fun readText(path: String): String? {
            readCount.incrementAndGet()
            onRead(path)
            return File(root, path).takeIf { it.isFile }?.readText()
        }
        override fun writeText(path: String, text: String, mimeType: String) {
            if (interruptMarker && path.startsWith(RemoteManifestJournal.COMMIT_PREFIX)) {
                File(root, path).writeText(text.take(7)); error("interrupted marker")
            }
            File(root, path).writeText(text)
        }
        override fun replaceFile(path: String, source: File, mimeType: String) { source.copyTo(File(root, path)) }
        override fun copyToLocal(path: String, destination: File) = File(root, path).takeIf { it.isFile }?.copyTo(destination, true)
        override fun hasDirectory(path: String) = File(root, path).isDirectory
        override fun ensureDirectory(path: String) { File(root, path).mkdirs() }
        override fun deleteTree(path: String) { File(root, path).deleteRecursively() }
        override fun deleteUnexpectedChildren(directoryPath: String, expectedDirectoryNames: Set<String>): Boolean = error("unsafe")
    }

    @Test fun rejectsTraversalBeforeCreatingFilesAndAcceptsBookIds() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            val sentinel = File(root, "sentinel").apply { writeText("keep") }
            val books = File(root, "books").apply { mkdirs() }
            listOf("../sentinel", "../../cache", "/tmp/x", "a/b", "a\\b", "", ".", "..").forEach {
                assertFailsWith<IllegalArgumentException> { safeChildDirectory(books, it) }
            }
            assertEquals("keep", sentinel.readText())
            assertEquals("book-1", safeChildDirectory(books, "book-1").name)
            requireSafeBookId(UUID.randomUUID().toString())
            listOf("../x", "books/../x", "/x", "a\\b", "a//b").forEach {
                assertFailsWith<IllegalArgumentException> { it.normalizedRemotePath() }
            }
            assertEquals("books/a/original.epub", "books/a/original.epub".normalizedRemotePath())
        } finally { root.deleteRecursively() }
    }
    @Test fun boundedReadsRejectOversizedAndHonorCombinedBudget() {
        assertContentEquals(byteArrayOf(1, 2), byteArrayOf(1, 2).inputStream().readBounded(2))
        assertFailsWith<IllegalArgumentException> { ByteArray(3).inputStream().readBounded(2) }
        val budget = ReadBudget(3)
        ByteArray(2).inputStream().readBounded(2, budget)
        assertFailsWith<IllegalArgumentException> { ByteArray(2).inputStream().readBounded(2, budget) }
    }
}
