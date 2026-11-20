package com.pockettheater.core.security

import java.util.concurrent.ConcurrentHashMap

/** Who is signed in right now. Kept in memory, so closing the theater signs everyone out. */
class SessionStore {
    private class Session(val user: String, val version: Int, val expires: Long)

    private val sessions = ConcurrentHashMap<String, Session>()

    fun start(user: String, version: Int): String {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expires < now }
        val token = Auth.newToken()
        sessions[token] = Session(user, version, now + Auth.SESSION_SECONDS * 1000)
        return token
    }

    /** The username and account version this token was given to, or null if it is unknown or has expired. */
    fun lookup(token: String?): Pair<String, Int>? {
        if (token.isNullOrEmpty()) return null
        val session = sessions[token] ?: return null
        if (session.expires < System.currentTimeMillis()) {
            sessions.remove(token)
            return null
        }
        return Pair(session.user, session.version)
    }

    fun end(token: String?) {
        if (!token.isNullOrEmpty()) sessions.remove(token)
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
