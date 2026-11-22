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
) {
    /** Found covers come from the web; their [prompt] holds the source page instead of a prompt. */
    val isFound: Boolean get() = style == STYLE_FOUND

    companion object {
        const val STYLE_FOUND = "found"
    }
}

sealed class OpenAiResult<out T> {
    data class Success<T>(val value: T) : OpenAiResult<T>()
    data class Failure(
        val message: String? = null,
        val code: String? = null,
        val param: String? = null,
    ) : OpenAiResult<Nothing>()
}
