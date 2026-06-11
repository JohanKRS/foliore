package com.foliore.shared.domain

data class EpubTechnicalInfo(
    val bookId: String,
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

