package com.pockettheater.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairCodeTest {
    @Test
    fun readsTheCodeFromAScannedQr() {
        assertEquals("K7M2QX9P", PairCode.fromQr("PT:K7M2QX9P"))
        assertEquals("K7M2QX9P", PairCode.fromQr("  PT:k7m2qx9p \n"))
    }

    @Test
    fun refusesQrCodesThatAreNotOurs() {
        assertNull(PairCode.fromQr("https://example.com"))
        assertNull(PairCode.fromQr("K7M2QX9P")) // a bare code is not a scanned QR text
        assertNull(PairCode.fromQr("PT:"))
        assertNull(PairCode.fromQr("PT:SHORT")) // too few characters
        assertNull(PairCode.fromQr("PT:K7M2QX9PA")) // one character too many
    }

    @Test
    fun readsATypedCodeInAnyFormat() {
        assertEquals("K7M2QX9P", PairCode.fromTyped("K7M2 QX9P"))
        assertEquals("K7M2QX9P", PairCode.fromTyped("k7m2-qx9p"))
        assertEquals("K7M2QX9P", PairCode.fromTyped(" K7M2QX9P "))
    }

    @Test
    fun refusesTypedTextThatCannotBeACode() {
        assertNull(PairCode.fromTyped(""))
        assertNull(PairCode.fromTyped("1234"))
        assertNull(PairCode.fromTyped("K7M2QX9!!"))
        assertNull(PairCode.fromTyped("K7M2QX9PZZ"))
    }
}
