package com.opencode.android.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val JSON = "application/json; charset=utf-8".toMediaType()

/** Cliente OpenAI-compatible. Cubre zen (https://opencode.ai/zen/v1), openai y custom. */
class OpenAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = defaultClient()
) : LlmClient {

    override suspend fun complete(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        systemPrompt: String
    ): LlmResult = withContext(Dispatchers.IO) {
        try {
            val body = buildRequest(messages, tools, systemPrompt)
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext LlmResult.error("HTTP ${response.code}: ${raw.take(500)}")
            }
            val json = JSONObject(raw)
            val choice = json.getJSONArray("choices").getJSONObject(0)
            val msg = choice.getJSONObject("message")

            val text = msg.optString("content", "").ifBlank { null }
            val calls = mutableListOf<ToolCall>()
            if (msg.has("tool_calls")) {
                val arr = msg.getJSONArray("tool_calls")
                for (i in 0 until arr.length()) {
                    val tc = arr.getJSONObject(i)
                    val fn = tc.getJSONObject("function")
                    calls.add(
                        ToolCall(
                            id = tc.getString("id"),
                            name = fn.getString("name"),
                            arguments = safeArgsString(fn)
                        )
                    )
                }
            }
            LlmResult(text = text, toolCalls = calls, error = null)
        } catch (e: JSONException) {
            LlmResult.error("Respuesta malformada del modelo: ${e.message}")
        } catch (e: Exception) {
            LlmResult.error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun buildRequest(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        systemPrompt: String
    ): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for (m in messages) {
            val o = JSONObject().put("role", m.role)
            when (m.role) {
                "tool" -> {
                    o.put("tool_call_id", m.toolCallId ?: "")
                    o.put("content", m.content ?: "")
                }
                else -> {
                    // content debe ser null cuando el assistant responde solo con tool_calls
                    if (m.content != null) o.put("content", m.content)
                    else o.put("content", JSONObject.NULL)
                    if (m.toolCalls.isNotEmpty()) {
                        val calls = JSONArray()
                        for (tc in m.toolCalls) {
                            calls.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.name)
                                    put("arguments", tc.arguments)
                                })
                            })
                        }
                        o.put("tool_calls", calls)
                    }
                }
            }
            arr.put(o)
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", arr)
            put("temperature", 0.2)
        }
        if (tools.isNotEmpty()) {
            val tArr = JSONArray()
            for (t in tools) {
                tArr.put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", t.parameters)
                    })
                })
            }
            body.put("tools", tArr)
        }
        return body.toString()
    }

    /** 'arguments' suele venir como string JSON; algunos backends lo mandan como objeto. */
    private fun safeArgsString(fn: JSONObject): String {
        val v = fn.opt("arguments") ?: return "{}"
        return if (v is String) v else v.toString()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}