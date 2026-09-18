package com.foliore.android.storage

import com.foliore.shared.epub.EpubParser
import java.io.File
import java.nio.file.Files
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KindleEpubConverterTest {
    @Test
    fun packagesReadableXhtmlAndDeterministicEpubWithUncompressedMimetypeFirst() = inDirectory { root ->
        val input = File(root, "book.mobi").apply { writeText("source must stay intact") }
        val converter = KindleEpubConverter { _, target ->
            File(target, "content.opf").writeText(OPF)
            File(target, "part00000.html").writeText("<html><head><title>Title</title></head><body><p>Ol&aacute; &amp; texto<br><mbp:pagebreak/><p>Next<img src='resource00000.png'></body></html>")
            File(target, "resource00000.png").writeBytes(byteArrayOf(1, 2, 3))
            0
        }
        val first = File(root, "first.epub")
        val second = File(root, "second.epub")
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
            converter.convert(input, first)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            converter.convert(input, second)
        } finally { TimeZone.setDefault(zone) }
        assertContentEquals(first.readBytes(), second.readBytes())
        assertEquals("source must stay intact", input.readText())
        ZipFile(first).use { zip ->
            val entry = zip.entries().nextElement()
            assertEquals("mimetype", entry.name)
            assertEquals(ZipEntry.STORED, entry.method)
            assertTrue(entry.extra == null || entry.extra.isEmpty())
            val html = zip.getInputStream(zip.getEntry("OEBPS/part00000.html")).readBytes()
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(html.inputStream())
            assertTrue(html.toString(Charsets.UTF_8).contains("Olá"))
            assertTrue(html.toString(Charsets.UTF_8).contains("page-break-before"))
        }
        AndroidZipEpubArchive(first).use { archive ->
            val parsed = EpubParser().parse(archive)
            assertTrue(parsed.isReadable)
            assertEquals("Conversion test", parsed.metadata.title)
            assertEquals(1, parsed.technicalInfo.spineItemCount)
        }
        assertFalse(root.listFiles().orEmpty().any { it.isDirectory })
    }

    @Test
    fun rejectsDrmAndRemovesPartialOutputAndWorkingDirectory() = inDirectory { root ->
        val source = File(root, "book.azw3").apply { writeText("protected original") }
        val target = File(root, "book.epub")
        val converter = KindleEpubConverter { _, directory ->
            File(directory, "partial.html").writeText("partial")
            2
        }
        val error = assertFailsWith<EbookConversionException> { converter.convert(source, target) }
        assertEquals(ImportFailureReason.DRM_PROTECTED, error.reason)
        assertEquals(listOf(source), root.listFiles()!!.toList())
    }

    @Test
    fun rejectsUnusableConversionWithoutInsertingEmptyBook() = inDirectory { root ->
        val source = File(root, "book.mobi").apply { writeText("original") }
        val target = File(root, "book.epub")
        val converter = KindleEpubConverter { _, directory ->
            File(directory, "content.opf").writeText("<package/>")
            0
        }
        assertFailsWith<EbookConversionException> { converter.convert(source, target) }
        assertFalse(target.exists())
        assertEquals(listOf(source), root.listFiles()!!.toList())
    }

    private fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("foliore-conversion").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    companion object {
        private const val OPF = """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">conversion-test</dc:identifier><dc:title>Conversion test</dc:title><dc:creator>Foliore</dc:creator><dc:language>pt</dc:language></metadata><manifest><item id="page" href="part00000.html" media-type="application/xhtml+xml"/><item id="image" href="resource00000.png" media-type="image/png"/></manifest><spine><itemref idref="page"/></spine></package>"""
    }
}
