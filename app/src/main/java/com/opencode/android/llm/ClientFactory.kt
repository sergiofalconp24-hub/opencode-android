package com.opencode.android.llm

import com.opencode.android.data.AppSettings

fun createLlmClient(settings: AppSettings): LlmClient {
    val kind = providerKindFrom(settings.provider)
    val apiKey = settings.apiKey
    return when (kind) {
        ProviderKind.Anthropic -> AnthropicClient(apiKey = apiKey, model = settings.model)
        ProviderKind.Gemini -> GeminiClient(apiKey = apiKey, model = settings.model)
        else -> {
            val base = when (kind) {
                ProviderKind.Zen -> settings.baseUrl.ifBlank { "https://opencode.ai/zen/v1" }
                ProviderKind.OpenAi -> settings.baseUrl.ifBlank { "https://api.openai.com/v1" }
                else -> settings.baseUrl
            }
            OpenAiClient(
                baseUrl = base,
                apiKey = apiKey,
                model = settings.model
            )
        }
    }
}