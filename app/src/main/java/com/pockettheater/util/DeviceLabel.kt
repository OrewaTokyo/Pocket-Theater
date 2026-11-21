package com.pockettheater.util

/** Turns a browser's identification text into a short, safe name such as "Chrome on Windows". */
object DeviceLabel {
    fun from(userAgent: String?): String {
        val ua = userAgent.orEmpty()
        val browser = when {
            "Edg/" in ua -> "Edge"
            "OPR/" in ua -> "Opera"
            "SamsungBrowser" in ua -> "Samsung Internet"
            "Firefox/" in ua -> "Firefox"
            "Chrome/" in ua || "CriOS" in ua -> "Chrome"
            "Safari/" in ua -> "Safari"
            else -> "Browser"
        }
        val system = when {
            "iPhone" in ua -> "iPhone"
            "iPad" in ua -> "iPad"
            "Android" in ua -> "Android"
            "Windows" in ua -> "Windows"
            "Mac OS" in ua -> "Mac"
            "CrOS" in ua -> "Chromebook"
            "Linux" in ua -> "Linux"
            else -> "an unknown device"
        }
        return "$browser on $system"
    }
}
