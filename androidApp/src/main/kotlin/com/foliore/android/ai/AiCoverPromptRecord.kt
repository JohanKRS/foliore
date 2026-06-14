package com.foliore.android.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiCoverPromptRecord(
    val id: String,
    val bookId: String,
    val title: String,
    val seriesName: String?,
    val seriesIndex: Double?,
    val coverImagePath: String?,
    val prompt: String,
    val style: String,
    val selectedAtEpochMillis: Long,
    val referenceBookId: String? = null,
    val referencePrompt: String? = null,
    val referenceCoverImagePath: String? = null,
)
