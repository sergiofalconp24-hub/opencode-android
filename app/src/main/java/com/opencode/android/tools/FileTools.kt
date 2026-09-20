package com.opencode.android.tools

import org.json.JSONObject
import java.io.File

class ListTool : Tool {
    override val name = "list"
    override val description = "Lista archivos y carpetas dentro del proyecto."
    override val parameters: JSONObject = basicParams("path").apply {
        addStringParam(this, "path", "Ruta relativa (e.g. \".\" para la raíz)")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val rel = Sandbox.sanitizePath(args.optString("path", "."))
        val dir = Sandbox.resolve(sandbox, rel.ifEmpty { "." }).let { if (it.isDirectory) it else it.parentFile }
        val base = sandbox
        if (dir == null || !dir.exists()) return ToolResult(false, "No existe: $rel")
        val entries = dir.listFiles().orEmpty().sortedBy { it.name }
        val sb = StringBuilder()
        for (f in entries) {
            val display = if (f.absolutePath.startsWith(base.absolutePath)) {
                base.toURI().relativize(f.toURI()).path
            } else f.absolutePath
            sb.append(if (f.isDirectory) "[dir] " else "[file] ")
            sb.append(display)
            if (f.isFile) sb.append("  (${f.length()} bytes)")
            sb.append("\n")
        }
        if (entries.isEmpty()) sb.append("(vacío)\n")
        return ToolResult(true, sb.toString().trim())
    }
}

class ReadTool : Tool {
    override val name = "read"
    override val description = "Lee el contenido de un archivo del proyecto."
    override val parameters: JSONObject = basicParams("path").apply {
        addStringParam(this, "path", "Ruta relativa del archivo a leer")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val path = Sandbox.sanitizePath(args.optString("path"))
        if (path.isEmpty()) return ToolResult(false, "path requerido")
        val file = Sandbox.resolve(sandbox, path)
        if (!file.exists()) return ToolResult(false, "No existe: $path")
        if (file.isDirectory) return ToolResult(false, "$path es una carpeta")
        if (file.length() > 1_500_000) return ToolResult(false, "Archivo demasiado grande (${file.length()} bytes)")
        return try {
            val content = file.readText()
            ToolResult(true, "--- $path ---\n$content")
        } catch (e: Exception) {
            ToolResult(false, "Error leyendo $path: ${e.message}")
        }
    }
}

class WriteTool : Tool {
    override val name = "write"
    override val description = "Escribe (crea o sobrescribe) un archivo con el contenido dado."
    override val parameters: JSONObject = basicParams("path", "content").apply {
        addStringParam(this, "path", "Ruta relativa del archivo")
        addStringParam(this, "content", "Contenido completo a escribir")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val path = Sandbox.sanitizePath(args.optString("path"))
        val content = args.optString("content")
        if (path.isEmpty()) return ToolResult(false, "path requerido")
        val file = Sandbox.resolve(sandbox, path)
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult(true, "Escrito: $path (${content.length} chars)")
        } catch (e: Exception) {
            ToolResult(false, "Error escribiendo $path: ${e.message}")
        }
    }
}

class EditTool : Tool {
    override val name = "edit"
    override val description = "Reemplaza un bloque exacto de texto por otro en un archivo (edición quirúrgica)."
    override val parameters: JSONObject = basicParams("path", "old_string", "new_string").apply {
        addStringParam(this, "path", "Ruta relativa del archivo")
        addStringParam(this, "old_string", "Fragmento exacto existente a reemplazar")
        addStringParam(this, "new_string", "Texto de reemplazo")
        addOptionalStringParam(this, "occurrence", "\"first\" (default) o \"all\"")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val path = Sandbox.sanitizePath(args.optString("path"))
        val oldS = args.optString("old_string")
        val newS = args.optString("new_string")
        if (oldS.isEmpty()) return ToolResult(false, "old_string requerido")
        val file = Sandbox.resolve(sandbox, path)
        if (!file.exists()) return ToolResult(false, "No existe: $path")
        return try {
            val original = file.readText()
            if (!original.contains(oldS)) return ToolResult(false, "old_string no encontrado. Usa read para ver el contenido actual.")
            val all = args.optString("occurrence", "first") == "all"
            val updated = if (all) {
                original.replace(oldS, newS)
            } else {
                val idx = original.indexOf(oldS)
                if (idx < 0) original
                else original.substring(0, idx) + newS + original.substring(idx + oldS.length)
            }
            file.writeText(updated)
            val occurrences = countOccurrences(original, oldS)
            val changed = if (occurrences > 1 && !all) " (había $occurrences coincidencias; se editó solo la primera)"
            else ""
            ToolResult(true, "Editado $path$changed")
        } catch (e: Exception) {
            ToolResult(false, "Error editando $path: ${e.message}")
        }
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            val f = text.indexOf(needle, idx)
            if (f < 0) break
            count++
            idx = f + needle.length
        }
        return count
    }
}