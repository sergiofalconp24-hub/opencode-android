package com.opencode.android.server

import android.content.Context
import com.opencode.android.agent.AgentEngine
import com.opencode.android.data.SettingsStore

object ServerHolder {
    @Volatile var server: LocalServer? = null
    @Volatile var agent: AgentEngine? = null

    fun settings(context: Context) = SettingsStore(context)

    fun start(context: Context) {
        if (agent == null) agent = AgentEngine(context.applicationContext)
    }

    fun serverUrl(port: Int): String = "http://127.0.0.1:$port"
}