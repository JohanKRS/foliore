package com.foliore.android.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Where an AI request is sent. Both are optional; the app works fully without either. */
enum class AiProvider {
    /** The user's ChatGPT Plus or Pro plan, through "Sign in with ChatGPT". */
    CHATGPT,

    /** The user's own OpenAI API key. */
    OPENAI_API,
}

object AiFeatureFlags {
    /**
     * ChatGPT plan usage does not support image generation yet
     * (https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference).
     * When OpenAI enables it, flip this to send cover generation through the plan as well.
     */
    const val CHATGPT_IMAGE_GENERATION = false
}

/** What the AI features can do with the current sign-in and key. */
data class AiCapabilities(
    val textProvider: AiProvider? = null,
    val imageProvider: AiProvider? = null,
) {
    val canFillText: Boolean get() = textProvider != null
    val canFindCovers: Boolean get() = textProvider != null
    val canGenerateCovers: Boolean get() = imageProvider != null
    val hasCoverTools: Boolean get() = canFindCovers || canGenerateCovers
    val usesChatGptPlan: Boolean get() = textProvider == AiProvider.CHATGPT || imageProvider == AiProvider.CHATGPT

    companion object {
        fun from(chatGptSignedIn: Boolean, hasApiKey: Boolean): AiCapabilities = AiCapabilities(
            textProvider = when {
                chatGptSignedIn -> AiProvider.CHATGPT
                hasApiKey -> AiProvider.OPENAI_API
                else -> null
            },
            imageProvider = when {
                chatGptSignedIn && AiFeatureFlags.CHATGPT_IMAGE_GENERATION -> AiProvider.CHATGPT
                hasApiKey -> AiProvider.OPENAI_API
                else -> null
            },
        )
    }
}

/** Failure codes shared by the AI service and the screens that explain them. */
object AiErrorCodes {
    const val PROVIDER_DISABLED = "provider_disabled"
    const val CHATGPT_SIGN_IN_REQUIRED = "chatgpt_sign_in_required"
    const val CHATGPT_NOT_ELIGIBLE = "chatgpt_not_eligible"
    const val CHATGPT_USAGE_LIMIT = "chatgpt_usage_limit"
    const val CHATGPT_UNAVAILABLE = "chatgpt_unavailable"
    const val UNSUPPORTED_CAPABILITY = "unsupported_capability"
    const val RATE_LIMITED = "rate_limited"
    const val REQUEST_FAILED = "request_failed"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Maps an error response to a code. ChatGPT plan errors follow
     * https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery; some
     * responses only carry {"detail": "..."}.
     */
    fun failure(status: Int, body: String, provider: AiProvider = AiProvider.CHATGPT): OpenAiResult.Failure {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val error = root?.get("error") as? JsonObject
        val code = error?.string("code") ?: error?.string("type")
        val message = error?.string("message") ?: root?.string("detail")
        val param = error?.string("param")
        return OpenAiResult.Failure(message = message, code = classify(status, code, provider), param = param)
    }

    fun streamFailure(event: JsonObject): OpenAiResult.Failure {
        val response = event["response"] as? JsonObject
        val error = (response?.get("error") as? JsonObject) ?: (event["error"] as? JsonObject) ?: event
        val code = error.string("code")
        return OpenAiResult.Failure(
            message = error.string("message"),
            code = classify(status = 200, code = code, provider = AiProvider.CHATGPT),
            param = error.string("param"),
        )
    }

    internal fun classify(status: Int, code: String?, provider: AiProvider): String = when (code) {
        "subscription_sharing_usage_limit_exceeded" -> CHATGPT_USAGE_LIMIT
        "subscription_sharing_user_not_eligible" -> CHATGPT_NOT_ELIGIBLE
        "subscription_sharing_invalid_user" -> CHATGPT_SIGN_IN_REQUIRED
        "subscription_sharing_unsupported_capability" -> UNSUPPORTED_CAPABILITY
        "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> CHATGPT_UNAVAILABLE
        "slow_down", "rate_limit_exceeded" -> RATE_LIMITED
        else -> when {
            provider == AiProvider.CHATGPT && status == 401 -> CHATGPT_SIGN_IN_REQUIRED
            provider == AiProvider.CHATGPT && status == 503 -> CHATGPT_UNAVAILABLE
            status == 429 -> RATE_LIMITED
            else -> REQUEST_FAILED
        }
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
}
