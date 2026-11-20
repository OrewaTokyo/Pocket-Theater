package com.pockettheater.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pockettheater.core.storage.Account
import com.pockettheater.core.storage.AccountStatus
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.core.storage.UserStore
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.gap
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.NetworkUtil

class MainActivity : Activity() {
    private lateinit var store: ShareStore
    private lateinit var users: UserStore
    private lateinit var browser: DocumentBrowser
    private lateinit var statusView: TextView
    private lateinit var urlView: TextView
    private lateinit var toggleButton: TextView
    private lateinit var requestsTitle: TextView
    private lateinit var requestBox: LinearLayout
    private lateinit var accountBox: LinearLayout
    private lateinit var folderBox: LinearLayout
    private var link: String? = null
    private var peopleRevision = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateStatus()
            if (users.revision() != peopleRevision) rebuildPeople()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShareStore(this)
        users = UserStore(this)
        browser = DocumentBrowser(contentResolver)
        buildScreen()
        rebuildPeople()
        rebuildFolders()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        getSystemService(NotificationManager::class.java).cancel(ServerService.NOTIF_REQUEST)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun buildScreen() {
        window.decorView.setBackgroundColor(Theme.Midnight)
        window.statusBarColor = Theme.Midnight
        window.navigationBarColor = Theme.Midnight

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }
        statusView = textView("", 16f, Theme.Muted, bold = true)
        urlView = textView("", 22f, Theme.Marquee, bold = true).apply { setTextIsSelectable(true) }
        toggleButton = actionButton("Open theater", filled = true) {
            val intent = Intent(this, ServerService::class.java)
            if (ServerService.isRunning) stopService(intent) else startForegroundService(intent)
        }
        requestsTitle = textView("Sign-up requests", 17f, Theme.Ink, bold = true)
        requestBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        accountBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        folderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        column.addView(textView("Pocket Theater", 30f, Theme.Ink, bold = true))
        column.addView(gap(6))
        column.addView(statusView)
        column.addView(gap(20))
        column.addView(textView("Website link (open it on your laptop, same Wi-Fi)", 14f, Theme.Muted))
        column.addView(urlView)
        column.addView(gap(8))
        column.addView(actionButton("Copy link", filled = false) {
            link?.let { copy("Pocket Theater link", it) }
        })
        column.addView(gap(16))
        column.addView(toggleButton)

        column.addView(gap(36))
        column.addView(textView("Admin tools", 24f, Theme.Marquee, bold = true))
        column.addView(textView("Only you can see these. People ask for access on the website, you approve them here.", 14f, Theme.Muted))
        column.addView(gap(20))
        column.addView(requestsTitle)
        column.addView(requestBox)
        column.addView(gap(20))
        column.addView(textView("Accounts", 17f, Theme.Ink, bold = true))
        column.addView(accountBox)
        column.addView(gap(20))
        column.addView(textView("Shared folders", 17f, Theme.Ink, bold = true))
        column.addView(folderBox)
        column.addView(gap(12))
        column.addView(actionButton("Add folder", filled = false) { pickFolder() })

        setContentView(
            ScrollView(this).apply {
                addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        )
    }

    private fun updateStatus() {
        val on = ServerService.isRunning
        val error = ServerService.lastError
        statusView.text = when {
            on -> "Theater is open"
            error != null -> "Could not open: $error"
            else -> "Theater is closed"
        }
        statusView.setTextColor(if (on) Theme.Cyan else Theme.Muted)
        val ip = NetworkUtil.localIpv4()
        val address = if (on && ip != null) "http://$ip:${ServerService.PORT}" else null
        link = address
        urlView.text = when {
            !on -> "Open the theater to get your link"
            address == null -> "No Wi-Fi found. Connect to Wi-Fi or turn on the hotspot."
            else -> address
        }
        toggleButton.text = if (on) "Close theater" else "Open theater"
    }

    private fun rebuildPeople() {
        peopleRevision = users.revision()
        requestBox.removeAllViews()
        accountBox.removeAllViews()

        val pending = users.pending()
        requestsTitle.text = if (pending.isEmpty()) "Sign-up requests" else "Sign-up requests (${pending.size})"
        if (pending.isEmpty()) {
            requestBox.addView(textView("No one is waiting.", 15f, Theme.Muted))
        }
        pending.forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            card.addView(textView(p.name, 17f, Theme.Ink, bold = true))
            card.addView(textView("Wants to join", 13f, Theme.Muted))
            card.addView(gap(6))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(
                actionButton("Approve", filled = true) { users.approve(p.name); rebuildPeople() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            )
            buttons.addView(
                actionButton("Decline", filled = false) { users.decline(p.name); rebuildPeople() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            card.addView(buttons)
            requestBox.addView(card)
        }

        val members = users.members()
        if (members.isEmpty()) {
            accountBox.addView(textView("No one has an account yet.", 15f, Theme.Muted))
        }
        members.forEach { a ->
            val off = a.status == AccountStatus.OFF
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                setOnClickListener { manage(a) }
            }
            row.addView(textView(a.name, 17f, Theme.Ink, bold = true))
            row.addView(
                textView(if (off) "Turned off \u00B7 tap to manage" else "Active \u00B7 tap to manage", 13f, if (off) Theme.Muted else Theme.Cyan)
            )
            accountBox.addView(row)
        }
    }

    private fun manage(account: Account) {
        val off = account.status == AccountStatus.OFF
        val options = arrayOf("Reset password", if (off) "Turn on" else "Turn off", "Delete account")
        AlertDialog.Builder(this)
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("New password for ${account.name}")
            .setMessage("$fresh\n\nGive this to ${account.name}. It works right away, and they can change it from Account on the website. They were signed out everywhere.")
            .setPositiveButton("Done", null)
            .setNeutralButton("Copy") { _, _ -> copy("New password", fresh) }
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }

    private fun confirmDelete(account: Account) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${account.name}?")
            .setMessage("They will be signed out and will have to ask for access again.")
            .setPositiveButton("Delete") { _, _ -> users.delete(account.name); rebuildPeople() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copy(label: String, text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun rebuildFolders() {
        folderBox.removeAllViews()
        val libs = store.libraries()
        if (libs.isEmpty()) {
            folderBox.addView(textView("No folders yet. Add one so people have something to watch.", 15f, Theme.Muted))
            return
        }
        libs.forEach { lib ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(textView(lib.name, 17f, Theme.Ink, bold = true))
            names.addView(textView(Theme.categoryLabel(lib.category), 14f, Theme.categoryColor(lib.category), bold = true))
            row.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(actionButton("Remove", filled = false) { removeFolder(lib.id, lib.uri) })
            folderBox.addView(row)
        }
    }

    private fun removeFolder(id: String, uri: String) {
        try {
            contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Already released; nothing to do.
        }
        store.remove(id)
        rebuildFolders()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_FOLDER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = browser.displayName(uri)
        AlertDialog.Builder(this)
            .setTitle("What is in \"$name\"?")
            .setItems(ShareStore.CATEGORIES.map { Theme.categoryLabel(it) }.toTypedArray()) { _, which ->
                store.add(name, ShareStore.CATEGORIES[which], uri.toString())
                rebuildFolders()
            }
            .show()
    }

    companion object {
        private const val REQUEST_FOLDER = 100
    }
}
