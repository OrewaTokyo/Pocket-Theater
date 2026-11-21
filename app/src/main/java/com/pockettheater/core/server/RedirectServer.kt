package com.pockettheater.core.server

import fi.iki.elonen.NanoHTTPD

/** Listens on the old plain port and sends everyone to the secure one, so old links and bookmarks keep working. */
class RedirectServer(port: Int, private val securePort: Int) : NanoHTTPD(port) {
    private val hostPattern = Regex("^[A-Za-z0-9.\\-]{1,100}$")

    override fun serve(session: IHTTPSession): Response {
        val host = session.headers["host"].orEmpty().substringBefore(':')
        if (!hostPattern.matches(host)) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "Bad request")
        }
        // Only the front page. The page and path are never copied into the redirect, so nothing can be injected.
        return redirect("https://$host:$securePort/")
    }
}
