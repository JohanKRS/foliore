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

    fun language(value: String?): String? = value?.substringAfterLast('/')?.lowercase(Locale.ROOT)
        ?.replace('_', '-')?.substringBefore('-')?.let {
            when (it) { "eng" -> "en"; "por" -> "pt"; "spa" -> "es"; "fre", "fra" -> "fr"
                "ger", "deu" -> "de"; "ita" -> "it"; "jpn" -> "ja"; "chi", "zho" -> "zh"; "rus" -> "ru"; else -> it }
        }?.takeIf(String::isNotBlank)

    private fun similarity(a: String, b: String): Double {
        val x = normalize(a).split(' ').filter(String::isNotBlank).toSet()
        val y = normalize(b).split(' ').filter(String::isNotBlank).toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 2.0 * x.intersect(y).size / (x.size + y.size)
    }

    fun score(candidate: MetadataLookupCandidate, draft: BookMetadataDraft): Double {
        val exactIsbn = bestIsbn(draft)?.let { it in candidate.matchedIsbns } == true
        val title = titleVariants(draft.title).maxOfOrNull { requested ->
            titleVariants(candidate.metadata.title).maxOfOrNull { similarity(requested, it) } ?: 0.0
        } ?: 0.0
        val author = draft.authors.maxOfOrNull { requested -> candidate.metadata.authors.maxOfOrNull { similarity(requested, it) } ?: 0.0 } ?: 0.0
        if (!exactIsbn && title < 0.70) return -1.0
        val language = language(draft.language)
        val remoteLanguage = language(candidate.sourceLanguage)
        val languageScore = if (language == null || remoteLanguage == null) 0.0 else if (language == remoteLanguage) 12.0 else -15.0
        val completeness = listOf(candidate.metadata.description, candidate.metadata.publisher, candidate.metadata.publishedDate).count { !it.isNullOrBlank() }
        return (if (exactIsbn) 200.0 else 0.0) + title * 80 + author * 35 + languageScore + completeness
    }

    fun rank(candidates: List<MetadataLookupCandidate>, draft: BookMetadataDraft, limit: Int): List<MetadataLookupCandidate> = candidates
        .map { it to score(it, draft) }.filter { it.second >= 0 }
        .sortedWith(compareByDescending<Pair<MetadataLookupCandidate, Double>> { it.second }.thenBy { it.first.id })
        .map { it.first }.distinctBy { candidate ->
            // Only remote edition ISBNs identify an edition; inherited EPUB values do not.
            candidate.matchedIsbns.sorted().firstOrNull()?.let { "isbn:$it" }
                ?: "${normalize(candidate.metadata.title)}|${candidate.metadata.authors.map(::normalize).sorted()}|${language(candidate.metadata.language)}|${normalize(candidate.metadata.publisher.orEmpty())}|${candidate.metadata.publishedDate}"
        }.take(limit.coerceAtLeast(0))
}
