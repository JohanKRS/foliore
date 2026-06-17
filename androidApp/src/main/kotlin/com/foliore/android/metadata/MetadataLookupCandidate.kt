package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft

data class MetadataLookupCandidate(
    val id: String,
    val sourceName: String,
    val metadata: BookMetadataDraft,
    val coverImagePath: String?,
    val sourceUrl: String?,
)

sealed class MetadataLookupResult {
    data class Success(val candidates: List<MetadataLookupCandidate>) : MetadataLookupResult()
    data class Failure(val message: String? = null) : MetadataLookupResult()
}
