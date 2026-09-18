package com.foliore.android.storage

import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.MetadataSource
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.test.*

class EpubMetadataEditorTest {
    private val metadata = BookMetadata("book", "New & title", null, listOf("Same author", "New author"), null,
        "pt", null, null, "Description <updated>", emptyList(), null, emptyMap(), null, null, MetadataSource.MANUAL)

    @Test fun preservesIdentifiersAndValidRefinementsWhileRemovingReplacedAuthorsAndTheirRefinements() {
        val result = edit("3.0")
        val doc = Jsoup.parse(result, "", Parser.xmlParser())
        assertEquals("New & title", doc.getElementById("title")!!.text())
        assertEquals("Same author", doc.getElementById("author")!!.text())
        assertTrue(doc.select("meta[refines='#author']").isNotEmpty())
        assertNull(doc.getElementById("old-author"))
        assertNull(doc.getElementById("old-role"))
        assertTrue(doc.select("meta[refines='#old-author'],meta[refines='#old-role'],meta[refines='#publisher']").isEmpty())
        assertEquals("2026-09-18T12:34:56Z", doc.select("meta[property='dcterms:modified']").single().text())
        assertEquals("cover-image", doc.select("item[id='foliore-cover']").single().attr("properties"))
        assertEquals("font-key-must-not-change", doc.getElementById("uid")!!.text())
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(result.byteInputStream())
    }

    @Test fun keepsEpub2MetadataAndCoverFreeOfEpub3OnlyAttributes() {
        val result = edit("2.0")
        val doc = Jsoup.parse(result, "", Parser.xmlParser())
        assertTrue(doc.select("meta[property='dcterms:modified']").isEmpty())
        assertFalse(doc.select("item[id='foliore-cover']").single().hasAttr("properties"))
        assertEquals("foliore-cover", doc.select("meta[name=cover]").single().attr("content"))
    }

    @Test fun addsCoverAndModifiedDateInThePackageNamespaceWithAnExplicitPrefix() {
        val result = EpubMetadataEditor.update("""<opf:package xmlns:opf="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
            <opf:metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">book</dc:identifier><dc:title>Title</dc:title></opf:metadata>
            <opf:manifest/><opf:spine/></opf:package>""", metadata, "cover", "cover.jpg", true, "2026-09-18T12:34:56Z")
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(result.byteInputStream())
        val opf = "http://www.idpf.org/2007/opf"
        assertEquals(1, document.getElementsByTagNameNS(opf, "item").length)
        assertEquals(2, document.getElementsByTagNameNS(opf, "meta").length)
        assertEquals(2, document.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator").length)
    }

    private fun edit(version: String) = EpubMetadataEditor.update("""<package xmlns="http://www.idpf.org/2007/opf" version="$version" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:identifier id="uid">font-key-must-not-change</dc:identifier><dc:title id="title">Old title</dc:title>
        <dc:creator id="author">Same author</dc:creator><meta refines="#author" property="role">aut</meta>
        <dc:creator id="old-author">Old author</dc:creator><meta id="old-role" refines="#old-author" property="role">aut</meta><meta refines="#old-role" property="alternate-script">example</meta>
        <dc:publisher id="publisher">Publisher</dc:publisher><meta refines="#publisher" property="file-as">Publisher</meta>
        </metadata><manifest/><spine/></package>""", metadata, "foliore-cover", "cover.jpg", true, "2026-09-18T12:34:56Z")
}
