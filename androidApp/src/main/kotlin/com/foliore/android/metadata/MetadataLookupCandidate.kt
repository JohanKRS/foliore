package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft

data class MetadataLookupCandidate(
    val id: String,
    val sourceName: String,
    val metadata: BookMetadataDraft,
    val coverImagePath: String?,
    val coverPixelWidth: Int? = null,
    val coverPixelHeight: Int? = null,
    val sourceUrl: String?,
    val matchedIsbns: Set<String> = emptySet(),
    val coverUrls: List<String> = emptyList(),
    val editionKey: String? = null,
    val workKey: String? = null,
    val editionResolved: Boolean = false,
    val sourceLanguage: String? = null,
    val authorKeys: List<String> = emptyList(),
    val detailsResolved: Boolean = false,
    val workEditionCount: Int = 0,
    val seriesWorkKeys: List<String> = emptyList(),
)

sealed class MetadataLookupResult {
    data class Success(val candidates: List<MetadataLookupCandidate>) : MetadataLookupResult()
    data class Failure(val message: String? = null, val notConfigured: Boolean = false) : MetadataLookupResult()
}
