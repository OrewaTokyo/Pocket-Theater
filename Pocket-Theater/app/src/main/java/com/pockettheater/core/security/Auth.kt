package com.pockettheater.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object Auth {
    const val USER = "theater"
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

    fun isAuthorized(header: String?, password: String): Boolean {
        if (header == null || !header.startsWith("Basic ", ignoreCase = true)) return false
        val decoded = try {
            String(Base64.decode(header.substring(6).trim(), Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val expected = "$USER:$password"
        return MessageDigest.isEqual(
            decoded.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    fun newPassword(): String {
        val random = SecureRandom()
        return (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
