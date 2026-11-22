package com.pockettheater.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Keeps a short report when the app crashes, or when something goes wrong that the app recovered from,
 * so the next time you open the app you can copy it and send it to whoever is fixing the problem.
 */
object CrashLog {
    private const val FILE = "last-problem.txt"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun header(kind: String): String =
        "Pocket Theater problem report\n$kind\n${Date()}\n" +
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"

    /** Writes a report if the app is about to close because of an error nobody caught. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file(app).writeText(header("The app closed unexpectedly (thread ${thread.name})") + Log.getStackTraceString(error))
            } catch (e: Throwable) {
                // Nothing more can be done.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Remembers an error the app recovered from. Only the first one is kept, and a crash report is never overwritten. */
    fun record(context: Context, where: String, error: Throwable) {
        try {
            val target = file(context)
            if (!target.exists()) target.writeText(header("An error was handled in $where") + Log.getStackTraceString(error))
        } catch (e: Throwable) {
            // Nothing more can be done.
        }
    }

    fun pending(context: Context): String? =
        try {
            file(context).takeIf { it.exists() }?.readText()
        } catch (e: Throwable) {
            null
        }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (e: Throwable) {
            // Nothing more can be done.
        }
    }
}
