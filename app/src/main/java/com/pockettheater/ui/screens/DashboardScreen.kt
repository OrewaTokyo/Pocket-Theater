package com.pockettheater.ui.screens

import android.app.Activity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pockettheater.R
import com.pockettheater.core.security.TlsIdentity
import com.pockettheater.core.server.ServerStats
import com.pockettheater.core.storage.UserStore
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.BarChart
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.RingGauge
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.glassDrawable
import com.pockettheater.ui.components.iconBadgeRes
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.sectionLabel
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.BatteryHelper
import com.pockettheater.util.DeviceStats
import com.pockettheater.util.Format
import com.pockettheater.util.NetworkUtil
import com.pockettheater.util.QrImage

/** Opens and closes the theater, shows the website link, and shows how the server is doing. */
class DashboardScreen(private val activity: Activity) : Screen {
    private val users = UserStore(activity)
    private val device = DeviceStats(activity)
    private var link: String? = null
    private var addressesAt = 0L
    private var cachedAddresses: List<NetworkUtil.Address> = emptyList()
    private var batteryCheckedAt = 0L
    private var batteryUnrestricted = true

    private val statusDot = View(activity)
    private var pulse: ObjectAnimator? = null
    private val statusText = activity.textView("", 13f, Theme.Muted, bold = true).apply { letterSpacing = 0.12f }
    private val linkView = activity.textView("", 20f, Theme.Marquee, bold = true).apply { setTextIsSelectable(true) }
    private val errorView = activity.textView("", 14f, Theme.Red)
    private val warningView = activity.textView("", 14f, Theme.Marquee)
    private val linkHint = activity.textView("Website link", 13f, Theme.Muted)
    private val otherAddresses = activity.textView("", 13f, Theme.Muted).apply { setTextIsSelectable(true) }
    private val batteryTip = activity.card(16)
    private val toggle = activity.actionButton("Open theater", filled = true) {
        val intent = Intent(activity, ServerService::class.java)
        when {
            ServerService.isStarting -> Unit // already on its way up
            ServerService.isRunning -> activity.stopService(intent)
            else -> activity.startForegroundService(intent)
        }
    }

    private val wakeBig = bigNumber()
    private val wakeSub = subText()
    private val usersBig = bigNumber()
    private val usersSub = subText()
    private val dataBig = bigNumber()
    private val dataSub = subText()
    private val requestsBig = bigNumber()
    private val requestsSub = subText()

    private val cpuGauge = RingGauge(activity)
    private val cpuSub = subText(center = true)
    private val ramGauge = RingGauge(activity)
    private val ramSub = subText(center = true)
    private val batteryGauge = RingGauge(activity)
    private val batterySub = subText(center = true)

    private val chart = BarChart(activity)
    private val peakText = activity.textView("", 13f, Theme.Muted)

    override val view: View = build()

