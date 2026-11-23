package com.pockettheater.core.security

import android.content.SharedPreferences

/** Counts wrong tries in a row. After every 5th, the person has to wait: 30 seconds, then 1, 2, 4 and at most 5 minutes. */
class AttemptLimiter(private val prefs: SharedPreferences) {
    fun secondsToWait(): Int {
        val left = prefs.getLong(KEY_WAIT_UNTIL, 0L) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    /** Wrong tries left before the next wait starts. */
    fun triesLeft(): Int = 5 - prefs.getInt(KEY_FAILS, 0) % 5

    fun reset() {
        prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_WAIT_UNTIL, 0L).apply()
    }

    fun fail() {
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails % 5 == 0) {
            val seconds = minOf(30L shl minOf(fails / 5 - 1, 4), 300L)
            edit.putLong(KEY_WAIT_UNTIL, System.currentTimeMillis() + seconds * 1000)
        }
        edit.apply()
    }

    companion object {
        private const val KEY_FAILS = "fails"
        private const val KEY_WAIT_UNTIL = "wait_until"
    }
}
