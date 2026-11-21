package com.pockettheater.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Layout size for a child of a vertical or horizontal row. Margins are in dp, width and height in pixels or MATCH/WRAP. */
fun Context.lp(
    width: Int,
    height: Int,
    weight: Float = 0f,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(width, height, weight).apply { setMargins(dp(left), dp(top), dp(right), dp(bottom)) }

/** A rounded dark box with a thin border, used for every block on the dashboard and profile. */
fun Context.card(paddingDp: Int = 16): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Theme.Surface)
            setStroke(dp(1), Theme.Border)
        }
    }

/** Small spaced-out capital heading such as SERVER USAGE. */
fun Context.sectionLabel(text: String): TextView =
    textView(text.uppercase(), 12f, Theme.Muted, bold = true).apply { letterSpacing = 0.15f }

/** A rounded square with an emoji inside, tinted with the given colour. */
fun Context.iconBadge(icon: String, tint: Int, sizeDp: Int = 40): TextView =
    textView(icon, 20f, Theme.Ink).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor((tint and 0x00FFFFFF) or 0x33000000)
        }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** A rounded square holding a line icon from the drawable folder, tinted with the given colour. */
fun Context.iconBadgeRes(drawable: Int, tint: Int, sizeDp: Int = 44): View =
    FrameLayout(this).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor((tint and 0x00FFFFFF) or 0x33000000)
        }
        addView(
            ImageView(context).apply {
                setImageResource(drawable)
                setColorFilter(tint)
            },
            FrameLayout.LayoutParams(dp(sizeDp * 50 / 100), dp(sizeDp * 50 / 100), Gravity.CENTER)
        )
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** A small rounded button for a row, such as Change or Turn off. */
fun Context.pillButton(text: String, color: Int, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor((color and 0x00FFFFFF) or 0x22000000)
        }
        isClickable = true
        setOnClickListener { onClick() }
    }
