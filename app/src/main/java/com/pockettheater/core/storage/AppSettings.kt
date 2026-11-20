package com.pockettheater.core.storage

import android.content.Context

/** The few switches the admin can flip in Profile > Settings. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("pocket_theater_settings", Context.MODE_PRIVATE)

    /** When off, the website refuses new sign-up requests. */
    var signupsOpen: Boolean
        get() = prefs.getBoolean("signups_open", true)
        set(value) { prefs.edit().putBoolean("signups_open", value).apply() }

    /** When off, the phone stays quiet about new sign-up requests (they still wait in Admin tools). */
    var notifyRequests: Boolean
        get() = prefs.getBoolean("notify_requests", true)
        set(value) { prefs.edit().putBoolean("notify_requests", value).apply() }

    /** When on, opening the app asks for the phone's fingerprint, face or PIN first. */
    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock_enabled", false)
        set(value) { prefs.edit().putBoolean("lock_enabled", value).apply() }

    /** How long the app may be out of sight before it locks again. 0 means every time. */
    var lockAfterSeconds: Int
        get() = prefs.getInt("lock_after_seconds", 60)
        set(value) { prefs.edit().putInt("lock_after_seconds", value).apply() }
}
