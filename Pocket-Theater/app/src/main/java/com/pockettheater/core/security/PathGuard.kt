package com.pockettheater.core.security

/** Turns a requested path into safe folder names. Returns null if the path tries to escape. */
object PathGuard {
    fun segments(raw: String?): List<String>? {
        if (raw.isNullOrEmpty()) return emptyList()
        val parts = raw.split('/').filter { it.isNotEmpty() }
        for (p in parts) {
            if (p == "." || p == ".." || p.contains('\u0000') || p.contains('\\')) return null
        }
        return parts
    }
}
