package com.pockettheater.core.security

import android.content.Context
import android.util.Base64

/**
 * The password asked for on the phone before the admin website opens on another device.
 * It is only ever checked here on the phone, never over the network, and only a scrambled form is stored.
 * Like the PIN, it makes you wait after 5 wrong tries in a row.
 */
class AdminPassword(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pocket_theater_admin_password", Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.contains(KEY_HASH)

    fun set(password: String) {
        val salt = Auth.newSalt()
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(Auth.hash(password, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_WAIT_UNTIL, 0L)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun secondsToWait(): Int {
        val left = prefs.getLong(KEY_WAIT_UNTIL, 0L) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun triesLeft(): Int = 5 - prefs.getInt(KEY_FAILS, 0) % 5

    fun verify(password: String): PinLock.Result {
        if (secondsToWait() > 0) return PinLock.Result.WAIT
        val salt = Base64.decode(prefs.getString(KEY_SALT, "").orEmpty(), Base64.NO_WRAP)
        val hash = Base64.decode(prefs.getString(KEY_HASH, "").orEmpty(), Base64.NO_WRAP)
        if (hash.isNotEmpty() && Auth.matches(password, salt, hash)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_WAIT_UNTIL, 0L).apply()
            return PinLock.Result.OK
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails % 5 == 0) {
            val seconds = minOf(30L shl minOf(fails / 5 - 1, 4), 300L)
            edit.putLong(KEY_WAIT_UNTIL, System.currentTimeMillis() + seconds * 1000)
        }
        edit.apply()
        return PinLock.Result.WRONG
    }

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_FAILS = "fails"
        private const val KEY_WAIT_UNTIL = "wait_until"
    }
}
