package com.pockettheater.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun sizes() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.5 KB", Format.bytes(1536))
        assertEquals("5.0 MB", Format.bytes(5L * 1024 * 1024))
        assertEquals("150 MB", Format.bytes(150L * 1024 * 1024))
    }

    @Test
    fun durations() {
        assertEquals("45s", Format.duration(45_000))
        assertEquals("2m", Format.duration(125_000))
        assertEquals("1h 1m", Format.duration(3_660_000))
        assertEquals("1d 1h", Format.duration(90_000_000))
    }

    @Test
    fun counts() {
        assertEquals("1,234,567", Format.count(1_234_567))
    }
}
