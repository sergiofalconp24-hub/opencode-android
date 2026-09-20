package com.opencode.android.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val GJ = "application/json; charset=utf-8".toMediaType()

/** Cliente Gemini (generativelanguage.googleapis.com, model:generateContent). */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : LlmClient {

    override suspend fun complete(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        systemPrompt: String
    ): LlmResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                if (tools.isNotEmpty()) put("tools", buildGeminiTools(tools))
                put("contents", buildContents(messages))
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(json.toString().toRequestBody(GJ))
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext LlmResult.error("HTTP ${response.code}: ${raw.take(500)}")
            }
            val body = JSONObject(raw)

            var text: String? = null
            val calls = mutableListOf<ToolCall>()
            val candidates = body.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                if (contentObj != null) {
                    val parts = contentObj.optJSONArray("parts") ?: JSONArray()
                    val texts = ArrayList<String>()
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        when {
                            p.has("text") -> texts.add(p.getString("text"))
                            p.has("functionCall") -> {
                                val fc = p.getJSONObject("functionCall")
                                calls.add(ToolCall(
                                    id = "fc-${System.currentTimeMillis()}-$i",
                                    name = fc.getString("name"),
                                    arguments = fc.optJSONObject("args")?.toString() ?: "{}"
                                ))
                            }
                        }
                    }
                    if (texts.isNotEmpty()) text = texts.joinToString("\n")
                }
            }

            LlmResult(text = text, toolCalls = calls, error = null)
        } catch (e: Exception) {
            LlmResult.error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Gemini exige roles alternados (model/user) y fusiona parts por strict alternation. */
    private fun buildContents(messages: List<ChatMessage>): JSONArray {
        val contents = JSONArray()

        fun lastRole(): String =
            if (contents.length() > 0) contents.getJSONObject(contents.length() - 1).getString("role") else ""

        for (m in messages) {
            val role = if (m.role == "assistant") "model" else "user"
            val parts = JSONArray()

            if (m.role == "tool") {
                if (lastRole() != "user") {
                    contents.put(JSONObject().put("role", "user").put("parts", JSONArray()))
                }
                parts.put(JSONObject().put("functionResponse", JSONObject().apply {
                    put("name", m.name ?: "unknown")
                    put("response", JSONObject().put("result", m.content ?: ""))
                }))
            } else if (m.role == "assistant") {
                if (!m.content.isNullOrBlank()) parts.put(JSONObject().put("text", m.content))
                for (tc in m.toolCalls) {
                    val input = try {
                        JSONObject(tc.arguments)
                    } catch (e: Exception) {
                        JSONObject().put("raw", tc.arguments)
                    }
                    parts.put(JSONObject().put("functionCall", JSONObject().apply {
                        put("name", tc.name)
                        put("args", input)
                    }))
                }
            } else {
                parts.put(JSONObject().put("text", m.content ?: ""))
            }

            if (lastRole() == role) {
                contents.getJSONObject(contents.length() - 1).getJSONArray("parts").apply {
                    for (i in 0 until parts.length()) put(parts.get(i))
                }
            } else {
                contents.put(JSONObject().put("role", role).put("parts", parts))
            }
        }
        return contents
    }

    private fun buildGeminiTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("functionDeclarations", JSONArray().apply {
                for (t in tools) {
                    put(JSONObject().apply {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", t.parameters)
                    })
                }
            })
        })
        return arr
    }
}