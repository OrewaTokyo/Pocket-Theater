package com.pockettheater.core.server

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newChunkedResponse
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.FileNotFoundException
import java.io.InputStream

/** Sends a file, or just the part the browser asks for, so videos can be skipped through. */
object RangeHandler {

    fun serve(resolver: ContentResolver, uri: Uri, mime: String, rangeHeader: String?): Response {
        val pfd = try {
            resolver.openFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
            null
        } ?: return jsonError(Status.NOT_FOUND, "That file is not there any more.")
        val total = pfd.statSize
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)

        if (total < 0) {
            return newChunkedResponse(Status.OK, mime, CountingInputStream(stream))
        }

        val span = RangeParser.parse(rangeHeader, total)
        if (span == null) {
            stream.close()
            return newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                .also { it.addHeader("Content-Range", "bytes */$total") }
        }

        try {
            stream.channel.position(span.start)
        } catch (e: Exception) {
            skipFully(stream, span.start)
        }

        val length = if (total == 0L) 0L else span.end - span.start + 1
        val body = CountingInputStream(BoundedStream(stream, length))
        val response = newFixedLengthResponse(
            if (span.partial) Status.PARTIAL_CONTENT else Status.OK, mime, body, length
        )
        response.addHeader("Accept-Ranges", "bytes")
        if (span.partial) response.addHeader("Content-Range", "bytes ${span.start}-${span.end}/$total")
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
