package com.foliore.shared.data

import com.foliore.shared.domain.Book
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.EpubTechnicalInfo
import kotlinx.serialization.Serializable

@Serializable
data class MetadataSnapshot(
    val schemaVersion: Int,
    val bookId: String,
    val revision: Long,
    val updatedAt: String,
    val updatedByDeviceId: String,
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
    val originalHashSha256: String,
    val status: String,
)

@Serializable
data class TechnicalSnapshot(
    val schemaVersion: Int,
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

object PortableSnapshots {
    fun metadata(book: Book, metadata: BookMetadata): MetadataSnapshot {
        return MetadataSnapshot(
            schemaVersion = 1,
            bookId = book.id,
            revision = book.revision,
            updatedAt = book.updatedAt,
            updatedByDeviceId = book.updatedByDeviceId,
            title = metadata.title,
            subtitle = metadata.subtitle,
            authors = metadata.authors,
            sortAuthor = metadata.sortAuthor,
            language = metadata.language,
            publisher = metadata.publisher,
            publishedDate = metadata.publishedDate,
            description = metadata.description,
            tags = metadata.tags,
            isbn = metadata.isbn,
            identifiers = metadata.identifiers,
            seriesName = metadata.seriesName,
            seriesIndex = metadata.seriesIndex,
            originalHashSha256 = book.originalHashSha256,
            status = book.status.name,
        )
    }

    fun technical(info: EpubTechnicalInfo): TechnicalSnapshot {
        return TechnicalSnapshot(
            schemaVersion = 1,
            bookId = info.bookId,
            epubVersion = info.epubVersion,
            opfPath = info.opfPath,
            navPath = info.navPath,
            ncxPath = info.ncxPath,
            coverHref = info.coverHref,
            packageIdentifier = info.packageIdentifier,
            hasNav = info.hasNav,
            hasNcx = info.hasNcx,
            manifestItemCount = info.manifestItemCount,
            spineItemCount = info.spineItemCount,
            warnings = info.warnings,
            errors = info.errors,
        )
    }
}

