package com.foliore.android.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class EpubPublicationTest {
    @Test fun resolvesEncodedPathsAndRejectsExternalOrTraversalResources() {
        assertEquals("OPS/chap 1.xhtml" to "note", EpubPublication.resolve("OPS/book.opf", "chap%201.xhtml#note"))
        assertEquals("images/cover.jpg" to null, EpubPublication.resolve("OPS/ch.xhtml", "../images/cover.jpg"))
        assertNull(EpubPublication.resolve("OPS/ch.xhtml", "../../private"))
        assertNull(EpubPublication.resolve("OPS/ch.xhtml", "https://example.org/tracker"))
        assertNull(EpubPublication.resolve("OPS/ch.xhtml", "//example.org/tracker"))
        assertFalse(EpubPublication.safePath("../private"))
    }
    @Test fun readsEpub3SpineAndNestedNavigationAndStripsActiveContent() = fixture(true) { file ->
        EpubPublication.open(file).use { publication ->
            assertEquals(listOf("OPS/one.xhtml", "OPS/two.xhtml"), publication.sections.map { it.href })
            assertEquals(listOf("One", "Two", "Subsection"), publication.toc.map { it.title })
            assertEquals(1,publication.toc.last().depth)
            assertEquals("sub",publication.toc.last().fragment)
            val html = publication.resource("OPS/one.xhtml")!!.bytes.toString(Charsets.UTF_8)
            assertFalse("alert(" in html); assertFalse("onclick" in html); assertFalse("<iframe" in html)
            assertTrue("Content-Security-Policy" in html); assertTrue("/_reader/runtime.js" in html)
            assertTrue("<em>emphasis</em>" in html); assertTrue("cover.svg" in html)
            assertTrue("needle" in publication.text(0))
            assertNull(publication.resource("../private"))
            assertEquals("A footnote",publication.linkedText("OPS/notes.xhtml", "note"))
        }
    }
    @Test fun readsEpub2NcxAndFixedLayoutDirection() = fixture(false) { file ->
        EpubPublication.open(file).use { p ->
            assertEquals("First",p.toc.first().title)
            assertTrue(p.rtl); assertTrue(p.sections.all { it.fixedLayout })
        }
    }
    @Test fun opensTheActualConversionFixture() {
        EpubPublication.open(File("src/test/resources/conversion/source.epub")).use { assertTrue(it.sections.isNotEmpty()); assertTrue(it.text(0).isNotBlank()) }
    }
    @Test fun protectsDeclaredEpub2AndEpub3CoversWithoutMarkingOtherIllustrations() {
        for (epub3 in listOf(false, true)) fixture(epub3, cover = true) { file ->
            EpubPublication.open(file).use { publication ->
                val doc = org.jsoup.Jsoup.parse(publication.resource("OPS/one.xhtml")!!.bytes.toString(Charsets.UTF_8))
                assertTrue(doc.selectFirst("img[src=cover.svg]")!!.hasAttr("data-foliore-cover"))
                assertFalse(doc.selectFirst("img[src=art.png]")!!.hasAttr("data-foliore-cover"))
                val semanticCover = org.jsoup.Jsoup.parse(publication.sanitize("<body epub:type='cover'><img src='art.png'/></body>", "OPS/two.xhtml"))
                assertTrue(semanticCover.body().hasAttr("data-foliore-cover"))
            }
        }
    }
    @Test fun enablesIllustrationAdaptationForExistingPreferencesAndPersistsOptOut() {
        val json = kotlinx.serialization.json.Json
        assertTrue(json.decodeFromString<ReaderPreferences>("{\"theme\":\"dark\"}").adaptIllustrations)
        val preferences = ReaderPreferences(adaptIllustrations = false)
        assertEquals(preferences, json.decodeFromString<ReaderPreferences>(json.encodeToString(ReaderPreferences.serializer(), preferences)))
    }
    @Test fun originalImageViewerUsesOriginalResourcesAndNeverTheReadingFilters() = fixture(true) { file ->
        EpubPublication.open(file).use { publication ->
            val document = org.jsoup.Jsoup.parse(publication.imageDocument("OPS/one.xhtml", 0)!!)
            assertEquals("https://appassets.androidplatform.net/publication/OPS/cover.svg", document.selectFirst("img")!!.attr("src"))
            assertTrue(document.select("link").isEmpty())
            assertEquals("https://appassets.androidplatform.net/_reader/image-viewer.js", document.select("script").single().attr("src"))
            assertTrue(document.select("script").single().attr("nonce").isNotBlank())
            assertFalse("invert(" in document.outerHtml()); assertFalse("mix-blend-mode" in document.outerHtml())
            assertNull(publication.imageDocument("OPS/one.xhtml", -1)); assertNull(publication.imageDocument("OPS/one.xhtml", 999))
            assertNull(publication.imageDocument("../private", 0))
            assertFalse("example.org" in publication.imageDocument("OPS/one.xhtml", 0, "https://example.org/tracker.png")!!)
        }
    }
    @Test fun originalImageViewerSupportsInlineDataAndSvgImageReferencesButBlocksExternalSources() {
        val data = "data:image/png;base64," + "A".repeat(10000)
        fixture(true, images = """<img src="$data"/><svg><image xlink:href="../images/a%20b.png"/></svg><img src="https://example.org/image.png"/><img srcset="large.png 2x"/>""") { file ->
            EpubPublication.open(file).use { publication ->
                assertEquals(data, org.jsoup.Jsoup.parse(publication.imageDocument("OPS/one.xhtml", 0)!!).selectFirst("img")!!.attr("src"))
                assertEquals("https://appassets.androidplatform.net/publication/images/a%20b.png", org.jsoup.Jsoup.parse(publication.imageDocument("OPS/one.xhtml", 1)!!).selectFirst("img")!!.attr("src"))
                assertNull(publication.imageDocument("OPS/one.xhtml", 2))
                val responsive = publication.imageDocument("OPS/one.xhtml", 3, "https://appassets.androidplatform.net/publication/OPS/large.png")!!
                assertEquals("https://appassets.androidplatform.net/publication/OPS/large.png", org.jsoup.Jsoup.parse(responsive).selectFirst("img")!!.attr("src"))
            }
        }
    }
    @Test fun decodesIdpfFontsWithoutChangingTheirStoredBytes() {
        val original = ByteArray(2048) { (it % 251).toByte() }
        val key = java.security.MessageDigest.getInstance("SHA-1").digest("urn:uuid:readertest".toByteArray())
        val encoded = original.copyOf().also { bytes -> for (i in 0 until 1040) bytes[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
        val encryption = """<encryption><EncryptedData><EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/><CipherData><CipherReference URI="OPS/font.otf"/></CipherData></EncryptedData></encryption>""".toByteArray()
        fixture(true, mapOf("OPS/font.otf" to encoded, "META-INF/encryption.xml" to encryption)) { file ->
            EpubPublication.open(file).use { publication -> assertContentEquals(original, publication.resource("OPS/font.otf")!!.bytes) }
            java.util.zip.ZipFile(file).use { zip -> assertContentEquals(encoded, zip.getInputStream(zip.getEntry("OPS/font.otf")).readBytes()) }
        }
    }
    private fun fixture(nav: Boolean, resources: Map<String, ByteArray> = emptyMap(), cover: Boolean = false,
        images: String = """<img src="cover.svg"/><img src="art.png"/>""", test: (File) -> Unit) {
        val file = kotlin.io.path.createTempFile(suffix = ".epub").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun put(path:String,text:String) { zip.putNextEntry(ZipEntry(path));zip.write(text.toByteArray());zip.closeEntry() }
                resources.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
                put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
                put("OPS/book.opf", """<package unique-identifier="book-id"><metadata><dc:identifier id="book-id"> urn:uuid: reader&#x9;test </dc:identifier><meta property="rendition:layout">${if(nav) "reflowable" else "pre-paginated"}</meta>${if (cover && !nav) "<meta name='cover' content='cover'/>" else ""}</metadata><manifest>${if (cover) "<item id='cover' href='cover.svg' media-type='image/svg+xml' properties='${if (nav) "cover-image" else ""}'/>" else ""}<item id="font" href="font.otf" media-type="font/otf"/><item id="a" href="one.xhtml" media-type="application/xhtml+xml"/><item id="b" href="two.xhtml" media-type="application/xhtml+xml"/><item id="note" href="notes.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="${if(nav) "nav" else ""}"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest><spine toc="ncx" page-progression-direction="rtl"><itemref idref="a"/><itemref idref="b"/></spine></package>""")
                put("OPS/nav.xhtml", """<html><body><nav epub:type="toc"><ol><li><a href="one.xhtml">One</a></li><li><a href="two.xhtml">Two</a><ol><li><a href="two.xhtml#sub">Subsection</a></li></ol></li></ol></nav></body></html>""")
                put("OPS/toc.ncx", """<ncx><navMap><navPoint><navLabel><text>First</text></navLabel><content src="one.xhtml"/></navPoint></navMap></ncx>""")
                put("OPS/one.xhtml", """<html><head><script>alert('bad')</script></head><body onclick="alert(1)"><h1>One</h1><p>needle <em>emphasis</em></p>$images<iframe src="https://example.org"></iframe></body></html>""")
                put("OPS/two.xhtml", "<html><body><h1>Two</h1><p id='sub'>Second needle</p></body></html>")
                put("OPS/notes.xhtml", "<html><body><p id='note'>A footnote</p></body></html>")
            }
            test(file)
        } finally { file.delete() }
    }
}
