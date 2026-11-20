package com.pockettheater.core.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * Asks Android to check it is really you: fingerprint, face, or the phone's own PIN, pattern or password.
 * The app stores no PIN of its own, so there is nothing here to leak or forget.
 */
class AppLock(private val activity: Activity) {
    private val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private var signal: CancellationSignal? = null

    /** False when the phone has no screen lock at all, in which case the app cannot be locked. */
    fun available(): Boolean {
        val manager = activity.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Shows the system unlock sheet. `onResult(true)` only when the person really passed it. */
    fun authenticate(title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        signal?.cancel()
        val fresh = CancellationSignal()
        signal = fresh
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(fresh, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        })
    }
}
