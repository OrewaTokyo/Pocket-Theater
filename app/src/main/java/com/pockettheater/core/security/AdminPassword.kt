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
    private val limiter = AttemptLimiter(prefs)

    fun isSet(): Boolean = prefs.contains(KEY_HASH)

    fun set(password: String) {
        val salt = Auth.newSalt()
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Auth.encode(password, salt))
            .apply()
        limiter.reset()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun secondsToWait(): Int = limiter.secondsToWait()

    fun triesLeft(): Int = limiter.triesLeft()

    fun verify(password: String): PinLock.Result {
        if (limiter.secondsToWait() > 0) return PinLock.Result.WAIT
        val salt = Base64.decode(prefs.getString(KEY_SALT, "").orEmpty(), Base64.NO_WRAP)
        val stored = prefs.getString(KEY_HASH, "").orEmpty()
        if (stored.isNotEmpty() && Auth.check(password, salt, stored)) {
            limiter.reset()
            return PinLock.Result.OK
        }
        limiter.fail()
        return PinLock.Result.WRONG
    }

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
    }
}