    private fun bigNumber(): TextView = activity.textView("-", 30f, Theme.Ink, light = true)
    private fun subText(center: Boolean = false): TextView =
        activity.textView("", 13f, Theme.Muted).apply { if (center) gravity = Gravity.CENTER }

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(24))
        }

        // Title and status
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(activity.textView("Dashboard", 30f, Theme.Ink, light = true), activity.lp(0, WRAP, 1f))
        statusDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.Muted) }
        statusDot.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            // no point pulsing while this page is not on screen
            override fun onViewAttachedToWindow(v: View) { pulse?.resume() }
            override fun onViewDetachedFromWindow(v: View) { pulse?.pause() }
        })
        val chip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(12), activity.dp(7), activity.dp(14), activity.dp(7))
            background = activity.glassDrawable(16)
        }
        chip.addView(statusDot, activity.lp(activity.dp(10), activity.dp(10), right = 8))
        chip.addView(statusText)
        header.addView(chip)
        column.addView(header)

        // Website link and the open/close button
        val hero = activity.card(18)
        hero.addView(linkHint)
        hero.addView(linkView, activity.lp(MATCH, WRAP, top = 4))
        hero.addView(otherAddresses, activity.lp(MATCH, WRAP, top = 4))
        hero.addView(errorView)
        hero.addView(warningView, activity.lp(MATCH, WRAP, top = 6))
        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            activity.actionButton("Copy link", filled = false) { link?.let { copy(it) } },
            activity.lp(0, WRAP, 1f, right = 6)
        )
        buttons.addView(
            activity.actionButton("QR code", filled = false) { showQr() },
            activity.lp(0, WRAP, 1f, left = 6)
        )
        hero.addView(buttons, activity.lp(MATCH, WRAP, top = 14))
        hero.addView(toggle, activity.lp(MATCH, WRAP, top = 10))
        column.addView(hero, activity.lp(MATCH, WRAP, top = 18))

        // Shown only while the theater is open and Android may still close it to save battery
        batteryTip.addView(activity.textView("Keep the theater awake", 16f, Theme.Marquee, bold = true))
        batteryTip.addView(
            activity.textView("Android may close the theater to save battery, which drops everyone's connection.", 14f, Theme.Muted),
            activity.lp(MATCH, WRAP, top = 2)
        )
        batteryTip.addView(
            activity.actionButton("Allow no battery limits", filled = false) { BatteryHelper.ask(activity) },
            activity.lp(MATCH, WRAP, top = 10)
        )
        batteryTip.visibility = View.GONE
        column.addView(batteryTip, activity.lp(MATCH, WRAP, top = 14))

        // Four numbers
        column.addView(
            pair(
                statCard(R.drawable.ic_stat_clock, Theme.Cyan, "WAKE TIME", wakeBig, wakeSub),
                statCard(R.drawable.ic_group, Theme.Pink, "USERS", usersBig, usersSub)
            ),
            activity.lp(MATCH, WRAP, top = 14)
        )
        column.addView(
            pair(
                statCard(R.drawable.ic_stat_upload, Theme.Neon, "DATA SENT", dataBig, dataSub),
                statCard(R.drawable.ic_stat_globe, Theme.Marquee, "REQUESTS", requestsBig, requestsSub)
            ),
            activity.lp(MATCH, WRAP, top = 12)
        )

        // Server usage
        column.addView(activity.sectionLabel("Server usage"), activity.lp(WRAP, WRAP, top = 26, bottom = 10))
        val gauges = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        gauges.addView(gaugeCard(cpuGauge, "CPU", cpuSub), activity.lp(0, WRAP, 1f, right = 5))
        gauges.addView(gaugeCard(ramGauge, "RAM", ramSub), activity.lp(0, WRAP, 1f, left = 5, right = 5))
        gauges.addView(gaugeCard(batteryGauge, "BATTERY", batterySub), activity.lp(0, WRAP, 1f, left = 5))
        column.addView(gauges)

        // Site traffic
        column.addView(activity.sectionLabel("Site traffic"), activity.lp(WRAP, WRAP, top = 26, bottom = 10))
        val traffic = activity.card(16)
        traffic.addView(activity.textView("Data sent per hour", 15f, Theme.Ink, bold = true))
        traffic.addView(peakText)
        traffic.addView(chart, activity.lp(MATCH, activity.dp(110), top = 14))
        val axis = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        axis.addView(activity.textView("12h ago", 12f, Theme.Muted), activity.lp(0, WRAP, 1f))
        axis.addView(activity.textView("now", 12f, Theme.Marquee, bold = true))
        traffic.addView(axis, activity.lp(MATCH, WRAP, top = 6))
        column.addView(traffic)

        return ScrollView(activity).apply { addView(column) }
    }

    private fun statCard(icon: Int, tint: Int, caption: String, big: TextView, sub: TextView): View {
        val c = activity.card(16)
        c.addView(activity.iconBadgeRes(icon, tint, 42))
        c.addView(big, activity.lp(WRAP, WRAP, top = 12))
        c.addView(activity.textView(caption, 12f, Theme.Muted, bold = true).apply { letterSpacing = 0.12f })
        c.addView(sub, activity.lp(WRAP, WRAP, top = 2))
        return c
    }

    private fun pair(a: View, b: View): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, activity.lp(0, MATCH, 1f, right = 6))
        addView(b, activity.lp(0, MATCH, 1f, left = 6))
    }

    private fun gaugeCard(gauge: RingGauge, name: String, sub: TextView): View {
        val c = activity.card(12)
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(gauge, activity.lp(MATCH, WRAP))
        c.addView(activity.textView(name, 13f, Theme.Ink, bold = true).apply { letterSpacing = 0.1f }, activity.lp(WRAP, WRAP, top = 8))
        c.addView(sub, activity.lp(MATCH, WRAP))
        return c
    }

    /** A QR code for the link, so a tablet or second phone can open the website by scanning it. */
    private fun showQr() {
        val address = link
        if (address == null) {
            Toast.makeText(activity, "Open the theater first", Toast.LENGTH_SHORT).show()
            return
        }
        val picture = QrImage.bitmap(address, activity.dp(280))
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(activity.dp(20), activity.dp(8), activity.dp(20), activity.dp(8))
        }
        body.addView(ImageView(activity).apply { setImageBitmap(picture); contentDescription = "QR code for the website link" }, activity.lp(picture.width, picture.height))
        body.addView(
            activity.textView(address, 16f, Theme.Marquee, bold = true).apply { gravity = Gravity.CENTER; setTextIsSelectable(true) },
            activity.lp(MATCH, WRAP, top = 12)
        )
        val note = if (ServerService.isSecure) {
            val fingerprint = try { TlsIdentity.fingerprint() } catch (e: Exception) { "" }
            "Scan it with a phone or tablet on the same Wi-Fi or hotspot. The first time, the browser warns about the certificate, because this phone made it. " +
                "Check that the fingerprint it shows matches this one, then continue.\n\n" + fingerprint
        } else {
            "Scan it with a phone or tablet on the same Wi-Fi or hotspot. This link is not encrypted."
        }
        body.addView(
            activity.textView(note, 13f, Theme.Muted).apply { gravity = Gravity.CENTER; setTextIsSelectable(true) },
            activity.lp(MATCH, WRAP, top = 10)
        )
        val dialog = AlertDialog.Builder(activity).setTitle("Scan to open").setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Done", null).create()
        dialog.show()
    }

    private fun copy(text: String) {
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pocket Theater link", text))
        Toast.makeText(activity, "Link copied", Toast.LENGTH_SHORT).show()
    }

    /** The little dot beats slowly while the theater is open, like a heartbeat. */
    private fun setPulsing(on: Boolean) {
        if (on) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.3f).apply {
                    duration = 900L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }
        } else if (pulse != null) {
            pulse?.cancel()
            pulse = null
            statusDot.alpha = 1f
        }
    }

    override fun refresh() {
        val open = ServerService.isRunning
        val error = ServerService.lastError

        val starting = ServerService.isStarting
        statusText.text = if (starting) "STARTING" else if (open) "OPEN" else "CLOSED"
        statusText.setTextColor(if (open) Theme.Neon else Theme.Muted)
        (statusDot.background as GradientDrawable).setColor(if (open) Theme.Neon else Theme.Muted)
        setPulsing(open && !starting)

        val now = SystemClock.elapsedRealtime()
        if (now - addressesAt > 4_000L) {
            cachedAddresses = NetworkUtil.addresses()
            addressesAt = now
        }
        if (now - batteryCheckedAt > 4_000L) {
            batteryUnrestricted = BatteryHelper.isUnrestricted(activity)
            batteryCheckedAt = now
        }
        val addresses = cachedAddresses
        val best = addresses.firstOrNull()
        val address = if (open && best != null) ServerService.url(best.ip) else null
        link = address
        linkHint.text = when {
            !open || best == null -> "Website link"
            else -> (if (ServerService.isSecure) "Secure link (HTTPS)" else "Link (not encrypted)") +
                " \u00B7 " + (if (best.kind == "Hotspot") "open it on a device joined to this hotspot" else "open it on a device on the same ${best.kind}")
        }
        linkView.text = when {
            !open -> "Open the theater to get your link"
            address == null -> "No Wi-Fi or hotspot found. Connect to Wi-Fi or turn on the hotspot."
            else -> address
        }
        val others = if (open) addresses.drop(1) else emptyList()
        otherAddresses.text = if (others.isEmpty()) "" else
            "Also reachable at: " + others.joinToString("   ") { ServerService.url(it.ip) + " (${it.kind})" }
        otherAddresses.visibility = if (others.isEmpty()) View.GONE else View.VISIBLE
        batteryTip.visibility = if (open && !batteryUnrestricted) View.VISIBLE else View.GONE
        warningView.text = if (open) ServerService.lastWarning.orEmpty() else ""
        warningView.visibility = if (warningView.text.isEmpty()) View.GONE else View.VISIBLE
        errorView.text = if (!open && error != null) "Could not open: $error" else ""
        errorView.visibility = if (errorView.text.isEmpty()) View.GONE else View.VISIBLE
        toggle.text = if (starting) "Starting\u2026" else if (open) "Close theater" else "Open theater"

        val stats = ServerStats.snapshot(activity)
        wakeBig.text = if (open) Format.duration(stats.awakeMs) else "Asleep"
        wakeSub.text = "Total awake " + Format.duration(stats.lifetimeAwakeMs)
        val members = users.members().size
        usersBig.text = members.toString()
        usersSub.text = "${stats.online} online \u00B7 ${users.pending().size} waiting"
        dataBig.text = Format.bytes(stats.lifetimeBytes)
        dataSub.text = "This session " + Format.bytes(stats.sessionBytes)
        requestsBig.text = Format.count(stats.lifetimeRequests)
        requestsSub.text = "This session " + Format.count(stats.sessionRequests)

        val reading = device.read()
        cpuGauge.set(reading.cpuPercent, "${reading.cpuPercent.toInt()}%", Theme.Cyan)
        cpuSub.text = "This app only"
        ramGauge.set(reading.ramPercent, "${reading.ramPercent.toInt()}%", if (reading.ramPercent > 90f) Theme.Red else Theme.Neon)
        ramSub.text = "App uses ${reading.appRamMb} MB"
        val low = reading.batteryPercent <= 20
        batteryGauge.set(
            reading.batteryPercent.toFloat(), "${reading.batteryPercent}%",
            if (low && !reading.charging) Theme.Red else if (reading.batteryPercent <= 40 && !reading.charging) Theme.Marquee else Theme.Neon
        )
        batterySub.text = (if (reading.charging) "Charging" else "On battery") + " \u00B7 " + reading.tempC.toInt() + "\u00B0C"

        chart.values = stats.bytesPerHour
        var peak = 0L
        for (v in stats.bytesPerHour) if (v > peak) peak = v
        peakText.text = if (peak > 0L) "Busiest hour: " + Format.bytes(peak) else "No traffic yet since the app started"
    }
}
