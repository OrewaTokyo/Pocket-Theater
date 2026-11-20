package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.pockettheater.core.security.AppLock
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
class SettingsPage(private val activity: Activity, private val appLock: AppLock) : Screen {
    private val settings = AppSettings(activity)
    private val users = UserStore(activity)
    private val lockAfterText = activity.textView("", 14f, Theme.Muted)

    override val view: View = build()

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(24))
        }

        column.addView(activity.sectionLabel("Security"), activity.lp(WRAP, WRAP, top = 8, bottom = 10))
        val security = activity.card(16)
        security.addView(
            toggleRow(
                "Lock the app",
                "Ask for your fingerprint, face or phone PIN when the app opens. The website keeps working while it is locked.",
                settings.lockEnabled
            ) { on, revert -> changeLock(on, revert) }
        )
        val lockAfter = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnClickListener { chooseLockDelay() }
        }
        lockAfter.addView(activity.textView("Lock after leaving the app", 16f, Theme.Ink, bold = true))
        lockAfter.addView(lockAfterText)
        security.addView(lockAfter, activity.lp(MATCH, WRAP, top = 16))
        showLockDelay()
        column.addView(security)

        column.addView(activity.sectionLabel("Sign-ups"), activity.lp(WRAP, WRAP, top = 24, bottom = 10))
        val signups = activity.card(16)
        signups.addView(
            toggleRow(
                "Allow new sign-up requests",
                "Turn off to stop people asking for access. Existing accounts keep working.",
                settings.signupsOpen
            ) { on, _ -> settings.signupsOpen = on }
        )
        signups.addView(
            toggleRow(
                "Notify me about requests",
                "A phone notification when someone asks to join. Requests still wait in Admin tools.",
                settings.notifyRequests
            ) { on, _ -> settings.notifyRequests = on },
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

    /** `onChange` gets the new value and a function that puts the switch back if the change did not go through. */
    private fun toggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean, () -> Unit) -> Unit): View {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(activity.textView(title, 16f, Theme.Ink, bold = true))
        texts.addView(activity.textView(subtitle, 14f, Theme.Muted))
        row.addView(texts, activity.lp(0, WRAP, 1f, right = 12))
        val switch = Switch(activity)
        switch.isChecked = on
        var reverting = false
        switch.setOnCheckedChangeListener { _, checked ->
            if (!reverting) {
                onChange(checked) {
                    reverting = true
                    switch.isChecked = !checked
                    reverting = false
                }
            }
        }
        row.addView(switch, activity.lp(WRAP, WRAP))
        return row
    }

    private fun changeLock(on: Boolean, revert: () -> Unit) {
        if (on && !appLock.available()) {
            Toast.makeText(activity, "Set a screen lock (PIN, pattern or fingerprint) in your phone's settings first.", Toast.LENGTH_LONG).show()
            revert()
            return
        }
        appLock.authenticate(if (on) "Turn on app lock" else "Turn off app lock", "Confirm it is you") { ok ->
            if (ok) settings.lockEnabled = on else revert()
        }
    }

    private fun chooseLockDelay() {
        val labels = arrayOf("Every time I leave", "After 1 minute", "After 5 minutes")
        val seconds = intArrayOf(0, 60, 300)
        AlertDialog.Builder(activity)
            .setTitle("Lock after leaving the app")
            .setItems(labels) { _, which ->
                settings.lockAfterSeconds = seconds[which]
                showLockDelay()
            }
            .show()
    }

    private fun showLockDelay() {
        lockAfterText.text = when (settings.lockAfterSeconds) {
            0 -> "Every time I leave"
            60 -> "After 1 minute"
            300 -> "After 5 minutes"
            else -> "After ${settings.lockAfterSeconds} seconds"
        }
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
