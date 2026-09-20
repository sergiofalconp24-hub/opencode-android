package com.opencode.android.tools

import org.json.JSONArray
import org.json.JSONObject

/** Resultado de ejecutar una herramienta. */
data class ToolResult(
    val ok: Boolean,
    val output: String
)

interface Tool {
    val name: String
    val description: String
    val parameters: JSONObject

    /** Ejecuta la herramienta sobre un path ya resuelto al sandbox. */
    fun run(args: JSONObject, sandbox: java.io.File): ToolResult
}

fun basicParams(vararg required: String): JSONObject = JSONObject().apply {
    put("type", "object")
    put("properties", JSONObject())
    put("required", JSONArray().apply { required.forEach { put(it) } })
}

fun addStringParam(params: JSONObject, name: String, desc: String, enumValues: Array<String>? = null) {
    val prop = JSONObject().put("type", "string").put("description", desc)
    if (enumValues != null) prop.put("enum", JSONArray().apply { enumValues.forEach { put(it) } })
    params.getJSONObject("properties").put(name, prop)
    if (!params.has("required")) params.put("required", JSONArray())
    val required = params.getJSONArray("required")
    if (!contains(required, name)) required.put(name)
}

fun addOptionalStringParam(params: JSONObject, name: String, desc: String) {
    params.getJSONObject("properties").put(name, JSONObject().put("type", "string").put("description", desc))
}

fun addBoolParam(params: JSONObject, name: String, desc: String) {
    params.getJSONObject("properties").put(name, JSONObject().put("type", "boolean").put("description", desc))
}

private fun contains(arr: JSONArray, v: String): Boolean {
    for (i in 0 until arr.length()) if (arr.getString(i) == v) return true
    return false
}