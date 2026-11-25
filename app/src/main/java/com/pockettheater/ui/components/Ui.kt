package com.pockettheater.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pockettheater.ui.theme.Theme

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/**
 * A line of text. `bold` is a firm weight for headings, `light` is a thin weight for big numbers.
 */
fun Context.textView(text: String, sizeSp: Float, color: Int, bold: Boolean = false, light: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        else if (light) typeface = Typeface.create(Typeface.DEFAULT, 300, false)
    }

/** A pane of frosted glass: translucent so the moving sky shows through, with light catching its top edge. */
fun Context.glassDrawable(
    radiusDp: Int = 20,
    strokeColor: Int = Theme.GlassStroke,
    fillTop: Int = Theme.GlassTop,
    fillBottom: Int = Theme.GlassBottom,
): Drawable {
    val radius = dp(radiusDp).toFloat()
    val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(fillTop, fillBottom)).apply {
        cornerRadius = radius
        setStroke(dp(1), strokeColor)
    }
    val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Theme.GlassSheen, 0x00FFFFFF)).apply {
        cornerRadius = radius
    }
    return LayerDrawable(arrayOf<Drawable>(base, sheen))
}

/** A solid colour that glows slightly from the top, for selected chips and the like. */
fun Context.gradientFill(color: Int, radiusDp: Int = 20): Drawable {
    val lighter = SkyScene.lerpColor(color, Color.WHITE, 0.25f)
    return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lighter, color)).apply {
        cornerRadius = dp(radiusDp).toFloat()
    }
}

/** Presses in a little while a finger is down and springs back, so buttons feel like they have weight. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressFeedback() {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
        false // the tap itself still goes through
    }
}

/** A button face with a light ripple where the finger lands. */
private fun Context.withRipple(face: Drawable, radiusDp: Int): Drawable {
    val mask = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(Color.WHITE)
    }
    return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), face, mask)
}

fun Context.actionButton(text: String, filled: Boolean, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        letterSpacing = 0.02f
        setPadding(dp(20), dp(14), dp(20), dp(14))
        setTextColor(if (filled) Theme.OnAmber else Theme.Marquee)
        val face: Drawable = if (filled) {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Theme.Amber, Theme.Orange)).apply {
                cornerRadius = dp(14).toFloat()
            }
        } else {
            glassDrawable(14, strokeColor = (Theme.Marquee and 0x00FFFFFF) or 0xAA000000.toInt())
        }
        background = withRipple(face, 14)
        if (filled) {
            elevation = dp(8).toFloat() // lifts it, and the shadow is tinted amber so the button seems to glow
            outlineSpotShadowColor = Theme.Marquee
            outlineAmbientShadowColor = Theme.Marquee
        }
        isClickable = true
        pressFeedback()
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

/** A pane of frosted glass, used for every block on the dashboard and profile. */
fun Context.card(paddingDp: Int = 16): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
        background = glassDrawable(20)
        elevation = dp(3).toFloat()
    }

/** Small spaced-out capital heading such as SERVER USAGE. */
fun Context.sectionLabel(text: String): TextView =
    textView(text.uppercase(), 12f, Theme.Muted, bold = true).apply { letterSpacing = 0.18f }

private fun Context.badgeBackground(tint: Int): Drawable {
    val rgb = tint and 0x00FFFFFF
    return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(rgb or 0x59000000, rgb or 0x1A000000)).apply {
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), rgb or 0x44000000)
    }
}

/** A rounded square with an emoji inside, tinted with the given colour. */
fun Context.iconBadge(icon: String, tint: Int, sizeDp: Int = 40): TextView =
    textView(icon, 20f, Theme.Ink).apply {
        gravity = Gravity.CENTER
        background = badgeBackground(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** A glowing rounded square holding a line icon from the drawable folder, tinted with the given colour. */
fun Context.iconBadgeRes(drawable: Int, tint: Int, sizeDp: Int = 44): View =
    FrameLayout(this).apply {
        background = badgeBackground(tint)
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
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        setTextColor(color)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        val rgb = color and 0x00FFFFFF
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(rgb or 0x33000000, rgb or 0x16000000)).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), rgb or 0x55000000)
        }
        background = withRipple(face, 12)
        isClickable = true
        pressFeedback()
        setOnClickListener { onClick() }
    }

/** Takes this view out of whatever it is in now. A view can only sit in one place, so call this before adding a view that was shown before. */
fun <T : View> T.detach(): T {
    (parent as? ViewGroup)?.removeView(this)
    return this
}

/** The block that holds a page's rows: past any ScrollView or single wrapper, down to the column itself. */
private fun columnOf(page: View): View {
    var view = page
    while (true) {
        val group = view as? ViewGroup ?: return view
        if ((view is ScrollView || view is FrameLayout) && group.childCount == 1) view = group.getChildAt(0) else return view
    }
}

/** Fades and slides a page's blocks in one after another, so a page opens with a little life instead of snapping on. */
fun View.staggerIn() {
    val column = columnOf(this) as? ViewGroup ?: return
    val shift = context.dp(14).toFloat()
    for (i in 0 until column.childCount) {
        val child = column.getChildAt(i)
        child.alpha = 0f
        child.translationY = shift
        child.animate().alpha(1f).translationY(0f)
            .setStartDelay(minOf(i * 45L, 400L)).setDuration(320L)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
    }
}

/** Fades and slides a whole page in from the side, for moving between a list and the page opened from it. */
fun View.slideIn() {
    alpha = 0f
    translationX = context.dp(18).toFloat()
    animate().alpha(1f).translationX(0f).setStartDelay(0L).setDuration(260L).setInterpolator(DecelerateInterpolator(1.6f)).start()
}
