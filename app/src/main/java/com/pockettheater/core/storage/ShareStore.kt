package com.pockettheater.core.storage

import android.content.Context
import com.pockettheater.core.security.Auth
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SharedLibrary(val id: String, val name: String, val category: String, val uri: String)

/** Remembers which folders are shared and the server password. */
class ShareStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("pocket_theater", Context.MODE_PRIVATE)

    fun libraries(): List<SharedLibrary> {
        val arr = JSONArray(prefs.getString(KEY_LIBS, "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SharedLibrary(o.getString("id"), o.getString("name"), o.getString("category"), o.getString("uri"))
        }
    }

    fun find(id: String?): SharedLibrary? = libraries().firstOrNull { it.id == id }

    fun add(name: String, category: String, uri: String) {
        val current = libraries()
        if (current.any { it.uri == uri }) return
        save(current + SharedLibrary(UUID.randomUUID().toString().take(8), name, category, uri))
    }

    fun remove(id: String) = save(libraries().filter { it.id != id })

    fun password(): String {
        val saved = prefs.getString(KEY_PASSWORD, null)
        if (saved != null) return saved
        return regeneratePassword()
    }

    fun regeneratePassword(): String {
        val fresh = Auth.newPassword()
        prefs.edit().putString(KEY_PASSWORD, fresh).apply()
        return fresh
    }

    private fun save(list: List<SharedLibrary>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category).put("uri", it.uri))
        }
        prefs.edit().putString(KEY_LIBS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_LIBS = "libraries"
        private const val KEY_PASSWORD = "password"
        val CATEGORIES = listOf("Movies", "Anime", "Games", "Documents", "Other")
    }
}
