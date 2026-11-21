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

    /** How long the app may be out of sight before the PIN is asked for again. 0 means every time. */
    var lockAfterSeconds: Int
        get() = prefs.getInt("lock_after_seconds", 60)
        set(value) { prefs.edit().putInt("lock_after_seconds", value).apply() }

    /** When on, the app shows a blank picture in the recent-apps list and screenshots are blocked. */
    var hideInRecents: Boolean
        get() = prefs.getBoolean("hide_in_recents", false)
        set(value) { prefs.edit().putBoolean("hide_in_recents", value).apply() }

    /** When on, the theater opens itself again if Android closes the app while it was open. */
    var autoRestart: Boolean
        get() = prefs.getBoolean("auto_restart", true)
        set(value) { prefs.edit().putBoolean("auto_restart", value).apply() }

    /** When on, the website uses HTTPS with a certificate the phone makes for itself. */
    var useHttps: Boolean
        get() = prefs.getBoolean("use_https", true)
        set(value) { prefs.edit().putBoolean("use_https", value).apply() }

    /** When on, the admin can open admin tools in a browser on another device by pairing it with a QR code. Off by default. */
    var adminWebEnabled: Boolean
        get() = prefs.getBoolean("admin_web_enabled", false)
        set(value) { prefs.edit().putBoolean("admin_web_enabled", value).apply() }
}
