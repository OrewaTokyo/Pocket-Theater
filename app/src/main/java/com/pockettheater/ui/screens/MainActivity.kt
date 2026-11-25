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
import android.view.animation.OvershootInterpolator
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
import com.pockettheater.ui.components.SkyBackdrop
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.glassDrawable
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.staggerIn
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.ui.components.QrScanner
import com.pockettheater.util.CrashLog

/** The shell: the three pages (Dashboard, Folders, Profile) and the tab bar along the bottom. */
class MainActivity : Activity() {
    private lateinit var store: ShareStore
    private lateinit var browser: DocumentBrowser
    private lateinit var profile: ProfileScreen
    private lateinit var folders: FoldersScreen
    private lateinit var screens: List<Screen>
    private lateinit var content: FrameLayout
    private lateinit var pinLock: PinLock
    private lateinit var settings: AppSettings
    private lateinit var lockView: View
    private lateinit var lockPad: PinPad
    private var locked = false
    private var leftAt = 0L
    private var skipLockUntil = 0L
    private val tabItems = ArrayList<LinearLayout>()
    private val tabIcons = ArrayList<ImageView>()
    private val tabLabels = ArrayList<TextView>()
    private lateinit var tabRow: LinearLayout
    private lateinit var tabIndicator: View
    private var tab = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            // One failing refresh must never close the app, so it is recorded and the loop carries on.
            try {
                screens[tab].refresh()
            } catch (e: Exception) {
                CrashLog.record(this@MainActivity, "refreshing " + screens[tab].javaClass.simpleName, e)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShareStore(this)
        browser = DocumentBrowser(contentResolver)
        settings = AppSettings(this)
        pinLock = PinLock(this)
        profile = ProfileScreen(this, pinLock)
        folders = FoldersScreen(this) { pickFolder() }
        screens = listOf(
            DashboardScreen(this),
            folders,
            profile
        )
        window.statusBarColor = Theme.Deep
        window.navigationBarColor = Theme.Deep
        lockView = buildLockView()
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Theme.Deep)
            addView(SkyBackdrop(this@MainActivity), FrameLayout.LayoutParams(MATCH, MATCH)) // the moving sky behind everything
            addView(buildShell(), FrameLayout.LayoutParams(MATCH, MATCH))
            addView(lockView, FrameLayout.LayoutParams(MATCH, MATCH))
        })
        select(savedInstanceState?.getInt(STATE_TAB) ?: 0)
        applyScreenPrivacy()
        lockNow()

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

    /** Call just before opening another screen on purpose (the folder picker, the QR scanner), so coming back does not ask for the PIN. */
    fun skipNextLock() {
        skipLockUntil = SystemClock.elapsedRealtime() + 120_000L
    }

    override fun onStart() {
        super.onStart()
        if (SystemClock.elapsedRealtime() < skipLockUntil) {
            skipLockUntil = 0L
            leftAt = 0L
            return
        }
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
        // With a PIN set, the recent-apps picture would show what the PIN is meant to hide, so it is blanked (screenshots still work).
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!(pinLock.isSet() || settings.hideInRecents))
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
        tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabs = listOf(
            Triple(R.drawable.ic_tab_dashboard, "Dashboard", 0),
            Triple(R.drawable.ic_tab_folders, "Folders", 1),
            Triple(R.drawable.ic_tab_profile, "Profile", 2),
        )
        tabs.forEach { (iconRes, name, index) ->
            val iconView = ImageView(this).apply {
                setImageResource(iconRes)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO // the label under it already says what it is
            }
            val labelView = textView(name, 12f, Theme.Muted, bold = true).apply { gravity = Gravity.CENTER }
            tabIcons.add(iconView)
            tabLabels.add(labelView)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                isClickable = true
                contentDescription = "$name tab"
                setOnClickListener { select(index) }
                addView(iconView, lp(dp(24), dp(24)))
                addView(labelView, lp(WRAP, WRAP, top = 2))
            }
            tabItems.add(item)
            tabRow.addView(item, lp(0, WRAP, 1f, 2, 4, 2, 4))
        }

        // The glowing pill that slides behind the chosen tab.
        tabIndicator = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x40FFC65C, 0x1AFF9A3D)).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x66FFC65C)
            }
        }
        val bar = FrameLayout(this).apply {
            background = glassDrawable(26)
            elevation = dp(10).toFloat()
            setPadding(dp(4), dp(2), dp(4), dp(2))
            addView(tabIndicator, FrameLayout.LayoutParams(1, MATCH).apply { setMargins(0, dp(4), 0, dp(4)) })
            addView(tabRow, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        tabRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionIndicator(false) }

        // transparent, so the sky behind shows through the pages
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, lp(MATCH, 0, 1f))
            addView(bar, lp(MATCH, WRAP, left = 12, top = 4, right = 12, bottom = 8))
        }
    }

    /** Puts the sliding pill behind the chosen tab, gliding there when `animate` is true. */
    private fun positionIndicator(animate: Boolean) {
        if (tabRow.width == 0 || tabItems.isEmpty()) return
        val cell = tabRow.width / tabItems.size.toFloat()
        val params = tabIndicator.layoutParams as FrameLayout.LayoutParams
        val wanted = (cell - dp(4)).toInt()
        if (params.width != wanted) {
            params.width = wanted
            tabIndicator.layoutParams = params
        }
        val x = tabRow.left + tab * cell + dp(2)
        if (animate) {
            tabIndicator.animate().translationX(x).setDuration(320L).setInterpolator(OvershootInterpolator(0.8f)).start()
        } else {
            tabIndicator.animate().cancel()
            tabIndicator.translationX = x
        }
    }

    private fun select(index: Int) {
        val changed = index != tab || content.childCount == 0
        tab = index
        content.removeAllViews()
        val page = screens[index].view
        content.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
        try {
            screens[index].refresh()
        } catch (e: Exception) {
            CrashLog.record(this, "opening " + screens[index].javaClass.simpleName, e)
        }
        if (changed) page.staggerIn() // the page's blocks drift in one after another
        for (i in tabIcons.indices) {
            val on = i == index
            tabIcons[i].setColorFilter(if (on) Theme.Marquee else Theme.Muted)
            tabLabels[i].setTextColor(if (on) Theme.Marquee else Theme.Muted)
            val scale = if (on) 1.15f else 1f
            tabIcons[i].animate().scaleX(scale).scaleY(scale).setDuration(220L).start()
        }
        positionIndicator(changed)
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        skipNextLock()
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_FOLDER)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (QrScanner.handleResult(requestCode, resultCode, data)) return // the QR scanner built into the app
        if (requestCode != REQUEST_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            android.widget.Toast.makeText(this, "Android did not allow access to that folder. Pick it again.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val name = browser.displayName(uri)
        AlertDialog.Builder(this)
            .setTitle("What is in \"$name\"?")
            .setItems(ShareStore.CATEGORIES.map { Theme.categoryLabel(it) }.toTypedArray()) { _, which ->
                store.add(name, ShareStore.CATEGORIES[which], uri.toString())
                folders.reload()
            }
            .show()
    }

    companion object {
        private const val REQUEST_FOLDER = 100
        private const val STATE_TAB = "tab"
    }
}
