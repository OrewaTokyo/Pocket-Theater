package com.pockettheater.core.server

import android.content.Context
import com.pockettheater.core.security.PathGuard
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newChunkedResponse
import java.io.IOException

/** Serves the laptop-facing site that is packed inside the app (assets/web). */
class StaticAssets(private val context: Context) {

    fun serve(uri: String): Response {
        val requested = uri.trimStart('/').ifEmpty { "index.html" }
        val segments = PathGuard.segments(requested) ?: return jsonError(Status.NOT_FOUND, "Not found")
        val path = "web/" + segments.joinToString("/")
        return try {
            newChunkedResponse(Status.OK, mimeFor(path), CountingInputStream(context.assets.open(path)))
        } catch (e: IOException) {
            jsonError(Status.NOT_FOUND, "Not found")
        }
    }

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "md" -> "text/markdown; charset=utf-8"
        else -> "application/octet-stream"
    }
}
