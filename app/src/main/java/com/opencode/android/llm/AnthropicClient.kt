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

private val TJ = "application/json; charset=utf-8".toMediaType()

/**
 * Cliente Anthropic (Messages API) con estructura correcta de mensajes:
 * - roles alternados y fusionados cuando son consecutivos
 * - assistant → bloques text + tool_use
 * - tool → user(tool_result) inmediatamente después del assistant que la invocó
 */
class AnthropicClient(
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
                put("model", model)
                put("max_tokens", 8192)
                put("system", systemPrompt)
                if (tools.isNotEmpty()) put("tools", buildAnthropicTools(tools))
                put("messages", buildMessages(messages))
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(json.toString().toRequestBody(TJ))
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext LlmResult.error("HTTP ${response.code}: ${raw.take(500)}")
            }
            val body = JSONObject(raw)

            val texts = ArrayList<String>()
            val calls = mutableListOf<ToolCall>()
            val contentArr = body.getJSONArray("content")
            for (i in 0 until contentArr.length()) {
                val block = contentArr.getJSONObject(i)
                when (block.optString("type")) {
                    "text" -> texts.add(block.optString("text", ""))
                    "tool_use" -> calls.add(
                        ToolCall(
                            id = block.getString("id"),
                            name = block.getString("name"),
                            arguments = block.optJSONObject("input")?.toString() ?: "{}"
                        )
                    )
                }
            }
            LlmResult(
                text = texts.joinToString("\n").ifBlank { null },
                toolCalls = calls,
                error = null
            )
        } catch (e: Exception) {
            LlmResult.error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Construye el array de mensajes con roles alternados. */
    private fun buildMessages(messages: List<ChatMessage>): JSONArray {
        val out = JSONArray()

        fun currentOrNew(role: String): JSONObject {
            val last = if (out.length() > 0) out.getJSONObject(out.length() - 1) else null
            if (last != null && last.getString("role") == role) return last
            val o = JSONObject().put("role", role).put("content", JSONArray())
            out.put(o)
            return o
        }

        for (m in messages) {
            when (m.role) {
                "assistant" -> {
                    val o = currentOrNew("assistant")
                    val arr = o.getJSONArray("content")
                    if (!m.content.isNullOrBlank()) {
                        arr.put(JSONObject().put("type", "text").put("text", m.content))
                    }
                    for (tc in m.toolCalls) {
                        val input = try {
                            JSONObject(tc.arguments)
                        } catch (e: Exception) {
                            JSONObject().put("raw", tc.arguments)
                        }
                        arr.put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", tc.id)
                            put("name", tc.name)
                            put("input", input)
                        })
                    }
                }
                "tool" -> {
                    val o = currentOrNew("user")
                    o.getJSONArray("content").put(JSONObject().apply {
                        put("type", "tool_result")
                        put("tool_use_id", m.toolCallId ?: "")
                        put("content", m.content ?: "")
                    })
                }
                else -> {
                    val o = currentOrNew("user")
                    o.getJSONArray("content").put(
                        JSONObject().put("type", "text").put("text", m.content ?: "")
                    )
                }
            }
        }
        return out
    }

    private fun buildAnthropicTools(tools: List<ToolSpec>): JSONArray {
        val arr = JSONArray()
        for (t in tools) {
            arr.put(JSONObject().apply {
                put("name", t.name)
                put("description", t.description)
                put("input_schema", t.parameters)
            })
        }
        return arr
    }
}