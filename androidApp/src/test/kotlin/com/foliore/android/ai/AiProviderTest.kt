package com.foliore.android.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiProviderTest {
    @Test
    fun noSignInAndNoKeyLeavesAiOff() {
        val capabilities = AiCapabilities.from(chatGptSignedIn = false, hasApiKey = false)
        assertFalse(capabilities.canFillText)
        assertFalse(capabilities.hasCoverTools)
        assertFalse(capabilities.usesChatGptPlan)
    }

    @Test
    fun chatGptHandlesTextAndCoverSearchButNotImagesYet() {
        val capabilities = AiCapabilities.from(chatGptSignedIn = true, hasApiKey = false)
        assertEquals(AiProvider.CHATGPT, capabilities.textProvider)
        assertTrue(capabilities.canFindCovers)
        assertEquals(AiFeatureFlags.CHATGPT_IMAGE_GENERATION, capabilities.canGenerateCovers)
        assertTrue(capabilities.usesChatGptPlan)
    }

    @Test
    fun apiKeyGeneratesCoversWhileChatGptKeepsText() {
        val both = AiCapabilities.from(chatGptSignedIn = true, hasApiKey = true)
        assertEquals(AiProvider.CHATGPT, both.textProvider)
        if (!AiFeatureFlags.CHATGPT_IMAGE_GENERATION) assertEquals(AiProvider.OPENAI_API, both.imageProvider)

        val keyOnly = AiCapabilities.from(chatGptSignedIn = false, hasApiKey = true)
        assertEquals(AiProvider.OPENAI_API, keyOnly.textProvider)
        assertEquals(AiProvider.OPENAI_API, keyOnly.imageProvider)
        assertFalse(keyOnly.usesChatGptPlan)
    }

    @Test
    fun chatGptPlanErrorsAreClassified() {
        fun code(body: String, status: Int = 400) = AiErrorCodes.failure(status, body).code
        assertEquals(AiErrorCodes.CHATGPT_USAGE_LIMIT, code("""{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}""", 429))
        assertEquals(AiErrorCodes.CHATGPT_NOT_ELIGIBLE, code("""{"error":{"code":"subscription_sharing_user_not_eligible"}}""", 403))
        assertEquals(AiErrorCodes.CHATGPT_SIGN_IN_REQUIRED, code("""{"error":{"code":"subscription_sharing_invalid_user"}}""", 401))
        assertEquals(AiErrorCodes.UNSUPPORTED_CAPABILITY, code("""{"error":{"code":"subscription_sharing_unsupported_capability"}}"""))
        assertEquals(AiErrorCodes.CHATGPT_UNAVAILABLE, code("""{"error":{"code":"subscription_sharing_usage_unavailable"}}""", 503))
        assertEquals(AiErrorCodes.CHATGPT_SIGN_IN_REQUIRED, code("""{"detail":"Unauthorized"}""", 401))
        assertEquals(AiErrorCodes.RATE_LIMITED, code("not json", 429))
        assertEquals(AiErrorCodes.REQUEST_FAILED, code("""{"error":{"code":"server_error"}}""", 500))
    }

    @Test
    fun apiKeyErrorsDoNotAskForAChatGptSignIn() {
        assertEquals(AiErrorCodes.REQUEST_FAILED, AiErrorCodes.failure(401, "{}", AiProvider.OPENAI_API).code)
        assertEquals(AiErrorCodes.RATE_LIMITED, AiErrorCodes.failure(429, "{}", AiProvider.OPENAI_API).code)
    }

    @Test
    fun unsupportedParametersKeepTheirName() {
        val failure = AiErrorCodes.failure(400, """{"error":{"code":"unsupported_parameter","param":"reasoning","message":"no"}}""")
        assertEquals("reasoning", failure.param)
        assertEquals("no", failure.message)
        assertNull(AiErrorCodes.failure(400, "{}").param)
    }
}
