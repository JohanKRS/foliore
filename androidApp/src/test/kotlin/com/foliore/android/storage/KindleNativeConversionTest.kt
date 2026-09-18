package com.foliore.android.storage

import com.foliore.shared.epub.EpubParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before

/** Run with -PconversionNativeLibraryDir=<host CMake build directory>. */
class KindleNativeConversionTest {
    @Before fun requireNativeBuild() { assumeTrue(System.getProperty("foliore.nativeConversionTests") == "true") }

    @Test fun legacyMobi() = checkBook("legacy.mobi")
    @Test fun azw3WithChaptersImagesAndLinks() = checkBook("kindle.azw3")
    @Test fun hybridUsesKf8() = checkBook("hybrid.mobi")

    @Test fun drmIsRejectedWithoutChangingTheSource() = withDirectory { root ->
        val bytes = fixture("legacy.mobi").readBytes()
        val recordStart = ByteBuffer.wrap(bytes, 78, 4).int
        bytes[recordStart + 12] = 0
        bytes[recordStart + 13] = 2
        val source = File(root, "protected.mobi").apply { writeBytes(bytes) }
        val hash = source.sha256()
        val error = assertFailsWith<EbookConversionException> { KindleEpubConverter().convert(source, File(root, "out.epub")) }
        assertEquals(ImportFailureReason.DRM_PROTECTED, error.reason)
        assertEquals(hash, source.sha256())
        assertEquals(listOf(source), root.listFiles()!!.toList())
    }

    @Test fun truncatedAndUnrelatedInputsFailCleanly() = withDirectory { root ->
        for (bytes in listOf("not a book".toByteArray(), fixture("kindle.azw3").readBytes().copyOf(90))) {
            val source = File(root, "invalid.azw3").apply { writeBytes(bytes) }
            assertFailsWith<EbookConversionException> { KindleEpubConverter().convert(source, File(root, "out.epub")) }
            assertEquals(listOf(source), root.listFiles()!!.toList())
        }
    }

    private fun checkBook(name: String) = withDirectory { root ->
        val source = fixture(name)
        val sourceHash = source.sha256()
        val result = File(root, "converted.epub")
        KindleEpubConverter().convert(source, result)
        assertEquals(sourceHash, source.sha256())
        AndroidZipEpubArchive(result).use { archive ->
            val parsed = EpubParser().parse(archive)
            assertTrue(parsed.isReadable)
            assertEquals("Conversão Foliore", parsed.metadata.title)
            assertTrue("Equipe Foliore" in parsed.metadata.authors)
            assertNotNull(parsed.coverPath)
            assertNotNull(archive.readBytes(parsed.coverPath!!))
            assertTrue(parsed.technicalInfo.hasNcx)
        }
        ZipFile(result).use { zip ->
            val documents = zip.entries().asSequence().filter { it.name.endsWith(".html") }.toList()
            val text = documents.joinToString { entry ->
                val bytes = zip.getInputStream(entry).readBytes()
                DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(bytes.inputStream())
                val html = Jsoup.parse(bytes.toString(Charsets.UTF_8))
                html.select("a[href], img[src], link[href]").forEach { element ->
                    val reference = element.attr(if (element.tagName() == "img") "src" else "href")
                    if (reference.isNotEmpty() && !reference.contains(':')) {
                        val path = reference.substringBefore('#').ifEmpty { entry.name.substringAfterLast('/') }
                        val referenced = assertNotNull(zip.getEntry("OEBPS/$path"), "$name: broken reference $reference")
                        if ('#' in reference && reference.substringAfter('#').isNotEmpty()) {
                            val target = Jsoup.parse(zip.getInputStream(referenced).readBytes().toString(Charsets.UTF_8))
                            val id = reference.substringAfter('#')
                            assertTrue(target.getElementById(id) != null || target.getElementsByAttributeValue("name", id).isNotEmpty(), "$name: missing anchor $reference")
                        }
                    }
                }
                bytes.toString(Charsets.UTF_8)
            }
            assertTrue(text.contains("coração"), name)
            assertTrue(text.contains("Última página"), name)
            assertTrue(text.contains("href="), name)
            assertTrue(text.contains("<img"), name)
        }
        val second = File(root, "again.epub")
        KindleEpubConverter().convert(source, second)
        assertEquals(result.sha256(), second.sha256())
    }

    private fun fixture(name: String) = File("src/test/resources/conversion", name)
    private fun withDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("foliore-native-conversion").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
