package com.opencode.android.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.opencode.android.R
import com.opencode.android.agent.AgentEngine
import com.opencode.android.data.SettingsStore
import fi.iki.elonen.NanoHTTPD

/**
 * Servicio en primer plano que mantiene vivo el servidor local del agente.
 * Sin el servicio, Android podría matar el proceso al quedar la app en segundo plano.
 */
class ServerService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        ensureServerRunning()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ServerHolder.server?.stop()
        ServerHolder.server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureServerRunning() {
        ServerHolder.start(this)
        if (ServerHolder.server == null) {
            val agent: AgentEngine = ServerHolder.agent!!
            val port = SettingsStore(this).settings.value.port
            val server = LocalServer(this, port, agent)
            try {
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                ServerHolder.server = server
            } catch (e: Exception) {
                server.stop()
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.notif_channel_desc)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Servidor local activo en puerto ${SettingsStore(this).settings.value.port}")
            .setSmallIcon(R.drawable.ic_stat_opencode)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "opencode_server"
        private const val NOTIF_ID = 42
    }
}