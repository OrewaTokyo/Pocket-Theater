package com.pockettheater.ui.components

import android.app.Activity
import android.content.Intent
import com.google.zxing.integration.android.IntentIntegrator
import com.pockettheater.ui.screens.ScanActivity

/**
 * The scanner that is built into the app. It opens the phone's camera on a screen of its own, reads one QR code and
 * hands the text back. Nothing is photographed or saved, and it does not need Google Play services.
 *
 * Usage: call [scan] with a callback, and pass the activity's `onActivityResult` to [handleResult].
 */
object QrScanner {
    private var waiting: ((String?) -> Unit)? = null

    /** Opens the camera. The callback gets the text of the QR code, or null if the scan was cancelled or the camera was not allowed. */
    fun scan(activity: Activity, onResult: (String?) -> Unit) {
        waiting = onResult
        IntentIntegrator(activity)
            .setCaptureActivity(ScanActivity::class.java)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Point the camera at the QR code on the website")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .setBarcodeImageEnabled(false)
            .initiateScan()
    }

    /** Call this from the activity's onActivityResult. Returns true when the result belonged to the scanner. */
    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != IntentIntegrator.REQUEST_CODE) return false
        val callback = waiting
        waiting = null
        val text = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents
        callback?.invoke(text)
        return true
    }
}
