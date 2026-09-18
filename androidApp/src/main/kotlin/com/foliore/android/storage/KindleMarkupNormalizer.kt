package com.foliore.android.storage

import java.io.File
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

/** libmobi extracts both legacy HTML and KF8 HTML5. Package them as EPUB 3 XHTML. */
internal object KindleMarkupNormalizer {
    fun normalize(directory: File) {
        val opfFile = File(directory, "content.opf")
        val opf = Jsoup.parse(opfFile.readText(), "", Parser.xmlParser())
        val pkg = requireNotNull(opf.selectFirst("package"))
        val metadata = requireNotNull(pkg.selectFirst("metadata"))
        val manifest = requireNotNull(pkg.selectFirst("manifest"))
        val spine = requireNotNull(pkg.selectFirst("spine"))
        val title = metadata.getElementsByTag("dc:title").first()?.text().orEmpty().ifBlank { "Book" }
        directory.listFiles().orEmpty().filter { it.extension == "html" }.forEach { file ->
            val document = normalizeHtml(file, title)
            manifest.children().firstOrNull { it.attr("href") == file.name }?.let { item ->
                val properties = item.attr("properties").split(' ').filter { it.isNotBlank() }.toMutableSet()
                if (document.selectFirst("svg") != null) properties += "svg"
                if (document.selectFirst("math") != null) properties += "mathml"
                if (properties.isNotEmpty()) item.attr("properties", properties.joinToString(" "))
            }
            file.writeText(document.outerHtml())
        }
        pkg.attr("version", "3.0")
        // A stable timestamp makes conversion hashes independent of device, timezone and retry.
        metadata.appendElement("meta").attr("property", "dcterms:modified").text("1980-01-01T00:00:00Z")
        metadata.getElementsByTag("dc:date").forEach { it.removeAttr("opf:event") }
        val coverId = metadata.selectFirst("meta[name=cover]")?.attr("content")
        manifest.children().firstOrNull { it.id() == coverId }?.attr("properties", "cover-image")

        val nav = Jsoup.parse("<html><head></head><body></body></html>")
        xhtml(nav)
        nav.selectFirst("html")!!.attr("xmlns:epub", "http://www.idpf.org/2007/ops")
        nav.head().appendElement("title").text(title)
        val list = nav.body().appendElement("nav").attr("epub:type", "toc").appendElement("ol")
        val ncxName = manifest.children().firstOrNull { it.id() == spine.attr("toc") }?.attr("href")
        val ncx = ncxName?.takeIf { '/' !in it && '\\' !in it }?.let { File(directory, it) }?.takeIf { it.isFile }
        fun addPoints(parent: Element, target: Element) {
            parent.children().filter { it.tagName() == "navPoint" }.forEach { point ->
                val href = point.children().firstOrNull { it.tagName() == "content" }?.attr("src").orEmpty()
                if (href.isNotBlank()) {
                    val li = target.appendElement("li")
                    li.appendElement("a").attr("href", href).text(point.selectFirst("navLabel text")?.text().orEmpty().ifBlank { title })
                    if (point.children().any { it.tagName() == "navPoint" }) addPoints(point, li.appendElement("ol"))
                }
            }
        }
        ncx?.let { file ->
            val document = Jsoup.parse(file.readText(), "", Parser.xmlParser())
            val order = linkedMapOf<String, Int>()
            document.select("navPoint,navTarget,pageTarget").forEach { point ->
                val target = point.children().firstOrNull { it.tagName() == "content" }?.attr("src")
                if (target != null) point.attr("playOrder", order.getOrPut(target) { order.size + 1 }.toString())
            }
            document.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml)
            file.writeText(document.outerHtml())
            document.selectFirst("navMap")?.let { addPoints(it, list) }
        }
        if (list.children().isEmpty()) spine.children().forEach { ref ->
            manifest.children().firstOrNull { it.id() == ref.attr("idref") }?.let { item ->
                list.appendElement("li").appendElement("a").attr("href", item.attr("href")).text(title)
            }
        }
        check(list.children().isNotEmpty())
        val navName = "foliore-navigation.xhtml"
        check(!File(directory, navName).exists())
        File(directory, navName).writeText(nav.outerHtml())
        manifest.appendElement("item").attr("id", "foliore-navigation").attr("href", navName)
            .attr("media-type", "application/xhtml+xml").attr("properties", "nav")
        opf.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml)
        opfFile.writeText(opf.outerHtml())
    }

    private fun normalizeHtml(file: File, title: String): Document {
        val document = Jsoup.parse(file.readText())
        xhtml(document)
        if (document.head().selectFirst("title") == null) document.head().appendElement("title").text(title)
        document.select("meta[http-equiv=Content-Type],meta[charset],guide").remove()
        document.getElementsByTag("mbp:pagebreak").forEach { it.tagName("div").css("page-break-before", "always") }
        document.select("font").forEach {
            it.tagName("span")
            val size = it.attr("size").toIntOrNull()?.let { n -> if (it.attr("size").startsWith('+') || it.attr("size").startsWith('-')) n + 3 else n }
            if (size != null) it.css("font-size", listOf("0.63em", "0.82em", "1em", "1.13em", "1.5em", "2em", "3em")[size.coerceIn(1, 7) - 1])
            if (it.hasAttr("face")) it.css("font-family", it.attr("face"))
            if (it.hasAttr("color")) it.css("color", it.attr("color"))
            it.removeAttr("size").removeAttr("face").removeAttr("color")
        }
        document.select("tt").forEach { it.tagName("span").css("font-family", "monospace") }
        document.getAllElements().forEach { element ->
            if (element.hasAttr("align")) {
                val align = element.attr("align").lowercase()
                when {
                    element.tagName() == "img" && align in setOf("left", "right") -> element.css("float", align)
                    element.tagName() == "img" -> element.css("vertical-align", if (align == "center") "middle" else align)
                    element.tagName() == "table" && align == "center" -> { element.css("margin-left", "auto"); element.css("margin-right", "auto") }
                    else -> element.css("text-align", align)
                }
                element.removeAttr("align")
            }
            for ((attribute, property) in listOf("valign" to "vertical-align", "bgcolor" to "background-color")) {
                if (element.hasAttr(attribute)) { element.css(property, element.attr(attribute)); element.removeAttr(attribute) }
            }
            // MOBI block height/width encode spacing/indentation, not a fixed box.
            if (element.tagName() in setOf("p", "div", "blockquote", "li", "ul", "ol")) {
                for ((attribute, property) in listOf("height" to "margin-top", "width" to "text-indent")) {
                    val value = element.attr(attribute)
                    if (Regex("-?[0-9]+(?:\\.[0-9]+)?(?:px|em|pt|%)?").matches(value)) element.css(property, if (value.last().isDigit()) "${value}px" else value)
                    element.removeAttr(attribute)
                }
                if (element.tagName() != "li" || element.parent()?.tagName() != "ol") element.removeAttr("value")
            }
            if (element.tagName() in setOf("hr", "table", "td", "th")) {
                for (attribute in listOf("width", "height")) {
                    val value = element.attr(attribute)
                    if (Regex("[0-9]+(?:\\.[0-9]+)?(?:px|em|pt|%)?").matches(value)) element.css(attribute, if (value.last().isDigit()) "${value}px" else value)
                    element.removeAttr(attribute)
                }
                if (element.attr("border") == "0") { element.css("border", "0"); element.removeAttr("border") }
            }
            element.removeAttr("cid")
        }
        document.select("img:not([alt])").attr("alt", "")
        // libmobi renames resources but can retain obsolete favicon/cover link hints.
        document.select("link[href]").filter { link ->
            val href = link.attr("href")
            link.attr("rel").split(' ').any { it == "icon" || it == "coverpage" } &&
                ':' !in href && !File(file.parentFile, href.substringBefore('#')).isFile
        }.forEach { it.remove() }
        return document
    }

    private fun xhtml(document: Document) {
        document.childNodes().filterIsInstance<DocumentType>().forEach { it.remove() }
        document.prependChild(DocumentType("html", "", ""))
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml)
            .charset(Charsets.UTF_8).prettyPrint(false)
        document.selectFirst("html")?.attr("xmlns", "http://www.w3.org/1999/xhtml")
    }

    private fun Element.css(property: String, value: String) {
        // Existing author CSS takes precedence over presentational HTML attributes.
        attr("style", "$property:$value;${attr("style")}")
    }
}
