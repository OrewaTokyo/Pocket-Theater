package com.pockettheater.core.server

import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONObject

/** Status codes we define ourselves, so the build does not depend on which ones this NanoHTTPD version has. */
enum class Http(private val code: Int, private val text: String) : Response.IStatus {
    SEE_OTHER(303, "See Other"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
    UNSUPPORTED_MEDIA(415, "Unsupported Media Type"),
    TOO_MANY(429, "Too Many Requests");

    override fun getRequestStatus(): Int = code
    override fun getDescription(): String = "$code $text"
}

fun jsonResponse(status: Response.IStatus, body: JSONObject): Response {
    val text = body.toString()
    ServerStats.bytes(text.toByteArray().size.toLong())
    return newFixedLengthResponse(status, "application/json; charset=utf-8", text)
        .also { it.addHeader("Cache-Control", "no-store") }
}

fun jsonError(status: Response.IStatus, message: String): Response =
    jsonResponse(status, JSONObject().put("error", message))

fun redirect(to: String): Response =
    newFixedLengthResponse(Http.SEE_OTHER, "text/plain; charset=utf-8", "")
        .also {
            it.addHeader("Location", to)
            it.addHeader("Cache-Control", "no-store")
        }
