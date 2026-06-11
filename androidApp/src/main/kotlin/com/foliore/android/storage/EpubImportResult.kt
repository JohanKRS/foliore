package com.foliore.android.storage

sealed interface EpubImportResult {
    data class Imported(val bookId: String) : EpubImportResult
    data class Duplicate(val existingBookId: String) : EpubImportResult
    data class Failed(val reason: ImportFailureReason) : EpubImportResult
}

enum class ImportFailureReason {
    NOT_EPUB,
    CANNOT_OPEN,
    CANNOT_COPY,
    STORAGE_ERROR,
}

