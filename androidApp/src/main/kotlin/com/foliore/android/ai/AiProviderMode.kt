package com.foliore.android.ai

enum class AiProviderMode {
    DISABLED,
    OPENAI_API,
    CODEX_OAUTH_EXPERIMENTAL,
}

val AiProviderMode.isEnabled: Boolean
    get() = this != AiProviderMode.DISABLED
