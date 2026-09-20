package com.opencode.android.agent

import android.content.Context
import com.opencode.android.data.AppDatabase
import com.opencode.android.data.MessageEntity
import com.opencode.android.llm.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Gestión de la conversación.
 *
 * El historial LLM correcto se mantiene EN MEMORIA ([history]) porque persistir
 * tool_calls + tool_results de forma fiel en Room exigiría reconstruir payloads
 * completos (name + arguments) para que el backend no rechace la petición.
 *
 * La base de datos persiste SOLO el transcript visible (user / assistant final)
 * para reconstruir contexto tras reinicio del proceso y para mostrar el chat.
 */
class ConversationManager(private val context: Context) {

    private val dao = AppDatabase.get(context).messageDao()
    private val memory = ArrayList<ChatMessage>()
    private var seeded = false

    /** Devuelve el historial actual (memoria) y lo siembra desde DB la primera vez. */
    suspend fun history(): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (!seeded) {
            val rows = dao.observeAll().first()
            for (row in rows) {
                when (row.role) {
                    "user" -> memory.add(ChatMessage("user", row.content))
                    "assistant" -> memory.add(ChatMessage("assistant", row.content))
                    else -> Unit // filas "tool" huérfanas (de versiones previas) se omiten
                }
            }
            seeded = true
        }
        memory.toList()
    }

    fun remember(msg: ChatMessage) {
        memory.add(msg)
    }

    suspend fun insert(role: String, content: String): Long = withContext(Dispatchers.IO) {
        dao.insert(MessageEntity(role = role, content = content))
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        memory.clear()
        seeded = false
        dao.clear()
    }
}