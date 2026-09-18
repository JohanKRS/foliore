package com.foliore.android.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OriginalFileArchiveTest {
    @Test fun keepsEveryFormatAndOriginalNameAndDeletesOnlyTheSelectedArchive() = inDirectory { root ->
        val archive = OriginalFileArchive(File(root, "originals"))
        val sources = listOf("epub", "mobi", "azw3", "pdf").map { extension ->
            File(root, "library.$extension").apply { writeText("Original content $extension") }
        }
        archive.initialize("book-1", sources.map { OriginalFileSource(it, "Meu livro.${it.extension}") })
        val entries = archive.list("book-1")
        assertEquals(4, entries.size)
        entries.zip(sources).forEach { (entry, source) ->
            assertEquals("Meu livro.${source.extension}", entry.name)
            assertContentEquals(source.readBytes(), archive.file("book-1", entry.id)!!.readBytes())
        }
        assertTrue(archive.delete("book-1", entries.first().id))
        assertEquals(3, archive.list("book-1").size)
        assertNull(archive.file("book-1", entries.first().id))
        assertEquals("Original content epub", sources.first().readText())
    }

    @Test fun deletionSurvivesReloadAndCannotBeUndoneByLegacyMigration() = inDirectory { root ->
        val source = File(root, "original.epub").apply { writeText("Library copy") }
        val archiveRoot = File(root, "originals")
        val archive = OriginalFileArchive(archiveRoot)
        archive.initialize("book-2", listOf(OriginalFileSource(source, "Original.epub")))
        assertTrue(archive.delete("book-2", archive.list("book-2").single().id))
        val reloaded = OriginalFileArchive(archiveRoot)
        reloaded.initialize("book-2", listOf(OriginalFileSource(source, "Original.epub")))
        assertTrue(reloaded.isInitialized("book-2"))
        assertTrue(reloaded.list("book-2").isEmpty())
        assertTrue(source.isFile)
    }

    @Test fun replacingTheWorkingCopyDoesNotChangeTheArchive() = inDirectory { root ->
        val source = File(root, "original.epub").apply { writeText("Imported bytes") }
        val archive = OriginalFileArchive(File(root, "originals"))
        archive.initialize("book-3", listOf(OriginalFileSource(source, "Before.epub")))
        source.writeText("Edited or downloaded library version")
        assertEquals("Imported bytes", archive.file("book-3", archive.list("book-3").single().id)!!.readText())
    }

    @Test fun rejectsTraversalAndUnlistedFiles() = inDirectory { root ->
        val archiveRoot = File(root, "originals")
        val archive = OriginalFileArchive(archiveRoot)
        val source = File(root, "private.epub").apply { writeText("Keep") }
        assertFailsWith<IllegalArgumentException> { archive.initialize("../escape", listOf(OriginalFileSource(source, "file"))) }
        archive.initialize("book-4", listOf(OriginalFileSource(source, "../../private.epub")))
        val unlisted = File(archiveRoot, "book-4/00000000-0000-0000-0000-000000000000.epub").apply { writeText("Unlisted") }
        assertFalse(archive.delete("book-4", "../private.epub"))
        assertFalse(archive.delete("book-4", unlisted.name))
        assertNull(archive.file("book-4", unlisted.name))
        assertTrue(source.isFile)
        assertEquals("private.epub", archive.list("book-4").single().name)
    }

    @Test fun failedArchivalLeavesSourceAndNoPartialArchive() = inDirectory { root ->
        val archiveRoot = File(root, "originals")
        val archive = OriginalFileArchive(archiveRoot)
        val source = File(root, "valid.mobi").apply { writeText("Keep me") }
        assertFailsWith<IllegalArgumentException> {
            archive.initialize("book-5", listOf(OriginalFileSource(source, source.name), OriginalFileSource(File(root, "missing"), "missing")))
        }
        assertTrue(source.isFile)
        assertFalse(archive.isInitialized("book-5"))
        assertTrue(archiveRoot.listFiles().orEmpty().isEmpty())
    }

    @Test fun deletingABookCleansItsArchiveWithoutTouchingOtherBooks() = inDirectory { root ->
        val archive = OriginalFileArchive(File(root, "originals"))
        val source = File(root, "book.azw3").apply { writeText("Keep") }
        listOf("one", "two").forEach { archive.initialize(it, listOf(OriginalFileSource(source, source.name))) }
        archive.deleteBook("one")
        assertFalse(archive.isInitialized("one"))
        assertNotNull(archive.file("two", archive.list("two").single().id))
    }

    @Test fun conflictCopiesPreserveOriginalsAndDuplicateAdoptionRespectsDeletion() = inDirectory { root ->
        val archive = OriginalFileArchive(File(root, "originals"))
        val source = File(root, "book.mobi").apply { writeText("Original") }
        archive.initialize("source", listOf(OriginalFileSource(source, source.name)))
        archive.copyBook("source", "conflict")
        archive.deleteBook("source")
        val original = archive.list("conflict").single()
        assertEquals("Original", archive.file("conflict", original.id)!!.readText())
        archive.initialize("retained", emptyList())
        archive.copyBook("conflict", "retained")
        assertTrue(archive.list("retained").isEmpty())
        archive.delete("conflict", original.id)
        archive.copyBook("conflict", "deleted-conflict")
        assertTrue(archive.isInitialized("deleted-conflict"))
        assertTrue(archive.list("deleted-conflict").isEmpty())
    }

    private fun inDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("foliore-originals-test").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
