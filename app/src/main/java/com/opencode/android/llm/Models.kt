package com.opencode.android.llm

import org.json.JSONObject

/** Mensajes intercambiados con el LLM. role: system | user | assistant | tool */
data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCallId: String? = null,
    val name: String? = null,
    val toolCalls: List<ToolCall> = emptyList()
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String   // JSON string
)

sealed class ProviderKind {
    object Zen : ProviderKind()      // https://opencode.ai/zen/v1 (OpenAI-compatible, gratis)
    object OpenAi : ProviderKind()   // api.openai.com/v1
    object Anthropic : ProviderKind()
    object Gemini : ProviderKind()
    object Custom : ProviderKind()
}

fun providerKindFrom(name: String): ProviderKind = when (name.lowercase()) {
    "zen" -> ProviderKind.Zen
    "openai" -> ProviderKind.OpenAi
    "anthropic" -> ProviderKind.Anthropic
    "gemini" -> ProviderKind.Gemini
    else -> ProviderKind.Custom
}

fun defaultBaseUrl(kind: ProviderKind): String = when (kind) {
    ProviderKind.Zen -> "https://opencode.ai/zen/v1"
    ProviderKind.OpenAi -> "https://api.openai.com/v1"
    ProviderKind.Anthropic -> "https://api.anthropic.com"
    ProviderKind.Gemini -> "https://generativelanguage.googleapis.com"
    ProviderKind.Custom -> ""
}

interface LlmClient {
    /** Devuelve la respuesta del modelo. Si el modelo emite tool_calls, se devuelven en [LlmResult.toolCalls]. */
    suspend fun complete(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        systemPrompt: String
    ): LlmResult
}

data class LlmResult(
    val text: String?,
    val toolCalls: List<ToolCall>,
    val error: String?
) {
    companion object {
        fun error(msg: String) = LlmResult(text = null, toolCalls = emptyList(), error = msg)
    }
}

/** Especificación de una herramienta expuesta al LLM (schema JSON function). */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject
)