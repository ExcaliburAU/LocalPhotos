package dev.exau.photos.data

import android.content.Context

class AlbumCovers(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun idFor(bucketId: Long): Long? {
        if (!prefs.contains(bucketId.toString())) return null
        val id = prefs.getLong(bucketId.toString(), -1L)
        return id.takeIf { it >= 0L }
    }

    fun set(bucketId: Long, mediaId: Long) {
        prefs.edit().putLong(bucketId.toString(), mediaId).apply()
    }

    companion object {
        private const val PREFS = "album_covers"
    }
}
