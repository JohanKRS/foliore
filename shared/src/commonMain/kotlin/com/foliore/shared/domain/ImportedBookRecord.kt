package com.foliore.shared.domain

data class ImportedBookRecord(
    val book: Book,
    val metadata: BookMetadata,
    val technicalInfo: EpubTechnicalInfo,
)

