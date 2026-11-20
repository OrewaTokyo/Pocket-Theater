package com.pockettheater.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme

/** The shell: the three pages (Dashboard, Folders, Profile) and the tab bar along the bottom. */
class MainActivity : Activity() {
    private lateinit var store: ShareStore
    private lateinit var browser: DocumentBrowser
    private lateinit var profile: ProfileScreen
    private lateinit var screens: List<Screen>
    private lateinit var content: FrameLayout
    private val tabIcons = ArrayList<TextView>()
    private val tabLabels = ArrayList<TextView>()
    private var tab = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            screens[tab].refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShareStore(this)
        browser = DocumentBrowser(contentResolver)
        profile = ProfileScreen(this) { pickFolder() }
        screens = listOf(
            DashboardScreen(this),
            FoldersScreen(this) { select(2); profile.openAdminTools() },
            profile
        )
        window.statusBarColor = Theme.Midnight
        window.navigationBarColor = Theme.Surface
        setContentView(buildShell())
        select(savedInstanceState?.getInt(STATE_TAB) ?: 0)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tab)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!screens[tab].onBack()) super.onBackPressed()
    }

    private fun buildShell(): View {
        content = FrameLayout(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Theme.Surface)
        }
        val tabs = listOf("\uD83D\uDCCA" to "Dashboard", "\uD83D\uDCC1" to "Folders", "\uD83D\uDC64" to "Profile")
        tabs.forEachIndexed { index, (icon, name) ->
            val iconView = textView(icon, 22f, Theme.Ink).apply { gravity = Gravity.CENTER }
            val labelView = textView(name, 12f, Theme.Muted, bold = true).apply { gravity = Gravity.CENTER }
            tabIcons.add(iconView)
            tabLabels.add(labelView)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10))
                isClickable = true
                setOnClickListener { select(index) }
                addView(iconView, lp(WRAP, WRAP))
                addView(labelView, lp(WRAP, WRAP, top = 2))
            }
            bar.addView(item, lp(0, WRAP, 1f))
        }

        val divider = View(this).apply { setBackgroundColor(Theme.Border) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.Midnight)
            addView(content, lp(MATCH, 0, 1f))
            addView(divider, lp(MATCH, dp(1)))
            addView(bar, lp(MATCH, WRAP))
        }
    }

    private fun select(index: Int) {
        tab = index
        content.removeAllViews()
        content.addView(screens[index].view, FrameLayout.LayoutParams(MATCH, MATCH))
        screens[index].refresh()
        for (i in tabIcons.indices) {
            val on = i == index
            tabIcons[i].alpha = if (on) 1f else 0.5f
            tabLabels[i].setTextColor(if (on) Theme.Marquee else Theme.Muted)
        }
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
                profile.foldersChanged()
            }
            .show()
    }

    companion object {
        private const val REQUEST_FOLDER = 100
        private const val STATE_TAB = "tab"
    }
}
