package com.foliore.android.ai

data class AiCoverStyleRequest(
    val id: String,
    val label: String,
    val style: String,
)

data class AiGeneratedCoverOption(
    val id: String,
    val label: String,
    val style: String,
    val prompt: String,
    val imagePath: String?,
)

sealed class OpenAiResult<out T> {
    data class Success<T>(val value: T) : OpenAiResult<T>()
    data class Failure(val message: String? = null) : OpenAiResult<Nothing>()
}
