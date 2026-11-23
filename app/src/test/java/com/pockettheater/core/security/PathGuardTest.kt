package com.pockettheater.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathGuardTest {
    @Test
    fun emptyPathMeansTheTopFolder() {
        assertEquals(emptyList<String>(), PathGuard.segments(null))
        assertEquals(emptyList<String>(), PathGuard.segments(""))
    }

    @Test
    fun normalPathsAreSplit() {
        assertEquals(listOf("Season 1", "Episode 01.mp4"), PathGuard.segments("Season 1/Episode 01.mp4"))
        assertEquals(listOf("a", "b"), PathGuard.segments("a//b"))
    }

    @Test
    fun pathsThatTryToEscapeAreRefused() {
        assertNull(PathGuard.segments("../secret"))
        assertNull(PathGuard.segments("a/../b"))
        assertNull(PathGuard.segments("."))
        assertNull(PathGuard.segments("a\\b"))
        assertNull(PathGuard.segments("a\u0000b"))
    }
}
