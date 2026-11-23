package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.pockettheater.core.security.AdminPassword
import com.pockettheater.core.security.Auth
import com.pockettheater.core.security.PinLock
import com.pockettheater.core.security.SessionStore
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.service.ServerService
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.theme.Theme

/**
 * Opens the admin website on another device: pick the device, scan the QR code on its screen, type the admin password.
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

    /** Step 1: the admin picks a device. Its screen starts showing a QR code. */
    fun start(device: SessionStore.Device) {
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
        (activity as? MainActivity)?.skipNextLock() // the scanner is another screen, so coming back must not ask for the PIN
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
                        onPaired()
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
}
