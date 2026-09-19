package dev.opencode.android.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import dev.opencode.android.data.OpenCodeRepository
import dev.opencode.android.data.SseEvent
import dev.opencode.android.data.model.ChatRole
import dev.opencode.android.data.model.ChatUiMessage
import dev.opencode.android.data.model.SessionInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessions: List<SessionInfo> = emptyList(),
    val activeSessionId: String? = null,
    val activeTitle: String = "",
    val model: String? = null,
    val messages: List<ChatUiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val connected: Boolean = true,
    val busyLoading: Boolean = false,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = OpenCodeRepository.get()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var promptJob: Job? = null
    private var activeGenerationMessageId: String? = null

    init {
        observeEvents()
        refresh()
    }

    private fun observeEvents() {
        viewModelScope.launch {
            repo.events.collect { e -> handleEvent(e) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val sessions = runCatching { repo.client()?.listSessions() ?: emptyList() }.getOrDefault(emptyList())
            val health = runCatching { repo.client()?.health() }.getOrNull()
            val model = runCatching { repo.client()?.defaultModel() }.getOrNull()
            _state.update {
                it.copy(
                    sessions = sessions,
                    model = model,
                    connected = health != null || sessions.isNotEmpty(),
                )
            }
            if (_state.value.activeSessionId == null && sessions.isNotEmpty()) {
                selectSession(sessions.first().id)
            }
        }
    }

    fun newSession() {
        viewModelScope.launch {
            val s = runCatching { repo.client()?.createSession() }.getOrNull()
            if (s != null) {
                _state.update {
                    it.copy(
                        activeSessionId = s.id,
                        activeTitle = s.title,
                        messages = emptyList(),
                        isStreaming = false,
                    )
                }
                refresh()
            }
        }
    }

    fun selectSession(id: String) {
        _state.update { it.copy(activeSessionId = id, activeTitle = it.sessions.firstOrNull { s -> s.id == id }?.title ?: "", isStreaming = false) }
        loadMessages(id)
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            runCatching { repo.client()?.deleteSession(id) }
            if (_state.value.activeSessionId == id) {
                _state.update { it.copy(activeSessionId = null, messages = emptyList(), isStreaming = false) }
            }
            refresh()
        }
    }

    fun renameActive(newTitle: String) {
        val id = _state.value.activeSessionId ?: return
        viewModelScope.launch {
            runCatching { repo.client()?.renameSession(id, newTitle) }
            _state.update { it.copy(activeTitle = newTitle) }
            refresh()
        }
    }

    private fun loadMessages(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busyLoading = true) }
            val msgs = runCatching { repo.client()?.listMessages(id) ?: emptyList() }.getOrDefault(emptyList())
            _state.update { it.copy(messages = msgs, busyLoading = false) }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return
        promptJob?.cancel()
        viewModelScope.launch {
            val sessionId = ensureSession()
            if (sessionId == null) {
                _state.update { st ->
                    st.copy(
                        messages = st.messages + ChatUiMessage(
                            id = "err-${System.currentTimeMillis()}",
                            role = ChatRole.ASSISTANT,
                            text = "No se pudo contactar con el servidor de opencode.\nComprueba que «opencode serve» está activo y revisa la URL en Ajustes.",
                            isError = true,
                        ),
                    )
                }
                return@launch
            }
            _state.update { st ->
                st.copy(
                    messages = st.messages + listOf(
                        ChatUiMessage(id = "u-${System.currentTimeMillis()}", role = ChatRole.USER, text = text.trim()),
                        ChatUiMessage(id = "a-${System.currentTimeMillis()}", role = ChatRole.ASSISTANT, text = "", isStreaming = true),
                    ),
                    isStreaming = true,
                    connected = true,
                )
            }
            activeGenerationMessageId = _state.value.messages.last().id

            val result = runCatching { repo.client()?.sendMessage(sessionId, text) }
            val assistant = result.getOrNull()
            assistant?.let { (finalText, tools) ->
                _state.update { st ->
                    val m = st.messages.toMutableList()
                    val idx = m.indexOfLast { it.isStreaming }
                    if (idx >= 0) {
                        m[idx] = m[idx].copy(
                            text = finalText.ifBlank { m[idx].text },
                            isStreaming = false,
                            isError = false,
                            toolName = tools.firstOrNull(),
                        )
                    }
                    st.copy(messages = m)
                }
            }
            finalizeGeneration(assistant != null)
            refresh()
        }
    }

    fun stop() {
        val id = _state.value.activeSessionId ?: return
        viewModelScope.launch {
            runCatching { repo.client()?.abort(id) }
            finalizeGeneration(true)
        }
        promptJob?.cancel()
    }

    private fun finalizeGeneration(good: Boolean) {
        _state.update { st ->
            val m = st.messages.toMutableList()
            val idx = m.indexOfLast { it.isStreaming }
            if (idx >= 0) {
                m[idx] = m[idx].copy(isStreaming = false, isError = !good)
            }
            st.copy(messages = m, isStreaming = false)
        }
        activeGenerationMessageId = null
    }

    private suspend fun ensureSession(): String? {
        val current = _state.value.activeSessionId
        if (current != null) return current
        val existing = _state.value.sessions.firstOrNull()
        if (existing != null) {
            _state.update { it.copy(activeSessionId = existing.id, activeTitle = existing.title) }
            return existing.id
        }
        // Sin sesión: créala para que el primer mensaje siempre se pueda enviar.
        val created = runCatching { repo.client()?.createSession() }.getOrNull()
        if (created != null) {
            _state.update { it.copy(activeSessionId = created.id, activeTitle = created.title, messages = emptyList()) }
            refresh()
            return created.id
        }
        return null
    }

    private fun handleEvent(e: SseEvent) {
        if (e.type != "message.part.updated" && e.type != "message.updated" && e.type != "message.error") return
        if (!_state.value.isStreaming) return
        val sessionId = _state.value.activeSessionId ?: return
        val obj = JsonParser.parseString(e.data).asJsonObject
        val props = obj.get("properties")?.asJsonObject ?: return
        val evSession = props.get("sessionID")?.asString ?: return
        if (evSession != sessionId) return

        when (e.type) {
            "message.part.updated" -> {
                val part = props.get("part")?.asJsonObject ?: return
                if (part.get("type")?.asString != "text") return
                val text = part.get("text")?.asString ?: return
                _state.update { st ->
                    val m = st.messages.toMutableList()
                    val idx = m.indexOfLast { it.isStreaming }
                    if (idx >= 0) m[idx] = m[idx].copy(text = text)
                    st.copy(messages = m)
                }
            }
            "message.error" -> {
                val text = props.get("text")?.asString ?: "El servidor devolvió un error"
                _state.update { st ->
                    val m = st.messages.toMutableList()
                    val idx = m.indexOfLast { it.isStreaming }
                    if (idx >= 0) m[idx] = m[idx].copy(isStreaming = false, isError = true, text = text)
                    st.copy(messages = m, isStreaming = false)
                }
                activeGenerationMessageId = null
            }
        }
    }

    companion object {
        const val TAG = "ChatViewModel"
    }
}