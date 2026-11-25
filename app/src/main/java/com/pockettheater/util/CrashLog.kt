package com.pockettheater.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Keeps a short report when the app crashes, or when something goes wrong that the app recovered from,
 * so the next time you open the app you can copy it and send it to whoever is fixing the problem.
 *
 * A report is only shown by the same version of the app that wrote it. After an update the old report is thrown away,
 * because the problem it describes may be fixed, and an old report must never keep coming back.
 */
object CrashLog {
    private const val FILE = "last-problem.txt"
    private const val VERSION_FILE = "last-problem.version"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun versionFile(context: Context) = File(context.applicationContext.filesDir, VERSION_FILE)

    private fun currentVersion(context: Context): Long =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } catch (e: Throwable) {
            0L
        }

    private fun save(context: Context, text: String) {
        file(context).writeText(text)
        versionFile(context).writeText(currentVersion(context).toString())
    }

    private fun header(kind: String): String =
        "Pocket Theater problem report\n$kind\n${Date()}\n" +
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"

    /** Writes a report if the app is about to close because of an error nobody caught. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save(app, header("The app closed unexpectedly (thread ${thread.name})") + Log.getStackTraceString(error))
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
            if (!target.exists()) save(context, header("An error was handled in $where") + Log.getStackTraceString(error))
        } catch (e: Throwable) {
            // Nothing more can be done.
        }
    }

    /** The report waiting to be shown, or null. A report from an older version of the app (or one with no version) is deleted. */
    fun pending(context: Context): String? =
        try {
            val report = file(context).takeIf { it.exists() }?.readText()
            val savedVersion = versionFile(context).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
            if (report != null && savedVersion != currentVersion(context)) {
                clear(context)
                null
            } else {
                report
            }
        } catch (e: Throwable) {
            null
        }

    fun clear(context: Context) {
        try {
            file(context).delete()
            versionFile(context).delete()
        } catch (e: Throwable) {
            // Nothing more can be done.
        }
    }
}
