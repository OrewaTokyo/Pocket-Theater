package com.pockettheater.ui.components

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.View
import com.pockettheater.util.CrashLog

/**
 * The soft part of the sky (gradient, clouds, haze). It is drawn small and stretched up by [SkyBackdrop], which is
 * invisible on something this soft and keeps the graphics chip cool on an older phone.
 */
class SkyShaderView(context: Context) : View(context) {
    var mood = 0f
    var seconds = 0f
    private var painter: SkyPainter = create()
    private var reported = false

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null) // drawn once at its small size, then stretched by the compositor
    }

    private fun create(): SkyPainter {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                return AgslSkyPainter()
            } catch (e: Throwable) {
                report("building the sky shader", e)
            }
        }
        return GradientSkyPainter()
    }

    private fun report(where: String, error: Throwable) {
        if (reported) return
        reported = true
        CrashLog.record(context, where, error) // handled: the app carries on with the plain gradient sky
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        try {
            painter.draw(canvas, width, height, mood, seconds)
        } catch (e: Throwable) {
            report("drawing the sky", e)
            painter = GradientSkyPainter()
            painter.draw(canvas, width, height, mood, seconds)
        }
    }
}
