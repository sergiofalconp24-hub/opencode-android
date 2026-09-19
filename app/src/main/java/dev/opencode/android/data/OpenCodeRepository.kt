package dev.opencode.android.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Evento SSE normalizado. */
data class SseEvent(val type: String, val data: String)

/**
 * Repositorio único de la app. Mantiene el cliente HTTP + la conexión SSE viva
 * apuntando al servidor opencode configurado en Ajustes.
 */
class OpenCodeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opencode_settings", Context.MODE_PRIVATE)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val okHttp = OkHttpClient()
    private var client: OpenCodeClient? = null
    private var sse: SseClient? = null
    private var lastUrl: String? = null

    private val _events = MutableSharedFlow<SseEvent>(extraBufferCapacity = 200)
    val events = _events.asSharedFlow()

    var connectedUrl: String = ""
        private set

    fun baseUrl(): String = prefs.getString("base_url", "http://127.0.0.1:4096") ?: "http://127.0.0.1:4096"

    fun password(): String = prefs.getString("server_password", "") ?: ""

    fun autoAcceptTools(): Boolean = prefs.getBoolean("auto_accept", true)

    fun themeDark(): Boolean = prefs.getBoolean("dark_theme", true)

    fun setThemeDark(v: Boolean) {
        prefs.edit().putBoolean("dark_theme", v).apply()
    }

    fun setConnection(baseUrl: String, password: String) {
        prefs.edit().putString("base_url", baseUrl).putString("server_password", password).apply()
        reconnect()
    }

    fun setAutoAccept(v: Boolean) {
        prefs.edit().putBoolean("auto_accept", v).apply()
    }

    fun reconnect() {
        val base = baseUrl()
        val pass = password()
        val normalized = base.trim().trimEnd('/').ifBlank { "http://127.0.0.1:4096" }
        client = OpenCodeClient(normalized, pass.ifBlank { null }, okHttp)

        sse?.close()
        sse = null

        val headers = mutableMapOf<String, String>()
        if (pass.isNotBlank()) {
            headers["Authorization"] = "Basic " + android.util.Base64.encodeToString(
                "opencode:$pass".toByteArray(), android.util.Base64.NO_WRAP
            )
        }

        val c = client!!
        val newSse = SseClient(normalized, headers, okHttp, scope).apply {
            listener = object : SseClient.Listener {
                override fun onServerEvent(type: String, data: String) {
                    scope.launch {
                        _events.emit(SseEvent(type, data))
                        if (type == "permission.requested") {
                            val sessionId = try {
                                JsonParser.parseString(data).asJsonObject.get("sessionID")?.asString
                            } catch (_: Exception) {
                                null
                            }
                            val permissionId = try {
                                JsonParser.parseString(data).asJsonObject.get("permissionID")?.asString
                            } catch (_: Exception) {
                                null
                            }
                            if (sessionId != null && permissionId != null && autoAcceptTools()) {
                                c.respondPermission(sessionId, permissionId, true)
                            }
                        }
                    }
                }
            }
        }
        newSse.start()
        sse = newSse
        connectedUrl = normalized
        lastUrl = normalized
    }

    fun client(): OpenCodeClient? = client?.takeIf { connectedUrl == lastUrl }

    fun close() {
        sse?.close()
        sse = null
    }

    companion object {
        @Volatile
        private var instance: OpenCodeRepository? = null

        fun init(context: Context): OpenCodeRepository {
            return instance ?: synchronized(this) {
                instance ?: OpenCodeRepository(context.applicationContext).also { instance = it }
            }
        }

        fun get(): OpenCodeRepository = instance!!
    }
}