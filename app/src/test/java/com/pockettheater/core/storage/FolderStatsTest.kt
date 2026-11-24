package com.pockettheater.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderStatsTest {
    private fun file(name: String, mime: String = "") = DocEntry("id", name, false, mime, 0L, 0L)
    private fun dir(name: String) = DocEntry("id", name, true, "vnd.android.document/directory", 0L, 0L)

    @Test
    fun countsEachKind() {
        val summary = FolderStats.count(
            listOf(dir("Season 1"), file("a.mp4"), file("b.mkv"), file("c.mp3"), file("d.jpg"), file("e.pdf"), file("f.bin"), file(".hidden.mp4"))
        )
        assertEquals(1, summary.folders)
        assertEquals(2, summary.videos)
        assertEquals(1, summary.audio)
        assertEquals(1, summary.images)
        assertEquals(1, summary.documents)
        assertEquals(1, summary.other)
        assertEquals(7, summary.items) // the hidden file is not counted
        assertTrue(summary.visible)
    }

    @Test
    fun anEmptyFolderIsNotShownOnTheWebsite() {
        val summary = FolderStats.count(emptyList())
        assertEquals(0, summary.items)
        assertFalse(summary.visible)
        assertEquals("Empty", FolderStats.describe(summary))
    }

    @Test
    fun aFolderWithOnlyHiddenFilesCountsAsEmpty() {
        assertFalse(FolderStats.count(listOf(file(".nomedia"))).visible)
    }

    @Test
    fun aFolderThePhoneCannotReadIsNotShown() {
        val summary = FolderStats.Summary(0, 0, 0, 0, 0, 0, false, 0L)
        assertFalse(summary.visible)
        assertEquals("Can't read this folder", FolderStats.describe(summary))
    }

    @Test
    fun theDescriptionShowsAtMostThreeParts() {
        val summary = FolderStats.Summary(2, 5, 1, 4, 0, 0, true, 0L)
        assertEquals("2 folders \u00B7 5 videos \u00B7 1 audio file", FolderStats.describe(summary))
    }

    @Test
    fun singularWords() {
        assertEquals("1 video", FolderStats.describe(FolderStats.Summary(0, 1, 0, 0, 0, 0, true, 0L)))
        assertEquals("1 folder", FolderStats.describe(FolderStats.Summary(1, 0, 0, 0, 0, 0, true, 0L)))
    }
}
