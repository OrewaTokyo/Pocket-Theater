package com.pockettheater.util

import java.util.Locale

/** Turns raw numbers into short text for the screen. */
object Format {
    fun bytes(n: Long): String {
        if (n < 1024) return "$n B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = n.toDouble()
        var i = -1
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[i])
        else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    fun count(n: Long): String = String.format(Locale.US, "%,d", n)
}
