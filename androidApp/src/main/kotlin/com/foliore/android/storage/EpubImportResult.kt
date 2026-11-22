package com.foliore.android.storage

sealed interface EpubImportResult {
    data class Imported(val bookId: String) : EpubImportResult
    data class Duplicate(val existingBookId: String) : EpubImportResult
    data class NeedsReview(val pendingImport: PendingEpubImport) : EpubImportResult
    data class Failed(val reason: ImportFailureReason) : EpubImportResult
}

class PendingEpubImport internal constructor(
    val token: String,
    val existingBookId: String,
    val existingTitle: String,
    val existingAuthors: List<String>,
    val incomingTitle: String,
    val incomingAuthors: List<String>,
    internal val displayName: String,
    internal val tempFilePath: String,
    internal val sha256: String,
    internal val contentFingerprint: String,
    internal val sourceFilePath: String? = null,
)

enum class ImportFailureReason {
    NOT_EPUB,
    CANNOT_OPEN,
    CANNOT_COPY,
    STORAGE_ERROR,
    DRM_PROTECTED,
    CONVERSION_FAILED,
    TOO_LARGE,
}
