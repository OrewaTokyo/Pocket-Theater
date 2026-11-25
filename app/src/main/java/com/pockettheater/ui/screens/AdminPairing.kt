package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.pockettheater.core.security.AdminPassword
import com.pockettheater.core.security.Auth
import com.pockettheater.core.security.PairCode
import com.pockettheater.core.security.PinLock
import com.pockettheater.core.security.SessionStore
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.QrScanner
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.theme.Theme

/**
 * Opens the admin website on another device.
 *
 * The usual way starts on the website: its "Admin sign-in" button shows a QR code, the admin scans it here with the
 * scanner built into the app, types the admin password on this phone, and the website opens by itself ([scan]).
 * The other way starts here: pick the device in Settings, its screen shows the QR code ([start]).
 *
 * `onChanged` is called when the password was saved, and `onPaired` when a device was opened as admin.
 */
class AdminPairing(
    private val activity: Activity,
    private val adminPassword: AdminPassword,
    private val settings: AppSettings,
    private val onChanged: () -> Unit,
    private val onPaired: () -> Unit,
) {
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    /** A box that asks for one line of text. `onCancel` runs when the admin backs out. */
    private fun askText(title: String, message: String, secret: Boolean, onCancel: () -> Unit = {}, onOk: (String) -> Unit) {
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
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    fun createPassword() {
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
                    onChanged()
                }
            }
        }
    }

    fun changePassword() {
        askText("Current admin password", "Enter the current password first.", true) { current ->
            when (adminPassword.verify(current)) {
                PinLock.Result.OK -> createPassword()
                PinLock.Result.WAIT -> toast("Too many tries. Wait ${adminPassword.secondsToWait()} seconds.")
                PinLock.Result.WRONG -> toast("Wrong password. ${adminPassword.triesLeft()} tries left.")
            }
        }
    }

    /** True when the admin website can be opened right now. Otherwise says what is missing. */
    private fun ready(): Boolean = when {
        !settings.adminWebEnabled -> { toast("Turn on \"Allow the admin website\" first (Profile > Settings > Admin website)."); false }
        !adminPassword.isSet() -> { toast("Set an admin password first."); false }
        !ServerService.isRunning -> { toast("Open the theater on the Dashboard first, so the website can show its QR code."); false }
        !ServerService.isSecure -> { toast("The admin website needs HTTPS."); false }
        else -> true
    }

    // ---- starting on the website: scan its QR code ----

    /** Opens the scanner for the QR code that the website's "Admin sign-in" button shows. */
    fun scan() {
        if (ready()) openScanner()
    }

    private fun openScanner() {
        (activity as? MainActivity)?.skipNextLock() // the scanner is another screen, so coming back must not ask for the PIN
        try {
            QrScanner.scan(activity) { text ->
                if (text == null) {
                    scannerClosed()
                } else {
                    val code = PairCode.fromQr(text)
                    if (code == null) {
                        toast("That is not a Pocket Theater code.")
                        scannerClosed()
                    } else {
                        useCode(code)
                    }
                }
            }
        } catch (e: Exception) {
            toast("The scanner could not open. Type the code instead.")
            typeCode()
        }
    }

    /** The scanner closed without a code, for example because the camera was not allowed. The code can be typed instead. */
    private fun scannerClosed() {
        AlertDialog.Builder(activity)
            .setTitle("No QR code scanned")
            .setMessage("Scan the QR code on the admin website, or type the code printed under it.")
            .setPositiveButton("Scan again") { _, _ -> openScanner() }
            .setNeutralButton("Type code") { _, _ -> typeCode() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun typeCode() {
        askText("Type the code", "Enter the code shown under the QR code.", false) { typed ->
            val code = PairCode.fromTyped(typed)
            if (code == null) toast("A code has 8 letters and numbers.") else useCode(code)
        }
    }

    /** The code was read. Find the browser that shows it, tell the website it was scanned, and ask for the admin password. */
    private fun useCode(code: String) {
        val device = SessionStore.shared.deviceForCode(code)
        if (device == null) {
            toast("That code ran out or is not from this theater. On the website tap Admin sign-in for a new one.")
            return
        }
        SessionStore.shared.markScanned(device.id, code, true)
        askPassword(device, code)
    }

    /** Only the right password opens the admin website, and only on the device that shows the code. */
    private fun askPassword(device: SessionStore.Device, code: String) {
        if (!SessionStore.shared.pairingMatches(device.id, code)) {
            toast("The code ran out. On the website tap Admin sign-in for a new one.")
            return
        }
        val message = "Open the admin website on:\n${device.label}\nsigned in as ${device.user} \u00B7 ${device.ip}\n\n" +
            "Only continue if that is your own device. Enter the admin password."
        askText("Admin password", message, true, onCancel = { SessionStore.shared.markScanned(device.id, code, false) }) { password ->
            when (adminPassword.verify(password)) {
                PinLock.Result.OK ->
                    if (SessionStore.shared.completePairing(device.id, code)) {
                        toast("The admin website is opening on ${device.label}")
                        onPaired()
                    } else {
                        toast("The code ran out. On the website tap Admin sign-in for a new one.")
                    }
                PinLock.Result.WAIT -> {
                    SessionStore.shared.markScanned(device.id, code, false)
                    toast("Too many tries. Wait ${adminPassword.secondsToWait()} seconds.")
                }
                PinLock.Result.WRONG -> {
                    toast("Wrong password. ${adminPassword.triesLeft()} tries left.")
                    askPassword(device, code)
                }
            }
        }
    }

    // ---- starting on the phone: pick the device, its screen shows the QR code ----

    /** The admin picks a device in Settings. Its screen starts showing a QR code. */
    fun start(device: SessionStore.Device) {
        when {
            !ready() -> Unit
            SessionStore.shared.beginPairing(device.id) == null -> toast("That device is no longer signed in.")
            else -> pairingDialog(device)
        }
    }

    private fun pairingDialog(device: SessionStore.Device) {
        AlertDialog.Builder(activity)
            .setTitle("QR code is on ${device.label} (${device.user})")
            .setMessage("Look at that device's screen. Make sure it is your own device. Scan the QR code with this phone, or choose Type code and enter the code under it. It works for 2 minutes.")
            .setPositiveButton("Scan QR") { _, _ -> openScanner() }
            .setNeutralButton("Type code") { _, _ -> typeCode() }
            .setNegativeButton("Cancel") { _, _ -> SessionStore.shared.cancelPairing(device.id) }
            .setCancelable(false)
            .show()
    }
}
