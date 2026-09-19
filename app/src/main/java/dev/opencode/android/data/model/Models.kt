package dev.opencode.android.data.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class SessionInfo(
    val id: String,
    val title: String,
    val time: String,
    val agent: String? = null,
    val model: String? = null,
)

data class ProjectInfo(
    val id: String,
    val path: String,
    val worktree: String? = null,
)

data class AgentInfo(
    val id: String,
    val name: String,
    val description: String = "",
)

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
)

enum class ChatRole { USER, ASSISTANT }

data class ChatUiMessage(
    val id: String,
    val role: ChatRole,
    val text: String = "",
    val reasoning: String = "",
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val toolName: String? = null,
    val toolState: String? = null,
)

/** Resultado de enviar un mensaje: texto del asistente + lista de partes (tool, row, reasoning). */
data class AssistantResult(
    val text: String,
    val toolEvents: List<String>,
)

object Json {

    fun parse(data: String?): JsonElement? {
        if (data.isNullOrBlank()) return null
        return try {
            JsonParser.parseString(data)
        } catch (_: Exception) {
            null
        }
    }

    fun sessionFrom(obj: JsonObject): SessionInfo {
        val id = obj.get("id")?.asString ?: ""
        val title = obj.get("title")?.asString ?: obj.get("summary")?.asString ?: "Nueva conversación"
        val time = formatTime(obj.get("time"))
        val agent = obj.get("agent")?.asString
        val model = modelLabel(obj.get("model"))
        return SessionInfo(id, title, time, agent, model)
    }

    fun formatTime(el: JsonElement?): String {
        return try {
            when {
                el == null || el.isJsonNull -> ""
                el.isJsonObject -> {
                    val ms = el.asJsonObject.get("updated")?.asLong
                        ?: el.asJsonObject.get("created")?.asLong ?: return ""
                    formatMillis(ms)
                }
                else -> el.asString
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatMillis(ms: Long): String {
        return try {
            val sdf = java.text.SimpleDateFormat(
                "dd MMM yyyy HH:mm", java.util.Locale.getDefault()
            ).apply { timeZone = java.util.TimeZone.getDefault() }
            sdf.format(java.util.Date(ms))
        } catch (_: Exception) {
            ""
        }
    }

    /** El campo model puede ser string ("big-pickle") u objeto {providerID, modelID}. */
    fun modelLabel(el: JsonElement?): String? {
        return try {
            when {
                el == null || el.isJsonNull -> null
                el.isJsonObject -> {
                    val o = el.asJsonObject
                    val mid = o.get("modelID")?.asString ?: o.get("id")?.asString ?: return null
                    val p = o.get("providerID")?.asString
                    if (p != null) "$p/$mid" else mid
                }
                else -> el.asString
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sessions(arr: JsonArray): List<SessionInfo> {
        return arr.mapNotNull { e ->
            try {
                if (e.isJsonObject) sessionFrom(e.asJsonObject) else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun projectFrom(obj: JsonObject): ProjectInfo {
        val id = obj.get("id")?.asString ?: ""
        val path = obj.get("path")?.asString ?: obj.get("worktree")?.asString ?: ""
        val worktree = obj.get("worktree")?.asString
        return ProjectInfo(id, path, worktree)
    }

    fun fileEntries(arr: JsonArray): List<FileEntry> {
        return arr.mapNotNull { e ->
            try {
                val o = e.asJsonObject
                val name = o.get("name")?.asString ?: return@mapNotNull null
                val type = o.get("type")?.asString ?: "file"
                val path = o.get("path")?.asString ?: o.get("fullPath")?.asString ?: name
                FileEntry(name, path, type.contains("dir"))
            } catch (_: Exception) {
                null
            }
        }.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** Convierte un "photo"/"prompt" result con {info, parts[]} en AssistantResult. */
    fun assistantFrom(json: JsonElement): AssistantResult {
        val parts = json.asJsonObject.get("parts")?.asJsonArray ?: JsonArray()
        val sb = StringBuilder()
        val tools = mutableListOf<String>()
        for (p in parts) {
            val obj = p.asJsonObject
            val type = obj.get("type")?.asString ?: continue
            when (type) {
                "text" -> sb.append(obj.get("text")?.asString.orEmpty())
                "tool" -> {
                    try {
                        val name = obj.get("tool")?.asJsonObject?.get("name")?.asString ?: "tool"
                        val call = obj.get("tool")?.asJsonObject?.get("call")?.asJsonObject
                        val state = call?.get("state")?.asString ?: "running"
                        val title = obj.get("state")?.asString
                        tools.add(listOfNotNull(name).joinToString(" ") { it } + (title?.let { " · $it" } ?: ""))
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return AssistantResult(sb.toString().trim(), tools)
    }
}