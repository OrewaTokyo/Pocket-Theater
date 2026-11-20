package com.pockettheater.core.server

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.pockettheater.core.security.Auth
import com.pockettheater.core.security.PathGuard
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.ShareStore
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONArray
import org.json.JSONObject

/** The read-only server. Everything needs the password; only shared folders can be reached. */
class PocketServer(context: Context, port: Int) : NanoHTTPD(port) {
    private val appContext = context.applicationContext
    private val store = ShareStore(appContext)
    private val browser = DocumentBrowser(appContext.contentResolver)
    private val assets = StaticAssets(appContext)

    override fun serve(session: IHTTPSession): Response {
        val response = try {
            route(session)
        } catch (e: Exception) {
            jsonError(Status.INTERNAL_ERROR, "Could not read that folder. Check that the phone still has access to it.")
        }
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("Referrer-Policy", "no-referrer")
        return response
    }

    private fun route(session: IHTTPSession): Response {
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return jsonError(Status.METHOD_NOT_ALLOWED, "This server is read-only")
        }
        if (!Auth.isAuthorized(session.headers["authorization"], store.password())) {
            Thread.sleep(400)
            return newFixedLengthResponse(Status.UNAUTHORIZED, "text/plain; charset=utf-8", "Password needed")
                .also { it.addHeader("WWW-Authenticate", "Basic realm=\"Pocket Theater\", charset=\"UTF-8\"") }
        }
        return when (session.uri) {
            "/api/libraries" -> libraries()
            "/api/list" -> list(session)
            "/file" -> file(session)
            else -> assets.serve(session.uri)
        }
    }

    private fun IHTTPSession.param(name: String): String? = parameters[name]?.firstOrNull()

    private fun libraries(): Response {
        val arr = JSONArray()
        store.libraries().forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category))
        }
        return jsonResponse(Status.OK, JSONObject().put("libraries", arr))
    }

    private fun list(session: IHTTPSession): Response {
        val lib = store.find(session.param("lib")) ?: return jsonError(Status.NOT_FOUND, "Unknown folder")
        val segments = PathGuard.segments(session.param("path")) ?: return jsonError(Status.BAD_REQUEST, "Invalid path")
        val tree = Uri.parse(lib.uri)
        val target = browser.resolve(tree, segments) ?: return jsonError(Status.NOT_FOUND, "Folder not found")
        if (!target.isDir) return jsonError(Status.BAD_REQUEST, "Not a folder")

        val entries = JSONArray()
        browser.children(tree, target.docId).filter { !it.name.startsWith(".") }.forEach {
            entries.put(
                JSONObject().put("name", it.name).put("dir", it.isDir)
                    .put("size", it.size).put("mime", it.mime).put("modified", it.modified)
            )
        }
        return jsonResponse(
            Status.OK,
            JSONObject()
                .put("library", JSONObject().put("id", lib.id).put("name", lib.name).put("category", lib.category))
                .put("path", JSONArray(segments))
                .put("entries", entries)
        )
    }

    private fun file(session: IHTTPSession): Response {
        val lib = store.find(session.param("lib")) ?: return jsonError(Status.NOT_FOUND, "Unknown folder")
        val segments = PathGuard.segments(session.param("path")) ?: return jsonError(Status.BAD_REQUEST, "Invalid path")
        if (segments.isEmpty()) return jsonError(Status.BAD_REQUEST, "No file given")
        val tree = Uri.parse(lib.uri)
        val target = browser.resolve(tree, segments) ?: return jsonError(Status.NOT_FOUND, "File not found")
        if (target.isDir) return jsonError(Status.BAD_REQUEST, "That is a folder")

        var mime = target.mime
        if (mime.isBlank() || mime == "application/octet-stream") {
            val ext = target.name.substringAfterLast('.', "").lowercase()
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        return RangeHandler.serve(
            appContext.contentResolver,
            browser.documentUri(tree, target.docId),
            mime,
            session.headers["range"]
        )
    }
}
