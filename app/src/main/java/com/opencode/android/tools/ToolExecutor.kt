package com.opencode.android.tools

import android.content.Context
import com.opencode.android.llm.ToolSpec
import org.json.JSONObject

class ToolExecutor(private val context: Context) {
    private val tools: List<Tool> = listOf(
        ListTool(), ReadTool(), WriteTool(), EditTool(), PatchTool(),
        GlobTool(), GrepTool(), BashTool()
    )

    fun specs(): List<ToolSpec> = tools.map { ToolSpec(it.name, it.description, it.parameters) }

    /** Ejecuta una herramienta por nombre con el sandbox del proyecto. */
    fun execute(name: String, arguments: String): ToolResult {
        val base = Sandbox.baseDir(context)
        val tool = tools.find { it.name == name }
            ?: return ToolResult(false, "Herramienta desconocida: $name")
        return try {
            val args = if (arguments.isBlank()) JSONObject() else JSONObject(arguments)
            tool.run(args, base)
        } catch (e: Exception) {
            ToolResult(false, "Error en $name: ${e.message}")
        }
    }
}