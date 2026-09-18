package com.foliore.android.storage

import com.foliore.shared.domain.BookMetadata
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

/** Edits package metadata without breaking identifiers, refinements or EPUB 2 compatibility. */
internal object EpubMetadataEditor {
    fun update(
        opfXml: String,
        metadata: BookMetadata,
        coverItemId: String,
        coverHref: String,
        needsCoverItem: Boolean,
        modifiedAt: String,
    ): String {
        val document = Jsoup.parse(opfXml, "", Parser.xmlParser())
        document.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml)
        val pkg = document.getAllElements().first { it.localName == "package" }
        val meta = pkg.children().first { it.localName == "metadata" }
        val epub3 = pkg.attr("version").startsWith("3")
        meta.attr("xmlns:dc", "http://purl.org/dc/elements/1.1/")
        val removedIds = mutableSetOf<String>()
        fun remove(element: Element) {
            if (element.id().isNotEmpty()) removedIds += element.id()
            element.remove()
        }
        fun entries(name: String) = meta.children().filter { it.localName == name }
        fun single(name: String, value: String?) {
            val existing = entries(name)
            if (value.isNullOrBlank()) existing.forEach(::remove)
            else {
                val target = existing.firstOrNull() ?: meta.appendElement("dc:$name")
                if (target.text() != value && target.id().isNotEmpty()) {
                    // A sort key for the previous title no longer describes its replacement.
                    meta.children().filter { it.attr("refines") == "#${target.id()}" && it.attr("property") == "file-as" }.forEach(::remove)
                }
                target.text(value)
            }
        }
        single("title", metadata.title)
        val authors = entries("creator").toMutableList()
        metadata.authors.ifEmpty { listOf(metadata.title) }.forEach { author ->
            val existing = authors.firstOrNull { it.text() == author }
            if (existing != null) {
                authors.remove(existing)
                meta.appendChild(existing)
            } else meta.appendElement("dc:creator").text(author)
        }
        authors.forEach(::remove)
        single("language", metadata.language ?: "und")
        single("publisher", metadata.publisher)
        single("date", metadata.publishedDate)
        single("description", metadata.description)
        // Refinements can themselves be refined; remove the entire orphaned chain.
        do {
            val orphaned = meta.children().filter { it.attr("refines").removePrefix("#") in removedIds }
            orphaned.forEach(::remove)
        } while (orphaned.isNotEmpty())
        if (epub3) {
            val existing = meta.children().filter { it.attr("property") == "dcterms:modified" }
            val modified = existing.firstOrNull() ?: meta.appendOpfElement("meta").attr("property", "dcterms:modified")
            modified.text(modifiedAt)
            existing.drop(1).forEach { it.remove() }
        }
        if (needsCoverItem) {
            val manifest = pkg.children().first { it.localName == "manifest" }
            val ids = document.getAllElements().map { it.id() }.toSet()
            val id = generateSequence(coverItemId) { it + "-cover" }.first { it !in ids }
            val item = manifest.appendOpfElement("item").attr("id", id).attr("href", coverHref).attr("media-type", "image/jpeg")
            if (epub3) item.attr("properties", "cover-image")
            val cover = meta.children().firstOrNull { it.localName == "meta" && it.attr("name") == "cover" }
                ?: meta.appendOpfElement("meta").attr("name", "cover")
            cover.attr("content", id)
        }
        return document.outerHtml()
    }

    private val Element.localName: String get() = tagName().substringAfter(':')
    private fun Element.appendOpfElement(name: String): Element =
        appendElement(tagName().substringBeforeLast(':', "").let { if (it.isEmpty()) name else "$it:$name" })
}
