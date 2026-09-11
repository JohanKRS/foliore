package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft

enum class MetadataField {
    TITLE, SUBTITLE, AUTHORS, SORT_AUTHOR, SERIES, SERIES_INDEX, LANGUAGE, PUBLISHER, PUBLISHED_DATE,
    TAGS, ISBN, DESCRIPTION, IDENTIFIERS, COVER;

    fun available(metadata: BookMetadataDraft): Boolean = when (this) {
        TITLE -> metadata.title.isNotBlank()
        SUBTITLE -> !metadata.subtitle.isNullOrBlank()
        AUTHORS -> metadata.authors.isNotEmpty()
        SORT_AUTHOR -> !metadata.sortAuthor.isNullOrBlank()
        SERIES -> !metadata.seriesName.isNullOrBlank()
        SERIES_INDEX -> metadata.seriesIndex != null
        LANGUAGE -> !metadata.language.isNullOrBlank()
        PUBLISHER -> !metadata.publisher.isNullOrBlank()
        PUBLISHED_DATE -> !metadata.publishedDate.isNullOrBlank()
        TAGS -> metadata.tags.isNotEmpty()
        ISBN -> !metadata.isbn.isNullOrBlank()
        DESCRIPTION -> !metadata.description.isNullOrBlank()
        IDENTIFIERS -> metadata.identifiers.isNotEmpty()
        COVER -> false
    }
    companion object {
        val defaultSelection: Set<MetadataField> = entries.toSet() - COVER
    }
}

/** Missing source values and unchecked fields never erase existing information. */
fun BookMetadataDraft.applySelectedMetadata(incoming: BookMetadataDraft, selected: Set<MetadataField>): BookMetadataDraft {
    fun use(field: MetadataField) = field in selected && field.available(incoming)
    return copy(
        title = if (use(MetadataField.TITLE)) incoming.title else title,
        subtitle = if (use(MetadataField.SUBTITLE)) incoming.subtitle else subtitle,
        authors = if (use(MetadataField.AUTHORS)) incoming.authors else authors,
        sortAuthor = if (use(MetadataField.SORT_AUTHOR)) incoming.sortAuthor else sortAuthor,
        seriesName = if (use(MetadataField.SERIES)) incoming.seriesName else seriesName,
        seriesIndex = if (use(MetadataField.SERIES_INDEX) && (use(MetadataField.SERIES) || !seriesName.isNullOrBlank())) incoming.seriesIndex else seriesIndex,
        language = if (use(MetadataField.LANGUAGE)) MetadataLanguages.code(incoming.language) ?: incoming.language else language,
        publisher = if (use(MetadataField.PUBLISHER)) incoming.publisher else publisher,
        publishedDate = if (use(MetadataField.PUBLISHED_DATE)) incoming.publishedDate else publishedDate,
        description = if (use(MetadataField.DESCRIPTION)) incoming.description else description,
        tags = if (use(MetadataField.TAGS)) incoming.tags else tags,
        isbn = if (use(MetadataField.ISBN)) incoming.isbn else isbn,
        identifiers = if (use(MetadataField.IDENTIFIERS)) identifiers + incoming.identifiers.filterKeys {
            MetadataField.ISBN in selected || !it.contains("isbn", ignoreCase = true)
        } else identifiers,
    )
}

internal fun emptySourceMetadata(title: String) = BookMetadataDraft(title, null, emptyList(), null, null, null,
    null, null, emptyList(), null, emptyMap(), null, null)

/** Combine only confirmed identical editions; keep the preferred source's nonempty values. */
internal fun MetadataLookupCandidate.complementWith(other: MetadataLookupCandidate): MetadataLookupCandidate {
    val missing = MetadataField.entries.filterNot { it.available(metadata) }.toSet().let { fields ->
        if (metadata.seriesName != null && other.metadata.seriesName != null &&
            metadata.seriesName?.seriesKey() != other.metadata.seriesName?.seriesKey()) fields - MetadataField.SERIES_INDEX else fields
    }
    return copy(
        metadata = metadata.applySelectedMetadata(other.metadata, missing).copy(identifiers = other.metadata.identifiers + metadata.identifiers),
        matchedIsbns = matchedIsbns + other.matchedIsbns,
        coverUrls = (coverUrls + other.coverUrls).distinct(),
        sourceName = (sourceName.split(" · ") + other.sourceName.split(" · ")).distinct().joinToString(" · "),
        editionKey = editionKey ?: other.editionKey, workKey = workKey ?: other.workKey,
        authorKeys = authorKeys.ifEmpty { other.authorKeys },
        workEditionCount = maxOf(workEditionCount, other.workEditionCount),
        seriesWorkKeys = (seriesWorkKeys + other.seriesWorkKeys).distinct(),
        sourceLanguage = sourceLanguage ?: other.sourceLanguage,
    )
}
