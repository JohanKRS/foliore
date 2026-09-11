package com.foliore.android.metadata

import com.foliore.shared.domain.publicationDateOrNull

import com.foliore.shared.domain.BookMetadataDraft
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal fun searchBnf(draft: BookMetadataDraft, limit: Int, getText: (String) -> String): MetadataLookupResult {
    val title = MetadataSearchRules.titleVariants(draft.title).lastOrNull()?.searchPhrase().orEmpty()
    if (title.isBlank()) return MetadataLookupResult.Success(emptyList())
    val author = draft.authors.firstOrNull()?.authorSearchName()?.searchPhrase()
    // One bounded request includes the exact ISBN and title alternatives.
    val titleQuery = "bib.title all \"$title\"" + if (author.isNullOrBlank()) "" else " and bib.author all \"$author\""
    val query = MetadataSearchRules.bestIsbn(draft)?.let { "(bib.isbn all \"$it\") or ($titleQuery)" } ?: titleQuery
    return try {
        val xml = getText("https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&recordSchema=dublincore&maximumRecords=24&query=${query.urlEncoded()}")
        MetadataLookupResult.Success(MetadataSearchRules.rank(parseBnfRecords(xml), draft, limit))
    } catch (_: Exception) { MetadataLookupResult.Failure() }
}

internal fun parseBnfRecords(xml: String): List<MetadataLookupCandidate> {
    // Fail closed before invoking Android/JVM parsers, whose supported security flags differ.
    require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml))
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entity blocked") }
    }
    val root = builder.parse(InputSource(StringReader(xml)))
    require(root.getElementsByTagNameNS("http://www.loc.gov/zing/srw/diagnostic/", "diagnostic").length == 0)
    val records = root.getElementsByTagNameNS("http://www.openarchives.org/OAI/2.0/oai_dc/", "dc")
    return (0 until records.length).mapNotNull { i ->
        val node = records.item(i) as Element
        fun values(name: String): List<String> {
            val children = node.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", name)
            return (0 until children.length).map { children.item(it).textContent.trim() }.filter(String::isNotBlank)
        }
        val types = values("type")
        if (types.isNotEmpty() && types.none { it.equals("text", true) || it.equals("printed text", true) || it.equals("texte imprimé", true) }) return@mapNotNull null
        val identifiers = values("identifier")
        val ark = identifiers.firstOrNull { Regex("https?://catalogue\\.bnf\\.fr/ark:/12148/[A-Za-z0-9]+$").matches(it) }
            ?.substringAfter("/ark:/") ?: return@mapNotNull null
        val title = values("title").firstOrNull()?.substringBefore(" / ")?.trim() ?: return@mapNotNull null
        // Contributors include translators/illustrators; do not silently turn them into authors.
        val authors = values("creator").map { it.substringBefore(". Auteur").catalogAuthor() }.distinct()
        val isbns = identifiers.mapNotNull(MetadataSearchRules::isbn).toSet()
        val descriptions = values("description")
        val series = catalogSeries(explicit = descriptions.filter { it.startsWith("Collection :", true) }.map { it.substringAfter(':').trim() }, titles = listOf(title), subjects = values("subject"))
        val language = values("language").firstNotNullOfOrNull(MetadataLanguages::code)
        MetadataLookupCandidate("bnf:$ark", "BnF",
            emptySourceMetadata(title).copy(authors = authors, sortAuthor = authors.firstOrNull()?.sortAuthor(),
                publisher = values("publisher").firstOrNull(), publishedDate = values("date").firstOrNull().publicationDateOrNull(),
                language = language, isbn = isbns.sorted().firstOrNull(), tags = values("subject").distinct().take(16),
                // Catalog notes and physical dimensions are not a synopsis.
                description = descriptions.filter { it.startsWith("Résumé :", true) }.joinToString("\n\n") { it.substringAfter(':').trim() }.takeIf(String::isNotBlank),
                seriesName = series?.name, seriesIndex = series?.index,
                identifiers = mapOf("bnf" to ark)),
            coverImagePath = null, sourceUrl = "https://catalogue.bnf.fr/ark:/$ark", matchedIsbns = isbns, sourceLanguage = language,
            coverUrls = isbns.sorted().take(2).map { "https://covers.openlibrary.org/b/isbn/$it-L.jpg?default=false" },
        )
    }
}
