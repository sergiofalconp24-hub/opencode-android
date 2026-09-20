package com.opencode.android.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Evento de streaming para la UI y para SSE (/api/stream). */
sealed class AgentEvent {
    data class Token(val text: String) : AgentEvent()                       // fragmento de texto del modelo
    data class ToolUse(val name: String, val args: String) : AgentEvent()   // la herramienta se invocó
    data class ToolResult(val name: String, val output: String) : AgentEvent()
    data class Status(val message: String) : AgentEvent()                   // "ejecutando", "terminado", etc.
    object Done : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}

object AgentBus {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    val events = _events.asSharedFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Emisión no suspendible (fire-and-forget); descarta si el buffer está lleno. */
    fun emitSync(event: AgentEvent) {
        _events.tryEmit(event)
    }

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}

/** Metadatos para la UI: estado de una conversación en curso. */
data class RunState(
    val running: Boolean = false,
    val step: String = "idle",          // "thinking" | "tool" | "error"
    val currentTool: String? = null,
    val stepsTaken: Int = 0
)

object RunBus {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    fun update(transform: (RunState) -> RunState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = RunState()
    }
}