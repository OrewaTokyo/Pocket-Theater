package com.pockettheater.util

import android.app.Application

/** Starts first, before any screen or the server. Sets up the problem report. */
class PocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
