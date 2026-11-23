package com.pockettheater.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLabelTest {
    @Test
    fun commonBrowsers() {
        val chromeWindows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        assertEquals("Chrome on Windows", DeviceLabel.from(chromeWindows))
        assertEquals("Edge on Windows", DeviceLabel.from("$chromeWindows Edg/120.0.0.0"))
        val safariPhone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        assertEquals("Safari on iPhone", DeviceLabel.from(safariPhone))
        val chromeAndroid = "Mozilla/5.0 (Linux; Android 13; V2252) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        assertEquals("Chrome on Android", DeviceLabel.from(chromeAndroid))
    }

    @Test
    fun unknownText() {
        assertEquals("Browser on an unknown device", DeviceLabel.from(null))
        assertEquals("Browser on an unknown device", DeviceLabel.from("curl/8.0"))
    }

    @Test
    fun nothingFromTheTextIsCopiedIn() {
        // Only fixed names come out, so a hostile browser cannot put its own words on the admin's screen.
        assertEquals("Browser on an unknown device", DeviceLabel.from("<script>alert(1)</script>"))
    }
}
