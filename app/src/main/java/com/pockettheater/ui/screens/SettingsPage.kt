package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.pockettheater.R
import com.pockettheater.core.security.AdminPassword
import com.pockettheater.core.security.Auth
import com.pockettheater.core.security.SessionStore
import com.pockettheater.core.security.PinLock
import com.pockettheater.core.security.TlsIdentity
import com.pockettheater.core.server.ServerStats
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.core.storage.UserStore
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.Markdown
import com.pockettheater.ui.components.PinPad
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.iconBadgeRes
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.pillButton
import com.pockettheater.ui.components.sectionLabel
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.BatteryHelper

/** Settings sorted into categories, with a search box that finds any single setting. */
class SettingsPage(private val activity: Activity, private val pinLock: PinLock) : Screen {
    private class Category(
        val id: String,
        val icon: Int,
        val tint: Int,
        val title: String,
        val subtitle: String,
        val summary: () -> String,
        val build: (LinearLayout) -> Unit,
    )

    private class Entry(val title: String, val keywords: String, val category: String)

    private val settings = AppSettings(activity)
    private val users = UserStore(activity)
    private val adminPassword = AdminPassword(activity)
    private val deviceBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var deviceSignature = ""
    private val holder = FrameLayout(activity)
    private var openId: String? = null
    private var shownBatteryOk: Boolean? = null

    private val categories = listOf(
        Category("privacy", R.drawable.ic_shield, Theme.Cyan, "Privacy and data", "PIN lock and what is stored",
            { if (pinLock.isSet()) "PIN on" else "PIN off" }, { c -> buildPrivacy(c) }),
        Category("signups", R.drawable.ic_group, Theme.Pink, "Sign-ups and accounts", "Who can ask to join",
            { if (settings.signupsOpen) "Open" else "Closed" }, { c -> buildSignups(c) }),
        Category("admin", R.drawable.ic_build, Theme.Marquee, "Admin website", "Open admin tools on another device",
            { if (settings.adminWebEnabled) "On" else "Off" }, { c -> buildAdmin(c) }),
        Category("notifications", R.drawable.ic_bell, Theme.Marquee, "Notifications", "Sign-up request alerts",
            { if (settings.notifyRequests) "On" else "Off" }, { c -> buildNotifications(c) }),
        Category("connection", R.drawable.ic_wifi, Theme.Cyan, "Connection", "Keep the theater running",
            { if (BatteryHelper.isUnrestricted(activity)) "Allowed" else "Limited" }, { c -> buildConnection(c) }),
        Category("data", R.drawable.ic_storage, Theme.Neon, "Data and counters", "Dashboard totals",
            { "" }, { c -> buildData(c) }),
        Category("about", R.drawable.ic_info, Theme.Muted, "About", "Version and website port",
            { versionName() }, { c -> buildAbout(c) }),
    )

    private val entries = listOf(
        Entry("PIN lock", "lock pin code password security privacy", "privacy"),
        Entry("Ask for the PIN", "lock after delay minute time away", "privacy"),
        Entry("Change PIN", "lock code", "privacy"),
        Entry("Turn off PIN lock", "lock remove disable", "privacy"),
        Entry("Hide the app in recent apps", "screenshot screenshots recents blank preview secure", "privacy"),
        Entry("Where your data lives", "privacy storage local phone online account", "privacy"),
        Entry("Allow the admin website", "admin web browser laptop pair qr scan remote", "admin"),
        Entry("Admin password", "admin password pair qr", "admin"),
        Entry("Show QR on a device", "connect device qr scan pair admin list devices", "admin"),
        Entry("Allow new sign-up requests", "register registration join access people close open", "signups"),
        Entry("Sign everyone out", "log out sessions password", "signups"),
        Entry("Notify me about requests", "alerts notification sign-up", "notifications"),
        Entry("Use HTTPS", "https tls ssl secure encrypt encryption certificate warning padlock", "connection"),
        Entry("Certificate fingerprint", "https tls certificate sha fingerprint verify browser warning", "connection"),
        Entry("Make a new certificate", "https certificate renew regenerate new", "connection"),
        Entry("Run without battery limits", "battery optimisation optimization restricted background closed kill awake", "connection"),
        Entry("Restart automatically", "restart kill closed sticky reopen", "connection"),
        Entry("Hotspot and Wi-Fi tips", "hotspot wifi band 2.4 5 ghz timer charging autostart connection drops", "connection"),
        Entry("Reset data counters", "totals traffic dashboard zero clear data sent requests", "data"),
        Entry("Terms of use", "terms policy legal agreement rules", "about"),
        Entry("Privacy policy", "privacy policy legal data", "about"),
        Entry("Security policy", "security policy report vulnerability legal", "about"),
        Entry("Version", "about build app", "about"),
        Entry("Website port", "port address 8080 link", "about"),
    )

