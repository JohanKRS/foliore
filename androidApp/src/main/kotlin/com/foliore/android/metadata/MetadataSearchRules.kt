package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft
import java.text.Normalizer
import java.util.Locale

internal object MetadataSearchRules {
    fun isbn(raw: String?): String? {
        val value = raw?.trim()?.replace(Regex("(?i)^(?:urn:)?isbn(?:-1[03])?:?\\s*"), "")
            ?.replace(Regex("[\\s-]"), "")?.uppercase(Locale.ROOT) ?: return null
        if (value.length == 13 && value.all(Char::isDigit) && value.startsWith("97") &&
            value.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum() % 10 == 0) return value
        if (value.length != 10 || !value.take(9).all(Char::isDigit) || !(value.last().isDigit() || value.last() == 'X')) return null
        if (value.mapIndexed { i, c -> (if (c == 'X') 10 else c - '0') * (10 - i) }.sum() % 11 != 0) return null
        val stem = "978" + value.take(9)
        val check = (10 - stem.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum() % 10) % 10
        return stem + check
    }

    fun bestIsbn(draft: BookMetadataDraft): String? = isbn(draft.isbn) ?: draft.identifiers
        .filterKeys { it.contains("isbn", true) }.values.firstNotNullOfOrNull(::isbn)

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun titleVariants(title: String): List<String> {
        val clean = title.replace('’', '\'').replace('“', '"').replace('”', '"').trim()
        val short = clean.substringBefore(':').replace(Regex("\\s*[\\[(].*?[\\])]\\s*"), " ").trim()
        return (listOf(clean, short) + short.split(Regex("(?i)\\s+(?:aka|a\\.k\\.a\\.)\\s+")))
            .filter(String::isNotBlank).distinctBy(::normalize).take(4)
    }

    fun language(value: String?): String? = MetadataLanguages.code(value)?.substringBefore('-')

    private fun similarity(a: String, b: String): Double {
        val x = normalize(a).split(' ').filter(String::isNotBlank).toSet()
        val y = normalize(b).split(' ').filter(String::isNotBlank).toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 2.0 * x.intersect(y).size / (x.size + y.size)
    }

    fun score(candidate: MetadataLookupCandidate, draft: BookMetadataDraft): Double {
        val exactIsbn = bestIsbn(draft)?.let { it in candidate.matchedIsbns } == true
        val requestedText = normalize(draft.title + " " + draft.subtitle.orEmpty())
        val candidateText = normalize(candidate.metadata.title + " " + candidate.metadata.subtitle.orEmpty())
        val adaptations = listOf("graphic novel", "roman graphique", "coloring book", "colouring book", "study guide",
            "summary and analysis", "bandes dessinees", "bande dessinee", "guia de estudo", "resumo e analise")
        if (!exactIsbn && adaptations.any { it in candidateText && it !in requestedText }) return -1.0
        val candidateTitles = titleVariants(candidate.metadata.title)
        val shortTitleWithDifferentSubtitle = ':' in candidate.metadata.title && ':' !in draft.title &&
            normalize(draft.title).split(' ').size <= 2 &&
            !normalize(draft.title).matches(Regex("\\d{4}")) &&
            !Regex("(?i)edition|edição|anniversary|novel|romance|being").containsMatchIn(candidate.metadata.title.substringAfter(':'))
        val comparableTitles = if (shortTitleWithDifferentSubtitle) listOf(candidate.metadata.title) else candidateTitles
        val title = titleVariants(draft.title).maxOfOrNull { requested ->
            comparableTitles.maxOfOrNull { similarity(requested, it) } ?: 0.0
        } ?: 0.0
        val author = draft.authors.maxOfOrNull { requested -> candidate.metadata.authors.maxOfOrNull { similarity(requested, it) } ?: 0.0 } ?: 0.0
        if (!exactIsbn && title < 0.70) return -1.0
        if (!exactIsbn && draft.authors.any { it.any { c -> c in 'a'..'z' || c in 'A'..'Z' } } &&
            candidate.metadata.authors.any { it.any { c -> c in 'a'..'z' || c in 'A'..'Z' } } && author < 0.20) return -1.0
        val language = language(draft.language)
        val remoteLanguage = language(candidate.sourceLanguage)
        val languageScore = if (language == null || remoteLanguage == null) 0.0 else if (language == remoteLanguage) 12.0 else -15.0
        val completeness = listOf(candidate.metadata.description, candidate.metadata.publisher, candidate.metadata.publishedDate).count { !it.isNullOrBlank() }
        // A direct edition lookup may list paperback/hardback ISBNs together. It is still
        // more specific than a scan listing ISBNs from several publication years.
        val exactCatalogEdition = candidate.editionResolved && candidate.sourceName == "Open Library"
        val editionConfidence = if (exactIsbn && (candidate.matchedIsbns.size == 1 || exactCatalogEdition)) 60.0 else 0.0
        val catalog = if (candidate.sourceName == "Open Library" || candidate.sourceName == "BnF") 4.0 else 0.0
        val establishedWork = (kotlin.math.ln(candidate.workEditionCount.coerceAtLeast(0) + 1.0) * 6).coerceAtMost(24.0)
        val series = if (candidate.metadata.seriesName.isNullOrBlank()) 0.0 else 4.0
        return (if (exactIsbn) 200.0 else 0.0) + title * 80 + author * 35 + languageScore + completeness + editionConfidence + catalog + establishedWork + series
    }

