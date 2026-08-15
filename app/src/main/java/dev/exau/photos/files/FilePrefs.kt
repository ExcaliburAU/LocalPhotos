package dev.exau.photos.files

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class FileSort {
    Name,
    Date,
    Size,
}

data class FavoriteFolder(
    val name: String,
    val rootPath: String,
    val rootName: String,
    val relative: String,
) {
    val key: String get() = "$rootPath\n$relative"
    val subtitle: String
        get() = if (relative.isBlank()) rootName else "$rootName/$relative"
}

class FilePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var showHidden: Boolean
        get() = prefs.getBoolean("show_hidden", false)
        set(value) { prefs.edit().putBoolean("show_hidden", value).apply() }

    var sort: FileSort
        get() = FileSort.entries.getOrNull(prefs.getInt("sort", 0)) ?: FileSort.Name
        set(value) { prefs.edit().putInt("sort", value.ordinal).apply() }

    fun favorites(): List<FavoriteFolder> {
        val raw = prefs.getString("favorites", "[]").orEmpty()
        val arr = JSONArray(raw)
        val list = ArrayList<FavoriteFolder>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += FavoriteFolder(
                name = o.optString("name"),
                rootPath = o.optString("rootPath"),
                rootName = o.optString("rootName"),
                relative = o.optString("relative"),
            )
        }
        return list
    }

    fun isFavorite(rootPath: String, relative: String): Boolean =
        favorites().any { it.rootPath == rootPath && it.relative == relative }

    fun toggleFavorite(folder: FavoriteFolder): List<FavoriteFolder> {
        val current = favorites()
        val next = if (current.any { it.key == folder.key }) {
            current.filterNot { it.key == folder.key }
        } else {
            current + folder
        }
        val arr = JSONArray()
        next.forEach { item ->
            arr.put(
                JSONObject()
                    .put("name", item.name)
                    .put("rootPath", item.rootPath)
                    .put("rootName", item.rootName)
                    .put("relative", item.relative),
            )
        }
        prefs.edit().putString("favorites", arr.toString()).apply()
        return next
    }

    companion object {
        private const val PREFS = "file_prefs"
    }
}
