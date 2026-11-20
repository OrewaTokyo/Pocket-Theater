package com.pockettheater.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.pockettheater.ui.theme.Theme

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.textView(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.actionButton(text: String, filled: Boolean, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        setTextColor(if (filled) Theme.Midnight else Theme.Marquee)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (filled) {
                setColor(Theme.Marquee)
            } else {
                setColor(Theme.Surface)
                setStroke(dp(2), Theme.Marquee)
            }
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

fun Context.gap(heightDp: Int): View =
    View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }
