package com.pockettheater.ui.components

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout

/**
 * The moving sky behind the whole app. It holds two layers: the soft sky (gradient and clouds), drawn small and
 * stretched up, and the sharp layer in front of it (comet, meteors, city). It runs at about 30 frames a second while
 * the app is on screen, stops when it is not, and shows one still picture instead when animations are turned off in
 * Android's settings or Battery Saver is on.
 */
class SkyBackdrop(context: Context) : FrameLayout(context) {
    private val soft = SkyShaderView(context)
    private val sharp = SkyOverlayView(context)
    private var startedAt = 0L
    private var lastFrame = 0L
    private var running = false
    private var animated = true

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            if (now - lastFrame >= FRAME_MS) {
                val dt = ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
                lastFrame = now
                renderFrame(dt, (now - startedAt) / 1000f)
            }
            postOnAnimation(this)
        }
    }

    init {
        addView(soft, LayoutParams(1, 1))
        addView(sharp, LayoutParams(MATCH, MATCH))
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun renderFrame(dt: Float, seconds: Float) {
        val mood = if (animated) SkyScene.mood(seconds) else STILL_MOOD
        soft.mood = mood
        soft.seconds = seconds
        sharp.advance(dt, seconds, mood)
        soft.invalidate()
        sharp.invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        soft.measure(
            MeasureSpec.makeMeasureSpec(maxOf(1, w / SOFT_SCALE), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxOf(1, h / SOFT_SCALE), MeasureSpec.EXACTLY)
        )
        sharp.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        soft.layout(0, 0, soft.measuredWidth, soft.measuredHeight)
        soft.pivotX = 0f
        soft.pivotY = 0f
        soft.scaleX = w / soft.measuredWidth.toFloat()
        soft.scaleY = h / soft.measuredHeight.toFloat()
        sharp.layout(0, 0, w, h)
        if (!running) renderFrame(0f, seconds())
    }

    /** Seconds since the sky started, or a fixed calm moment when nothing is animated or it has not started yet. */
    private fun seconds(): Float =
        if (animated && startedAt != 0L) (SystemClock.uptimeMillis() - startedAt) / 1000f else STILL_SECONDS

    /** Looks at Android's animation and battery settings. */
    private fun checkSettings() {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        animated = scale > 0f && !saver
        sharp.animated = animated
    }

    private fun start() {
        if (running) return
        checkSettings()
        if (startedAt == 0L) startedAt = SystemClock.uptimeMillis()
        if (!animated) {
            renderFrame(0f, STILL_SECONDS) // one calm picture, nothing moving
            return
        }
        running = true
        lastFrame = 0L
        postOnAnimation(loop)
    }

    private fun stop() {
        running = false
        removeCallbacks(loop)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (windowVisibility == View.VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) start() else stop()
    }

    companion object {
        private const val FRAME_MS = 32L
        private const val SOFT_SCALE = 3
        private const val STILL_SECONDS = 25f
        private const val STILL_MOOD = 0.12f
    }
}
