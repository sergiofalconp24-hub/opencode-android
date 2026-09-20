package com.opencode.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val provider: String = "zen",                        // zen | openai | anthropic | gemini | custom
    val baseUrl: String = "https://opencode.ai/zen/v1",  // endpoint compatible OpenAI (zen/openai/custom)
    val apiKey: String = "",
    val model: String = "big-pickle",
    val port: Int = 8787,
    val systemPrompt: String = "You are OpenCode, an autonomous coding agent running on Android. You can perform file operations inside the project sandbox and run bash commands. Be concise, work step by step, and always complete the task yourself."
)

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        provider = prefs.getString("provider", "zen") ?: "zen",
        baseUrl = prefs.getString("baseUrl", "https://opencode.ai/zen/v1") ?: "https://opencode.ai/zen/v1",
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", "big-pickle") ?: "big-pickle",
        port = prefs.getInt("port", 8787),
        systemPrompt = prefs.getString("systemPrompt", AppSettings().systemPrompt) ?: AppSettings().systemPrompt
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit().apply {
            putString("provider", next.provider)
            putString("baseUrl", next.baseUrl)
            putString("apiKey", next.apiKey)
            putString("model", next.model)
            putInt("port", next.port)
            putString("systemPrompt", next.systemPrompt)
        }.apply()
        _settings.value = next
    }
}