package dev.opencode.android.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.opencode.android.data.model.AgentInfo
import dev.opencode.android.data.model.AssistantResult
import dev.opencode.android.data.model.ChatUiMessage
import dev.opencode.android.data.model.ChatRole
import dev.opencode.android.data.model.FileEntry
import dev.opencode.android.data.model.Json
import dev.opencode.android.data.model.ProjectInfo
import dev.opencode.android.data.model.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP del servidor de opencode (opencode serve).
 * Compatible con las rutas de las series 1.x (/prompt, /messages) y con
 * fallback a las rutas de la serie 2.x (/message) si el servidor responde 404.
 */
class OpenCodeClient(
    private val baseUrl: String,
    private val password: String?,
    okHttp: OkHttpClient,
) {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val http: OkHttpClient = okHttp.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun normalizedBase(): String {
        val b = baseUrl.trim().trimEnd('/')
        return if (b.isBlank()) "http://127.0.0.1:4096" else b
    }

    private fun authHeader(): String? {
        val p = password?.takeIf { it.isNotBlank() } ?: return null
        val user = "opencode"
        val token = try {
            Base64.getEncoder().encodeToString("$user:$p".toByteArray())
        } catch (_: Exception) {
            android.util.Base64.encodeToString("$user:$p".toByteArray(), android.util.Base64.NO_WRAP)
        }
        return "Basic $token"
    }

    private fun url(): String = normalizedBase()

    private suspend fun request(
        method: String,
        pathAndQuery: String,
        body: String? = null,
    ): okhttp3.Response = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url() + pathAndQuery)
            .method(method, body?.let { it.toRequestBody(json) })
        authHeader()?.let { builder.header("Authorization", it) }
        http.newCall(builder.build()).execute()
    }

    private fun parseObject(text: String?): JsonObject? = try {
        text?.let { JsonParser.parseString(it).takeIf { el -> el.isJsonObject }?.asJsonObject }
    } catch (_: Exception) {
        null
    }

    private fun parseArray(text: String?): JsonArray? = try {
        text?.let { JsonParser.parseString(it).takeIf { el -> el.isJsonArray }?.asJsonArray }
    } catch (_: Exception) {
        null
    }

    private suspend fun jsonObject(method: String, pathAndQuery: String, body: String? = null): JsonObject? {
        request(method, pathAndQuery, body).use { resp ->
            if (!resp.isSuccessful) return null
            return parseObject(resp.body?.string())
        }
    }

    /**
     * Ejecuta paths primario y alternativo. IMPORTANTE: el servidor sirve la SPA (HTML)
     * con 200 en rutas inexistentes, por eso se valida que la respuesta sea JSON válido
     * y se usa el fallback cuando no lo es.
     */
    private suspend fun sendJson(
        body: String,
        primary: String,
        fallback: String? = null,
    ): JsonObject? {
        var obj = request("POST", primary, body).use { resp ->
            if (resp.isSuccessful) parseObject(resp.body?.string()) else null
        }
        if (obj == null && fallback != null) {
            obj = request("POST", fallback, body).use { resp ->
                if (resp.isSuccessful) parseObject(resp.body?.string()) else null
            }
        }
        return obj
    }

    suspend fun health(): String? = withContext(Dispatchers.IO) {
        var v = jsonObject("GET", "/health")
        if (v == null) v = jsonObject("GET", "/global/health")
        v?.get("version")?.asString ?: v?.get("healthy")?.asBoolean?.toString()
    }

    suspend fun listSessions(): List<SessionInfo> = withContext(Dispatchers.IO) {
        request("GET", "/session").use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = parseArray(resp.body?.string()) ?: return@withContext emptyList()
            Json.sessions(arr)
        }
    }

    suspend fun currentSession(): SessionInfo? =
        jsonObject("GET", "/session/current")?.let { Json.sessionFrom(it) }

    suspend fun createSession(title: String? = null): SessionInfo? = withContext(Dispatchers.IO) {
        val body = if (title.isNullOrBlank()) "{}" else "{\"title\":\"${escapeJson(title)}\"}"
        jsonObject("POST", "/session", body)?.let { Json.sessionFrom(it) }
    }

    suspend fun deleteSession(id: String): Boolean = withContext(Dispatchers.IO) {
        request("DELETE", "/session/$id").use { it.isSuccessful }
    }

    suspend fun renameSession(id: String, title: String): Boolean = withContext(Dispatchers.IO) {
        request("PATCH", "/session/$id", "{\"title\":\"${escapeJson(title)}\"}").use { it.isSuccessful }
    }

    /** Mensajes históricos de una sesión. /messages no existe en 1.18.x (devuelve HTML); usa /message. */
    suspend fun listMessages(sessionId: String, limit: Int = 80): List<ChatUiMessage> =
        withContext(Dispatchers.IO) {
            var bodyStr = request("GET", "/session/$sessionId/messages?limit=$limit").use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            var arr = parseArray(bodyStr)
            if (arr == null) {
                bodyStr = request("GET", "/session/$sessionId/message?limit=$limit").use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
                arr = parseArray(bodyStr)
            }
            if (arr == null) return@withContext emptyList()
            val out = mutableListOf<ChatUiMessage>()
            for (e in arr) {
                val obj = e.asJsonObject
                val info = obj.get("info")?.asJsonObject ?: continue
                val role = info.get("role")?.asString ?: continue
                val parts = obj.get("parts")?.asJsonArray ?: JsonArray()
                val text = StringBuilder()
                for (p in parts) {
                    val po = p.asJsonObject
                    val type = po.get("type")?.asString
                    if (type == "text") text.append(po.get("text")?.asString.orEmpty())
                }
                val id = info.get("id")?.asString ?: ""
                when (role) {
                    "user" -> out += ChatUiMessage(id, ChatRole.USER, text.toString().trim())
                    "assistant" -> out += ChatUiMessage(id, ChatRole.ASSISTANT, text.toString().trim())
                }
            }
            out
        }

    /** Envía un mensaje y espera la respuesta. Envía la parte "prompt/file" típica de la TUI. */
    suspend fun sendMessage(sessionId: String, text: String): AssistantResult? =
        withContext(Dispatchers.IO) {
            val body = "{\"parts\":[{\"type\":\"text\",\"text\":${gsonQuote(text)}}]}"
            val obj = sendJson(body, "/session/$sessionId/prompt", "/session/$sessionId/message") ?: return@withContext null
            Json.assistantFrom(obj)
        }

    suspend fun abort(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        request("POST", "/session/$sessionId/abort").use { it.isSuccessful }
    }

    suspend fun respondPermission(sessionId: String, permissionId: String, accept: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val body = if (accept) {
                "{\"state\":\"allow\",\"response\":\"allow\",\"remember\":true}"
            } else {
                "{\"state\":\"deny\",\"response\":\"deny\",\"remember\":true}"
            }
            request("POST", "/session/$sessionId/permissions/$permissionId", body).use { it.isSuccessful }
        }

    suspend fun listProjects(): List<ProjectInfo> = withContext(Dispatchers.IO) {
        request("GET", "/project").use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = parseArray(resp.body?.string()) ?: return@withContext emptyList()
            arr.mapNotNull { e ->
                try {
                    if (e.isJsonObject) Json.projectFrom(e.asJsonObject) else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun currentProject(): ProjectInfo? =
        jsonObject("GET", "/project/current")?.let { Json.projectFrom(it) }

    suspend fun listDir(path: String? = null): List<FileEntry> = withContext(Dispatchers.IO) {
        val q = path?.let { "?path=${encode(it)}" } ?: ""
        request("GET", "/file$q").use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = parseArray(resp.body?.string()) ?: return@withContext emptyList()
            if (arr.isEmpty()) return@withContext emptyList()
            Json.fileEntries(arr)
        }
    }

    suspend fun readContent(path: String): String? = withContext(Dispatchers.IO) {
        request("GET", "/file/content?path=${encode(path)}").use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val text = resp.body?.string() ?: return@withContext null
            val json = try {
                JsonParser.parseString(text).asJsonObject
            } catch (_: Exception) {
                return@withContext text
            }
            json.get("content")?.asString ?: json.get("text")?.asString ?: text
        }
    }

    suspend fun findFiles(query: String, type: String? = null): List<String> = withContext(Dispatchers.IO) {
        val q = "query=${encode(query)}" + (type?.let { "&type=$it" } ?: "")
        request("GET", "/find/file?$q").use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = parseArray(resp.body?.string()) ?: return@withContext emptyList()
            arr.map { it.asString }
        }
    }

    suspend fun agents(): List<AgentInfo> = withContext(Dispatchers.IO) {
        request("GET", "/agent").use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = parseArray(resp.body?.string()) ?: return@withContext emptyList()
            arr.mapNotNull { e ->
                try {
                    val o = e.asJsonObject
                    AgentInfo(
                        id = o.get("id")?.asString ?: "",
                        name = o.get("name")?.asString ?: o.get("id")?.asString ?: "",
                        description = o.get("description")?.asString ?: "",
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun defaultModel(): String? = withContext(Dispatchers.IO) {
        request("GET", "/config/providers").use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val obj = parseObject(resp.body?.string()) ?: return@withContext null
            val def = obj.get("default")?.asJsonObject
            val p = def?.keySet()?.firstOrNull() ?: return@withContext null
            val m = def.get(p)?.asString ?: return@withContext null
            "$p/$m"
        }
    }

    private fun gsonQuote(s: String): String = escapeJson(s)

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}