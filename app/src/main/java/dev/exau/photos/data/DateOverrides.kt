package dev.exau.photos.data

import android.content.Context

class DateOverrides(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("date_overrides", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_SCREENSHOT)) {
            prefs.edit().putLong(KEY_SCREENSHOT, 1_739_361_916_000L).apply()
        }
    }

    fun apply(items: List<MediaItem>): List<MediaItem> {
        if (prefs.all.isEmpty()) return items
        return items.map { item ->
            val taken = prefs.getLong(item.displayName.lowercase(), 0L)
            if (taken > 0L) item.copy(dateTaken = taken) else item
        }.sortedByDescending { it.dateTaken }
    }

    companion object {
        private const val KEY_SCREENSHOT = "screenshot_20250212-220516.png"
    }
}
