package com.pockettheater.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Password scrambling, random tokens and the rules for usernames and passwords. */
object Auth {
    const val COOKIE = "pt_session"
    const val SESSION_SECONDS = 14L * 24 * 3600
    const val USERNAME_RULE = "Username must be 3 to 20 letters, numbers or _ (lowercase)."
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    /** The number of rounds used before passwords were stored with their own count. PINs still use it. */
    private const val LEGACY_ITERATIONS = 120_000
    private const val ITERATIONS = 310_000
    private val common = setOf(
        "password", "password1", "password123", "12345678", "123456789", "1234567890", "qwertyui", "qwerty123",
        "11111111", "iloveyou", "letmein1", "welcome1", "admin123", "abc12345", "pockettheater", "00000000",
    )
    private val random = SecureRandom()
    private val usernamePattern = Regex("^[a-z0-9_]{3,20}$")

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    /** Passwords are never stored. Only this scrambled form is, and it cannot be turned back into the password. */
    fun hash(password: String, salt: ByteArray, iterations: Int = LEGACY_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Scrambles a password for storing, as "rounds:hash", so the number of rounds can be raised later without locking anyone out. */
    fun encode(password: String, salt: ByteArray): String =
        "$ITERATIONS:" + Base64.encodeToString(hash(password, salt, ITERATIONS), Base64.NO_WRAP)

    /** Checks a password against what [encode] stored. Older accounts stored just the hash, so those are still accepted. */
    fun check(password: String, salt: ByteArray, stored: String): Boolean {
        val iterations = if (stored.contains(':')) (stored.substringBefore(':').toIntOrNull() ?: return false) else LEGACY_ITERATIONS
        val expected = Base64.decode(stored.substringAfter(':', stored), Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(password, salt, iterations), expected)
    }

    /** Takes as long as a real check, so a wrong username cannot be told apart from a wrong password by timing. */
    fun spendTime(password: String) {
        hash(password, ByteArray(16), ITERATIONS)
    }

    fun matches(password: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(password, salt), expected)

    /** A temporary password the admin hands to someone after a reset. */
    fun newPassword(): String = (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** A short code for pairing a browser as admin. No letters or digits that look alike. */
    fun newCode(): String = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("").uppercase()

    fun newToken(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun validUsername(name: String): Boolean = usernamePattern.matches(name)

    /** A sentence saying what is wrong with the password, or null when it is fine. */
    fun passwordProblem(password: String): String? = when {
        password.length < 8 -> "Password needs at least 8 characters."
        password.length > 64 -> "Password can be at most 64 characters."
        password.lowercase() in common || password.toSet().size == 1 -> "That password is too easy to guess. Pick another one."
        else -> null
    }
}
