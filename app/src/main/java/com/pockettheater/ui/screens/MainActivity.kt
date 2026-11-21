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
import android.os.SystemClock
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pockettheater.R
import com.pockettheater.core.security.PinLock
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.PinPad
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.MarkdownText

/** The shell: the three pages (Dashboard, Folders, Profile) and the tab bar along the bottom. */
class MainActivity : Activity() {
    private lateinit var store: ShareStore
    private lateinit var browser: DocumentBrowser
    private lateinit var profile: ProfileScreen
    private lateinit var screens: List<Screen>
    private lateinit var content: FrameLayout
    private lateinit var pinLock: PinLock
    private lateinit var settings: AppSettings
    private lateinit var lockView: View
    private lateinit var lockPad: PinPad
    private var locked = false
    private var leftAt = 0L
    private val tabItems = ArrayList<LinearLayout>()
    private val tabIcons = ArrayList<ImageView>()
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
        settings = AppSettings(this)
        pinLock = PinLock(this)
        profile = ProfileScreen(this, pinLock) { pickFolder() }
        screens = listOf(
            DashboardScreen(this),
            FoldersScreen(this) { select(2); profile.openAdminTools() },
            profile
        )
        window.statusBarColor = Theme.Midnight
        window.navigationBarColor = Theme.Surface
        lockView = buildLockView()
        setContentView(FrameLayout(this).apply {
            addView(buildShell(), FrameLayout.LayoutParams(MATCH, MATCH))
            addView(lockView, FrameLayout.LayoutParams(MATCH, MATCH))
        })
        select(savedInstanceState?.getInt(STATE_TAB) ?: 0)
        applyScreenPrivacy()
        lockNow()
        askLegalIfNeeded()

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

    override fun onStart() {
        super.onStart()
        val awayMs = SystemClock.elapsedRealtime() - leftAt
        if (!locked && leftAt != 0L && awayMs >= settings.lockAfterSeconds * 1000L) lockNow()
    }

    override fun onStop() {
        leftAt = SystemClock.elapsedRealtime()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        getSystemService(NotificationManager::class.java).cancel(ServerService.NOTIF_REQUEST)
        applyScreenPrivacy()
        handler.post(ticker)
    }

    /** Blank picture in the recent-apps list, and no screenshots, when the admin asked for it. */
    private fun applyScreenPrivacy() {
        if (settings.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** The first time, and whenever the Terms change in a way that matters, the admin has to agree before using the app. */
    private fun askLegalIfNeeded() {
        if (locked || settings.acceptedLegalVersion >= AppSettings.LEGAL_VERSION) return
        AlertDialog.Builder(this)
            .setTitle("Before you start")
            .setMessage(
                "Pocket Theater keeps everything on this phone and sends nothing to the developer or to any online service.\n\n" +
                    "You are responsible for the files you share and for the people you approve. Read the Terms of use and the Privacy policy, then agree to carry on."
            )
            .setCancelable(false)
            .setPositiveButton("I agree") { _, _ -> settings.acceptedLegalVersion = AppSettings.LEGAL_VERSION }
            .setNeutralButton("Read them") { _, _ -> chooseLegalToRead() }
            .setNegativeButton("Close app") { _, _ -> finish() }
            .show()
    }

    private fun chooseLegalToRead() {
        AlertDialog.Builder(this)
            .setTitle("Read first")
            .setItems(arrayOf("Terms of use", "Privacy policy")) { _, which ->
                showLegalText(if (which == 0) "terms" else "privacy")
            }
            .setOnCancelListener { askLegalIfNeeded() }
            .show()
    }

    private fun showLegalText(name: String) {
        val text = TextView(this).apply {
            this.text = MarkdownText.fromAsset(this@MainActivity, name)
            setTextColor(Theme.Ink)
            textSize = 15f
            setLineSpacing(0f, 1.2f)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("Back") { _, _ -> chooseLegalToRead() }
            .setOnCancelListener { chooseLegalToRead() }
            .show()
    }

    /** Covers the app and asks for the PIN. Does nothing when no PIN is set. */
    private fun lockNow() {
        if (!pinLock.isSet()) return
        locked = true
        lockPad.clear()
        lockPad.setMessage("Enter your PIN to open it.")
        lockView.visibility = View.VISIBLE
    }

    private fun tryPin(pin: String) {
        when (pinLock.verify(pin)) {
            PinLock.Result.OK -> {
                locked = false
                leftAt = 0L
                lockPad.clear()
                lockView.visibility = View.GONE
                askLegalIfNeeded()
            }
            PinLock.Result.WAIT -> {
                lockPad.clear()
                lockPad.setMessage("Too many tries. Wait ${pinLock.secondsToWait()} seconds.", true)
            }
            PinLock.Result.WRONG -> {
                lockPad.clear()
                val wait = pinLock.secondsToWait()
                lockPad.setMessage(if (wait > 0) "Too many tries. Wait $wait seconds." else "Wrong PIN. ${pinLock.triesLeft()} tries left.", true)
            }
        }
    }

    private fun buildLockView(): View {
        lockPad = PinPad(this) { pin -> tryPin(pin) }
        val screenWidth = resources.displayMetrics.widthPixels
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.logo_splash)
                    adjustViewBounds = true
                    contentDescription = "Pocket Theater"
                },
                lp((screenWidth * 0.4f).toInt(), WRAP)
            )
            addView(textView("Pocket Theater is locked", 22f, Theme.Ink, bold = true).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP, top = 4, bottom = 8))
            addView(lockPad, lp(MATCH, WRAP))
            addView(
                textView("Forgot it? Clearing the app's data in Android settings removes the lock, and your accounts and folders with it.", 13f, Theme.Muted)
                    .apply { gravity = Gravity.CENTER },
                lp(MATCH, WRAP, top = 18)
            )
        }
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = resources.displayMetrics.heightPixels * 0.65f
                setGradientCenter(0.5f, 0.4f)
                colors = intArrayOf(Theme.SplashGlow, Theme.SplashEdge)
            }
            isClickable = true // catches touches so nothing underneath can be used
            visibility = View.GONE
            addView(ScrollView(this@MainActivity).apply { addView(column) }, FrameLayout.LayoutParams(MATCH, MATCH))
        }
    }

    private fun buildShell(): View {
        content = FrameLayout(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Theme.Surface)
        }
        val tabs = listOf(
            Triple(R.drawable.ic_tab_dashboard, "Dashboard", 0),
            Triple(R.drawable.ic_tab_folders, "Folders", 1),
            Triple(R.drawable.ic_tab_profile, "Profile", 2),
        )
        tabs.forEach { (iconRes, name, index) ->
            val iconView = ImageView(this).apply { setImageResource(iconRes) }
            val labelView = textView(name, 12f, Theme.Muted, bold = true).apply { gravity = Gravity.CENTER }
            tabIcons.add(iconView)
            tabLabels.add(labelView)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                setOnClickListener { select(index) }
                addView(iconView, lp(dp(24), dp(24)))
                addView(labelView, lp(WRAP, WRAP, top = 2))
            }
            tabItems.add(item)
            bar.addView(item, lp(0, WRAP, 1f, 8, 6, 8, 6))
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
            tabIcons[i].setColorFilter(if (on) Theme.Marquee else Theme.Muted)
            tabLabels[i].setTextColor(if (on) Theme.Marquee else Theme.Muted)
            tabItems[i].background = if (on) GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0x22FFB547)
            } else null
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
