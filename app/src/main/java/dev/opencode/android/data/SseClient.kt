package dev.opencode.android.data

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Cliente de Server-Sent Events del servidor opencode.
 * Evento inicial: server.connected. Luego message.part.updated, message.updated,
 * permission.requested, session.*, etc. Reconecta automáticamente con backoff.
 */
class SseClient(
    private val baseUrl: String,
    private val headers: Map<String, String>,
    private val okHttp: OkHttpClient,
    private val scope: CoroutineScope,
) {

    interface Listener {
        fun onServerEvent(type: String, data: String)
    }

    var listener: Listener? = null

    private val eventSourceListener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            retries = 0
            Log.d(TAG, "SSE abierto")
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            // El servidor no envía «event:»; el tipo va dentro del JSON de data.
            val jsonType = try {
                JsonParser.parseString(data).asJsonObject.get("type")?.asString
            } catch (_: Exception) {
                null
            }
            val t = jsonType ?: type ?: "message"
            try {
                listener?.onServerEvent(t, data)
            } catch (e: Exception) {
                Log.w(TAG, "Error procesando evento $t", e)
            }
        }

        override fun onClosed(eventSource: EventSource) {
            Log.d(TAG, "SSE cerrado")
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            Log.w(TAG, "SSE falló: ${t?.message} (${response?.code})")
            retry()
        }
    }

    private var current: EventSource? = null
    private var closed = false
    private var retries = 0

    fun start() {
        current = EventSources.createFactory(okHttp).newEventSource(
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/event")
                .apply {
                    headers.forEach { (k, v) -> header(k, v) }
                }
                .build(),
            eventSourceListener,
        )
    }

    private fun retry() {
        if (closed) return
        scope.launch(Dispatchers.IO) {
            retries++
            val backoff = minOf(15000L, 1000L * retries)
            delay(backoff)
            if (scope.isActive && !closed) start()
        }
    }

    fun close() {
        closed = true
        current?.cancel()
        current = null
    }

    companion object {
        private const val TAG = "SseClient"
    }
}