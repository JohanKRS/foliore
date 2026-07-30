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

    @Test
    fun prefersPublicationYearOverModificationDate() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "content.opf" to """
                    <package version="2.0">
                      <metadata>
                        <dc:title>Publication Date Test</dc:title>
                        <dc:date opf:event="modification">2026-06-14T01:23:45Z</dc:date>
                        <dc:date opf:event="publication">2011-04-12</dc:date>
                      </metadata>
                    </package>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals("2011", result.metadata.publishedDate)
    }

    @Test
    fun ignoresModificationOnlyDates() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "content.opf" to """
                    <package version="3.0">
                      <metadata>
                        <dc:title>Modified Only</dc:title>
                        <meta property="dcterms:modified">2026-06-14T01:23:45Z</meta>
                      </metadata>
                    </package>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals(null, result.metadata.publishedDate)
    }

    @Test
    fun normalizesNeutralDateToYear() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "content.opf" to """
                    <package version="3.0">
                      <metadata>
                        <dc:title>Neutral Date</dc:title>
                        <dc:date>1999-08-07T00:00:00Z</dc:date>
                      </metadata>
                    </package>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals("1999", result.metadata.publishedDate)
    }

    @Test
    fun extractsIsbnFromMultipleIdentifiers() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "content.opf" to """
                    <package version="3.0">
                      <metadata>
                        <dc:title>Identifier Test</dc:title>
                        <dc:identifier id="uuid">urn:uuid:12345678-1234-1234-1234-123456789abc</dc:identifier>
                        <dc:identifier id="isbn" opf:scheme="ISBN">978-0-306-40615-7</dc:identifier>
                      </metadata>
                    </package>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals("urn:uuid:12345678-1234-1234-1234-123456789abc", result.metadata.identifier)
        assertEquals("9780306406157", result.metadata.isbn)
        assertEquals("978-0-306-40615-7", result.metadata.identifiers["isbn"])
    }

    @Test
    fun resolvesEpub2GuideCoverPageImage() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/content.opf" to """
                    <package version="2.0">
                      <metadata>
                        <dc:title>Guide Cover Test</dc:title>
                      </metadata>
                      <manifest>
                        <item id="titlepage" href="Text/titlepage.xhtml" media-type="application/xhtml+xml"/>
                        <item id="image001" href="Images/9780000000000.jpg" media-type="image/jpeg"/>
                      </manifest>
                      <guide>
                        <reference type="cover" title="Cover" href="Text/titlepage.xhtml"/>
                      </guide>
                    </package>
                """.trimIndent(),
                "OEBPS/Text/titlepage.xhtml" to """
                    <html>
                      <body>
                        <img alt="Cover" src="../Images/9780000000000.jpg"/>
                      </body>
                    </html>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals("OEBPS/Images/9780000000000.jpg", result.coverPath)
        assertEquals("OEBPS/Images/9780000000000.jpg", result.technicalInfo.coverHref)
    }

    @Test
    fun resolvesEpub2GuideSvgImageReference() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OPS/package.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/package.opf" to """
                    <package version="2.0">
                      <metadata><dc:title>SVG Guide Test</dc:title></metadata>
                      <manifest>
                        <item id="coverpage" href="cover.xhtml" media-type="application/xhtml+xml"/>
                        <item id="art" href="art/front.svg" media-type="image/svg+xml"/>
                      </manifest>
                      <guide>
                        <reference type="cover" href="cover.xhtml"/>
                      </guide>
                    </package>
                """.trimIndent(),
                "OPS/cover.xhtml" to """
                    <html>
                      <body>
                        <svg><image xlink:href="art/front.svg"/></svg>
                      </body>
                    </html>
                """.trimIndent(),
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals("OPS/art/front.svg", result.coverPath)
    }

    @Test
    fun findsUndeclaredCoverFileInMalformedEpub() {
        val archive = FakeArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/content.opf" to """
                    <package version="2.0">
                      <metadata><dc:title>Malformed Cover Test</dc:title></metadata>
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                    </package>
                """.trimIndent(),
                "OEBPS/cover.jpg" to "fake image bytes",
                "OEBPS/logo.jpg" to "fake logo bytes",
            ),
        )

        val result = EpubParser().parse(archive)

        assertEquals("OEBPS/cover.jpg", result.coverPath)
        assertEquals("OEBPS/cover.jpg", result.technicalInfo.coverHref)
    }

    private class FakeArchive(private val entries: Map<String, String>) : EpubArchive {
        override fun listEntries(): List<String> = entries.keys.toList()

        override fun readText(path: String): String? = entries[path]

        override fun readBytes(path: String): ByteArray? = entries[path]?.encodeToByteArray()
    }
}
