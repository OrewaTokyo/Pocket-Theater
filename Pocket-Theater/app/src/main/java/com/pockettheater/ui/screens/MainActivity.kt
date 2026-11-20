package com.pockettheater.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.gap
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.NetworkUtil

class MainActivity : Activity() {
    private lateinit var store: ShareStore
    private lateinit var browser: DocumentBrowser
    private lateinit var statusView: TextView
    private lateinit var urlView: TextView
    private lateinit var passwordView: TextView
    private lateinit var toggleButton: TextView
    private lateinit var folderBox: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShareStore(this)
        browser = DocumentBrowser(contentResolver)
        buildScreen()
        rebuildFolders()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
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
        passwordView = textView("", 16f, Theme.Ink).apply { setTextIsSelectable(true) }
        toggleButton = actionButton("Open theater", filled = true) {
            val intent = Intent(this, ServerService::class.java)
            if (ServerService.isRunning) stopService(intent) else startForegroundService(intent)
        }
        folderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        column.addView(textView("Pocket Theater", 30f, Theme.Ink, bold = true))
        column.addView(gap(6))
        column.addView(statusView)
        column.addView(gap(20))
        column.addView(textView("Open this link on your laptop (same Wi-Fi)", 14f, Theme.Muted))
        column.addView(urlView)
        column.addView(gap(16))
        column.addView(textView("User: ${com.pockettheater.core.security.Auth.USER}", 16f, Theme.Ink))
        column.addView(passwordView)
        column.addView(gap(8))
        column.addView(actionButton("New password", filled = false) {
            store.regeneratePassword()
            updateStatus()
        })
        column.addView(gap(24))
        column.addView(toggleButton)
        column.addView(gap(32))
        column.addView(textView("Shared folders", 20f, Theme.Ink, bold = true))
        column.addView(gap(8))
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
        urlView.text = when {
            !on -> "Open the theater to get your link"
            ip == null -> "No Wi-Fi found. Connect to Wi-Fi or turn on the hotspot."
            else -> "http://$ip:${ServerService.PORT}"
        }
        passwordView.text = "Password: ${store.password()}"
        toggleButton.text = if (on) "Close theater" else "Open theater"
    }

    private fun rebuildFolders() {
        folderBox.removeAllViews()
        val libs = store.libraries()
        if (libs.isEmpty()) {
            folderBox.addView(textView("No folders yet. Add one so your laptop has something to watch.", 15f, Theme.Muted))
            return
        }
        libs.forEach { lib ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            names.addView(textView(lib.name, 17f, Theme.Ink, bold = true))
            names.addView(textView(lib.category, 13f, Theme.Muted))
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
            .setItems(ShareStore.CATEGORIES.toTypedArray()) { _, which ->
                store.add(name, ShareStore.CATEGORIES[which], uri.toString())
                rebuildFolders()
            }
            .show()
    }

    companion object {
        private const val REQUEST_FOLDER = 100
    }
}
