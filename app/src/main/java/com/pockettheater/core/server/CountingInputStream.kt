package com.pockettheater.core.server

import java.io.FilterInputStream
import java.io.InputStream

/** Passes data through unchanged and adds up how many bytes the server sent. */
class CountingInputStream(source: InputStream) : FilterInputStream(source) {
    override fun read(): Int {
        val b = super.read()
        if (b >= 0) ServerStats.bytes(1L)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) ServerStats.bytes(n.toLong())
        return n
    }
}
