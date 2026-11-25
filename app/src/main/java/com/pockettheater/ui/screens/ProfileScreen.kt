package com.pockettheater.ui.screens

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pockettheater.R
import com.pockettheater.core.security.AdminPassword
import com.pockettheater.core.security.PinLock
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.core.storage.UserStore
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.detach
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.iconBadgeRes
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.slideIn
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme

/** Who you are on this phone, with Admin tools and Settings one tap away. */
class ProfileScreen(private val activity: Activity, pinLock: PinLock) : Screen {
    private val users = UserStore(activity)
    private val admin = AdminToolsPage(activity)
    private val settings = SettingsPage(activity, pinLock)
    private val pairing = AdminPairing(activity, AdminPassword(activity), AppSettings(activity), {}, {})
    private val holder = FrameLayout(activity)
    private val waitingText = activity.textView("", 14f, Theme.Muted)
    private var page: Screen? = null

    override val view: View = holder

    init {
        showHome()
    }

    fun openAdminTools() = open("Admin tools", admin)

    private fun open(title: String, body: Screen) {
        page = body
        val framed = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(12), activity.dp(14), activity.dp(20), activity.dp(8))
        }
        val back = activity.textView("\u2190", 26f, Theme.Marquee, bold = true).apply {
            setPadding(activity.dp(8), 0, activity.dp(16), 0)
            isClickable = true
            setOnClickListener { if (page?.onBack() != true) showHome() }
        }
        bar.addView(back)
        bar.addView(activity.textView(title, 24f, Theme.Ink, bold = true))
        framed.addView(bar)
        (body.view.parent as? ViewGroup)?.removeView(body.view) // it may still sit in the previous frame
        framed.addView(body.view, activity.lp(MATCH, 0, 1f))
        show(framed)
        framed.slideIn() // the page opened from the list glides in from the side
    }

    private fun showHome() {
        page = null
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(24))
        }
        column.addView(activity.textView("Profile", 30f, Theme.Ink, bold = true))

        val me = activity.card(18)
        val meRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val avatar = activity.textView("\uD83C\uDFAD", 30f, Theme.Ink).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x55FFC65C, 0x55FF5FA2)).apply {
                shape = GradientDrawable.OVAL
                setStroke(activity.dp(2), 0x99FFC65C.toInt())
            }
        }
        meRow.addView(avatar, activity.lp(activity.dp(64), activity.dp(64), right = 16))
        val names = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        names.addView(activity.textView("Admin", 22f, Theme.Ink, bold = true))
        names.addView(activity.textView("Owner of this Pocket Theater", 14f, Theme.Muted))
        meRow.addView(names)
        me.addView(meRow)
        column.addView(me, activity.lp(MATCH, WRAP, top = 18))

        column.addView(menuRow(R.drawable.ic_build, Theme.Marquee, "Admin tools", waitingText) { open("Admin tools", admin) }, activity.lp(MATCH, WRAP, top = 16))
        column.addView(
            menuRow(R.drawable.ic_qr_scan, Theme.Cyan, "Scan admin QR", activity.textView("Open the admin website on a laptop", 14f, Theme.Muted)) { pairing.scan() },
            activity.lp(MATCH, WRAP, top = 12)
        )
        column.addView(menuRow(R.drawable.ic_settings, Theme.Cyan, "Settings", null) { open("Settings", settings) }, activity.lp(MATCH, WRAP, top = 12))

        show(ScrollView(activity).apply { addView(column) })
        refreshHome()
    }

    private fun menuRow(icon: Int, tint: Int, title: String, subtitle: TextView?, onClick: () -> Unit): View {
        val row = activity.card(16)
        row.isClickable = true
        row.setOnClickListener { onClick() }
        val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(activity.iconBadgeRes(icon, tint, 44), activity.lp(activity.dp(44), activity.dp(44), right = 14))
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(activity.textView(title, 18f, Theme.Ink, bold = true))
        if (subtitle != null) texts.addView(subtitle.detach()) // the same text is reused each time the home page is drawn
        line.addView(texts, activity.lp(0, WRAP, 1f))
        line.addView(activity.textView("\u203A", 28f, Theme.Muted))
        row.addView(line)
        return row
    }

    private fun show(content: View) {
        holder.removeAllViews()
        holder.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun refreshHome() {
        val waiting = users.pending().size
        waitingText.text = when (waiting) {
            0 -> "Requests, accounts and shared folders"
            1 -> "1 person is waiting for approval"
            else -> "$waiting people are waiting for approval"
        }
        waitingText.setTextColor(if (waiting > 0) Theme.Marquee else Theme.Muted)
    }

    override fun refresh() {
        val open = page
        if (open != null) open.refresh() else refreshHome()
    }

    override fun onBack(): Boolean {
        val open = page ?: return false
        if (!open.onBack()) showHome()
        return true
    }
}
