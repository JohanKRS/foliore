package com.foliore.android.storage

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class EpubContentFingerprintTest {
    @Test
    fun ignoresMutableMetadataCoverNavigationAndZipEntryOrder() {
        withTempDirectory { directory ->
            val first = directory.resolve("first.epub")
            val second = directory.resolve("second.epub")
            first.writeZip(
                listOf(
                    "mimetype" to "application/epub+zip",
                    "EPUB/package.opf" to "<metadata><title>Old title</title></metadata>",
                    "EPUB/nav.xhtml" to "<nav>Old title</nav>",
                    "EPUB/toc.ncx" to "<docTitle>Old title</docTitle>",
                    "EPUB/images/cover.jpg" to "old-cover",
                    "EPUB/chapter-1.xhtml" to "<p>The actual story.</p>",
                    "EPUB/styles/book.css" to "p { margin: 1em; }",
                ),
            )
            second.writeZip(
                listOf(
                    "EPUB/styles/book.css" to "p { margin: 1em; }",
                    "EPUB/chapter-1.xhtml" to "<p>The actual story.</p>",
                    "EPUB/images/cover.jpg" to "new-cover",
                    "EPUB/toc.ncx" to "<docTitle>New title</docTitle>",
                    "EPUB/nav.xhtml" to "<nav>New title</nav>",
                    "EPUB/package.opf" to "<metadata><title>New title</title></metadata>",
                    "mimetype" to "application/epub+zip",
                ),
            )

            val firstFingerprint = first.fingerprint()
            val secondFingerprint = second.fingerprint()

            assertNotNull(firstFingerprint)
            assertEquals(firstFingerprint, secondFingerprint)
        }
    }

    @Test
    fun detectsChangedBookContent() {
        withTempDirectory { directory ->
            val first = directory.resolve("first.epub")
            val second = directory.resolve("second.epub")
            first.writeZip(baseEntries(chapter = "<p>First text.</p>"))
            second.writeZip(baseEntries(chapter = "<p>Changed text.</p>"))

            assertNotEquals(first.fingerprint(), second.fingerprint())
        }
    }

    private fun File.fingerprint(): String? {
        return EpubContentFingerprint.calculate(
            file = this,
            opfPath = "EPUB/package.opf",
            coverPath = "EPUB/images/cover.jpg",
            navPath = "EPUB/nav.xhtml",
            ncxPath = "EPUB/toc.ncx",
        )
    }

    private fun baseEntries(chapter: String): List<Pair<String, String>> {
        return listOf(
            "mimetype" to "application/epub+zip",
            "EPUB/package.opf" to "<metadata><title>Book</title></metadata>",
            "EPUB/nav.xhtml" to "<nav>Book</nav>",
            "EPUB/toc.ncx" to "<docTitle>Book</docTitle>",
            "EPUB/images/cover.jpg" to "cover",
            "EPUB/chapter-1.xhtml" to chapter,
        )
    }

    private fun File.writeZip(entries: List<Pair<String, String>>) {
        ZipOutputStream(outputStream().buffered()).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
    }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("foliore-fingerprint-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
