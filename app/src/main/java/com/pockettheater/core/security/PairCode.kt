package com.pockettheater.core.security

/**
 * Reads the pairing code the admin website shows. The QR code holds "PT:" followed by the code, and the same code is
 * printed under the QR code so it can be typed. Both ways end in the same 8 letters and numbers.
 */
object PairCode {
    private val shape = Regex("[A-Z0-9]{8}")

    /** The code inside a scanned QR text, or null when the text is not one of ours. */
    fun fromQr(text: String): String? {
        val trimmed = text.trim()
        return if (trimmed.startsWith("PT:")) clean(trimmed.removePrefix("PT:")) else null
    }

    /** The code from what was typed. Spaces, dashes and small letters are fine. Null when it cannot be a code. */
    fun fromTyped(text: String): String? = clean(text)

    private fun clean(raw: String): String? {
        val code = raw.filter { it.isLetterOrDigit() }.uppercase()
        return if (shape.matches(code)) code else null
    }
}
