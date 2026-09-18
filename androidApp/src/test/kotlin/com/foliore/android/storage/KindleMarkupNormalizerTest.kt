package com.foliore.android.storage

import java.io.File
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.test.*

class KindleMarkupNormalizerTest {
    @Test fun keepsModernSectionsAndImageDimensionsWhileTranslatingLegacyPresentation() = inDirectory { root ->
        File(root, "chapter.html").writeText("""<html><head><link rel="icon" href="old-cover.jpg"></head><body>
            <header id="intro">Introdução</header><p height="2em" width="1em" align="center"><font size="+1" color="#123456">Coração &amp; café</font></p>
            <img src="cover.jpg" width="400" height="600"><hr width="80%"><ul width="0pt"><li height="0pt" width="0pt" value="1"><tt>Texto</tt></li></ul>
            <footer>Fim</footer></body></html>""")
        KindleMarkupNormalizer.normalize(root)
        val result = File(root, "chapter.html").readText()
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(result.byteInputStream())
        val doc = Jsoup.parse(result)
        assertEquals("Introdução", doc.selectFirst("header")!!.text())
        assertEquals("Fim", doc.selectFirst("footer")!!.text())
        assertTrue(doc.text().contains("Coração & café"))
        assertTrue(doc.select("font,tt,link[rel=icon]").isEmpty())
        assertTrue(doc.selectFirst("p")!!.attr("style").contains("margin-top:2em"))
        assertTrue(doc.selectFirst("p")!!.attr("style").contains("text-indent:1em"))
        assertEquals("400", doc.selectFirst("img")!!.attr("width"))
        assertEquals("600", doc.selectFirst("img")!!.attr("height"))
        assertTrue(doc.selectFirst("img")!!.hasAttr("alt"))
        assertFalse(doc.selectFirst("li")!!.hasAttr("value"))
        assertTrue(doc.head().selectFirst("title")!!.text().isNotBlank())
        assertEquals("3.0", Jsoup.parse(File(root, "content.opf").readText(), "", Parser.xmlParser()).selectFirst("package")!!.attr("version"))
    }

    @Test fun keepsNestedNavigationAndGivesRepeatedTargetsTheSamePlayOrder() = inDirectory { root ->
        File(root, "chapter.html").writeText("<html><body><p id='intro'>Start</p><p id='next'>Next</p></body></html>")
        File(root, "toc.ncx").writeText("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
            <navPoint id="a" playOrder="1"><navLabel><text>First</text></navLabel><content src="chapter.html#intro"/>
            <navPoint id="b" playOrder="2"><navLabel><text>Next</text></navLabel><content src="chapter.html#next"/></navPoint></navPoint>
            <navPoint id="c" playOrder="3"><navLabel><text>First again</text></navLabel><content src="chapter.html#intro"/></navPoint>
            </navMap></ncx>""")
        KindleMarkupNormalizer.normalize(root)
        val ncx = Jsoup.parse(File(root, "toc.ncx").readText(), "", Parser.xmlParser())
        assertEquals(listOf("1", "2", "1"), ncx.select("navPoint").map { it.attr("playOrder") })
        val nav = Jsoup.parse(File(root, "foliore-navigation.xhtml").readText())
        assertEquals(listOf("chapter.html#intro", "chapter.html#next", "chapter.html#intro"), nav.select("a").map { it.attr("href") })
        assertEquals("Next", nav.selectFirst("li ol li a")!!.text())
    }

    private fun inDirectory(block: (File) -> Unit) {
        val root = kotlin.io.path.createTempDirectory().toFile()
        try {
            File(root, "content.opf").writeText("""<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">test</dc:identifier><dc:title>Title</dc:title><dc:language>en</dc:language></metadata><manifest><item id="chapter" href="chapter.html" media-type="application/xhtml+xml"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>""")
            block(root)
        } finally { root.deleteRecursively() }
    }
}
