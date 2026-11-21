package com.pockettheater.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/** Android closes background apps to save battery. This asks it to leave the theater alone. */
object BatteryHelper {
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Opens Android's own "run without battery limits" question, or the list of apps if that does not work. */
    @SuppressLint("BatteryLife")
    fun ask(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.packageName))
            )
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(activity, "Open your phone's battery settings and set Pocket Theater to unrestricted.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Opens this app's page in Android settings, where some phones keep Autostart and Background activity switches. */
    fun openAppSettings(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName))
            )
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open the app settings.", Toast.LENGTH_SHORT).show()
        }
    }
}
