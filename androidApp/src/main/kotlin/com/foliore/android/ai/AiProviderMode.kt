package com.foliore.android.ai

enum class AiProviderMode {
    DISABLED,
    OPENAI_API,
}

val AiProviderMode.isEnabled: Boolean
    get() = this != AiProviderMode.DISABLED
