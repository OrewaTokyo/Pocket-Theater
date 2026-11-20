package com.pockettheater.core.server

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newChunkedResponse
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.InputStream

/** Sends a file, or just the part the browser asks for, so videos can be skipped through. */
object RangeHandler {

    fun serve(resolver: ContentResolver, uri: Uri, mime: String, rangeHeader: String?): Response {
        val pfd = resolver.openFileDescriptor(uri, "r")
            ?: return jsonError(Status.NOT_FOUND, "File not found")
        val total = pfd.statSize
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)

        if (total < 0) {
            return newChunkedResponse(Status.OK, mime, stream)
        }

        var start = 0L
        var end = total - 1
        var partial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val spec = rangeHeader.substring(6).substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val first = spec.substring(0, dash)
                val last = spec.substring(dash + 1)
                try {
                    if (first.isEmpty()) {
                        start = maxOf(0L, total - last.toLong())
                    } else {
                        start = first.toLong()
                        if (last.isNotEmpty()) end = minOf(last.toLong(), total - 1)
                    }
                    partial = true
                } catch (e: NumberFormatException) {
                    start = 0L
                    end = total - 1
                }
            }
        }

        if (partial && (start >= total || start > end)) {
            stream.close()
            return newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                .also { it.addHeader("Content-Range", "bytes */$total") }
        }

        try {
            stream.channel.position(start)
        } catch (e: Exception) {
            skipFully(stream, start)
        }

        val length = if (total == 0L) 0L else end - start + 1
        val body = BoundedStream(stream, length)
        val response = newFixedLengthResponse(
            if (partial) Status.PARTIAL_CONTENT else Status.OK, mime, body, length
        )
        response.addHeader("Accept-Ranges", "bytes")
        if (partial) response.addHeader("Content-Range", "bytes $start-$end/$total")
        return response
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped <= 0) break
            left -= skipped
        }
    }

    private class BoundedStream(private val input: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = input.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun close() = input.close()
    }
}
