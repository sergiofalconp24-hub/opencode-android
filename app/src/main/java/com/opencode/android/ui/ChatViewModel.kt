package com.opencode.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.agent.AgentBus
import com.opencode.android.agent.AgentEngine
import com.opencode.android.agent.AgentEvent
import com.opencode.android.agent.ConversationManager
import com.opencode.android.data.AppDatabase
import com.opencode.android.data.AppSettings
import com.opencode.android.data.MessageEntity
import com.opencode.android.data.SettingsStore
import com.opencode.android.server.ServerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityItem(val label: String, val detail: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settingsStore = SettingsStore(app)
    private val conversationManager = ConversationManager(app)

    val messages: StateFlow<List<MessageEntity>> = db.messageDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val running = AgentBus.running

    private val _streaming = MutableStateFlow("")
    val streaming: StateFlow<String> = _streaming.asStateFlow()

    private val _activity = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activity: StateFlow<List<ActivityItem>> = _activity.asStateFlow()

    private val agent: AgentEngine
        get() = ServerHolder.agent ?: AgentEngine(getApplication()).also { ServerHolder.agent = it }

    init {
        ServerHolder.start(app)
        viewModelScope.launch {
            AgentBus.events.collect { event ->
                when (event) {
                    is AgentEvent.Token -> _streaming.update { it + event.text }
                    is AgentEvent.ToolUse -> {
                        if (event.name == "text") return@collect
                        _activity.update { it + ActivityItem("🔧 ${event.name}", event.args.take(160)) }
                    }
                    is AgentEvent.ToolResult ->
                        _activity.update { it + ActivityItem("✅ ${event.name}", event.output.take(220)) }
                    is AgentEvent.Status -> Unit
                    AgentEvent.Done -> _streaming.value = ""
                    is AgentEvent.Error -> _streaming.value = ""
                }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || running.value) return
        _streaming.value = ""
        _activity.value = emptyList()
        agent.start(trimmed)
    }

    fun stop() {
        agent.stop()
    }

    fun clearConversation() {
        viewModelScope.launch {
            conversationManager.clearAll()
            _streaming.value = ""
            _activity.value = emptyList()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }
}