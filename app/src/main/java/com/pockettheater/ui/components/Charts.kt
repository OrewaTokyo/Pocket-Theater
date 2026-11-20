package com.pockettheater.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.pockettheater.ui.theme.Theme

/** A round gauge: a faint ring with a coloured arc for the percentage and the value written in the middle. */
class RingGauge(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22FFFFFF
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.Ink
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val box = RectF()
    private var percent = 0f
    private var text = ""

    fun set(percent: Float, text: String, color: Int) {
        this.percent = percent.coerceIn(0f, 100f)
        this.text = text
        arc.color = color
        invalidate()
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
        val inset = stroke / 2f + 2f * density
        box.set(inset, inset, size - inset, size - inset)
        canvas.drawArc(box, 0f, 360f, false, track)
        if (percent > 0f) canvas.drawArc(box, -90f, 360f * percent / 100f, false, arc)
        label.textSize = size * 0.22f
        canvas.drawText(text, size / 2f, size / 2f + label.textSize * 0.35f, label)
    }
}

/** Bars side by side, tallest bar fills the height. The last bar (now) is drawn in the theme's gold. */
class BarChart(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    var values: LongArray = LongArray(12)
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val count = values.size
        if (count == 0) return
        var peak = 1L
        for (v in values) if (v > peak) peak = v
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 6f * density
        val barWidth = (w - gap * (count - 1)) / count
        val radius = 4f * density
        for (i in 0 until count) {
            val fraction = values[i].toFloat() / peak
            val barHeight = maxOf(3f * density, fraction * h)
            val left = i * (barWidth + gap)
            box.set(left, h - barHeight, left + barWidth, h)
            paint.color = if (i == count - 1) Theme.Marquee else Theme.Cyan
            paint.alpha = if (values[i] == 0L) 60 else 255
            canvas.drawRoundRect(box, radius, radius, paint)
        }
    }
}
