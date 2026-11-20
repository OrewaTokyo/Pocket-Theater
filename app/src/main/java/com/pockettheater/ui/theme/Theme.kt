package com.pockettheater.ui.theme

object Theme {
    val Midnight = 0xFF14112B.toInt()
    val Surface = 0xFF211B45.toInt()
    val Marquee = 0xFFFFB547.toInt()
    val Cyan = 0xFF5EE0FF.toInt()
    val Ink = 0xFFF3EEF8.toInt()
    val Muted = 0xFFA39BC4.toInt()
    val Border = 0xFF3A3170.toInt()
    val Red = 0xFFFF5C7A.toInt()
    val Pink = 0xFFFF7BB5.toInt()
    val Neon = 0xFF5CFF9D.toInt()
    val SplashGlow = 0xFF2B1B52.toInt()
    val SplashEdge = 0xFF0B0816.toInt()

    /** The shelf's colour. Same colours as the laptop site. */
    fun categoryColor(category: String): Int = when (category) {
        "Series" -> Cyan
        "Anime" -> Pink
        "Movies" -> Marquee
        "Games" -> Neon
        else -> Muted
    }

    /** The shelf's name with its icon, as shown on the phone. */
    fun categoryLabel(category: String): String = when (category) {
        "Series" -> "\uD83D\uDCFA Series"
        "Anime" -> "\uD83C\uDF65 Anime"
        "Movies" -> "\uD83C\uDFAC Movies"
        "Games" -> "\uD83C\uDFAE Gaming"
        "Documents" -> "\uD83D\uDCDA Documents"
        else -> "\uD83D\uDCE6 $category"
    }
}
