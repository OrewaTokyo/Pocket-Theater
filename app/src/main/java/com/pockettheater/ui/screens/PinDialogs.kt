package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.Toast
import com.pockettheater.core.security.PinLock
import com.pockettheater.ui.components.PinPad
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.theme.Theme

/** The steps for choosing, changing and removing the PIN. `onChanged` is called after the PIN lock was turned on or off. */
class PinDialogs(
    private val activity: Activity,
    private val pinLock: PinLock,
    private val onChanged: () -> Unit,
) {
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

    fun choose(): Unit = pinDialog("Choose a PIN", "Enter ${PinLock.MIN_DIGITS} to ${PinLock.MAX_DIGITS} digits.") { first, _, close ->
        close()
        confirmPin(first)
    }

    private fun confirmPin(first: String): Unit = pinDialog("Confirm your PIN", "Enter the same PIN again.") { again, pad, close ->
        if (again == first) {
            pinLock.set(first)
            close()
            Toast.makeText(activity, "PIN lock is on", Toast.LENGTH_SHORT).show()
            onChanged()
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

    fun change(): Unit = verifyCurrent { choose() }

    fun turnOff(): Unit = verifyCurrent {
        pinLock.clear()
        Toast.makeText(activity, "PIN lock is off", Toast.LENGTH_SHORT).show()
        onChanged()
    }
}
