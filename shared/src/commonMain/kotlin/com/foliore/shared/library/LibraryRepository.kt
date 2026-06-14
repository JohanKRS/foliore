package com.foliore.shared.library

import app.cash.sqldelight.db.SqlDriver
import com.foliore.shared.domain.BatchMetadataPatch
import com.foliore.shared.db.FolioreDatabase
import com.foliore.shared.domain.Book
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.ImportedBookRecord
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.MetadataSource
import com.foliore.shared.domain.SyncStatus
import com.foliore.shared.domain.publicationYearOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LibraryRepository(
    driver: SqlDriver,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val database = FolioreDatabase(driver)

    fun getBooks(): List<LibraryBook> {
        return database.libraryQueries.selectAll().executeAsList().map { row ->
            LibraryBook(
                book = Book(
                    id = row.id,
                    originalFilePath = row.original_file_path,
                    preparedFilePath = row.prepared_file_path,
                    coverImagePath = row.cover_image_path,
                    coverThumbPath = row.cover_thumb_path,
                    originalHashSha256 = row.original_hash_sha256,
                    fileSizeBytes = row.file_size_bytes,
                    importedAt = row.imported_at,
                    updatedAt = row.updated_at,
                    lastPreparedAt = row.last_prepared_at,
                    lastExportedAt = row.last_exported_at,
                    lastSyncedAt = row.last_synced_at,
                    status = row.status.toEnum(BookStatus.ERROR),
                    syncStatus = row.sync_status.toEnum(SyncStatus.LOCAL_ONLY),
                    revision = row.revision,
                    updatedByDeviceId = row.updated_by_device_id,
                    technicalStatus = row.technical_status,
                ),
                metadata = BookMetadata(
                    bookId = row.id,
                    title = row.title,
                    subtitle = row.subtitle,
                    authors = decodeList(row.authors_json),
                    sortAuthor = row.sort_author,
                    language = row.language,
                    publisher = row.publisher,
                    publishedDate = row.published_date.publicationYearOrNull(),
                    description = row.description,
                    tags = decodeList(row.tags_json),
                    isbn = row.isbn,
                    identifiers = decodeMap(row.identifiers_json),
                    seriesName = row.series_name,
                    seriesIndex = row.series_index,
                    source = row.source.toEnum(MetadataSource.EPUB),
                ),
            )
        }
    }

    fun getBook(bookId: String): LibraryBook? {
        return database.libraryQueries.selectById(bookId).executeAsOneOrNull()?.let { row ->
            LibraryBook(
                book = Book(
                    id = row.id,
                    originalFilePath = row.original_file_path,
                    preparedFilePath = row.prepared_file_path,
                    coverImagePath = row.cover_image_path,
                    coverThumbPath = row.cover_thumb_path,
                    originalHashSha256 = row.original_hash_sha256,
                    fileSizeBytes = row.file_size_bytes,
                    importedAt = row.imported_at,
                    updatedAt = row.updated_at,
                    lastPreparedAt = row.last_prepared_at,
                    lastExportedAt = row.last_exported_at,
                    lastSyncedAt = row.last_synced_at,
                    status = row.status.toEnum(BookStatus.ERROR),
                    syncStatus = row.sync_status.toEnum(SyncStatus.LOCAL_ONLY),
                    revision = row.revision,
                    updatedByDeviceId = row.updated_by_device_id,
                    technicalStatus = row.technical_status,
                ),
                metadata = BookMetadata(
                    bookId = row.id,
                    title = row.title,
                    subtitle = row.subtitle,
                    authors = decodeList(row.authors_json),
                    sortAuthor = row.sort_author,
                    language = row.language,
                    publisher = row.publisher,
                    publishedDate = row.published_date.publicationYearOrNull(),
                    description = row.description,
                    tags = decodeList(row.tags_json),
                    isbn = row.isbn,
                    identifiers = decodeMap(row.identifiers_json),
                    seriesName = row.series_name,
                    seriesIndex = row.series_index,
                    source = row.source.toEnum(MetadataSource.EPUB),
                ),
            )
        }
    }

    fun findBookIdByHash(hash: String): String? {
        return database.libraryQueries.findBookIdByHash(hash).executeAsOneOrNull()
    }

    fun deleteBook(bookId: String): LibraryBook? {
        val current = getBook(bookId) ?: return null
        database.transaction {
            database.libraryQueries.deleteMetadata(bookId)
            database.libraryQueries.deleteTechnicalInfo(bookId)
            database.libraryQueries.deleteBook(bookId)
        }
        return current
    }

    fun insertImportedBook(record: ImportedBookRecord) {
        val book = record.book
        val metadata = record.metadata
        val technicalInfo = record.technicalInfo

        database.transaction {
            database.libraryQueries.insertBook(
                id = book.id,
                original_file_path = book.originalFilePath,
                prepared_file_path = book.preparedFilePath,
                cover_image_path = book.coverImagePath,
                cover_thumb_path = book.coverThumbPath,
                original_hash_sha256 = book.originalHashSha256,
                file_size_bytes = book.fileSizeBytes,
                imported_at = book.importedAt,
                updated_at = book.updatedAt,
                last_prepared_at = book.lastPreparedAt,
                last_exported_at = book.lastExportedAt,
                last_synced_at = book.lastSyncedAt,
                status = book.status.name,
                sync_status = book.syncStatus.name,
                revision = book.revision,
                updated_by_device_id = book.updatedByDeviceId,
                technical_status = book.technicalStatus,
            )
            database.libraryQueries.insertMetadata(
                book_id = metadata.bookId,
                title = metadata.title,
                subtitle = metadata.subtitle,
                authors_json = json.encodeToString(metadata.authors),
                sort_author = metadata.sortAuthor,
                language = metadata.language,
                publisher = metadata.publisher,
                published_date = metadata.publishedDate.publicationYearOrNull(),
                description = metadata.description,
                tags_json = json.encodeToString(metadata.tags),
                isbn = metadata.isbn,
                identifiers_json = json.encodeToString(metadata.identifiers),
                series_name = metadata.seriesName,
                series_index = metadata.seriesIndex,
                source = metadata.source.name,
            )
            database.libraryQueries.insertTechnical(
                book_id = technicalInfo.bookId,
                epub_version = technicalInfo.epubVersion,
                opf_path = technicalInfo.opfPath,
                nav_path = technicalInfo.navPath,
                ncx_path = technicalInfo.ncxPath,
                cover_href = technicalInfo.coverHref,
                package_identifier = technicalInfo.packageIdentifier,
                has_nav = technicalInfo.hasNav.toLongFlag(),
                has_ncx = technicalInfo.hasNcx.toLongFlag(),
                manifest_item_count = technicalInfo.manifestItemCount,
                spine_item_count = technicalInfo.spineItemCount,
                warnings_json = json.encodeToString(technicalInfo.warnings),
                errors_json = json.encodeToString(technicalInfo.errors),
            )
        }
    }

    fun updateMetadata(
        bookId: String,
        draft: BookMetadataDraft,
        updatedAt: String,
        updatedByDeviceId: String,
    ): LibraryBook? {
        if (!draft.isValid()) return null
        val current = getBook(bookId) ?: return null
        val nextBook = current.book.asEdited(updatedAt, updatedByDeviceId)
        val nextMetadata = current.metadata.copy(
            title = draft.title.trim(),
            subtitle = draft.subtitle.cleanOptional(),
            authors = draft.authors.mapNotNull { it.cleanOptional() },
            sortAuthor = draft.sortAuthor.cleanOptional(),
            language = draft.language.cleanOptional(),
            publisher = draft.publisher.cleanOptional(),
            publishedDate = draft.publishedDate.publicationYearOrNull(),
            description = draft.description.cleanOptional(),
            tags = draft.tags.mapNotNull { it.cleanOptional() },
            isbn = draft.isbn.cleanOptional(),
            identifiers = draft.identifiers.filterValues { it.isNotBlank() },
            seriesName = draft.seriesName.cleanOptional(),
            seriesIndex = draft.seriesIndex,
            source = MetadataSource.MANUAL,
        )

        database.transaction {
            upsertBook(nextBook)
            upsertMetadata(nextMetadata)
        }

        return LibraryBook(nextBook, nextMetadata)
    }

    fun updateMetadataBatch(
        bookIds: List<String>,
        patch: BatchMetadataPatch,
        updatedAt: String,
        updatedByDeviceId: String,
    ): List<LibraryBook> {
        if (bookIds.isEmpty() || !patch.hasChanges()) return emptyList()

        val currentBooks = bookIds.distinct().mapNotNull(::getBook)
        val updatedBooks = currentBooks.mapIndexed { index, current ->
            val nextBook = current.book.asEdited(updatedAt, updatedByDeviceId)
            val nextMetadata = current.metadata.copy(
                authors = patch.authors?.mapNotNull { it.cleanOptional() } ?: current.metadata.authors,
                sortAuthor = patch.sortAuthor.cleanOptional() ?: current.metadata.sortAuthor,
                language = patch.language.cleanOptional() ?: current.metadata.language,
                publisher = patch.publisher.cleanOptional() ?: current.metadata.publisher,
                tags = patch.tags?.mapNotNull { it.cleanOptional() } ?: current.metadata.tags,
                seriesName = patch.seriesName.cleanOptional() ?: current.metadata.seriesName,
                seriesIndex = patch.seriesIndexStart?.let { it + index } ?: current.metadata.seriesIndex,
                source = MetadataSource.MANUAL,
            )
            LibraryBook(nextBook, nextMetadata)
        }

        database.transaction {
            updatedBooks.forEach { updated ->
                upsertBook(updated.book)
                upsertMetadata(updated.metadata)
            }
        }

        return updatedBooks
    }

    fun updateCover(
        bookId: String,
        coverImagePath: String,
        coverThumbPath: String,
        updatedAt: String,
        updatedByDeviceId: String,
    ): LibraryBook? {
        val current = getBook(bookId) ?: return null
        val nextBook = current.book.asEdited(updatedAt, updatedByDeviceId).copy(
            coverImagePath = coverImagePath,
            coverThumbPath = coverThumbPath,
        )

        database.transaction {
            upsertBook(nextBook)
        }

        return LibraryBook(nextBook, current.metadata)
    }

    fun markPrepared(
        bookId: String,
        preparedFilePath: String,
        updatedAt: String,
        updatedByDeviceId: String,
    ): LibraryBook? {
        val current = getBook(bookId) ?: return null
        val nextBook = current.book.copy(
            preparedFilePath = preparedFilePath,
            status = BookStatus.PREPARED,
            syncStatus = SyncStatus.PENDING_UPLOAD,
            revision = current.book.revision + 1,
            updatedAt = updatedAt,
            lastPreparedAt = updatedAt,
            updatedByDeviceId = updatedByDeviceId,
            technicalStatus = null,
        )

        database.transaction {
            upsertBook(nextBook)
        }

        return LibraryBook(nextBook, current.metadata)
    }

    fun markExported(
        bookId: String,
        exportedAt: String,
    ): LibraryBook? {
        val current = getBook(bookId) ?: return null
        val nextBook = current.book.copy(lastExportedAt = exportedAt)

        database.transaction {
            upsertBook(nextBook)
        }

        return LibraryBook(nextBook, current.metadata)
    }

    fun markSynced(
        bookId: String,
        syncedAt: String,
    ): LibraryBook? {
        val current = getBook(bookId) ?: return null
        val nextBook = current.book.copy(
            lastSyncedAt = syncedAt,
            syncStatus = SyncStatus.SYNCED,
        )

        database.transaction {
            upsertBook(nextBook)
        }

        return LibraryBook(nextBook, current.metadata)
    }

    fun markSyncConflict(bookId: String): LibraryBook? {
        val current = getBook(bookId) ?: return null
        val nextBook = current.book.copy(syncStatus = SyncStatus.CONFLICT)

        database.transaction {
            upsertBook(nextBook)
        }

        return LibraryBook(nextBook, current.metadata)
    }

    private fun upsertBook(book: Book) {
        database.libraryQueries.insertBook(
            id = book.id,
            original_file_path = book.originalFilePath,
            prepared_file_path = book.preparedFilePath,
            cover_image_path = book.coverImagePath,
            cover_thumb_path = book.coverThumbPath,
            original_hash_sha256 = book.originalHashSha256,
            file_size_bytes = book.fileSizeBytes,
            imported_at = book.importedAt,
            updated_at = book.updatedAt,
            last_prepared_at = book.lastPreparedAt,
            last_exported_at = book.lastExportedAt,
            last_synced_at = book.lastSyncedAt,
            status = book.status.name,
            sync_status = book.syncStatus.name,
            revision = book.revision,
            updated_by_device_id = book.updatedByDeviceId,
            technical_status = book.technicalStatus,
        )
    }

    private fun upsertMetadata(metadata: BookMetadata) {
        database.libraryQueries.insertMetadata(
            book_id = metadata.bookId,
            title = metadata.title,
            subtitle = metadata.subtitle,
            authors_json = json.encodeToString(metadata.authors),
            sort_author = metadata.sortAuthor,
            language = metadata.language,
            publisher = metadata.publisher,
            published_date = metadata.publishedDate.publicationYearOrNull(),
            description = metadata.description,
            tags_json = json.encodeToString(metadata.tags),
            isbn = metadata.isbn,
            identifiers_json = json.encodeToString(metadata.identifiers),
            series_name = metadata.seriesName,
            series_index = metadata.seriesIndex,
            source = metadata.source.name,
        )
    }

    private fun decodeList(raw: String): List<String> {
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    private fun decodeMap(raw: String): Map<String, String> {
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    private inline fun <reified T : Enum<T>> String.toEnum(default: T): T {
        return enumValues<T>().firstOrNull { it.name == this } ?: default
    }

    private fun Boolean.toLongFlag(): Long = if (this) 1L else 0L

    private fun Book.asEdited(updatedAt: String, updatedByDeviceId: String): Book {
        return copy(
            status = if (preparedFilePath != null) BookStatus.OUTDATED_PREPARED else BookStatus.EDITED,
            syncStatus = SyncStatus.PENDING_UPLOAD,
            revision = revision + 1,
            updatedAt = updatedAt,
            updatedByDeviceId = updatedByDeviceId,
        )
    }

    private fun String?.cleanOptional(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
