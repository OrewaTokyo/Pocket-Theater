package com.pockettheater.core.server

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts what the theater does: how long it has been awake, how much data it sent, how many requests came in
 * and who is around. Totals are saved on the phone. The hourly chart starts fresh whenever the app restarts.
 */
object ServerStats {
    private const val HOURS = 12
    private const val HOUR_MS = 3_600_000L
    private const val ONLINE_MS = 3 * 60_000L

    class Snapshot(
        val awakeMs: Long,
        val lifetimeAwakeMs: Long,
        val sessionBytes: Long,
        val lifetimeBytes: Long,
        val sessionRequests: Long,
        val lifetimeRequests: Long,
        val online: Int,
        val bytesPerHour: LongArray,
    )

    private var prefs: SharedPreferences? = null
    private var baseBytes = 0L
    private var baseRequests = 0L
    private var baseMs = 0L
    private var sessionBytes = 0L
    private var sessionRequests = 0L
    private var startedAt = 0L // 0 while the theater is closed
    private var lastFlush = 0L
    private val buckets = LongArray(HOURS) // the last slot is the current hour
    private var bucketHour = 0L
    private val lastSeen = ConcurrentHashMap<String, Long>()

    @Synchronized
    private fun load(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("pocket_theater_stats", Context.MODE_PRIVATE)
        prefs = p
        baseBytes = p.getLong("bytes", 0L)
        baseRequests = p.getLong("requests", 0L)
        baseMs = p.getLong("awake_ms", 0L)
    }

    @Synchronized
    fun begin(context: Context) {
        load(context)
        if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun end() {
        baseMs += elapsed()
        startedAt = 0L
        baseBytes += sessionBytes
        baseRequests += sessionRequests
        sessionBytes = 0L
        sessionRequests = 0L
        lastSeen.clear()
        flush()
    }

    @Synchronized
    fun bytes(count: Long) {
        roll()
        sessionBytes += count
        buckets[HOURS - 1] += count
    }

    @Synchronized
    fun request() {
        sessionRequests++
    }

    /** Remembers that this person was just active on the site. */
    fun visit(user: String) {
        lastSeen[user] = System.currentTimeMillis()
    }

    @Synchronized
    fun snapshot(context: Context): Snapshot {
        load(context)
        roll()
        if (SystemClock.elapsedRealtime() - lastFlush > 60_000L) flush()
        val cutoff = System.currentTimeMillis() - ONLINE_MS
        val awake = elapsed()
        return Snapshot(
            awake, baseMs + awake,
            sessionBytes, baseBytes + sessionBytes,
            sessionRequests, baseRequests + sessionRequests,
            lastSeen.values.count { it > cutoff },
            buckets.copyOf()
        )
    }

    /** Sets the saved totals back to zero. The theater's current awake time keeps running. */
    @Synchronized
    fun resetTotals(context: Context) {
        load(context)
        baseBytes = 0L
        baseRequests = 0L
        baseMs = 0L
        sessionBytes = 0L
        sessionRequests = 0L
        buckets.fill(0L)
        flush()
    }

    private fun elapsed(): Long = if (startedAt == 0L) 0L else SystemClock.elapsedRealtime() - startedAt

    private fun roll() {
        val hour = System.currentTimeMillis() / HOUR_MS
        if (bucketHour == 0L) {
            bucketHour = hour
            return
        }
        val shift = (hour - bucketHour).coerceAtMost(HOURS.toLong()).toInt()
        if (shift <= 0) return
        if (shift >= HOURS) {
            buckets.fill(0L)
        } else {
            System.arraycopy(buckets, shift, buckets, 0, HOURS - shift)
            buckets.fill(0L, HOURS - shift, HOURS)
        }
        bucketHour = hour
    }

    private fun flush() {
        val p = prefs ?: return
        p.edit()
            .putLong("bytes", baseBytes + sessionBytes)
            .putLong("requests", baseRequests + sessionRequests)
            .putLong("awake_ms", baseMs + elapsed())
            .apply()
        lastFlush = SystemClock.elapsedRealtime()
    }
}