    fun rank(candidates: List<MetadataLookupCandidate>, draft: BookMetadataDraft, limit: Int): List<MetadataLookupCandidate> {
        val byId = candidates.groupBy { it.id }.values.map { group -> group.reduce { first, other -> first.complementWith(other) } }
        val ranked = byId.map { it to score(it, draft) }.filter { it.second >= 0 }
            .sortedWith(compareByDescending<Pair<MetadataLookupCandidate, Double>> { it.second }.thenBy { it.first.id }).map { it.first }
        val editions = mutableListOf<MetadataLookupCandidate>()
        for (candidate in ranked) {
            val same = editions.indexOfFirst { existing ->
                existing.matchedIsbns.size == 1 && candidate.matchedIsbns.size == 1 &&
                    existing.matchedIsbns == candidate.matchedIsbns &&
                    (language(existing.sourceLanguage) == null || language(candidate.sourceLanguage) == null ||
                        language(existing.sourceLanguage) == language(candidate.sourceLanguage))
            }
            if (same >= 0) editions[same] = editions[same].complementWith(candidate)
            else editions += candidate
        }
        return editions.take(limit.coerceAtLeast(0)).map { candidate ->
            candidate.copy(seriesWorkKeys = (candidate.seriesWorkKeys + editions
                .filter { sameBibliographicTitleAndAuthor(candidate, it) }
                .sortedByDescending { it.workEditionCount }.mapNotNull { it.workKey }
                .filter { it != candidate.workKey }).distinct().take(1))
        }
    }

    private fun sameBibliographicTitleAndAuthor(a: MetadataLookupCandidate, b: MetadataLookupCandidate): Boolean {
        if (a.workKey == b.workKey || b.workKey == null) return false
        fun titles(c: MetadataLookupCandidate) = listOf(c.metadata.title, c.metadata.title + " " + c.metadata.subtitle.orEmpty()).map(::normalize).toSet()
        if (titles(a).intersect(titles(b)).isEmpty()) return false
        val authors = a.metadata.authors.map(::normalize).toSet().intersect(b.metadata.authors.map(::normalize).toSet())
        if (authors.isEmpty() && a.authorKeys.intersect(b.authorKeys.toSet()).isEmpty()) return false
        val languageA = language(a.sourceLanguage); val languageB = language(b.sourceLanguage)
        return languageA == null || languageB == null || languageA == languageB
    }
}
