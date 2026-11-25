package com.pockettheater.ui.components

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/** Paints the sky (and the dark ground under the horizon) into a view of the given size. */
interface SkyPainter {
    fun draw(canvas: Canvas, width: Int, height: Int, mood: Float, seconds: Float)
}

/**
 * The simple sky for phones older than Android 13, or if the cloud shader fails: a smooth gradient from the top of the
 * sky to the horizon that still turns from twilight to night. The moving stars, meteors and city come on top of it.
 */
class GradientSkyPainter : SkyPainter {
    private val paint = Paint()
    private val ground = Paint().apply { color = 0xFF09091A.toInt() }

    override fun draw(canvas: Canvas, width: Int, height: Int, mood: Float, seconds: Float) {
        val horizon = height * (1f - SkyScene.HORIZON)
        paint.shader = LinearGradient(0f, 0f, 0f, horizon, SkyScene.gradientColors(mood), SkyScene.STOPS, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), horizon + 1f, paint)
        canvas.drawRect(0f, horizon, width.toFloat(), height.toFloat(), ground)
    }
}
