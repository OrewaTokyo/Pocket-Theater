package com.pockettheater.ui.theme

import com.pockettheater.R

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

    // Frosted glass: translucent so the moving sky shows through, lighter at the top like light on a pane.
    val GlassTop = 0x9A2A2160.toInt()
    val GlassBottom = 0x8A171240.toInt()
    val GlassStroke = 0x40FFFFFF
    val GlassSheen = 0x1CFFFFFF
    val Divider = 0x1FFFFFFF
    val Deep = 0xFF0B0816.toInt()
    val Amber = 0xFFFFC65C.toInt()
    val Orange = 0xFFFF9A3D.toInt()
    val OnAmber = 0xFF1A1030.toInt()

    /** The shelf's colour. Same colours as the laptop site. */
    fun categoryColor(category: String): Int = when (category) {
        "Series" -> Cyan
        "Anime" -> Pink
        "Movies" -> Marquee
        "Games" -> Neon
        else -> Muted
    }

    /** The line icon for a shelf. */
    fun categoryIcon(category: String): Int = when (category) {
        "Series" -> R.drawable.ic_shelf_series
        "Anime" -> R.drawable.ic_shelf_anime
        "Movies" -> R.drawable.ic_shelf_movies
        "Games" -> R.drawable.ic_shelf_games
        "Documents" -> R.drawable.ic_shelf_docs
        else -> R.drawable.ic_tab_folders
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
