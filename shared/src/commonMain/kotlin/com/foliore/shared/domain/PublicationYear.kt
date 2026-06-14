package com.foliore.shared.domain

fun String?.publicationYearOrNull(): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return publicationYearRegex.find(raw)?.value
}

private val publicationYearRegex = Regex("\\b(1[0-9]{3}|20[0-9]{2}|21[0-9]{2})\\b")
