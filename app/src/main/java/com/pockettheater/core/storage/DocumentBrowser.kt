package com.pockettheater.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

data class DocEntry(
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val mime: String,
    val size: Long,
    val modified: Long
)

/** Reads folders the user picked through Android's folder picker. Never touches raw file paths. */
class DocumentBrowser(private val resolver: ContentResolver) {

    fun children(tree: Uri, parentDocId: String): List<DocEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val cols = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )
        val out = ArrayList<DocEntry>()
        resolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2) ?: ""
                out.add(
                    DocEntry(
                        docId = c.getString(0),
                        name = c.getString(1) ?: "",
                        isDir = mime == Document.MIME_TYPE_DIR,
                        mime = mime,
                        size = if (c.isNull(3)) -1L else c.getLong(3),
                        modified = if (c.isNull(4)) 0L else c.getLong(4)
                    )
                )
            }
        }
        return out
    }

    /** Walks down the picked folder one name at a time. */
    fun resolve(tree: Uri, segments: List<String>): DocEntry? {
        var current = DocEntry(DocumentsContract.getTreeDocumentId(tree), "", true, Document.MIME_TYPE_DIR, -1L, 0L)
        for (seg in segments) {
            if (!current.isDir) return null
            current = children(tree, current.docId).firstOrNull { it.name == seg } ?: return null
        }
        return current
    }

    fun documentUri(tree: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    fun displayName(tree: Uri): String {
        val doc = documentUri(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "Folder"
        }
        return "Folder"
    }
}
