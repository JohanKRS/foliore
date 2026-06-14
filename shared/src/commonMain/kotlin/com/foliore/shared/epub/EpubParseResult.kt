package com.foliore.shared.epub

data class EpubParseResult(
    val isReadable: Boolean,
    val metadata: ParsedEpubMetadata,
    val technicalInfo: ParsedEpubTechnicalInfo,
    val coverPath: String?,
)

data class ParsedEpubMetadata(
    val title: String?,
    val authors: List<String>,
    val language: String?,
    val identifier: String?,
    val identifiers: Map<String, String>,
    val isbn: String?,
    val description: String?,
    val publisher: String?,
    val publishedDate: String?,
)

data class ParsedEpubTechnicalInfo(
    val epubVersion: String?,
    val opfPath: String,
    val navPath: String?,
    val ncxPath: String?,
    val coverHref: String?,
    val packageIdentifier: String?,
    val hasNav: Boolean,
    val hasNcx: Boolean,
    val manifestItemCount: Long,
    val spineItemCount: Long,
    val warnings: List<String>,
    val errors: List<String>,
)
