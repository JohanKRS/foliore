package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft
import kotlinx.serialization.json.JsonObject

internal data class CatalogSeries(val name: String, val index: Double? = null)

private const val ordinal = "(?:[0-9]+(?:[.,][0-9]+)?|[IVXLCDM]+|one|two|three|four|five|six|seven|eight|nine|ten|first|second|third|fourth|fifth)"
private const val volumeLabel = "(?:book|bk\\.?|livro|volume|vol\\.?|v\\.?|tome|t\\.?|part|pt\\.?)"
private val numberedSuffix = Regex("^(.+?)\\s*(?:[,;:–—-]+\\s*)?(?:\\b$volumeLabel\\s*|#\\s*)($ordinal)\\.?$", RegexOption.IGNORE_CASE)
private val separatedSuffix = Regex("^(.+?)\\s*(?:--|[;#–—])\\s*($ordinal)\\.?$", RegexOption.IGNORE_CASE)
private val numberedPrefix = Regex("^$volumeLabel\\s*($ordinal)\\s+(?:of|in|de|da|do)\\s+(.+)$", RegexOption.IGNORE_CASE)

private fun String.seriesNumber(): Double? {
    replace(',', '.').toDoubleOrNull()?.let { return it.takeIf { n -> n.isFinite() && n in 0.0..999.0 } }
    val words = mapOf("one" to 1, "first" to 1, "two" to 2, "second" to 2, "three" to 3, "third" to 3,
        "four" to 4, "fourth" to 4, "five" to 5, "fifth" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
    words[lowercase()]?.let { return it.toDouble() }
    val roman = uppercase()
    if (roman.isEmpty() || !Regex("X{0,3}(IX|IV|V?I{0,3})").matches(roman)) return null
    val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
    return roman.mapIndexed { i, c -> values.getValue(c) * if (i < roman.lastIndex && values.getValue(c) < values.getValue(roman[i + 1])) -1 else 1 }.sum().toDouble()
}

private fun String.seriesName(): String? = trim().trim(',', ';', ':', '-', '–', '—', ' ')
    .takeIf { it.any(Char::isLetter) && !Regex("(?i)^\\d+(?:st|nd|rd|th)?\\s+ed\\.?$").matches(it) }
    ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

internal fun String.seriesKey(): String = MetadataSearchRules.normalize(this).removePrefix("the ").removeSuffix(" series")

/** Only bibliographic series fields allow a bare trailing number; book titles never do. */
internal fun String.catalogSeries(allowBareNumber: Boolean = true): CatalogSeries? {
    val raw = trim().replace(Regex("(?i)^series\\s*:\\s*"), "").replace('_', ' ')
    numberedPrefix.matchEntire(raw)?.let { m ->
        val name = m.groupValues[2].seriesName() ?: return null
        return m.groupValues[1].seriesNumber()?.let { CatalogSeries(name, it) }
    }
    (numberedSuffix.matchEntire(raw) ?: separatedSuffix.matchEntire(raw))?.let { m ->
        val name = m.groupValues[1].seriesName() ?: return null
        return m.groupValues[2].seriesNumber()?.let { CatalogSeries(name, it) }
    }
    if (allowBareNumber) {
        Regex("^(.+?)\\s+(\\d+(?:[.,]\\d+)?)$").matchEntire(raw)?.let { m ->
            val number = m.groupValues[2].seriesNumber()
            val name = m.groupValues[1].seriesName()
            if (number != null && name != null) return CatalogSeries(name, number)
        }
    }
    return raw.seriesName()?.let { CatalogSeries(it) }
}

internal fun String.seriesDetails(): Pair<String, Double?> = catalogSeries()?.let { it.name to it.index } ?: (trim() to null)

internal fun catalogSeries(
    explicit: List<String> = emptyList(), titles: List<String> = emptyList(), subjects: List<String> = emptyList(),
): CatalogSeries? {
    val fields = explicit.mapNotNull { it.catalogSeries() }
    val tagged = subjects.filter { it.startsWith("series:", true) }.mapNotNull { it.catalogSeries() }
    val annotations = titles.flatMap { text ->
        listOf(text) + Regex("[\\[(]([^\\[\\]()]+)[\\])]").findAll(text).map { it.groupValues[1] }.toList()
    }.mapNotNull { it.catalogSeries(allowBareNumber = false)?.takeIf { series -> series.index != null } }
    // Parenthesized series annotations are more precise than a full title containing them.
    val numbered = (annotations + fields).firstOrNull { it.index != null }
    if (numbered != null) return numbered
    val named = tagged.firstOrNull() ?: fields.firstOrNull() ?: return null
    val standaloneNumber = titles.firstNotNullOfOrNull {
        Regex("^$volumeLabel\\s*($ordinal)\\.?$", RegexOption.IGNORE_CASE).matchEntire(it.trim())?.groupValues?.get(1)?.seriesNumber()
    }
    return named.copy(index = standaloneNumber)
}

internal fun JsonObject.catalogSeries(): CatalogSeries? = catalogSeries(
    explicit = values("series"), titles = listOfNotNull(text("title"), text("subtitle")), subjects = strings("subjects"),
)

internal fun BookMetadataDraft.withSeries(series: CatalogSeries?): BookMetadataDraft =
    if (series == null) this else copy(seriesName = series.name, seriesIndex = series.index)

/** Only series information crosses editions of the same work; edition dates/ISBNs do not. */
internal fun seriesFromWorkEditions(workKey: String, editions: JsonObject, preferredName: String?, language: String?): CatalogSeries? {
    val evidence = editions.items("entries").filterIsInstance<JsonObject>().filter { edition ->
        edition.items("works").filterIsInstance<JsonObject>().any { it.text("key") == workKey }
    }.mapNotNull { edition ->
        edition.catalogSeries()?.takeIf { it.index != null }?.let { series ->
            series to edition.items("languages").filterIsInstance<JsonObject>().firstNotNullOfOrNull { MetadataSearchRules.language(it.text("key")) }
        }
    }
    val matching = preferredName?.let { name -> evidence.filter { it.first.name.seriesKey() == name.seriesKey() } }.orEmpty()
    val preferredLanguage = MetadataSearchRules.language(language)
    val relevant = matching.ifEmpty {
        evidence.filter { preferredLanguage != null && it.second == preferredLanguage }
            .ifEmpty { evidence.filter { it.second == null } }.ifEmpty { evidence }
    }
    // Conflicting positions (e.g. differently split editions) must not become a guessed number.
    if (relevant.isEmpty() || relevant.map { it.first.index }.distinct().size != 1) return null
    val chosen = relevant.firstOrNull { it.second != null && it.second == MetadataSearchRules.language(language) } ?: relevant.first()
    return if (matching.isNotEmpty() && preferredName != null) chosen.first.copy(name = preferredName) else chosen.first
}
