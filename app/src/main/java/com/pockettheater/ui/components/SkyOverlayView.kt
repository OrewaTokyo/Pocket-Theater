package com.pockettheater.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The sharp things in front of the soft sky: a comet that crosses now and then, meteors, and a city at the bottom
 * whose windows twinkle and glow brighter as night comes. A gentle dark veil keeps the text easy to read.
 * It draws with bitmaps made once and allocates nothing while it animates.
 */
class SkyOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val area = RectF()

    var animated = true
    private var mood = 0f
    private var seconds = 0f

    // veil over the sky
    private val veil = Paint()

    // the city: silhouettes and their windows, drawn once at half size (they are far away, so soft edges are fine)
    private var cityBack: Bitmap? = null
    private var cityFront: Bitmap? = null
    private var lightsBack: Bitmap? = null
    private var lightsFront: Bitmap? = null
    private var cityHeight = 0f
    private var cityWidth = 0f
    private var pad = 0f
    private var flickerX = FloatArray(0)
    private var flickerY = FloatArray(0)
    private var flickerPhase = FloatArray(0)
    private var flickerRate = FloatArray(0)
    private var towerX = 0f
    private var towerY = 0f
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rectPaint = Paint()

    // meteors and the comet
    private class Meteor(
        var x: Float, var y: Float, val dx: Float, val dy: Float, val speed: Float,
        val length: Float, val life: Float, val width: Float, val pink: Boolean,
    ) {
        var age = 0f
    }

    private val meteors = ArrayList<Meteor>()
    private var nextMeteor = 1.5f
    private var burst = 0
    private val random = Random()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot: Bitmap = makeDot()
    private val dotPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dotBox = RectF()

    private fun makeDot(): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(size / 2f, size / 2f, size / 2f, intArrayOf(0xFFFFFFFF.toInt(), 0x80FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    /** Moves the meteors on and remembers the time and mood for the next draw. Called about 30 times a second. */
    fun advance(dt: Float, seconds: Float, mood: Float) {
        this.seconds = seconds
        this.mood = mood
        if (!animated || width == 0) return
        nextMeteor -= dt
        if (nextMeteor <= 0f && mood > 0.2f) {
            if (burst > 0) {
                burst--
                nextMeteor = 0.08f + random.nextFloat() * 0.17f
            } else {
                nextMeteor = (0.8f + random.nextFloat() * 1.8f) / (0.4f + mood)
                if (random.nextFloat() < 0.12f) burst = 5 + random.nextInt(5) // now and then a whole shower
            }
            spawnMeteor()
        }
        var i = 0
        while (i < meteors.size) {
            val m = meteors[i]
            m.age += dt
            m.x += m.dx * m.speed * dt
            m.y += m.dy * m.speed * dt
            if (m.age > m.life || m.y > height * (1f - SkyScene.HORIZON)) meteors.removeAt(i) else i++
        }
    }

    private fun spawnMeteor() {
        val angle = 2.05f + (random.nextFloat() - 0.5f) * 0.16f // falling down and to the left
        meteors.add(
            Meteor(
                x = width * (0.1f + random.nextFloat() * 1.05f), y = -30f * density + random.nextFloat() * height * 0.3f,
                dx = cos(angle), dy = sin(angle), speed = (260f + random.nextFloat() * 160f) * density,
                length = (45f + random.nextFloat() * 70f) * density, life = 0.6f + random.nextFloat() * 0.8f,
                width = (0.8f + random.nextFloat() * 1.2f) * density, pink = random.nextFloat() < 0.12f,
            )
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        veil.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x590A0716, 0x730A0716, 0x4D0A0716),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        buildCity(w, h)
    }

    private fun layer(
        shape: Canvas, lights: Canvas, random: Random, bodyColor: Int, minWidth: Float, maxWidth: Float,
        minShare: Float, maxShare: Float, litChance: Float, brightness: Float, flickers: ArrayList<FloatArray>?,
    ) {
        val body = Paint().apply { color = bodyColor }
        val window = Paint()
        var x = -10f * density
        while (x < cityWidth) {
            val bw = (minWidth + random.nextFloat() * (maxWidth - minWidth)) * density
            val bh = cityHeight * (minShare + random.nextFloat() * (maxShare - minShare))
            val top = cityHeight - bh
            shape.drawRect(x, top, x + bw + 1f, cityHeight + 2f, body)
            val extra = random.nextFloat()
            if (extra < 0.25f) shape.drawRect(x + bw * 0.4f, top - (6f + random.nextFloat() * 10f) * density, x + bw * 0.4f + 2f * density, top, body) // an antenna
            else if (extra < 0.45f) shape.drawRect(x + bw * 0.15f, top - 5f * density, x + bw * 0.5f, top, body) // a rooftop box
            var wy = top + 8f * density
            while (wy < cityHeight - 6f * density) {
                var wx = x + 5f * density
                while (wx < x + bw - 6f * density) {
                    val q = random.nextFloat()
                    if (q < litChance) {
                        window.color = if (random.nextFloat() < 0.15f) 0xFFBFE6FF.toInt() else 0xFFFFD98A.toInt()
                        window.alpha = (255 * brightness * (0.5f + 0.5f * random.nextFloat())).toInt()
                        lights.drawRect(wx, wy, wx + 2.6f * density, wy + 3.6f * density, window)
                    } else if (flickers != null && q < litChance + 0.05f) {
                        flickers.add(floatArrayOf(wx, wy, random.nextFloat() * 6.2832f, 0.4f + random.nextFloat() * 1.2f))
                    }
                    wx += 6.5f * density
                }
                wy += 8.5f * density
            }
            x += bw + if (random.nextFloat() < 0.3f) (4f + random.nextFloat() * 8f) * density else 0f
        }
    }

    private fun buildCity(w: Int, h: Int) {
        pad = 24f * density
        cityWidth = w + pad * 2f
        cityHeight = h * 0.30f
        val bw = Math.ceil((cityWidth * CITY_SCALE).toDouble()).toInt().coerceAtLeast(1)
        val bh = Math.ceil((cityHeight * CITY_SCALE).toDouble()).toInt().coerceAtLeast(1)
        fun canvasFor(bitmap: Bitmap) = Canvas(bitmap).apply { scale(CITY_SCALE, CITY_SCALE) }
        val back = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val front = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val lb = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val lf = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val rnd = Random(11)
        val flick = ArrayList<FloatArray>()
        layer(canvasFor(back), canvasFor(lb), rnd, 0xFF18163C.toInt(), 26f, 60f, 0.22f, 0.62f, 0.22f, 0.55f, null)
        layer(canvasFor(front), canvasFor(lf), rnd, 0xFF07071A.toInt(), 20f, 46f, 0.12f, 0.50f, 0.38f, 1f, flick)

        // one tall tower with a blinking red light on top
        val g = canvasFor(front)
        val tx = cityWidth * 0.8f
        val tower = Paint().apply { color = 0xFF07071A.toInt() }
        val path = android.graphics.Path().apply {
            moveTo(tx - 16f * density, cityHeight)
            lineTo(tx - 10f * density, cityHeight * 0.45f)
            lineTo(tx - 6f * density, cityHeight * 0.3f)
            lineTo(tx - 3f * density, cityHeight * 0.14f)
            lineTo(tx - 1f * density, cityHeight * 0.02f)
            lineTo(tx + 1f * density, cityHeight * 0.02f)
            lineTo(tx + 3f * density, cityHeight * 0.14f)
            lineTo(tx + 6f * density, cityHeight * 0.3f)
            lineTo(tx + 10f * density, cityHeight * 0.45f)
            lineTo(tx + 16f * density, cityHeight)
            close()
        }
        g.drawPath(path, tower)
        g.drawRect(tx - 8f * density, cityHeight * 0.36f, tx + 8f * density, cityHeight * 0.36f + 5f * density, tower)
        towerX = tx
        towerY = cityHeight * 0.02f

        flickerX = FloatArray(flick.size) { flick[it][0] }
        flickerY = FloatArray(flick.size) { flick[it][1] }
        flickerPhase = FloatArray(flick.size) { flick[it][2] }
        flickerRate = FloatArray(flick.size) { flick[it][3] }
        cityBack?.recycle(); cityFront?.recycle(); lightsBack?.recycle(); lightsFront?.recycle()
        cityBack = back; cityFront = front; lightsBack = lb; lightsFront = lf
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, veil)
        drawComets(canvas, w, h)
        drawMeteors(canvas)
        drawCity(canvas, h, back = true)
        drawCity(canvas, h, back = false)
    }

    // The comet: a bright head and a long curved tail of soft dots, and a smaller piece travelling just behind it.
    private fun comet(canvas: Canvas, w: Float, h: Float, u: Float, scale: Float, alpha: Float, offX: Float, offY: Float, length: Float) {
        if (alpha <= 0.01f) return
        val dirX = 0.96f
        val dirY = 0.28f
        val hx = w * (0.06f + 0.88f * u) + offX
        val hy = h * (0.05f + 0.16f * u) + offY
        val radius = 7f * density * scale
        val steps = 36
        for (k in steps - 1 downTo 0) {
            val s = k / steps.toFloat()
            val d = s * length * scale
            val bend = s * s * length * scale * 0.22f
            val x = hx - dirX * d + dirY * bend
            val y = hy - dirY * d - dirX * bend
            val r = radius * (1.5f * Math.pow((1f - s).toDouble(), 0.9).toFloat() + 0.25f)
            val tint = if (s < 0.25f) 0xFFFFFFFF.toInt() else if (s < 0.6f) 0xFFB9A8FF.toInt() else 0xFF8A5CE0.toInt()
            dotPaint.color = tint
            dotPaint.alpha = (255 * alpha * 0.5f * Math.pow((1f - s).toDouble(), 1.5).toFloat()).toInt().coerceIn(0, 255)
            dotBox.set(x - r, y - r, x + r, y + r)
            canvas.drawBitmap(dot, null, dotBox, dotPaint)
        }
        dotPaint.color = 0xFFFFFFFF.toInt()
        dotPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        dotBox.set(hx - radius * 2.2f, hy - radius * 2.2f, hx + radius * 2.2f, hy + radius * 2.2f)
        canvas.drawBitmap(dot, null, dotBox, dotPaint)
    }

    private fun drawComets(canvas: Canvas, w: Float, h: Float) {
        val u = ((seconds + 14f) % COMET_SECONDS) / COMET_SECONDS
        val a = (sin(PI * u).toFloat() * 2.2f).coerceIn(0f, 1f)
        comet(canvas, w, h, u, 1f, a, 0f, 0f, w * 0.34f)
        comet(canvas, w, h, (u - 0.025f).coerceAtLeast(0f), 0.45f, a * 0.8f, -w * 0.03f, h * 0.03f, w * 0.2f)
    }

    private fun drawMeteors(canvas: Canvas) {
        for (i in 0 until meteors.size) {
            val m = meteors[i]
            val a = min(1f, m.age * 8f) * min(1f, (m.life - m.age) * 4f)
            if (a <= 0f) continue
            linePaint.color = if (m.pink) 0xFFFF9EC4.toInt() else 0xFFBEE0FF.toInt()
            for (seg in 0 until METEOR_SEGMENTS) {
                val t0 = seg / METEOR_SEGMENTS.toFloat()
                val t1 = (seg + 1) / METEOR_SEGMENTS.toFloat()
                linePaint.alpha = (255 * a * (1f - t0) * (1f - t0)).toInt().coerceIn(0, 255)
                linePaint.strokeWidth = m.width * (1f - 0.6f * t0)
                canvas.drawLine(m.x - m.dx * m.length * t0, m.y - m.dy * m.length * t0, m.x - m.dx * m.length * t1, m.y - m.dy * m.length * t1, linePaint)
            }
        }
    }

    private fun drawCity(canvas: Canvas, h: Float, back: Boolean) {
        val body = (if (back) cityBack else cityFront) ?: return
        val lights = (if (back) lightsBack else lightsFront) ?: return
        val left = -pad
        val top = h - cityHeight
        area.set(left, top, left + cityWidth, h)
        bitmapPaint.alpha = if (back) 217 else 255
        canvas.drawBitmap(body, null, area, bitmapPaint)
        // the windows glow brighter as the sky darkens
        bitmapPaint.alpha = (255 * ((if (back) 0.3f else 0.35f) + 0.65f * mood)).toInt().coerceIn(0, 255)
        canvas.drawBitmap(lights, null, area, bitmapPaint)
        if (back) return
        rectPaint.color = 0xFFFFD98A.toInt()
        for (i in flickerX.indices) {
            val on = sin(seconds * flickerRate[i] + flickerPhase[i])
            if (on < 0f) continue
            rectPaint.alpha = (255 * (0.3f + 0.7f * mood) * on).toInt().coerceIn(0, 255)
            canvas.drawRect(left + flickerX[i], top + flickerY[i], left + flickerX[i] + 2.6f * density, top + flickerY[i] + 3.6f * density, rectPaint)
        }
        rectPaint.color = 0xFFFF4D5E.toInt()
        rectPaint.alpha = if (sin(seconds * 3f) > 0f) 255 else 100
        canvas.drawRect(left + towerX - 1.5f * density, top + towerY - 2f * density, left + towerX + 1.5f * density, top + towerY + 1f * density, rectPaint)
    }

    companion object {
        private const val CITY_SCALE = 0.5f
        private const val COMET_SECONDS = 70f
        private const val METEOR_SEGMENTS = 7
    }
}