    override val view: View = holder

    init {
        showHome()
    }

    // ---- home: search and the category list ----

    private fun showHome() {
        openId = null
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(24))
        }
        column.addView(activity.textView("Saved on this phone.", 14f, Theme.Muted))

        val searchIcon = activity.getDrawable(R.drawable.ic_search)?.mutate()?.apply { setTint(Theme.Muted) }
        val search = EditText(activity).apply {
            hint = "Search settings"
            setHintTextColor(Theme.Muted)
            setTextColor(Theme.Ink)
            textSize = 16f
            setSingleLine(true)
            setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12))
            setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null)
            compoundDrawablePadding = activity.dp(10)
            background = GradientDrawable().apply {
                cornerRadius = activity.dp(16).toFloat()
                setColor(Theme.Surface)
                setStroke(activity.dp(1), Theme.Border)
            }
        }
        column.addView(search, activity.lp(MATCH, WRAP, top = 14))

        val groups = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        group(groups, "Security", listOf("privacy", "signups", "admin"))
        group(groups, "Theater", listOf("notifications", "connection", "data"))
        group(groups, "About", listOf("about"))
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        column.addView(groups)
        column.addView(results)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty().trim().lowercase()
                groups.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
                results.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                if (query.isNotEmpty()) showResults(results, query)
            }
        })

        show(ScrollView(activity).apply { addView(column) })
    }

    private fun group(into: LinearLayout, label: String, ids: List<String>) {
        into.addView(activity.sectionLabel(label), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        val rows = ids.map { id -> categoryRow(categories.first { it.id == id }) }
        into.addView(rowsCard(rows))
    }

    private fun categoryRow(cat: Category): View {
        val summary = cat.summary()
        return row(cat.title, cat.subtitle, if (summary.isEmpty()) chevron() else summaryWithChevron(summary), iconRes = cat.icon, tint = cat.tint) {
            showCategory(cat.id)
        }
    }

    private fun showResults(into: LinearLayout, query: String) {
        into.removeAllViews()
        val words = query.split(' ').filter { it.isNotEmpty() }
        val matches = entries.filter { e ->
            val cat = categories.first { it.id == e.category }
            val hay = (e.title + " " + e.keywords + " " + cat.title).lowercase()
            words.all { it in hay }
        }
        if (matches.isEmpty()) {
            into.addView(activity.textView("Nothing matches \"$query\".", 15f, Theme.Muted), activity.lp(WRAP, WRAP, top = 22))
            return
        }
        into.addView(activity.sectionLabel("Results"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        into.addView(rowsCard(matches.map { e ->
            val cat = categories.first { it.id == e.category }
            row(e.title, "In " + cat.title, chevron()) { showCategory(cat.id) }
        }))
    }

    // ---- one category ----

    private fun showCategory(id: String) {
        openId = id
        val cat = categories.first { it.id == id }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(24))
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(activity.iconBadgeRes(cat.icon, cat.tint, 56), activity.lp(activity.dp(56), activity.dp(56), right = 16))
        val names = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        names.addView(activity.textView(cat.title, 26f, Theme.Ink, bold = true))
        names.addView(activity.textView(cat.subtitle, 15f, Theme.Muted))
        header.addView(names, activity.lp(0, WRAP, 1f))
        column.addView(header, activity.lp(MATCH, WRAP, top = 6))
        cat.build(column)
        show(ScrollView(activity).apply { addView(column) })
    }

    /** Reads one of the bundled documents (the same text the website shows). */
    private fun showLegal(title: String, file: String) {
        openId = "legal"
        val text = try {
            activity.assets.open("web/legal/$file.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Could not open this document."
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(32))
        }
        Markdown.views(activity, text).forEach { column.addView(it) }
        show(ScrollView(activity).apply { addView(column) })
    }

    private fun buildPrivacy(column: LinearLayout) {
        column.addView(activity.sectionLabel("Privacy screen"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        val lockText = "Keeps the app closed to anyone who picks up your phone. It does not encrypt your folders."
        val rows = ArrayList<View>()
        if (pinLock.isSet()) {
            rows += row("PIN lock", lockText, valueText("On", Theme.Neon))
            rows += row("Ask for the PIN", "How long you can be away before the PIN is needed again.", dropdown(delayLabel()) { chooseDelay() })
            rows += row("Change PIN", "You enter the current PIN first.", activity.pillButton("Change", Theme.Ink) { changePin() })
            rows += row("Turn off PIN lock", "You enter the current PIN first.", activity.pillButton("Turn off", Theme.Red) { turnOffPin() })
        } else {
            rows += row("PIN lock", lockText, activity.pillButton("Turn on", Theme.Marquee) { choosePin() })
        }
        rows += row(
            "Hide the app in recent apps", "Shows a blank preview there and blocks screenshots.",
            switchView(settings.hideInRecents) { on ->
                settings.hideInRecents = on
                if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        )
        column.addView(rowsCard(rows))

        column.addView(activity.sectionLabel("On this phone"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Where your data lives",
                        "Accounts, scrambled passwords, shared folders and counters are kept on this phone only. Nothing is sent to an online account.",
                        valueText("This phone only", Theme.Muted)
                    )
                )
            )
        )
    }

    // ---- admin website ----

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    private fun buildAdmin(column: LinearLayout) {
        column.addView(activity.sectionLabel("Admin website"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Allow the admin website",
                        "Open admin tools in a browser on another device. You scan a QR code with this phone and enter the admin password. Needs HTTPS and the theater open. Off by default.",
                        switchView(settings.adminWebEnabled) { on -> changeAdminWeb(on) }
                    ),
                    row(
                        "Admin password",
                        if (adminPassword.isSet()) "Asked on this phone every time you open the admin website on a device."
                        else "Not set yet. You need one before you can allow the admin website.",
                        activity.pillButton(if (adminPassword.isSet()) "Change" else "Set", Theme.Marquee) {
                            if (adminPassword.isSet()) changeAdminPassword() else createAdminPassword()
                        }
                    )
                )
            )
        )
        column.addView(activity.sectionLabel("Devices on the website"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(deviceBox)
        rebuildDevices()
    }

    private fun devicesSignature(): String =
        "${ServerService.isRunning}${ServerService.isSecure}${settings.adminWebEnabled}" +
            SessionStore.shared.devices().joinToString(",") { it.id + it.admin }

    private fun rebuildDevices() {
        deviceSignature = devicesSignature()
        deviceBox.removeAllViews()
        val devices = SessionStore.shared.devices()
        val rows = ArrayList<View>()
        when {
            !ServerService.isRunning -> rows += row("The theater is closed", "Open it on the Dashboard to see who is signed in.", null)
            !ServerService.isSecure -> rows += row("HTTPS is off", "The admin website needs HTTPS. Turn it on under Connection, then close and open the theater.", null)
            devices.isEmpty() -> rows += row("No one is signed in", "Devices appear here when they sign in on the website.", null)
            else -> devices.forEach { d ->
                val since = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(d.since))
                rows += row(
                    d.label, "${d.user} \u00B7 ${d.ip} \u00B7 signed in $since",
                    if (d.admin) activity.pillButton("End admin", Theme.Red) {
                        SessionStore.shared.revokeAdmin(d.id)
                        rebuildDevices()
                    } else activity.pillButton("Show QR", Theme.Marquee) { startPairing(d) }
                )
            }
        }
        deviceBox.addView(rowsCard(rows))
    }

    private fun changeAdminWeb(on: Boolean) {
        when {
            on && !adminPassword.isSet() -> toast("Set an admin password first.")
            on && !settings.useHttps -> toast("Turn on HTTPS first, under Connection.")
            else -> {
                settings.adminWebEnabled = on
                if (!on) SessionStore.shared.devices().filter { it.admin }.forEach { SessionStore.shared.revokeAdmin(it.id) }
            }
        }
        showCategory("admin")
    }

    /** A box that asks for one line of text. */
    private fun askText(title: String, message: String, secret: Boolean, onOk: (String) -> Unit) {
        val input = EditText(activity).apply {
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setTextColor(Theme.Ink)
        }
        val frame = FrameLayout(activity).apply {
            setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(activity).setTitle(title).setMessage(message).setView(frame)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createAdminPassword() {
        askText("Admin password", "Choose a password of 8 to 64 characters. You type it on this phone, never on the website.", true) { first ->
            val problem = Auth.passwordProblem(first)
            if (problem != null) {
                toast(problem)
            } else {
                askText("Repeat the password", "Type it once more.", true) { again ->
                    if (again == first) {
                        adminPassword.set(first)
                        toast("Admin password saved")
                    } else {
                        toast("The two passwords are not the same.")
                    }
                    showCategory("admin")
                }
            }
        }
    }

    private fun changeAdminPassword() {
        askText("Current admin password", "Enter the current password first.", true) { current ->
            when (adminPassword.verify(current)) {
                PinLock.Result.OK -> createAdminPassword()
                PinLock.Result.WAIT -> toast("Too many tries. Wait ${adminPassword.secondsToWait()} seconds.")
                PinLock.Result.WRONG -> toast("Wrong password. ${adminPassword.triesLeft()} tries left.")
            }
        }
    }

    /** Step 1: the admin picks a device. Its screen starts showing a QR code. */
    private fun startPairing(device: SessionStore.Device) {
        when {
            !settings.adminWebEnabled -> toast("Turn on \"Allow the admin website\" first.")
            !adminPassword.isSet() -> toast("Set an admin password first.")
            !ServerService.isSecure -> toast("The admin website needs HTTPS.")
            SessionStore.shared.beginPairing(device.id) == null -> toast("That device is no longer signed in.")
            else -> pairingDialog(device)
        }
    }

    /** Step 2: scan the QR code on that device, or type the code under it. */
    private fun pairingDialog(device: SessionStore.Device) {
        AlertDialog.Builder(activity)
            .setTitle("QR code is on ${device.label} (${device.user})")
            .setMessage("Look at that device's screen. Make sure it is your own device. Scan the QR code with this phone, or choose Type code and enter the code under it. It works for 2 minutes.")
            .setPositiveButton("Scan QR") { _, _ -> scanQr(device) }
            .setNeutralButton("Type code") { _, _ ->
                askText("Type the code", "Enter the code shown under the QR code.", false) { checkCode(device, it) }
            }
            .setNegativeButton("Cancel") { _, _ -> SessionStore.shared.cancelPairing(device.id) }
            .setCancelable(false)
            .show()
    }

    private fun scanQr(device: SessionStore.Device) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(activity, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue.orEmpty()
                if (raw.startsWith("PT:")) {
                    checkCode(device, raw.removePrefix("PT:"))
                } else {
                    toast("That is not a Pocket Theater code.")
                    pairingDialog(device)
                }
            }
            .addOnCanceledListener { pairingDialog(device) }
            .addOnFailureListener { e ->
                toast("Scanning is not available (${e.message ?: "error"}). Type the code instead.")
                pairingDialog(device)
            }
    }

    /** Step 3: the code is right, so ask for the admin password. Only then does that device get the admin website. */
    private fun checkCode(device: SessionStore.Device, typed: String) {
        val code = typed.trim().replace("-", "").uppercase()
        if (!SessionStore.shared.pairingMatches(device.id, code)) {
            toast("That code is not for this device, or it ran out. Tap Show QR again.")
            return
        }
        askText("Admin password", "Enter the admin password to open the admin website on ${device.label}.", true) { password ->
            when (adminPassword.verify(password)) {
                PinLock.Result.OK ->
                    if (SessionStore.shared.completePairing(device.id, code)) {
                        toast("The admin website is opening on ${device.label}")
                        rebuildDevices()
                    } else {
                        toast("The code ran out. Tap Show QR again.")
                    }
                PinLock.Result.WAIT -> toast("Too many tries. Wait ${adminPassword.secondsToWait()} seconds.")
                PinLock.Result.WRONG -> {
                    toast("Wrong password. ${adminPassword.triesLeft()} tries left.")
                    checkCode(device, code)
                }
            }
        }
    }

    private fun buildSignups(column: LinearLayout) {
        column.addView(activity.sectionLabel("Sign-ups"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Allow new sign-up requests", "Turn off to stop people asking for access. Existing accounts keep working.",
                        switchView(settings.signupsOpen) { settings.signupsOpen = it }
                    )
                )
            )
        )
        column.addView(activity.sectionLabel("Accounts"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Sign everyone out", "Everyone has to sign in again with their password.",
                        activity.pillButton("Sign out", Theme.Marquee) { confirmSignOutAll() }
                    )
                )
            )
        )
    }

    private fun buildNotifications(column: LinearLayout) {
        column.addView(activity.sectionLabel("Sign-up requests"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Notify me about requests", "A phone notification when someone asks to join. Requests still wait in Admin tools.",
                        switchView(settings.notifyRequests) { settings.notifyRequests = it }
                    )
                )
            )
        )
    }

    private fun buildConnection(column: LinearLayout) {
        val unrestricted = BatteryHelper.isUnrestricted(activity)
        shownBatteryOk = unrestricted
        val fingerprint = try { TlsIdentity.fingerprint() } catch (e: Exception) { null }
        column.addView(activity.sectionLabel("Secure connection"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Use HTTPS",
                        "Encrypts everything between your devices and the phone. The browser warns once, because the phone makes its own certificate. Applies the next time you open the theater.",
                        switchView(settings.useHttps) { settings.useHttps = it }
                    ),
                    row(
                        "Certificate fingerprint",
                        if (fingerprint != null) "Compare it with the one your browser shows before you continue.\n$fingerprint"
                        else "The certificate could not be read on this phone.",
                        if (fingerprint != null) activity.pillButton("Copy", Theme.Ink) { copyText("Fingerprint", fingerprint) } else null
                    ),
                    row(
                        "Make a new certificate",
                        "Use this if you think someone copied the old one. Browsers will warn again.",
                        activity.pillButton("Renew", Theme.Red) { confirmRenewCertificate() }
                    )
                )
            )
        )
        column.addView(activity.sectionLabel("Staying awake"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Run without battery limits",
                        "Stops Android from closing the theater to save battery. Recommended while people are watching.",
                        if (unrestricted) valueText("Allowed", Theme.Neon)
                        else activity.pillButton("Allow", Theme.Marquee) { BatteryHelper.ask(activity) }
                    ),
                    row(
                        "Restart automatically",
                        "If Android closes the app while the theater is open, it opens the theater again by itself. Applies the next time you open the theater.",
                        switchView(settings.autoRestart) { settings.autoRestart = it }
                    )
                )
            )
        )
        column.addView(activity.sectionLabel("Hotspot and Wi-Fi tips"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row("Keep the hotspot on", "In your phone's hotspot settings, turn off the option that switches the hotspot off when no device is connected.", null),
                    row("Pick the band", "2.4 GHz reaches further and drops less. 5 GHz is faster but needs the laptop close to the phone.", null),
                    row("Keep it charging", "Streaming over a hotspot drains the battery fast. Plug the phone in for long sessions.", null),
                    row(
                        "Phone-specific switches",
                        "Some phones (Xiaomi, Oppo, Vivo, Samsung) also have Autostart or Background activity switches in the app's settings. Turn them on.",
                        activity.pillButton("App settings", Theme.Ink) { BatteryHelper.openAppSettings(activity) }
                    )
                )
            )
        )
    }

    private fun buildData(column: LinearLayout) {
        column.addView(activity.sectionLabel("Dashboard totals"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row(
                        "Reset data counters", "Sets total data sent, requests and awake time back to zero.",
                        activity.pillButton("Reset", Theme.Red) { confirmResetCounters() }
                    )
                )
            )
        )
    }

    private fun buildAbout(column: LinearLayout) {
        column.addView(activity.sectionLabel("Pocket Theater"), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
        column.addView(
            rowsCard(
                listOf(
                    row("Version", "This build of the app.", valueText(versionName(), Theme.Muted)),
                    row("Terms of use", "The rules for using Pocket Theater.", chevron()) { showLegal("Terms of Use", "terms") },
                    row("Privacy policy", "What is stored, what is seen and what leaves the phone.", chevron()) { showLegal("Privacy Policy", "privacy") },
                    row("Security policy", "How it is protected, the known limits and how to report a problem.", chevron()) { showLegal("Security Policy", "security") },
                    row(
                        "Website port",
                        "The link on the Dashboard ends with this number." + if (settings.useHttps) " The old port ${ServerService.PORT} sends visitors here." else "",
                        valueText((if (settings.useHttps) ServerService.HTTPS_PORT else ServerService.PORT).toString(), Theme.Muted)
                    )
                )
            )
        )
    }

    // ---- PIN steps ----

    private fun pinDialog(title: String, prompt: String, onPin: (String, PinPad, () -> Unit) -> Unit) {
        var dialog: AlertDialog? = null
        lateinit var pad: PinPad
        pad = PinPad(activity) { pin -> onPin(pin, pad) { dialog?.dismiss() } }
        pad.setMessage(prompt)
        val frame = FrameLayout(activity).apply {
            setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8))
            addView(pad)
        }
        val d = AlertDialog.Builder(activity).setTitle(title).setView(frame).setNegativeButton("Cancel", null).create()
        dialog = d
        d.show()
        d.window?.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = activity.dp(24).toFloat()
            setColor(Theme.Midnight)
        })
    }

    private fun choosePin(): Unit = pinDialog("Choose a PIN", "Enter ${PinLock.MIN_DIGITS} to ${PinLock.MAX_DIGITS} digits.") { first, _, close ->
        close()
        confirmPin(first)
    }

    private fun confirmPin(first: String): Unit = pinDialog("Confirm your PIN", "Enter the same PIN again.") { again, pad, close ->
        if (again == first) {
            pinLock.set(first)
            close()
            Toast.makeText(activity, "PIN lock is on", Toast.LENGTH_SHORT).show()
            showCategory("privacy")
        } else {
            pad.clear()
            pad.setMessage("The PINs do not match. Try again.", true)
        }
    }

    private fun verifyCurrent(then: () -> Unit): Unit = pinDialog("Enter your PIN", "Enter your current PIN.") { pin, pad, close ->
        when (pinLock.verify(pin)) {
            PinLock.Result.OK -> {
                close()
                then()
            }
            PinLock.Result.WAIT -> {
                pad.clear()
                pad.setMessage("Too many tries. Wait ${pinLock.secondsToWait()} seconds.", true)
            }
            PinLock.Result.WRONG -> {
                pad.clear()
                val wait = pinLock.secondsToWait()
                pad.setMessage(if (wait > 0) "Too many tries. Wait $wait seconds." else "Wrong PIN. ${pinLock.triesLeft()} tries left.", true)
            }
        }
    }

    private fun changePin(): Unit = verifyCurrent { choosePin() }

    private fun turnOffPin(): Unit = verifyCurrent {
        pinLock.clear()
        Toast.makeText(activity, "PIN lock is off", Toast.LENGTH_SHORT).show()
        showCategory("privacy")
    }

    private fun chooseDelay() {
        val labels = arrayOf("Every time I leave", "After 1 minute", "After 5 minutes", "After 15 minutes")
        val seconds = intArrayOf(0, 60, 300, 900)
        AlertDialog.Builder(activity)
            .setTitle("Ask for the PIN")
            .setItems(labels) { _, which ->
                settings.lockAfterSeconds = seconds[which]
                showCategory("privacy")
            }
            .show()
    }

    private fun delayLabel(): String = when (settings.lockAfterSeconds) {
        0 -> "Every time I leave"
        60 -> "After 1 minute"
        300 -> "After 5 minutes"
        900 -> "After 15 minutes"
        else -> "After ${settings.lockAfterSeconds} seconds"
    }

    private fun copyText(label: String, text: String) {
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun confirmRenewCertificate() {
        AlertDialog.Builder(activity)
            .setTitle("Make a new certificate?")
            .setMessage("Browsers will warn again the next time they connect, and you will compare the new fingerprint. Close and open the theater to start using it.")
            .setPositiveButton("Make new") { _, _ ->
                try {
                    TlsIdentity.renew()
                    Toast.makeText(activity, "New certificate made", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(activity, "Could not make a new certificate", Toast.LENGTH_LONG).show()
                }
                showCategory("connection")
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    // ---- small building blocks ----

    private fun show(content: View) {
        holder.removeAllViews()
        holder.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /** A card whose rows are separated by thin lines. */
    private fun rowsCard(rows: List<View>): LinearLayout {
        val card = activity.card(0)
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                val line = View(activity).apply { setBackgroundColor(Theme.Border) }
                card.addView(line, activity.lp(MATCH, 1))
            }
            card.addView(row, activity.lp(MATCH, WRAP))
        }
        return card
    }

    /** One setting: title and explanation on the left, a value, switch or button on the right. */
    private fun row(
        title: String,
        subtitle: String,
        right: View?,
        iconRes: Int = 0,
        tint: Int = 0,
        onClick: (() -> Unit)? = null,
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        if (iconRes != 0) row.addView(activity.iconBadgeRes(iconRes, tint, 44), activity.lp(activity.dp(44), activity.dp(44), right = 14))
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(activity.textView(title, 17f, Theme.Ink, bold = true))
        texts.addView(activity.textView(subtitle, 14f, Theme.Muted))
        row.addView(texts, activity.lp(0, WRAP, 1f, right = if (right != null) 12 else 0))
        if (right != null) row.addView(right)
        return row
    }

    private fun valueText(text: String, color: Int): TextView = activity.textView(text, 15f, color, bold = true)

    private fun chevron(): TextView = activity.textView("\u203A", 28f, Theme.Muted)

    private fun summaryWithChevron(summary: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(activity.textView(summary, 15f, Theme.Muted), activity.lp(WRAP, WRAP, right = 10))
        addView(chevron())
    }

    private fun dropdown(label: String, onClick: () -> Unit): TextView =
        activity.textView(label + "  \u25BE", 15f, Theme.Ink, bold = true).apply {
            setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10))
            background = GradientDrawable().apply {
                cornerRadius = activity.dp(12).toFloat()
                setColor(Theme.Midnight)
                setStroke(activity.dp(1), Theme.Border)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun switchView(on: Boolean, onChange: (Boolean) -> Unit): Switch {
        val switch = Switch(activity)
        switch.isChecked = on
        switch.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        return switch
    }

    override fun refresh() {
        if (openId == "admin" && deviceSignature != devicesSignature()) rebuildDevices()
        // After the person comes back from Android's battery question, show the new answer.
        if (openId == "connection") {
            val now = BatteryHelper.isUnrestricted(activity)
            if (shownBatteryOk != null && shownBatteryOk != now) showCategory("connection")
        }
    }

    override fun onBack(): Boolean {
        if (openId == null) return false
        if (openId == "legal") showCategory("about") else showHome()
        return true
    }
}
