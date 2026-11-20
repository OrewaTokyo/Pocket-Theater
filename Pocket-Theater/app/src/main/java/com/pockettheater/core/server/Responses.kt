package com.pockettheater.core.server

import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONObject

fun jsonResponse(status: Status, body: JSONObject): Response =
    newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString())
        .also { it.addHeader("Cache-Control", "no-store") }

fun jsonError(status: Status, message: String): Response =
    jsonResponse(status, JSONObject().put("error", message))
