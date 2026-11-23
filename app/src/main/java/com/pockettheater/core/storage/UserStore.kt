package com.pockettheater.core.storage

import android.content.Context
import android.util.Base64
import com.pockettheater.core.security.Auth
import org.json.JSONArray
import org.json.JSONObject

enum class AccountStatus { PENDING, ACTIVE, OFF }

/** One person on the website. `version` goes up whenever the password changes or the account is turned off, which signs them out. */
data class Account(
    val name: String,
    val status: AccountStatus,
    val salt: String,
    val hash: String,
    val version: Int,
    val created: Long,
    val agreed: Long = 0L, // when they accepted the Terms of Use and Privacy Policy while asking to join (0 for older accounts)
)

/** Remembers who asked for access, who the admin approved, and each person's scrambled password. */
class UserStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("pocket_theater_users", Context.MODE_PRIVATE)

    fun all(): List<Account> = synchronized(LOCK) { read() }
    fun find(name: String): Account? = all().firstOrNull { it.name == name }
    fun pending(): List<Account> = all().filter { it.status == AccountStatus.PENDING }
    fun members(): List<Account> = all().filter { it.status != AccountStatus.PENDING }

    /** Changes whenever anything about the accounts changes, so the screen knows when to redraw. */
    fun revision(): Int = prefs.getString(KEY, "").orEmpty().hashCode()

    /** Saves a sign-up request. Returns null when saved, or a sentence saying why not. */
    fun register(name: String, password: String, agreedAt: Long): String? {
        val salt = Auth.newSalt()
        val hash = Auth.encode(password, salt)
        return synchronized(LOCK) {
            // Requests nobody answered for a week drop away, so they cannot fill every waiting place for good.
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val list = read().filterNot { it.status == AccountStatus.PENDING && it.created in 1 until weekAgo }
            when {
                list.any { it.name == name } -> "That username is taken. Pick another one."
                list.count { it.status == AccountStatus.PENDING } >= MAX_PENDING ->
                    "Too many requests are waiting. Ask the admin to clear them first."
                else -> {
                    write(list + Account(name, AccountStatus.PENDING, enc(salt), hash, 0, System.currentTimeMillis(), agreedAt))
                    null
                }
            }
        }
    }

    /** The account if the password is right (whatever its status), otherwise null. */
    fun check(name: String, password: String): Account? {
        val account = find(name)
        if (account == null) {
            Auth.spendTime(password) // same work as a real check, so timing does not reveal which names exist
            return null
        }
        return if (Auth.check(password, dec(account.salt), account.hash)) account else null
    }

    fun approve(name: String) = change(name) {
        if (it.status == AccountStatus.PENDING) it.copy(status = AccountStatus.ACTIVE) else it
    }

    fun decline(name: String) = synchronized(LOCK) {
        write(read().filterNot { it.name == name && it.status == AccountStatus.PENDING })
    }

    fun setOff(name: String, off: Boolean) = change(name) {
        if (it.status == AccountStatus.PENDING) it
        else it.copy(status = if (off) AccountStatus.OFF else AccountStatus.ACTIVE, version = it.version + 1)
    }

    /** Signs everyone out at once. They can sign in again with their passwords. */
    fun signOutAll() = synchronized(LOCK) { write(read().map { it.copy(version = it.version + 1) }) }

    fun delete(name: String) = synchronized(LOCK) { write(read().filter { it.name != name }) }

    fun setPassword(name: String, password: String) {
        val salt = Auth.newSalt()
        val hash = Auth.encode(password, salt)
        change(name) { it.copy(salt = enc(salt), hash = hash, version = it.version + 1) }
    }

    /** Gives the account a new random password and returns it, for the admin to pass on. */
    fun resetPassword(name: String): String {
        val fresh = Auth.newPassword()
        setPassword(name, fresh)
        return fresh
    }

    private fun change(name: String, edit: (Account) -> Account) = synchronized(LOCK) {
        write(read().map { if (it.name == name) edit(it) else it })
    }

    private fun read(): List<Account> {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Account(
                o.getString("name"), AccountStatus.valueOf(o.getString("status")),
                o.getString("salt"), o.getString("hash"), o.optInt("version", 0), o.optLong("created", 0L), o.optLong("agreed", 0L)
            )
        }
    }

    private fun write(list: List<Account>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("status", it.status.name).put("salt", it.salt)
                    .put("hash", it.hash).put("version", it.version).put("created", it.created).put("agreed", it.agreed)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun enc(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun dec(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val KEY = "users"
        private const val MAX_PENDING = 20
        private val LOCK = Any()
    }
}
