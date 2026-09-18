package com.foliore.android.reader

import com.foliore.android.storage.StorageLimits
import com.foliore.android.storage.readBounded
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

internal data class ReaderSection(val href: String, val title: String, val fixedLayout: Boolean)
internal data class ReaderTocEntry(val title: String, val href: String, val fragment: String?, val depth: Int)
internal data class ReaderSearchResult(val section: Int, val offset: Int, val excerpt: String, val query: String)
internal data class PublicationResource(val mime: String, val bytes: ByteArray)

/** Loads one bounded resource at a time. Book code and remote resources never execute. */
internal class EpubPublication private constructor(private val zip: ZipFile) : Closeable {
    lateinit var sections: List<ReaderSection>; private set
    lateinit var toc: List<ReaderTocEntry>; private set
    var rtl: Boolean = false; private set
    private val mediaTypes = mutableMapOf<String, String>()
    private val encryptedPaths = mutableSetOf<String>()
    private val fontKeys = mutableMapOf<String, ByteArray>()
    private val nonce = UUID.randomUUID().toString()

    private fun read(path: String, limit: Long = StorageLimits.ENTRY_BYTES): ByteArray? = synchronized(zip) {
        val entry = zip.getEntry(path) ?: return null
        zip.getInputStream(entry).use { it.readBounded(limit) }
    }

    private fun xml(path: String) = Jsoup.parse(checkNotNull(read(path, StorageLimits.XML_BYTES)).toString(Charsets.UTF_8), "", Parser.xmlParser())

    private fun initialize() {
        val container = xml("META-INF/container.xml")
        val opf = container.local("rootfile").firstOrNull()?.attr("full-path") ?: error("Missing package")
        require(safePath(opf))
        val pkg = xml(opf)
        val manifest = pkg.local("manifest").firstOrNull() ?: error("Missing manifest")
        val items = manifest.local("item").mapNotNull { item -> resolve(opf, item.attr("href"))?.let { item.attr("id") to (item to it.first) } }.toMap()
        items.values.forEach { (item, path) -> mediaTypes[path] = item.attr("media-type") }
        readFontObfuscation(pkg)
        val spine = pkg.local("spine").firstOrNull() ?: error("Missing spine")
        rtl = spine.attr("page-progression-direction") == "rtl"
        val fixed = pkg.local("meta").any { it.attr("property") == "rendition:layout" && it.text() == "pre-paginated" }
        val refs = spine.local("itemref").filter { it.attr("linear") != "no" }
        sections = refs.mapNotNull { ref -> items[ref.attr("idref")]?.let { (item, path) ->
            if (item.attr("media-type") !in setOf("application/xhtml+xml", "text/html", "image/svg+xml")) null
            else ReaderSection(path, "", when {
                "rendition:layout-reflowable" in ref.attr("properties") -> false
                "rendition:layout-pre-paginated" in ref.attr("properties") -> true
                else -> fixed
            })
        } }
        require(sections.isNotEmpty() && sections.size <= StorageLimits.ENTRIES)
        toc = items.values.firstOrNull { (item, _) -> "nav" in item.attr("properties").split(' ') }?.let { (_, path) ->
            val navDoc = xml(path)
            val nav = navDoc.local("nav").firstOrNull { "toc" in it.attr("epub:type").split(' ') || it.attr("role") == "doc-toc" }
            nav?.local("a")?.mapNotNull { link -> resolve(path, link.attr("href"))?.let { (href, fragment) ->
                ReaderTocEntry(link.text().take(240), href, fragment, (link.parents().count { it.normalName() == "ol" } - 1).coerceIn(0, 6))
            } }
        }.orEmpty()
        if (toc.isEmpty()) {
            val ncx = items[spine.attr("toc")] ?: items.values.firstOrNull { it.first.attr("media-type") == "application/x-dtbncx+xml" }
            toc = ncx?.let { (_, path) -> xml(path).local("navPoint").mapNotNull { point ->
                val link = point.children().firstOrNull { it.tagName().substringAfter(':') == "content" }?.attr("src") ?: return@mapNotNull null
                resolve(path, link)?.let { (href, fragment) -> ReaderTocEntry(point.local("navLabel").firstOrNull()?.text().orEmpty().take(240), href, fragment,
                    point.parents().count { it.tagName().substringAfter(':') == "navPoint" }.coerceAtMost(6)) }
            } }.orEmpty()
        }
        toc = toc.filter { entry -> sections.any { it.href == entry.href } }.take(5000)
        sections = sections.mapIndexed { index, section -> section.copy(title = toc.firstOrNull { it.href == section.href }?.title?.takeIf { it.isNotBlank() } ?: "${index + 1}") }
        if (toc.isEmpty()) toc = sections.map { ReaderTocEntry(it.title, it.href, null, 0) }
    }

