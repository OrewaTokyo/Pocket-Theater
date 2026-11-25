package com.pockettheater.ui.components

import kotlin.math.PI
import kotlin.math.cos

/**
 * The look of the sky behind the whole app, with no Android code in it so it can be tested.
 * The sky slowly turns from a golden twilight into a deep blue night and back.
 */
object SkyScene {
    /** One full day-and-night turn, in seconds. */
    const val CYCLE_SECONDS = 140f

    /** Where the horizon sits, as a share of the screen height measured from the bottom. The city stands below it. */
    const val HORIZON = 0.22f

    /** 0 is golden twilight and 1 is deep night. It starts at twilight and eases in and out, so each look lingers a while. */
    fun mood(seconds: Float): Float {
        val c = (0.5 - 0.5 * cos(2.0 * PI * seconds / CYCLE_SECONDS)).toFloat()
        return c * c * (3f - 2f * c)
    }

    // Sky colours from the top of the sky down to the horizon (the same colours the shader uses).
    private val DAY = intArrayOf(rgb(26, 26, 92), rgb(56, 43, 140), rgb(120, 77, 173), rgb(219, 107, 153), rgb(255, 168, 117), rgb(255, 222, 163))
    private val NIGHT = intArrayOf(rgb(5, 10, 33), rgb(10, 23, 69), rgb(20, 43, 99), rgb(38, 66, 133), rgb(69, 87, 161), rgb(128, 102, 173))

    /** Where each colour sits between the top of the sky (0) and the horizon (1). */
    val STOPS = floatArrayOf(0f, 0.28f, 0.5f, 0.7f, 0.86f, 1f)

    /** The sky gradient for this mood, top first. Used when the phone cannot run the cloud shader. */
    fun gradientColors(mood: Float): IntArray = IntArray(DAY.size) { lerpColor(DAY[it], NIGHT[it], mood) }

    /** The colour of the haze at the horizon, which the fog over the city borrows. */
    fun horizonColor(mood: Float): Int = lerpColor(DAY[DAY.size - 1], NIGHT[NIGHT.size - 1], mood)

    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * k + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
