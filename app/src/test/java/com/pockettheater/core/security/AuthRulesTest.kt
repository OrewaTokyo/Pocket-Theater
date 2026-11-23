package com.pockettheater.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {
    @Test
    fun usernamesAreShortLowercaseWords() {
        assertTrue(Auth.validUsername("anime_fan"))
        assertTrue(Auth.validUsername("tokyo2026"))
        assertFalse(Auth.validUsername("ab"))
        assertFalse(Auth.validUsername("UPPER"))
        assertFalse(Auth.validUsername("has space"))
        assertFalse(Auth.validUsername("a".repeat(21)))
    }

    @Test
    fun goodPasswordsPass() {
        assertNull(Auth.passwordProblem("blue-Heron-47"))
    }

    @Test
    fun weakPasswordsAreRefused() {
        assertNotNull(Auth.passwordProblem("short"))
        assertNotNull(Auth.passwordProblem("x".repeat(65)))
        assertNotNull(Auth.passwordProblem("Password"))
        assertNotNull(Auth.passwordProblem("12345678"))
        assertNotNull(Auth.passwordProblem("aaaaaaaa"))
    }
}
