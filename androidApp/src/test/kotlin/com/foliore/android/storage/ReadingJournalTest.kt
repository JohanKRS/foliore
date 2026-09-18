package com.foliore.android.storage

import com.foliore.android.reader.*
import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*

class ReadingJournalTest {
    private fun state(clock: Long, progress: Double) = ReadingState(listOf(ReadingPosition("book", "a".repeat(64), ReadingLocator("OPS/ch.xhtml",progress), clock, "a")))
    private fun manifest(reading: ReadingState? = null, revision: Int = 1, deleted: Boolean = false) = buildJsonObject {
        put("updatedAt", "2026-09-17T12:00:00Z")
        put("books", buildJsonArray { add(buildJsonObject { put("bookId","book");put("revision",revision);put("deleted",deleted) }) })
        reading?.let { put("readingState",ReadingStateSync.encode(it)) }
    }
    @Test fun readingChangesNeverCreateBookConflictsAndSurviveAnOlderClient() {
        val first = RemoteManifestJournal.Commit("first",emptyList(),manifest(state(1,.8)))
        val second = RemoteManifestJournal.Commit("second",listOf("first"),manifest(state(2,.2)))
        val oldClient = RemoteManifestJournal.Commit("old",listOf("first","second"),manifest(revision=2))
        val merged = RemoteManifestJournal.merge(listOf(first,second,oldClient))
        assertEquals(1,merged["books"]!!.jsonArray.size)
        assertEquals(.2,ReadingStateSync.read(merged).positions.single().locator.progression)
    }
    @Test fun deletedBooksDoNotRecoverTheirReadingHistoryFromOldCommits() {
        val first = RemoteManifestJournal.Commit("first",emptyList(),manifest(state(1,.8)))
        val deleted = RemoteManifestJournal.Commit("deleted",listOf("first"),manifest(deleted=true))
        assertEquals(ReadingState(),ReadingStateSync.read(RemoteManifestJournal.merge(listOf(first,deleted))))
    }
    @Test fun concurrentReadingAndBookmarksMergeAcrossTheJournal() {
        val marker = ReadingBookmark("mark","book","a".repeat(64),ReadingLocator("OPS/ch.xhtml",.1),"Note",3,"b")
        val a = RemoteManifestJournal.Commit("a",emptyList(),manifest(state(2,.7)))
        val b = RemoteManifestJournal.Commit("b",emptyList(),manifest(ReadingState(bookmarks=listOf(marker))))
        val merged = ReadingStateSync.read(RemoteManifestJournal.merge(listOf(a,b)))
        assertEquals(.7,merged.positions.single().locator.progression);assertEquals(marker,merged.bookmarks.single())
        assertEquals(merged,ReadingStateSync.read(RemoteManifestJournal.merge(listOf(b,a))))
    }
    @Test fun garbageCollectionKeepsReadingOnlyChangesUntilAnotherCommitCarriesThem() {
        val root=kotlin.io.path.createTempDirectory().toFile()
        try {
            val store=Store(root)
            RemoteManifestJournal(store).also { it.read() }.publish(manifest().toString())
            RemoteManifestJournal(store).also { it.read() }.publish(manifest(state(2,.7)).toString())
            // Simulate a client without reader support publishing after observing the reader commit.
            RemoteManifestJournal(store).also { it.read() }.publish(manifest(revision=2).toString())
            val old=System.currentTimeMillis()-RemoteManifestJournal.RETENTION_MILLIS-100000
            root.listFiles()!!.forEach { it.setLastModified(old) }
            RemoteManifestJournal(store).collectGarbage()
            val merged=Json.parseToJsonElement(RemoteManifestJournal(store).read()!!).jsonObject
            assertEquals(.7,ReadingStateSync.read(merged).positions.single().locator.progression)
        } finally { root.deleteRecursively() }
    }
    @Test fun recoversProviderRenamedMarkersWithoutLosingTheirRecordsDuringCollection() {
        val root=kotlin.io.path.createTempDirectory().toFile()
        try {
            val store=Store(root)
            RemoteManifestJournal(store).also { it.read() }.publish(manifest(state(2,.7)).toString())
            val marker=root.listFiles()!!.single { it.name.startsWith(RemoteManifestJournal.COMMIT_PREFIX) }
            check(marker.renameTo(File(root, marker.name + ".txt")))
            root.listFiles()!!.forEach { it.setLastModified(System.currentTimeMillis()-RemoteManifestJournal.RETENTION_MILLIS-100000) }
            RemoteManifestJournal(store).collectGarbage()
            val merged=Json.parseToJsonElement(RemoteManifestJournal(store).read()!!).jsonObject
            assertEquals(.7,ReadingStateSync.read(merged).positions.single().locator.progression)
            RemoteManifestJournal(store).also { it.read() }.publish(manifest(state(3,.2)).toString())
            assertEquals(.2,ReadingStateSync.read(Json.parseToJsonElement(RemoteManifestJournal(store).read()!!).jsonObject).positions.single().locator.progression)
        } finally { root.deleteRecursively() }
    }
    private class Store(val root:File):RemoteLibraryStore {
        override val label="test"
        override fun listRootEntries()=root.listFiles().orEmpty().map { RemoteRootEntry(it.name,it.isDirectory,it.lastModified()) }
        override fun readText(path:String)=File(root,path).takeIf { it.isFile }?.readText()
        override fun writeText(path:String,text:String,mimeType:String) { File(root,path).apply { parentFile!!.mkdirs();writeText(text) } }
        override fun replaceFile(path:String,source:File,mimeType:String) { source.copyTo(File(root,path),true) }
        override fun copyToLocal(path:String,destination:File)=File(root,path).takeIf { it.isFile }?.copyTo(destination,true)
        override fun hasDirectory(path:String)=File(root,path).isDirectory
        override fun ensureDirectory(path:String) { File(root,path).mkdirs() }
        override fun deleteTree(path:String) { File(root,path).deleteRecursively() }
        override fun deleteUnexpectedChildren(directoryPath:String,expectedDirectoryNames:Set<String>)=false
    }
}
