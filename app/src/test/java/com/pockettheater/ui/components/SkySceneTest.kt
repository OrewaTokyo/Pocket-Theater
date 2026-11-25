package com.pockettheater.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySceneTest {
    @Test
    fun startsAtTwilightAndReachesNightHalfwayThenReturns() {
        assertEquals(0f, SkyScene.mood(0f), 0.0001f)
        assertEquals(1f, SkyScene.mood(SkyScene.CYCLE_SECONDS / 2f), 0.0001f)
        assertEquals(0f, SkyScene.mood(SkyScene.CYCLE_SECONDS), 0.0001f)
    }

    @Test
    fun moodStaysBetweenZeroAndOneAndOnlyRisesUntilNight() {
        var last = -1f
        var t = 0f
        while (t <= SkyScene.CYCLE_SECONDS * 3) {
            val m = SkyScene.mood(t)
            assertTrue("mood out of range at $t: $m", m >= 0f && m <= 1f)
            if (t <= SkyScene.CYCLE_SECONDS / 2f) assertTrue("mood fell on the way to night at $t", m >= last - 0.0001f)
            if (t <= SkyScene.CYCLE_SECONDS / 2f) last = m
            t += 0.5f
        }
    }

    @Test
    fun lingersAtBothEnds() {
        // eased: a tenth of the way to night the sky has barely changed
        assertTrue(SkyScene.mood(SkyScene.CYCLE_SECONDS * 0.05f) < 0.05f)
        assertTrue(SkyScene.mood(SkyScene.CYCLE_SECONDS * 0.45f) > 0.95f)
    }

    @Test
    fun colourBlendHitsBothEndsAndTheMiddle() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        assertEquals(red, SkyScene.lerpColor(red, blue, 0f))
        assertEquals(blue, SkyScene.lerpColor(red, blue, 1f))
        assertEquals(0xFF800080.toInt(), SkyScene.lerpColor(red, blue, 0.5f))
        assertEquals(red, SkyScene.lerpColor(red, blue, -3f)) // never goes past the ends
        assertEquals(blue, SkyScene.lerpColor(red, blue, 9f))
    }

    @Test
    fun gradientHasOneColourPerStopAndGetsDarkerAtNight() {
        val twilight = SkyScene.gradientColors(0f)
        val night = SkyScene.gradientColors(1f)
        assertEquals(SkyScene.STOPS.size, twilight.size)
        assertEquals(SkyScene.STOPS.size, night.size)
        assertEquals(0f, SkyScene.STOPS.first(), 0f)
        assertEquals(1f, SkyScene.STOPS.last(), 0f)
        for (i in 1 until SkyScene.STOPS.size) assertTrue(SkyScene.STOPS[i] > SkyScene.STOPS[i - 1])
        fun brightness(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        assertTrue(brightness(night.first()) < brightness(twilight.first()))
        assertTrue(brightness(night.last()) < brightness(twilight.last()))
        // the horizon is the brightest part of the sky, at twilight
        assertTrue(brightness(twilight.last()) > brightness(twilight.first()))
    }
}
