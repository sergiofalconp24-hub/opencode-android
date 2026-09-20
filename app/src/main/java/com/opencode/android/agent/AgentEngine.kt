package com.opencode.android.agent

import android.content.Context
import com.opencode.android.data.SettingsStore
import com.opencode.android.llm.ChatMessage
import com.opencode.android.llm.createLlmClient
import com.opencode.android.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motor del agente: bucle razonamiento-tools.
 * 1) llama al LLM con historial; 2) si hay tool_calls las ejecuta; 3) repite
 * hasta que el modelo responde texto final (o se alcanza MAX_STEPS).
 */
class AgentEngine(private val context: Context) {

    private val settingsStore = SettingsStore(context)
    private val conversation = ConversationManager(context)
    private val toolsExecutor = ToolExecutor(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null

    private val maxSteps = 24

    fun isRunning(): Boolean = AgentBus.running.value

    fun start(userInput: String) {
        if (AgentBus.running.value) return
        val text = userInput.trim()
        if (text.isEmpty()) return
        AgentBus.setRunning(true)
        RunBus.reset()
        runJob = scope.launch {
            try {
                runLoop(text)
            } finally {
                AgentBus.setRunning(false)
                RunBus.reset()
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        AgentBus.emitSync(AgentEvent.Status("detenido por el usuario"))
        AgentBus.setRunning(false)
        RunBus.reset()
        scope.launch {
            conversation.remember(ChatMessage("assistant", "⏹ Detenido por el usuario."))
            conversation.insert("assistant", "⏹ Detenido por el usuario.")
        }
    }

    private suspend fun runLoop(userInput: String) {
        val settings = settingsStore.settings.value
        val client = createLlmClient(settings)

        // 1. Historial (siembra desde DB la primera vez) + mensaje del usuario
        var history = conversation.history()
        conversation.remember(ChatMessage(role = "user", content = userInput))
        conversation.insert("user", userInput)
        history = history + ChatMessage(role = "user", content = userInput)

        var steps = 0
        while (steps < maxSteps) {
            steps++
            RunBus.update { it.copy(running = true, step = "thinking", stepsTaken = steps) }
            AgentBus.emitSync(AgentEvent.Status("paso $steps: razonando…"))

            val result = client.complete(
                messages = history,
                tools = toolsExecutor.specs(),
                systemPrompt = settings.systemPrompt
            )

            if (result.error != null) {
                val msg = "⚠️ ${result.error}"
                AgentBus.emitSync(AgentEvent.Error(result.error))
                conversation.remember(ChatMessage(role = "assistant", content = msg))
                conversation.insert("assistant", msg)
                AgentBus.emitSync(AgentEvent.Done)
                return
            }

            // 2. Respuesta del asistente (texto y/o tool_calls)
            val assistantMsg = ChatMessage(
                role = "assistant",
                content = result.text,
                toolCalls = result.toolCalls
            )
            history = history + assistantMsg
            conversation.remember(assistantMsg)

            if (!result.text.isNullOrBlank()) {
                AgentBus.emitSync(AgentEvent.Token(result.text))
            }

            // 3. Recortar historial largo antes de enviarlo (control de contexto)
            if (history.size > 60) history = history.takeLast(60)
            // Purgar tool_results huérfanos que el recorte de history dejó al inicio
            // (sin su assistant tool_call previo, el backend rechaza la petición).
            while (history.isNotEmpty() && history.first().role == "tool") {
                history = history.drop(1)
            }

            // 4. ¿Tool calls? → ejecutar cada una y continuar el bucle
            if (result.toolCalls.isNotEmpty()) {
                for (tc in result.toolCalls) {
                    RunBus.update { it.copy(step = "tool", currentTool = tc.name) }
                    AgentBus.emitSync(AgentEvent.ToolUse(tc.name, tc.arguments))
                    val toolResult = withContext(Dispatchers.IO) {
                        toolsExecutor.execute(tc.name, tc.arguments)
                    }
                    val rendered = if (toolResult.ok) toolResult.output else "ERROR: ${toolResult.output}"
                    AgentBus.emitSync(AgentEvent.ToolResult(tc.name, rendered.take(4000)))

                    val toolMsg = ChatMessage(
                        role = "tool",
                        content = rendered,
                        toolCallId = tc.id,
                        name = tc.name
                    )
                    history = history + toolMsg
                    conversation.remember(toolMsg)
                }
                continue
            }

            // 5. Sin tool calls → respuesta final persistida
            if (!result.text.isNullOrBlank()) {
                conversation.insert("assistant", result.text)
                AgentBus.emitSync(AgentEvent.Done)
                AgentBus.emitSync(AgentEvent.Status("completado en $steps paso(s)"))
                return
            }

            // Sin texto ni tools → finalizar limpio
            conversation.insert("assistant", "(el modelo no respondió)")
            AgentBus.emitSync(AgentEvent.Done)
            return
        }

        val msg = "⛔ Límite de pasos ($maxSteps) alcanzado. Simplifica la tarea o reinicia la conversación."
        conversation.remember(ChatMessage(role = "assistant", content = msg))
        conversation.insert("assistant", msg)
        AgentBus.emitSync(AgentEvent.Error("Límite de pasos alcanzado"))
    }
}