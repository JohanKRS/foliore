package com.foliore.shared.domain

data class BookMetadata(
    val bookId: String,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val sortAuthor: String?,
    val language: String?,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?,
    val tags: List<String>,
    val isbn: String?,
    val identifiers: Map<String, String>,
    val seriesName: String?,
    val seriesIndex: Double?,
    val source: MetadataSource,
)

enum class MetadataSource {
    EPUB,
    MANUAL,
    OPEN_LIBRARY,
    MIXED,
}

data class BookMetadataDraft(
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val sortAuthor: String?,
    val language: String?,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?,
    val tags: List<String>,
    val isbn: String?,
    val identifiers: Map<String, String>,
    val seriesName: String?,
    val seriesIndex: Double?,
) {
    fun isValid(): Boolean = title.isNotBlank()
}

data class BatchMetadataPatch(
    val authors: List<String>? = null,
    val sortAuthor: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val tags: List<String>? = null,
    val seriesName: String? = null,
    val seriesIndexStart: Double? = null,
) {
    fun hasChanges(): Boolean {
        return authors != null ||
            sortAuthor != null ||
            language != null ||
            publisher != null ||
            tags != null ||
            seriesName != null ||
            seriesIndexStart != null
    }
}
