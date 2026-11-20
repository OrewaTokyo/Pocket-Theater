package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import com.pockettheater.core.server.ServerStats
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.core.storage.UserStore
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.sectionLabel
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme

/** Switches and one-tap clean-ups for the admin. */
class SettingsPage(private val activity: Activity) : Screen {
    private val settings = AppSettings(activity)
    private val users = UserStore(activity)

    override val view: View = build()

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(24))
        }

        column.addView(activity.sectionLabel("Sign-ups"), activity.lp(WRAP, WRAP, top = 8, bottom = 10))
        val signups = activity.card(16)
        signups.addView(
            toggleRow(
                "Allow new sign-up requests",
                "Turn off to stop people asking for access. Existing accounts keep working.",
                settings.signupsOpen
            ) { settings.signupsOpen = it }
        )
        signups.addView(
            toggleRow(
                "Notify me about requests",
                "A phone notification when someone asks to join. Requests still wait in Admin tools.",
                settings.notifyRequests
            ) { settings.notifyRequests = it },
            activity.lp(MATCH, WRAP, top = 14)
        )
        column.addView(signups)

        column.addView(activity.sectionLabel("Clean up"), activity.lp(WRAP, WRAP, top = 24, bottom = 10))
        val cleanup = activity.card(16)
        cleanup.addView(activity.textView("Sign everyone out", 16f, Theme.Ink, bold = true))
        cleanup.addView(activity.textView("Everyone has to sign in again with their password.", 14f, Theme.Muted))
        cleanup.addView(
            activity.actionButton("Sign everyone out", filled = false) { confirmSignOutAll() },
            activity.lp(MATCH, WRAP, top = 10)
        )
        cleanup.addView(activity.textView("Reset data counters", 16f, Theme.Ink, bold = true), activity.lp(WRAP, WRAP, top = 20))
        cleanup.addView(activity.textView("Sets total data, requests and awake time on the Dashboard back to zero.", 14f, Theme.Muted))
        cleanup.addView(
            activity.actionButton("Reset counters", filled = false) { confirmResetCounters() },
            activity.lp(MATCH, WRAP, top = 10)
        )
        column.addView(cleanup)

        column.addView(activity.sectionLabel("About"), activity.lp(WRAP, WRAP, top = 24, bottom = 10))
        val about = activity.card(16)
        about.addView(activity.textView("Pocket Theater", 16f, Theme.Ink, bold = true))
        about.addView(activity.textView("Version " + versionName(), 14f, Theme.Muted))
        column.addView(about)

        return ScrollView(activity).apply { addView(column) }
    }

    private fun toggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(activity.textView(title, 16f, Theme.Ink, bold = true))
        texts.addView(activity.textView(subtitle, 14f, Theme.Muted))
        row.addView(texts, activity.lp(0, WRAP, 1f, right = 12))
        val switch = Switch(activity)
        switch.isChecked = on
        switch.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(switch, activity.lp(WRAP, WRAP))
        return row
    }

    private fun confirmSignOutAll() {
        AlertDialog.Builder(activity)
            .setTitle("Sign everyone out?")
            .setMessage("Everyone on the website will have to sign in again.")
            .setPositiveButton("Sign out") { _, _ ->
                users.signOutAll()
                Toast.makeText(activity, "Everyone was signed out", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmResetCounters() {
        AlertDialog.Builder(activity)
            .setTitle("Reset data counters?")
            .setMessage("Total data sent, requests and awake time go back to zero. This cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                ServerStats.resetTotals(activity)
                Toast.makeText(activity, "Counters reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun versionName(): String = try {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    override fun refresh() = Unit
}
