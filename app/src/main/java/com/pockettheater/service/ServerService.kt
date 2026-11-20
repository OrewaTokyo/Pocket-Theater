package com.pockettheater.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.pockettheater.core.server.PocketServer
import com.pockettheater.core.server.ServerStats
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.core.storage.UserStore
import com.pockettheater.ui.screens.MainActivity
import com.pockettheater.util.NetworkUtil
import fi.iki.elonen.NanoHTTPD

class ServerService : Service() {
    private var server: PocketServer? = null
    private lateinit var locks: WifiLockManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locks = WifiLockManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        showNotification()
        startServer()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer() {
        if (server != null) return
        try {
            val s = PocketServer(applicationContext, PORT) { name -> announceRequest(name) }
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            locks.acquire()
            lastError = null
            isRunning = true
            ServerStats.begin(applicationContext)
        } catch (e: Exception) {
            lastError = e.message ?: "Could not start the server"
            isRunning = false
            stopSelf()
        }
    }

    private fun stopServer() {
        if (server != null) ServerStats.end()
        server?.stop()
        server = null
        locks.release()
        isRunning = false
    }

    /** Tells the admin, even with the screen off, that someone is waiting to be approved. */
    private fun announceRequest(name: String) {
        if (!AppSettings(this).notifyRequests) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(REQUEST_CHANNEL_ID, "Sign-up requests", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val waiting = UserStore(this).pending().size
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (waiting > 1) "$waiting people are waiting for approval" else "$name wants to join. Open the app to approve."
        val notification = Notification.Builder(this, REQUEST_CHANNEL_ID)
            .setContentTitle("New sign-up request")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_REQUEST, notification)
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Server status", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val ip = NetworkUtil.localIpv4()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Theater is open")
            .setContentText(if (ip != null) "http://$ip:$PORT" else "Waiting for Wi-Fi")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Close theater", stop
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        const val PORT = 8080
        const val ACTION_STOP = "com.pockettheater.STOP"
        private const val CHANNEL_ID = "server_status"
        private const val REQUEST_CHANNEL_ID = "signup_requests"
        const val NOTIF_REQUEST = 2
        private const val NOTIF_ID = 1

        @Volatile var isRunning = false
        @Volatile var lastError: String? = null
    }
}
