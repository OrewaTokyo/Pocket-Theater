package com.pockettheater.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.os.SystemClock

/** What the phone is doing right now: how busy the app is, how full the memory is, and the battery. */
class DeviceStats(private val context: Context) {
    class Reading(
        val cpuPercent: Float,
        val ramPercent: Float,
        val appRamMb: Int,
        val batteryPercent: Int,
        val charging: Boolean,
        val tempC: Float,
    )

    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private var lastCpu = Process.getElapsedCpuTime()
    private var lastWall = SystemClock.elapsedRealtime()
    private var cpu = 0f

    fun read(): Reading {
        // Android does not let apps read the whole phone's CPU, so this is the share of the phone this app uses.
        val nowCpu = Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        val wall = nowWall - lastWall
        if (wall >= 900) {
            cpu = ((nowCpu - lastCpu).toFloat() / wall / cores * 100f).coerceIn(0f, 100f)
            lastCpu = nowCpu
            lastWall = nowWall
        }

        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val ram = if (memory.totalMem > 0) (memory.totalMem - memory.availMem).toFloat() / memory.totalMem * 100f else 0f
        val appMb = (Debug.getPss() / 1024).toInt()

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        return Reading(cpu, ram.coerceIn(0f, 100f), appMb, percent, charging, temp)
    }
}
