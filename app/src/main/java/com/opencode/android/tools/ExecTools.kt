package com.opencode.android.tools

import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GrepTool : Tool {
    override val name = "grep"
    override val description = "Busca un texto o regex dentro de los archivos del proyecto."
    override val parameters: JSONObject = basicParams("pattern").apply {
        addStringParam(this, "pattern", "Regex o texto a buscar")
        addOptionalStringParam(this, "path", "Directorio/archivo base (default: raíz)")
        addOptionalStringParam(this, "include", "Filtro de extensión, e.g. \"*.kt\"")
        addBoolParam(this, "ignoreCase", "Ignorar mayúsculas (default false)")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult {
        val pattern = args.optString("pattern")
        if (pattern.isEmpty()) return ToolResult(false, "pattern requerido")
        val rel = Sandbox.sanitizePath(args.optString("path", "."))
        val base = Sandbox.resolve(sandbox, rel.ifEmpty { "." })
        if (!base.exists()) return ToolResult(false, "No existe: $rel")
        val include = args.optString("include")
        val ignoreCase = args.optBoolean("ignoreCase", false)

        return try {
            val regex = Regex(pattern, if (ignoreCase) RegexOption.IGNORE_CASE else RegexOption.NONE)
            val results = mutableListOf<String>()
            val files = if (base.isFile) listOf(base) else base.walkTopDown().filter { it.isFile }.toList()
            var total = 0
            for (f in files) {
                if (include.isNotEmpty() && !f.name.endsWith(include.removePrefix("*."))) continue
                if (f.length() > 10_000_000) continue
                var lineNum = 0
                f.forEachLine { line ->
                    lineNum++
                    if (regex.containsMatchIn(line)) {
                        val display = if (f.absolutePath.startsWith(sandbox.absolutePath))
                            sandbox.toURI().relativize(f.toURI()).path else f.absolutePath
                        val excerpt = line.trim().take(180)
                        results.add("$display:$lineNum:  $excerpt")
                        total++
                        if (total > 200) return@forEachLine
                    }
                }
                if (total > 200) break
            }
            if (results.isEmpty()) ToolResult(true, "(sin coincidencias)")
            else ToolResult(true, results.take(200).joinToString("\n"))
        } catch (e: Exception) {
            ToolResult(false, "Error en grep: ${e.message}")
        }
    }
}

class BashTool : Tool {
    override val name = "bash"
    override val description = "Ejecuta un comando shell en el sandbox del proyecto (para build, instalación de deps, git, etc.)."
    override val parameters: JSONObject = basicParams("command").apply {
        addStringParam(this, "command", "Comando a ejecutar")
        addOptionalStringParam(this, "cwd", "Directorio de trabajo (default: raíz del proyecto)")
    }

    override fun run(args: JSONObject, sandbox: File): ToolResult = runBlockingBash(args, sandbox)

    private fun runBlockingBash(args: JSONObject, sandbox: File): ToolResult {
        val command = args.optString("command")
        if (command.isBlank()) return ToolResult(false, "command requerido")
        val cwdRel = Sandbox.sanitizePath(args.optString("cwd", "."))
        val workdir = Sandbox.resolve(sandbox, cwdRel.ifEmpty { "." }).takeIf { it.isDirectory } ?: sandbox
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(workdir)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                ToolResult(true, out.takeLast(8000) + "\n[TIMEOUT: 120s — proceso terminado]")
            } else {
                val exit = process.exitValue()
                val snippet = out.takeLast(8000)
                if (exit == 0) ToolResult(true, snippet)
                else ToolResult(true, "[exit $exit]\n$snippet")
            }
        } catch (e: Exception) {
            ToolResult(false, "Error ejecutando bash: ${e.message}")
        }
    }
}