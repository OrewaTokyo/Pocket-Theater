package com.pockettheater.core.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SharedLibrary(val id: String, val name: String, val category: String, val uri: String)

/** Remembers which folders are shared. */
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

    /** Renames a shared folder or moves it to another shelf. The folder itself is not touched. */
    fun update(id: String, name: String, category: String) =
        save(libraries().map { if (it.id == id) it.copy(name = name, category = category) else it })

    private fun save(list: List<SharedLibrary>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category).put("uri", it.uri))
        }
        prefs.edit().putString(KEY_LIBS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_LIBS = "libraries"
        // These ids are saved on the phone, so never rename one (Games is shown as Gaming).
        val CATEGORIES = listOf("Series", "Anime", "Movies", "Games", "Documents", "Other")
    }
}
