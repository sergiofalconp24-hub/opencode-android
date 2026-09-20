package com.opencode.android.tools

import org.json.JSONObject
import java.io.File

/** Replica el algoritmo de edición por bloques @@ de los diffs (como apply_patch/change). */
class PatchTool : Tool {
    override val name = "patch"
    override val description = "Aplica un diff unificado con bloques @@@ para modificar un archivo (inteligente, tolerante a contexto cambiado)."
    override val parameters: JSONObject = basicParams("path", "patch").apply {
        addStringParam(this, "path", "Ruta relativa del archivo")
        addStringParam(this, "patch", "Diff con bloques JSON, formato: [{\"oldString\": \"...\", \"newString\": \"...\"}]")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val path = Sandbox.sanitizePath(args.optString("path"))
        val file = Sandbox.resolve(sandbox, path)
        if (!file.exists()) return ToolResult(false, "No existe: $path")
        try {
            val patch = org.json.JSONArray(args.optString("patch"))
            val original = file.readText()
            var current = original
            for (i in 0 until patch.length()) {
                val block = patch.getJSONObject(i)
                val oldStr = block.getString("oldString")
                val newStr = block.optString("newString")
                if (oldStr.isEmpty()) {
                    // inserción: append (al final) o prepend (al inicio) del archivo
                    when {
                        block.optBoolean("prepend", false) -> current = newStr + "\n" + current
                        block.optBoolean("append", false) -> current = current + "\n" + newStr
                        else -> Unit // bloque vacío sin flags → se ignora
                    }
                } else if (current.contains(oldStr)) {
                    current = current.replace(oldStr, newStr)
                } else {
                    return ToolResult(false, "Bloque $i no encontrado en $path. El contexto cambió; relee el archivo.")
                }
            }
            file.writeText(current)
            return ToolResult(true, "Patch aplicado a $path")
        } catch (e: Exception) {
            return ToolResult(false, "Error aplicando patch a $path: ${e.message}")
        }
    }
}

class GlobTool : Tool {
    override val name = "glob"
    override val description = "Busca archivos por patrón glob dentro del proyecto."
    override val parameters: JSONObject = basicParams("pattern").apply {
        addStringParam(this, "pattern", "Patrón glob, e.g. \"**/*.kt\" o \"src/**/build.gradle.kts\"")
        addOptionalStringParam(this, "path", "Directorio base (default: raíz)")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val pattern = args.optString("pattern").ifBlank { "**/*" }
        val rel = Sandbox.sanitizePath(args.optString("path", "."))
        val base = Sandbox.resolve(sandbox, rel.ifEmpty { "." })
        if (!base.exists()) return ToolResult(false, "No existe: $rel")
        val regex = globToRegex(pattern)
        val matches = mutableListOf<File>()
        base.walkTopDown().forEach { f ->
            if (f.isFile) {
                val relPath = if (f.absolutePath.startsWith(sandbox.absolutePath))
                    sandbox.toURI().relativize(f.toURI()).path else f.absolutePath
                if (regex.matches(relPath)) matches.add(f)
            }
        }
        if (matches.isEmpty()) return ToolResult(true, "(sin coincidencias)")
        return ToolResult(true, matches.sortedBy { it.absolutePath }.joinToString("\n") {
            if (it.absolutePath.startsWith(sandbox.absolutePath))
                sandbox.toURI().relativize(it.toURI()).path else it.absolutePath
        })
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*")
                        i++
                    } else sb.append("[^/]*")
                }
                '?' -> sb.append("[^/]")
                '{' -> {
                    val end = glob.indexOf('}', i)
                    if (end > -1) {
                        sb.append("(?:").append(glob.substring(i + 1, end).split(",").joinToString("|")).append(")")
                        i = end
                    } else sb.append("\\{")
                }
                '[', ']', '(', ')', '+', '.', '\\', '^', '$', '|' -> sb.append("\\").append(c)
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString())
    }
}