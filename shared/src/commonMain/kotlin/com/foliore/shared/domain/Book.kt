package com.foliore.shared.domain

data class Book(
    val id: String,
    val originalFilePath: String,
    val preparedFilePath: String?,
    val coverImagePath: String?,
    val coverThumbPath: String?,
    val originalHashSha256: String,
    val fileSizeBytes: Long,
    val importedAt: String,
    val updatedAt: String,
    val lastPreparedAt: String?,
    val lastExportedAt: String?,
    val lastSyncedAt: String?,
    val status: BookStatus,
    val syncStatus: SyncStatus,
    val revision: Long,
    val updatedByDeviceId: String,
    val technicalStatus: String?,
)

enum class BookStatus {
    IMPORTED,
    NEEDS_REVIEW,
    EDITED,
    PREPARED,
    OUTDATED_PREPARED,
    ERROR,
}

enum class SyncStatus {
    LOCAL_ONLY,
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DOWNLOAD,
    CONFLICT,
    SYNC_ERROR,
}

