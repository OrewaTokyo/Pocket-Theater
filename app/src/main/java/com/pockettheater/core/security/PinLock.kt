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
    private val limiter = AttemptLimiter(prefs)

    fun isSet(): Boolean = prefs.contains(KEY_HASH)

    fun set(pin: String) {
        val salt = Auth.newSalt()
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(Auth.hash(pin, salt), Base64.NO_WRAP))
            .apply()
        limiter.reset()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun secondsToWait(): Int = limiter.secondsToWait()

    /** How many wrong tries are left before the next wait starts. */
    fun triesLeft(): Int = limiter.triesLeft()

    fun verify(pin: String): Result {
        if (limiter.secondsToWait() > 0) return Result.WAIT
        val salt = Base64.decode(prefs.getString(KEY_SALT, "").orEmpty(), Base64.NO_WRAP)
        val hash = Base64.decode(prefs.getString(KEY_HASH, "").orEmpty(), Base64.NO_WRAP)
        if (hash.isNotEmpty() && Auth.matches(pin, salt, hash)) {
            limiter.reset()
            return Result.OK
        }
        limiter.fail()
        return Result.WRONG
    }

    companion object {
        const val MIN_DIGITS = 4
        const val MAX_DIGITS = 6
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"

        fun validPin(pin: String): Boolean = pin.length in MIN_DIGITS..MAX_DIGITS && pin.all { it in '0'..'9' }
    }
}
