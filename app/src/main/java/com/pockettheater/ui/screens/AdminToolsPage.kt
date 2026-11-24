package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pockettheater.core.storage.Account
import com.pockettheater.core.storage.AccountStatus
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.core.storage.UserStore
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme

/** Approve or decline sign-up requests and manage accounts. Shared folders are on the Folders tab. */
class AdminToolsPage(private val activity: Activity) : Screen {
    private val users = UserStore(activity)
    private var peopleRevision = 0

    private val requestsTitle = activity.textView("Sign-up requests", 17f, Theme.Ink, bold = true)
    private val requestBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val accountBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    override val view: View = build()

    init {
        rebuildPeople()
    }

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(24))
        }
        column.addView(
            activity.textView("Only you can see these. People ask for access on the website, you approve them here.", 14f, Theme.Muted)
        )

        val requests = activity.card(16)
        requests.addView(requestsTitle)
        requests.addView(requestBox, activity.lp(MATCH, WRAP, top = 6))
        column.addView(requests, activity.lp(MATCH, WRAP, top = 16))

        val accounts = activity.card(16)
        accounts.addView(activity.textView("Accounts", 17f, Theme.Ink, bold = true))
        accounts.addView(accountBox, activity.lp(MATCH, WRAP, top = 6))
        column.addView(accounts, activity.lp(MATCH, WRAP, top = 14))

        return ScrollView(activity).apply { addView(column) }
    }

    override fun refresh() {
        if (users.revision() != peopleRevision) rebuildPeople()
    }

    private fun rebuildPeople() {
        peopleRevision = users.revision()
        requestBox.removeAllViews()
        accountBox.removeAllViews()

        val pending = users.pending()
        requestsTitle.text = if (pending.isEmpty()) "Sign-up requests" else "Sign-up requests (${pending.size})"
        if (pending.isEmpty()) {
            requestBox.addView(activity.textView("No one is waiting.", 15f, Theme.Muted))
        }
        pending.forEach { p ->
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, activity.dp(8), 0, activity.dp(8))
            }
            item.addView(activity.textView(p.name, 17f, Theme.Ink, bold = true))
            item.addView(activity.textView("Wants to join", 13f, Theme.Muted))
            val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(
                activity.actionButton("Approve", filled = true) { users.approve(p.name); rebuildPeople() },
                activity.lp(0, WRAP, 1f, right = 6)
            )
            buttons.addView(
                activity.actionButton("Decline", filled = false) { users.decline(p.name); rebuildPeople() },
                activity.lp(0, WRAP, 1f, left = 6)
            )
            item.addView(buttons, activity.lp(MATCH, WRAP, top = 8))
            requestBox.addView(item)
        }

        val members = users.members()
        if (members.isEmpty()) {
            accountBox.addView(activity.textView("No one has an account yet.", 15f, Theme.Muted))
        }
        members.forEach { a ->
            val off = a.status == AccountStatus.OFF
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, activity.dp(8), 0, activity.dp(8))
                isClickable = true
                setOnClickListener { manage(a) }
            }
            row.addView(activity.textView(a.name, 17f, Theme.Ink, bold = true))
            row.addView(
                activity.textView(
                    if (off) "Turned off \u00B7 tap to manage" else "Active \u00B7 tap to manage",
                    13f, if (off) Theme.Muted else Theme.Cyan
                )
            )
            accountBox.addView(row)
        }
    }

    private fun manage(account: Account) {
        val off = account.status == AccountStatus.OFF
        val options = arrayOf("Reset password", if (off) "Turn on" else "Turn off", "Delete account")
        AlertDialog.Builder(activity)
            .setTitle(account.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> resetPassword(account)
                    1 -> { users.setOff(account.name, !off); rebuildPeople() }
                    else -> confirmDelete(account)
                }
            }
            .show()
    }

    private fun resetPassword(account: Account) {
        val fresh = users.resetPassword(account.name)
        rebuildPeople()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("New password for ${account.name}")
            .setMessage("$fresh\n\nGive this to ${account.name}. It works right away, and they can change it from Account on the website. They were signed out everywhere.")
            .setPositiveButton("Done", null)
            .setNeutralButton("Copy") { _, _ -> copy(fresh) }
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }

    private fun confirmDelete(account: Account) {
        AlertDialog.Builder(activity)
            .setTitle("Delete ${account.name}?")
            .setMessage("They will be signed out and will have to ask for access again.")
            .setPositiveButton("Delete") { _, _ -> users.delete(account.name); rebuildPeople() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copy(text: String) {
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("New password", text))
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }
}
