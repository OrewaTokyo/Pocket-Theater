package com.pockettheater.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.pockettheater.core.security.PinLock
import com.pockettheater.ui.theme.Theme

/** A message, a row of dots and a number pad. Calls `onSubmit` with the digits when OK is tapped. */
class PinPad(context: Context, private val onSubmit: (String) -> Unit) : LinearLayout(context) {
    private val pin = StringBuilder()
    private val message = context.textView("", 15f, Theme.Muted).apply { gravity = Gravity.CENTER }
    private val dots = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(message, context.lp(MATCH, WRAP))
        addView(dots, context.lp(MATCH, WRAP, top = 16, bottom = 22))
        addRow("1", "2", "3")
        addRow("4", "5", "6")
        addRow("7", "8", "9")
        addRow("\u232B", "0", "OK")
        drawDots()
    }

    fun setMessage(text: String, error: Boolean = false) {
        message.text = text
        message.setTextColor(if (error) Theme.Red else Theme.Muted)
    }

    fun clear() {
        pin.setLength(0)
        drawDots()
    }

    private fun addRow(a: String, b: String, c: String) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf(a, b, c).forEach { key -> row.addView(keyView(key), context.lp(0, context.dp(58), 1f, 5, 5, 5, 5)) }
        addView(row, context.lp(MATCH, WRAP))
    }

    private fun keyView(key: String): View {
        val ok = key == "OK"
        return context.textView(key, if (ok) 18f else 26f, if (ok) Theme.Midnight else Theme.Ink, bold = ok).apply {
            gravity = Gravity.CENTER
            background = if (ok) context.gradientFill(Theme.Marquee, 16) else context.glassDrawable(16)
            isClickable = true
            pressFeedback()
            contentDescription = when (key) {
                "\u232B" -> "Delete"
                "OK" -> "Confirm"
                else -> key
            }
            setOnClickListener { press(key) }
        }
    }

    private fun press(key: String) {
        when (key) {
            "\u232B" -> if (pin.isNotEmpty()) pin.deleteCharAt(pin.length - 1)
            "OK" -> {
                if (pin.length < PinLock.MIN_DIGITS) {
                    setMessage("Enter at least ${PinLock.MIN_DIGITS} digits.", true)
                } else {
                    onSubmit(pin.toString())
                }
                return
            }
            else -> if (pin.length < PinLock.MAX_DIGITS) pin.append(key)
        }
        drawDots()
    }

    private fun drawDots() {
        dots.removeAllViews()
        val count = maxOf(PinLock.MIN_DIGITS, pin.length)
        for (i in 0 until count) {
            val filled = i < pin.length
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (filled) setColor(Theme.Ink) else setStroke(context.dp(2), Theme.Muted)
                }
            }
            dots.addView(dot, context.lp(context.dp(14), context.dp(14), left = 7, right = 7))
        }
    }
}
