package com.pockettheater.core.server

/** Reads the Range header a browser sends to ask for part of a file, such as "bytes=0-99". */
object RangeParser {
    /** The bytes to send. `partial` is true when the browser asked for only a part. */
    class Span(val start: Long, val end: Long, val partial: Boolean)

    /** Returns null when the request cannot be satisfied, which is answered with HTTP 416. A range longer than `maxChunk` is cut to that length. */
    fun parse(header: String?, total: Long, maxChunk: Long = Long.MAX_VALUE): Span? {
        val whole = Span(0L, total - 1, false)
        if (header == null || !header.startsWith("bytes=")) return whole
        val spec = header.substring(6).substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return whole
        val first = spec.substring(0, dash)
        val last = spec.substring(dash + 1)
        var start: Long
        var end = total - 1
        try {
            if (first.isEmpty()) {
                start = maxOf(0L, total - last.toLong()) // "-500" means the last 500 bytes
            } else {
                start = first.toLong()
                if (last.isNotEmpty()) end = minOf(last.toLong(), total - 1)
            }
        } catch (e: NumberFormatException) {
            return whole
        }
        if (start < 0 || start >= total || start > end) return null
        // Answer with one piece at a time. The browser simply asks for the next piece, so nothing is sent that is not needed.
        if (end - start + 1 > maxChunk) end = start + maxChunk - 1
        return Span(start, end, true)
    }
}
