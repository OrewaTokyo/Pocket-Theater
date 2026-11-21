package com.pockettheater.core.security

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Who is signed in right now, on which browser. Kept in memory, so closing the theater signs everyone out.
 * The same object also remembers which browsers the admin has opened the admin website on, and any QR pairing in progress.
 */
class SessionStore {
    /** A signed-in browser, without any secret. `id` is only a handle for the phone and the admin website. */
    class Device(
        val id: String,
        val user: String,
        val label: String,
        val ip: String,
        val since: Long,
        val lastSeen: Long,
        val admin: Boolean,
    )

    private class Session(
        val id: String,
        val user: String,
        val version: Int,
        val expires: Long,
        val label: String,
        val ip: String,
        val since: Long,
    ) {
        @Volatile var lastSeen = since
        @Volatile var adminUntil = 0L
        @Volatile var adminLimit = 0L
        @Volatile var pairCode: String? = null
        @Volatile var pairExpires = 0L
    }

    private val sessions = ConcurrentHashMap<String, Session>()

    fun start(user: String, version: Int, ip: String, label: String): String {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expires < now }
        val token = Auth.newToken()
        val id = Auth.newToken().take(16)
        sessions[token] = Session(id, user, version, now + Auth.SESSION_SECONDS * 1000, label, ip, now)
        return token
    }

    /** The username and account version this token was given to, or null if it is unknown or has expired. */
    fun lookup(token: String?): Pair<String, Int>? {
        if (token.isNullOrEmpty()) return null
        val session = sessions[token] ?: return null
        val now = System.currentTimeMillis()
        if (session.expires < now) {
            sessions.remove(token)
            return null
        }
        session.lastSeen = now
        return Pair(session.user, session.version)
    }

    fun end(token: String?) {
        if (!token.isNullOrEmpty()) sessions.remove(token)
    }

    fun clear() = sessions.clear()

    // ---- devices ----

    fun devices(): List<Device> {
        val now = System.currentTimeMillis()
        return sessions.values.filter { it.expires >= now }
            .sortedByDescending { it.lastSeen }
            .map { Device(it.id, it.user, it.label, it.ip, it.since, it.lastSeen, it.adminUntil > now) }
    }

    fun deviceId(token: String?): String? = if (token == null) null else sessions[token]?.id

    fun endDevice(id: String) {
        sessions.entries.removeIf { it.value.id == id }
    }

    private fun byId(id: String): Session? = sessions.values.firstOrNull { it.id == id }

    // ---- QR pairing and admin ----

    /** Starts a pairing for this device and returns the code its screen will show, or null if the device is gone. */
    fun beginPairing(deviceId: String): String? {
        val session = byId(deviceId) ?: return null
        val code = Auth.newCode()
        session.pairCode = code
        session.pairExpires = System.currentTimeMillis() + PAIR_MS
        return code
    }

    fun cancelPairing(deviceId: String) {
        byId(deviceId)?.pairCode = null
    }

    /** The pairing code waiting on this browser and when it runs out, or null. */
    fun pairing(token: String?): Pair<String, Long>? {
        val session = if (token == null) null else sessions[token]
        val code = session?.pairCode ?: return null
        if (session.pairExpires < System.currentTimeMillis()) {
            session.pairCode = null
            return null
        }
        return Pair(code, session.pairExpires)
    }

    /** True if this code is the live one for this device. Does not use it up. */
    fun pairingMatches(deviceId: String, code: String): Boolean {
        val session = byId(deviceId) ?: return false
        val expected = session.pairCode ?: return false
        if (session.pairExpires < System.currentTimeMillis()) return false
        return MessageDigest.isEqual(expected.toByteArray(), code.trim().uppercase().toByteArray())
    }

    /** Uses the code up and opens the admin website on that device for a limited time. */
    fun completePairing(deviceId: String, code: String): Boolean {
        if (!pairingMatches(deviceId, code)) return false
        val session = byId(deviceId) ?: return false
        val now = System.currentTimeMillis()
        session.pairCode = null
        session.adminLimit = now + ADMIN_MAX_MS
        session.adminUntil = now + ADMIN_IDLE_MS
        return true
    }

    /** Whether this browser may use the admin website. Using it (`extend`) keeps it open for another half hour, up to 4 hours in all. */
    fun isAdmin(token: String?, extend: Boolean): Boolean {
        val session = if (token == null) null else sessions[token]
        if (session == null) return false
        val now = System.currentTimeMillis()
        if (session.adminUntil <= now) return false
        if (extend) session.adminUntil = minOf(now + ADMIN_IDLE_MS, session.adminLimit)
        return session.adminUntil > now
    }

    fun revokeAdmin(deviceId: String) {
        byId(deviceId)?.let {
            it.adminUntil = 0L
            it.pairCode = null
        }
    }

    companion object {
        /** The one list of sign-ins, shared by the website server and the phone's screens. */
        val shared = SessionStore()
        private const val PAIR_MS = 2 * 60_000L
        private const val ADMIN_IDLE_MS = 30 * 60_000L
        private const val ADMIN_MAX_MS = 4 * 3_600_000L
    }
}

/** Slows down guessing: after too many tries from one device, it has to wait. */
class LoginThrottle(private val maxHits: Int, private val windowMs: Long) {
    private val hits = HashMap<String, MutableList<Long>>()

    @Synchronized
    fun blocked(key: String): Boolean = recent(key).size >= maxHits

    @Synchronized
    fun hit(key: String) {
        recent(key).add(System.currentTimeMillis())
    }

    @Synchronized
    fun clear(key: String) {
        hits.remove(key)
    }

    private fun recent(key: String): MutableList<Long> {
        val cutoff = System.currentTimeMillis() - windowMs
        val list = hits.getOrPut(key) { mutableListOf() }
        list.removeAll { it < cutoff }
        return list
    }
}
