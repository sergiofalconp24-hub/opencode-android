package com.opencode.android.server

import android.content.Context
import com.opencode.android.agent.AgentBus
import com.opencode.android.agent.AgentEngine
import com.opencode.android.agent.AgentEvent
import com.opencode.android.data.AppDatabase
import com.opencode.android.data.SettingsStore
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Servidor HTTP local (NanoHTTPD) expuesto en 127.0.0.1.
 * Endpoints:
 *   GET  /api/health    → {ok:true}
 *   GET  /api/status    → estado del servidor + running
 *   POST /api/chat      → {message: "..."} — lanza el agente
 *   POST /api/stop      → detiene el agente
 *   GET  /api/messages  → historial persistido
 *   GET  /api/stream    → SSE: eventos del agente en tiempo real
 */
class LocalServer(
    private val context: Context,
    port: Int,
    private val agent: AgentEngine
) : NanoHTTPD("127.0.0.1", port) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsStore = SettingsStore(context)

    companion object {
        private val STATUS = mapOf(
            200 to Response.Status.OK,
            400 to Response.Status.BAD_REQUEST,
            404 to Response.Status.NOT_FOUND,
            409 to Response.Status.CONFLICT,
            500 to Response.Status.INTERNAL_ERROR
        )
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return try {
            when {
                uri == "/api/health" -> json(JSONObject().put("ok", true))
                uri == "/api/status" -> status()
                uri == "/api/chat" && session.method == Method.POST -> handleChat(session)
                uri == "/api/stop" && session.method == Method.POST -> handleStop()
                uri == "/api/stream" -> handleStream()
                uri == "/api/messages" -> messages()
                else -> json(JSONObject().put("error", "not found"), 404)
            }
        } catch (e: Exception) {
            json(JSONObject().put("error", e.message ?: "server error"), 500)
        }
    }

    private fun status(): Response {
        val s = settingsStore.settings.value
        val obj = JSONObject()
            .put("ok", true)
            .put("running", agent.isRunning())
            .put("provider", s.provider)
            .put("model", s.model)
            .put("versions", JSONObject().put("opencode-android", "1.0.0"))
        return json(obj)
    }

    private fun handleChat(session: IHTTPSession): Response {
        val body = readBody(session)
        val message = JSONObject(body).optString("message", "")
        if (message.isBlank()) return json(JSONObject().put("error", "message vacío"), 400)
        if (agent.isRunning()) return json(JSONObject().put("error", "agente ocupado"), 409)

        scope.launch { agent.start(message) }
        return json(JSONObject().put("ok", true).put("message", "agente iniciado"))
    }

    private fun handleStop(): Response {
        agent.stop()
        return json(JSONObject().put("ok", true))
    }

    private fun messages(): Response {
        val rows = runBlocking {
            AppDatabase.get(context).messageDao().observeAll().first()
        }
        val arr = JSONArray()
        for (r in rows) {
            arr.put(JSONObject()
                .put("id", r.id)
                .put("role", r.role)
                .put("content", r.content)
                .put("ts", r.ts))
        }
        return json(JSONObject().put("messages", arr))
    }

    /**
     * SSE: cola acotada alimentada por un collector del AgentBus y consumida por
     * un InputStream bloqueante. Si la cola se llena se descartan los eventos más
     * viejos (el cliente local es rápido).
     */
    private fun handleStream(): Response {
        val queue = ArrayBlockingQueue<ByteArray>(128)
        val pump = scope.launch {
            AgentBus.events.collect { event ->
                val bytes = eventToSse(event)
                if (!queue.offer(bytes)) {
                    queue.poll()
                    queue.offer(bytes)
                }
            }
        }
        val input = object : InputStream() {
            private var pending: ByteArray? = null
            private var pos = 0

            override fun read(): Int {
                val one = ByteArray(1)
                val n = read(one, 0, 1)
                return if (n == -1) -1 else one[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pending == null) {
                    val chunk = queue.poll(90, TimeUnit.SECONDS)
                        ?: run { pump.cancel(); return -1 }
                    pending = chunk
                    pos = 0
                }
                val n = minOf(len, pending!!.size - pos)
                System.arraycopy(pending!!, pos, b, off, n)
                pos += n
                if (pos >= pending!!.size) pending = null
                return n
            }
        }
        return NanoHTTPD.newChunkedResponse(
            Response.Status.OK,
            "text/event-stream",
            input
        ).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("Connection", "keep-alive")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    private fun eventToSse(event: AgentEvent): ByteArray {
        val json = when (event) {
            is AgentEvent.Token -> JSONObject().put("type", "token").put("text", event.text)
            is AgentEvent.ToolUse -> JSONObject().put("type", "tool_use").put("name", event.name).put("arguments", event.args)
            is AgentEvent.ToolResult -> JSONObject().put("type", "tool_result").put("name", event.name).put("output", event.output)
            is AgentEvent.Status -> JSONObject().put("type", "status").put("message", event.message)
            AgentEvent.Done -> JSONObject().put("type", "done")
            is AgentEvent.Error -> JSONObject().put("type", "error").put("message", event.message)
        }
        return "data: $json\n\n".toByteArray(Charsets.UTF_8)
    }

    private fun readBody(session: IHTTPSession): String {
        return try {
            session.parseBody(HashMap<String, String>())
            val postData = session.parms["postData"] ?: return "{}"
            val tmp = File(postData)
            if (tmp.exists()) tmp.readText(Charsets.UTF_8) else postData
        } catch (e: Exception) {
            "{}"
        }
    }

    private fun json(obj: JSONObject, status: Int = 200): Response =
        NanoHTTPD.newFixedLengthResponse(
            STATUS[status] ?: Response.Status.OK,
            "application/json",
            obj.toString()
        )
}