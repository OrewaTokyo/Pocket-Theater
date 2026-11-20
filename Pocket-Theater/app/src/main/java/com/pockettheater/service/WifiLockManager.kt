package com.pockettheater.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/** Keeps Wi-Fi and the CPU awake while the server is open. */
class WifiLockManager(context: Context) {
    private val app = context.applicationContext
    private var wifiLock: WifiManager.WifiLock? = null
    private var cpuLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    fun acquire() {
        if (wifiLock == null) {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketTheater:wifi")
                .apply { setReferenceCounted(false) }
        }
        if (cpuLock == null) {
            val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            cpuLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketTheater:cpu")
                .apply { setReferenceCounted(false) }
        }
        wifiLock?.acquire()
        cpuLock?.acquire()
    }

    fun release() {
        wifiLock?.takeIf { it.isHeld }?.release()
        cpuLock?.takeIf { it.isHeld }?.release()
    }
}
