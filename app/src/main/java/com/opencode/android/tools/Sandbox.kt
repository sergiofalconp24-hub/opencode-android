package com.opencode.android.tools

import java.io.File

object Sandbox {
    fun projectRoot(): File = File("${System.getProperty("user.home") ?: ""}")
    fun baseDir(context: android.content.Context): File =
        File(context.applicationContext.filesDir, "projects").apply { mkdirs() }

    /** Resuelve un path relativo dentro del sandbox; evita escapes fuera del raíz. */
    fun resolve(base: File, input: String): File {
        val cleaned = input.removePrefix("/").replace("..", "").trim()
        return File(base, cleaned).normalize().takeIf { it.absolutePath.startsWith(base.absolutePath) }
            ?: File(base, cleaned)
    }

    fun sanitizePath(input: String): String {
        val cleaned = input.removePrefix("/").replace("..", "").trim()
        if (cleaned.isEmpty()) return ""
        return if (cleaned.endsWith("/")) cleaned.dropLast(1) else cleaned
    }
}