    fun resource(path: String): PublicationResource? {
        if (!safePath(path)) return null
        if (path in encryptedPaths && path !in fontKeys) return null
        val bytes = read(path) ?: return null
        fontKeys[path]?.let { key ->
            for (index in 0 until minOf(1040, bytes.size)) bytes[index] = (bytes[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
        val mime = mediaTypes[path]?.takeIf { it.isNotBlank() } ?: when (path.substringAfterLast('.').lowercase()) {
            "css" -> "text/css"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"
            "svg" -> "image/svg+xml"; "webp" -> "image/webp"; "woff" -> "font/woff"; "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"; "otf" -> "font/otf"; else -> "application/octet-stream"
        }
        if (mime in setOf("application/xhtml+xml", "text/html") || sections.any { it.href == path }) {
            return PublicationResource("text/html", sanitize(bytes.toString(Charsets.UTF_8), path).toByteArray())
        }
        // Standalone SVG images may contain scripts too. Their image context cannot execute them;
        // reject active MIME types regardless of their manifest declaration.
        if (mime !in setOf("text/css", "image/jpeg", "image/png", "image/gif", "image/svg+xml", "image/webp", "font/woff", "font/woff2", "font/ttf", "font/otf", "application/vnd.ms-opentype", "application/font-sfnt", "application/font-woff")) return null
        return PublicationResource(mime, bytes)
    }

    // EPUB 3.3 §4.4: IDPF font obfuscation is reversed only in the in-memory resource.
    private fun readFontObfuscation(pkg: Document) {
        if (zip.getEntry("META-INF/encryption.xml") == null) return
        val identifierId = pkg.local("package").firstOrNull()?.attr("unique-identifier")
        val identifier = pkg.local("identifier").firstOrNull { it.id() == identifierId }?.wholeText()
            ?.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }?.takeIf { it.isNotEmpty() }
        xml("META-INF/encryption.xml").local("EncryptedData").forEach { data ->
            val path = data.local("CipherReference").firstOrNull()?.attr("URI")?.let { resolve("container", it)?.first } ?: return@forEach
            encryptedPaths += path
            val algorithm = data.local("EncryptionMethod").firstOrNull()?.attr("Algorithm")
            if (algorithm == "http://www.idpf.org/2008/embedding" && identifier != null && mediaTypes[path] in setOf("font/ttf", "font/otf", "font/woff", "font/woff2", "application/vnd.ms-opentype", "application/font-sfnt", "application/font-woff")) {
                fontKeys[path] = MessageDigest.getInstance("SHA-1").digest(identifier.toByteArray(Charsets.UTF_8))
            }
        }
    }

    internal fun sanitize(source: String, path: String): String {
        val doc = Jsoup.parse(source)
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html).charset(Charsets.UTF_8).prettyPrint(false)
        doc.select("script, iframe, frame, frameset, object, embed, form, input, button, textarea, audio, video, base, meta[http-equiv]").remove()
        doc.allElements.forEach { element ->
            element.attributes().asList().filter { it.key.startsWith("on", true) || it.key in setOf("srcdoc", "autofocus") }.forEach { element.removeAttr(it.key) }
            listOf("href", "src", "xlink:href", "action").forEach { attribute ->
                if (element.attr(attribute).trim().startsWith("javascript:", true)) element.removeAttr(attribute)
            }
        }
        doc.head().select("meta[name=viewport]").remove()
        val fixed = sections.firstOrNull { it.href == path }?.fixedLayout == true
        val originalViewport = if (fixed) Jsoup.parse(source).selectFirst("meta[name=viewport]")?.attr("content") else null
        doc.head().prependElement("meta").attr("name", "viewport").attr("content", originalViewport ?: "width=device-width, initial-scale=1.0")
        doc.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
            "default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; script-src 'nonce-$nonce'; connect-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'")
        doc.head().appendElement("style").attr("id", "foliore-style")
        doc.body().attr("data-foliore-fixed", fixed.toString())
        doc.body().appendElement("script").attr("nonce", nonce).attr("src", "$ORIGIN/_reader/runtime.js")
        return doc.outerHtml()
    }

    fun text(section: Int): String {
        val source = read(sections[section].href, StorageLimits.XML_BYTES)?.toString(Charsets.UTF_8) ?: return ""
        val doc = Jsoup.parse(sanitize(source, sections[section].href))
        doc.select("script, style, nav, noscript, iframe, object").remove()
        // Whole text keeps whitespace, matching DOM Text nodes used by the locator.
        return doc.body().wholeText()
    }

    fun linkedText(path: String, fragment: String?): String? {
        if (!safePath(path) || mediaTypes[path] !in setOf("application/xhtml+xml", "text/html")) return null
        val source = read(path, StorageLimits.XML_BYTES)?.toString(Charsets.UTF_8) ?: return null
        val doc = Jsoup.parse(source)
        doc.select("script,style,noscript,iframe,object").remove()
        return (fragment?.let { doc.getElementById(it) } ?: doc.body()).text().take(16000)
    }

    override fun close() { synchronized(zip) { zip.close() } }

    companion object {
        const val ORIGIN = "https://appassets.androidplatform.net"
        fun url(path: String) = "$ORIGIN/publication/" + path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        fun open(file: File): EpubPublication {
            require(file.isFile && file.length() <= StorageLimits.EPUB_BYTES)
            val zip = ZipFile(file)
            return try { StorageLimits.inspect(zip); EpubPublication(zip).apply { initialize() } } catch (e: Throwable) { zip.close(); throw e }
        }
        fun safePath(path: String) = path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && !path.contains('\u0000') && path.split('/').none { it == ".." || it == "." }
        fun resolve(base: String, href: String): Pair<String, String?>? = runCatching {
            val relative = URI(href.trim().replace(" ", "%20"))
            if (relative.isAbsolute || relative.rawAuthority != null) return null
            val resolved = URI("/" + base.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }).resolve(relative).normalize()
            val path = resolved.path.removePrefix("/")
            if (!safePath(path)) null else path to resolved.fragment
        }.getOrNull()
        private fun Element.local(name: String): List<Element> = allElements.filter { it.tagName().substringAfter(':') == name }
    }
}
