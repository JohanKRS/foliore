package com.foliore.shared.domain

import kotlinx.datetime.LocalDate

/** Keep the precision supplied by a catalog, in an EPUB-compatible date format. */
fun String?.publicationDateOrNull(): String? {
    val raw = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val iso = Regex("^(\\d{4})-(\\d{2})(?:-(\\d{2}))?(?:[T ].*)?$").matchEntire(raw)
    if (iso != null) {
        val year = iso.groupValues[1].toInt()
        val month = iso.groupValues[2].toInt()
        val day = iso.groupValues[3].toIntOrNull()
        if (runCatching { LocalDate(year, month, day ?: 1) }.isSuccess) {
            return raw.take(if (day == null) 7 else 10)
        }
    }
    // Open Library's edition catalog also returns English month names.
    val words = raw.lowercase().replace(',', ' ').split(Regex("\\s+"))
    val month = words.firstOrNull()?.let { publicationMonths.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }
    val year = words.lastOrNull()?.takeIf { it.matches(Regex("\\d{4}")) }?.toIntOrNull()
    if (month != null && year != null && words.size in 2..3) {
        val day = if (words.size == 3) words[1].toIntOrNull() else null
        if ((words.size == 2 || day != null) && runCatching { LocalDate(year, month, day ?: 1) }.isSuccess) {
            val date = "$year-${month.toString().padStart(2, '0')}"
            return if (day == null) date else "$date-${day.toString().padStart(2, '0')}"
        }
    }
    return raw.publicationYearOrNull()
}

private val publicationMonths = listOf("january", "february", "march", "april", "may", "june", "july",
    "august", "september", "october", "november", "december")
