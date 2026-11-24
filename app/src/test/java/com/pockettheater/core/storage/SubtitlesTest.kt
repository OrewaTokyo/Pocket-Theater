package com.pockettheater.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {
    @Test
    fun whichFilesBelongToAVideo() {
        assertTrue(Subtitles.belongsTo("Movie.mp4", "Movie.srt"))
        assertTrue(Subtitles.belongsTo("Movie.mp4", "Movie.en.srt"))
        assertTrue(Subtitles.belongsTo("Movie.MP4", "movie.EN.SRT"))
        assertFalse(Subtitles.belongsTo("Movie.mp4", "Movie 2.srt"))
        assertFalse(Subtitles.belongsTo("Movie.mp4", "Other.srt"))
        assertFalse(Subtitles.belongsTo("Movie.mp4", "Movie.en.txt"))
    }

    @Test
    fun readableNames() {
        assertEquals("Subtitles", Subtitles.label("Movie.mp4", "Movie.srt"))
        assertEquals("English", Subtitles.label("Movie.mp4", "Movie.en.srt"))
        assertEquals("English (forced)", Subtitles.label("Movie.mp4", "Movie.eng.forced.srt"))
        assertEquals("French (SDH)", Subtitles.label("Movie.mp4", "Movie.fr.sdh.srt"))
        assertEquals("Klingon", Subtitles.label("Movie.mp4", "Movie.Klingon.srt"))
        assertEquals("en", Subtitles.language("Movie.mp4", "Movie.eng.srt"))
        assertEquals("", Subtitles.language("Movie.mp4", "Movie.srt"))
    }

    @Test
    fun srtBecomesWebVtt() {
        val srt = "1\n00:00:01,500 --> 00:00:03,000\nHello <i>there</i>\n\n2\n00:00:04,000 --> 00:00:05,250 X1:100\n{\\an8}Top line\n"
        val vtt = Subtitles.toVtt(srt, "srt")
        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("00:00:01.500 --> 00:00:03.000"))
        assertTrue(vtt.contains("00:00:04.000 --> 00:00:05.250"))
        assertFalse(vtt.contains("X1:100"))
        assertFalse(vtt.contains("{"))
        assertTrue(vtt.contains("Hello <i>there</i>"))
        assertTrue(vtt.contains("Top line"))
    }

    @Test
    fun assBecomesWebVtt() {
        val ass = """
            [Script Info]
            Title: x

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,Hello, {\i1}world{\i0}\Nsecond line
        """.trimIndent()
        val vtt = Subtitles.toVtt(ass, "ass")
        assertTrue(vtt.contains("00:00:01.500 --> 00:00:03.000\nHello, world\nsecond line"))
    }

    @Test
    fun vttPassesThrough() {
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n", Subtitles.toVtt("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n", "vtt"))
        assertTrue(Subtitles.toVtt("00:00:01.000 --> 00:00:02.000\nHi\n", "vtt").startsWith("WEBVTT"))
    }

    @Test
    fun textIsReadWhateverItsEncoding() {
        assertEquals("Hi", Subtitles.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'H'.code.toByte(), 'i'.code.toByte())))
        assertEquals("caf\u00E9", Subtitles.decode(byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())))
        assertEquals("caf\u00E9", Subtitles.decode("caf\u00E9".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun subtitleFilesAreNotItemsOfTheirOwn() {
        val video = DocEntry("1", "a.mp4", false, "video/mp4", 0L, 0L)
        val subtitle = DocEntry("2", "a.srt", false, "", 0L, 0L)
        assertEquals(1, FolderStats.count(listOf(video, subtitle)).items)
        assertFalse(FolderStats.count(listOf(subtitle)).visible)
    }
}
