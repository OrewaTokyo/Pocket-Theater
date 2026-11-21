package com.pockettheater.core.security

import android.content.Context
import android.util.Base64

/**
 * The PIN that keeps the phone app closed. Only a salted, scrambled form of the PIN is stored.
 * After 5 wrong tries in a row the app makes you wait, and the wait grows with every further 5.
 */
class PinLock(context: Context) {
    enum class Result { OK, WRONG, WAIT }

    private val prefs = context.applicationContext.getSharedPreferences("pocket_theater_pin", Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.contains(KEY_HASH)

    fun set(pin: String) {
        val salt = Auth.newSalt()
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(Auth.hash(pin, salt), Base64.NO_WRAP))
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

    /** How many wrong tries are left before the next wait starts. */
    fun triesLeft(): Int = 5 - prefs.getInt(KEY_FAILS, 0) % 5

    fun verify(pin: String): Result {
        if (secondsToWait() > 0) return Result.WAIT
        val salt = Base64.decode(prefs.getString(KEY_SALT, "").orEmpty(), Base64.NO_WRAP)
        val hash = Base64.decode(prefs.getString(KEY_HASH, "").orEmpty(), Base64.NO_WRAP)
        if (hash.isNotEmpty() && Auth.matches(pin, salt, hash)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_WAIT_UNTIL, 0L).apply()
            return Result.OK
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails % 5 == 0) {
            val seconds = minOf(30L shl minOf(fails / 5 - 1, 4), 300L) // 30s, 60s, 120s, 240s, then 5 minutes
            edit.putLong(KEY_WAIT_UNTIL, System.currentTimeMillis() + seconds * 1000)
        }
        edit.apply()
        return Result.WRONG
    }

    companion object {
        const val MIN_DIGITS = 4
        const val MAX_DIGITS = 6
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_FAILS = "fails"
        private const val KEY_WAIT_UNTIL = "wait_until"

        fun validPin(pin: String): Boolean = pin.length in MIN_DIGITS..MAX_DIGITS && pin.all { it in '0'..'9' }
    }
}
