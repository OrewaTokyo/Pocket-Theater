package com.pockettheater.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.pockettheater.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.max

/**
 * A round gauge: a faint ring, a glowing gradient arc that sweeps smoothly to the new percentage, and the value
 * written in the middle.
 */
class RingGauge(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22FFFFFF
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.Ink
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 600, false)
    }
    private val box = RectF()
    private val turn = Matrix()
    private var arcColor = Theme.Cyan
    private var shown = 0f // what is drawn right now; it eases towards `target`
    private var target = 0f
    private var animator: ValueAnimator? = null
    private var text = ""

    fun set(percent: Float, text: String, color: Int) {
        this.text = text
        arcColor = color
        val wanted = percent.coerceIn(0f, 100f)
        if (abs(wanted - target) > 0.4f) {
            target = wanted
            animator?.cancel()
            animator = ValueAnimator.ofFloat(shown, wanted).apply {
                duration = 700L
                interpolator = DecelerateInterpolator(1.8f)
                addUpdateListener {
                    shown = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        shown = target // when it comes back it is already where it should be
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val size = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val stroke = size * 0.11f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        glow.strokeWidth = stroke * 1.9f
        val inset = stroke + 2f * density
        box.set(inset, inset, size - inset, size - inset)
        canvas.drawArc(box, 0f, 360f, false, track)
        if (shown > 0.2f) {
            val sweep = 360f * shown / 100f
            val fraction = max(0.02f, shown / 100f)
            // the arc fades in from its start and burns brightest at its tip
            val gradient = SweepGradient(size / 2f, size / 2f, intArrayOf((arcColor and 0x00FFFFFF) or 0x66000000, arcColor), floatArrayOf(0f, fraction))
            turn.setRotate(-90f, size / 2f, size / 2f)
            gradient.setLocalMatrix(turn)
            glow.color = (arcColor and 0x00FFFFFF) or 0x30000000
            canvas.drawArc(box, -90f, sweep, false, glow)
            arc.shader = gradient
            canvas.drawArc(box, -90f, sweep, false, arc)
        }
        label.textSize = size * 0.22f
        canvas.drawText(text, size / 2f, size / 2f + label.textSize * 0.35f, label)
    }
}

/** Bars side by side that grow into place. The tallest fills the height and the last bar (now) glows amber. */
class BarChart(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply {
        color = 0x14FFFFFF
        strokeWidth = density
    }
    private val box = RectF()
    private var shown = FloatArray(0) // each bar's height as a share of the tallest, as drawn right now
    private var wanted = FloatArray(0)
    private var animator: ValueAnimator? = null

    var values: LongArray = LongArray(12)
        set(value) {
            field = value
            retarget()
        }

    private fun retarget() {
        var peak = 1L
        for (v in values) if (v > peak) peak = v
        val next = FloatArray(values.size) { values[it].toFloat() / peak }
        if (next.size == wanted.size && next.indices.all { abs(next[it] - wanted[it]) < 0.01f }) return // nothing changed
        val from = FloatArray(next.size) { if (it < shown.size) shown[it] else 0f }
        wanted = next
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                val k = it.animatedValue as Float
                shown = FloatArray(wanted.size) { i -> from[i] + (wanted[i] - from[i]) * k }
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        shown = wanted.copyOf()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.Cyan, 0xFF3A6BFF.toInt(), Shader.TileMode.CLAMP)
        nowPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Theme.Amber, 0xFFFF7A3D.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val count = shown.size
        if (count == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        for (line in 1..3) canvas.drawLine(0f, h * line / 4f, w, h * line / 4f, grid) // faint guide lines
        val gap = 6f * density
        val barWidth = (w - gap * (count - 1)) / count
        val radius = 4f * density
        for (i in 0 until count) {
            val barHeight = max(3f * density, shown[i] * h)
            val left = i * (barWidth + gap)
            box.set(left, h - barHeight, left + barWidth, h)
            val p = if (i == count - 1) nowPaint else paint
            p.alpha = if (i < values.size && values[i] == 0L) 60 else 255
            canvas.drawRoundRect(box, radius, radius, p)
        }
    }
}
