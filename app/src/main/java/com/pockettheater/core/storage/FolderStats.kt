package com.pockettheater.core.storage

import android.net.Uri
import android.provider.DocumentsContract
import java.util.concurrent.ConcurrentHashMap

/**
 * What is inside a shared folder, counted from its top level. The Folders tab shows it, and the website uses it to leave
 * out folders (and so whole shelves) that hold nothing yet.
 */
object FolderStats {
    class Summary(
        val folders: Int,
        val videos: Int,
        val audio: Int,
        val images: Int,
        val documents: Int,
        val other: Int,
        val readable: Boolean,
        val at: Long,
    ) {
        val items: Int get() = folders + videos + audio + images + documents + other

        /** Shown on the website only when the phone can read the folder and something is in it. */
        val visible: Boolean get() = readable && items > 0
    }

    private const val FRESH_MS = 60_000L
    private val cache = ConcurrentHashMap<String, Summary>()

    private val videoTypes = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "wmv", "flv", "ts", "mpg", "mpeg", "3gp", "ogv")
    private val audioTypes = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")
    private val imageTypes = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "svg")
    private val documentTypes = setOf("pdf", "epub", "cbz", "cbr", "txt", "md", "doc", "docx")

    fun isVideo(name: String, mime: String): Boolean =
        mime.lowercase().startsWith("video/") || name.substringAfterLast('.', "").lowercase() in videoTypes

    fun cached(uri: String): Summary? = cache[uri]

    fun isFresh(summary: Summary?): Boolean = summary != null && System.currentTimeMillis() - summary.at < FRESH_MS

    fun forget(uri: String) {
        cache.remove(uri)
    }

    /** The saved count if it is recent, otherwise a fresh one. Reading a big folder takes a moment, so not on the screen's own thread. */
    fun get(browser: DocumentBrowser, uri: String): Summary {
        val saved = cache[uri]
        return if (isFresh(saved)) saved!! else compute(browser, uri)
    }

    fun compute(browser: DocumentBrowser, uri: String): Summary {
        val summary = try {
            val tree = Uri.parse(uri)
            count(browser.children(tree, DocumentsContract.getTreeDocumentId(tree)))
        } catch (e: Exception) {
            Summary(0, 0, 0, 0, 0, 0, false, System.currentTimeMillis()) // permission gone, or the folder was deleted
        }
        cache[uri] = summary
        return summary
    }

    fun count(entries: List<DocEntry>): Summary {
        var folders = 0
        var videos = 0
        var audio = 0
        var images = 0
        var documents = 0
        var other = 0
        for (e in entries) {
            if (e.name.startsWith(".")) continue // hidden files stay hidden, here and on the website
            if (!e.isDir && Subtitles.isSubtitle(e.name)) continue // subtitles belong to a video, they are not items of their own
            val ext = e.name.substringAfterLast('.', "").lowercase()
            val type = e.mime.lowercase()
            when {
                e.isDir -> folders++
                type.startsWith("video/") || ext in videoTypes -> videos++
                type.startsWith("audio/") || ext in audioTypes -> audio++
                type.startsWith("image/") || ext in imageTypes -> images++
                type == "application/pdf" || ext in documentTypes -> documents++
                else -> other++
            }
        }
        return Summary(folders, videos, audio, images, documents, other, true, System.currentTimeMillis())
    }

    /** A short line such as "3 folders · 24 videos". */
    fun describe(summary: Summary): String {
        if (!summary.readable) return "Can't read this folder"
        val parts = ArrayList<String>()
        fun add(n: Int, one: String, many: String) {
            if (n > 0) parts.add("$n " + if (n == 1) one else many)
        }
        add(summary.folders, "folder", "folders")
        add(summary.videos, "video", "videos")
        add(summary.audio, "audio file", "audio files")
        add(summary.images, "image", "images")
        add(summary.documents, "document", "documents")
        add(summary.other, "other file", "other files")
        return if (parts.isEmpty()) "Empty" else parts.take(3).joinToString(" \u00B7 ")
    }
}
