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
    GOOGLE_BOOKS,
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

