package com.foliore.shared.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubParserTest {
    @Test
    fun parsesContainerOpfMetadataAndCover() {
        val archive = FakeArchive(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/package.opf" to """
                    <package version="3.0">
                      <metadata>
                        <dc:title>The Last Light</dc:title>
                        <dc:creator>Julian Hartwell</dc:creator>
                        <dc:language>en</dc:language>
                      </metadata>
                      <manifest>
                        <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                      </manifest>
                      <spine>
                        <itemref idref="nav"/>
                      </spine>
                    </package>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertTrue(result.isReadable)
        assertEquals("OPS/package.opf", result.technicalInfo.opfPath)
        assertEquals("The Last Light", result.metadata.title)
        assertEquals(listOf("Julian Hartwell"), result.metadata.authors)
        assertEquals("OPS/images/cover.jpg", result.coverPath)
        assertEquals("OPS/nav.xhtml", result.technicalInfo.navPath)
    }

    private class FakeArchive(private val entries: Map<String, String>) : EpubArchive {
        override fun listEntries(): List<String> = entries.keys.toList()

        override fun readText(path: String): String? = entries[path]

        override fun readBytes(path: String): ByteArray? = entries[path]?.encodeToByteArray()
    }
}

