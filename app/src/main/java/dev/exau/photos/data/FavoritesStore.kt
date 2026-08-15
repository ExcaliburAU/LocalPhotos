package dev.exau.photos.data

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun ids(): Set<Long> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun toggle(id: Long): Set<Long> {
        val next = ids().toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet(KEY, next.map { it.toString() }.toSet()).apply()
        return next
    }

    companion object {
        private const val KEY = "ids"
    }
}
