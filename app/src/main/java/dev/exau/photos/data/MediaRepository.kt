package dev.exau.photos.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MediaRepository(private val context: Context) {

    fun loadAll(): List<MediaItem> {
        val items = ArrayList<MediaItem>(512)
        items += queryImages(trashedOnly = false)
        items += queryVideos(trashedOnly = false)
        items.sortByDescending { it.dateTaken }
        return items
    }

    fun loadTrashed(): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val items = ArrayList<MediaItem>(64)
        items += queryImages(trashedOnly = true)
        items += queryVideos(trashedOnly = true)
        items.sortByDescending { it.dateTaken }
        return items
    }

    fun groupByDay(items: List<MediaItem>): List<MediaSection> {
        if (items.isEmpty()) return emptyList()
        val now = Calendar.getInstance()
        val grouped = linkedMapOf<String, MutableList<MediaItem>>()
        for (item in items) {
            val title = dayTitle(item.dateTaken, now)
            grouped.getOrPut(title) { mutableListOf() }.add(item)
        }
        return grouped.map { MediaSection(it.key, it.value) }
    }

    fun albums(items: List<MediaItem>): List<Album> {
        return items
            .groupBy { it.bucketId }
            .map { (id, list) ->
                val name = list.first().bucketName.ifBlank { "Other" }
                Album(
                    bucketId = id,
                    name = name,
                    cover = list.maxBy { it.dateTaken },
                    count = list.size,
                ) to list.maxOf { it.dateTaken }
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun scanCaptures() {
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val paths = LinkedHashSet<String>()
        listOf(dcim, File(dcim, "Camera"), pictures, File(pictures, "Camera")).forEach { dir ->
            if (!dir.isDirectory) return@forEach
            paths += dir.absolutePath
            dir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?.take(40)
                ?.forEach { paths += it.absolutePath }
        }
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun queryImages(trashedOnly: Boolean): List<MediaItem> =
        queryAllVolumes(image = true, trashedOnly = trashedOnly)

    private fun queryVideos(trashedOnly: Boolean): List<MediaItem> =
        queryAllVolumes(image = false, trashedOnly = trashedOnly)

    private fun queryAllVolumes(image: Boolean, trashedOnly: Boolean): List<MediaItem> {
        val uris = mediaUris(image)
        val seen = HashSet<String>()
        val items = ArrayList<MediaItem>()
        uris.forEach { uri ->
            query(uri, baseProjection(isVideo = !image), isVideo = !image, trashedOnly = trashedOnly).forEach { item ->
                if (seen.add(item.uri.toString())) items += item
            }
        }
        return items
    }

    private fun mediaUris(image: Boolean): List<android.net.Uri> {
        if (Build.VERSION.SDK_INT >= 29) {
            val volumes = MediaStore.getExternalVolumeNames(context)
            if (volumes.isNotEmpty()) {
                return volumes.map { volume ->
                    if (image) MediaStore.Images.Media.getContentUri(volume)
                    else MediaStore.Video.Media.getContentUri(volume)
                }
            }
        }
        return listOf(
            if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
    }

    private fun baseProjection(isVideo: Boolean): Array<String> {
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        if (isVideo) cols += MediaStore.Video.Media.DURATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cols += MediaStore.MediaColumns.DATE_EXPIRES
        }
        return cols.toTypedArray()
    }

    private fun query(
        collection: android.net.Uri,
        projection: Array<String>,
        isVideo: Boolean,
        trashedOnly: Boolean,
        includePending: Boolean = true,
    ): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        val sort = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(
                            MediaStore.QUERY_ARG_MATCH_TRASHED,
                            if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
                        )
                        if (!trashedOnly && includePending) {
                            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                        }
                    }
                }
                context.contentResolver.query(collection, projection, args, null)
            } else {
                context.contentResolver.query(collection, projection, null, null, sort)
            }
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val takenCol = it.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val addedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthCol = it.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = it.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val durationCol = if (isVideo) it.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                val bucketIdCol = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val expiresCol = it.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val takenRaw = if (takenCol >= 0) it.getLong(takenCol) else 0L
                    val added = it.getLong(addedCol)
                    val dateTaken = when {
                        takenRaw > 10_000_000_000L -> takenRaw
                        takenRaw > 1_000_000_000L -> takenRaw * 1000L
                        takenRaw > 0L -> takenRaw
                        else -> added * 1000L
                    }
                    val expiresRaw = if (expiresCol >= 0) it.getLong(expiresCol) else 0L
                    val dateExpires = when {
                        expiresRaw <= 0L -> 0L
                        expiresRaw > 10_000_000_000L -> expiresRaw
                        else -> expiresRaw * 1000L
                    }
                    result += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        mimeType = it.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                        displayName = it.getString(nameCol) ?: "media",
                        dateTaken = dateTaken,
                        sizeBytes = it.getLong(sizeCol),
                        width = if (widthCol >= 0) it.getInt(widthCol) else 0,
                        height = if (heightCol >= 0) it.getInt(heightCol) else 0,
                        durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L,
                        bucketId = if (bucketIdCol >= 0) it.getLong(bucketIdCol) else 0L,
                        bucketName = if (bucketNameCol >= 0) {
                            it.getString(bucketNameCol).orEmpty()
                        } else {
                            ""
                        },
                        dateExpires = dateExpires,
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: IllegalArgumentException) {
            return if (includePending) {
                query(collection, projection, isVideo, trashedOnly, includePending = false)
            } else {
                emptyList()
            }
        }
        return result
    }

    companion object {
        fun dayTitle(millis: Long, now: Calendar = Calendar.getInstance()): String {
            val day = Calendar.getInstance().apply { timeInMillis = millis }
            if (sameDay(day, now)) return "Today"
            val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (sameDay(day, yesterday)) return "Yesterday"
            val pattern = if (day.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "EEE d MMM" else "d MMM yyyy"
            return android.text.format.DateFormat.format(pattern, Date(millis)).toString()
        }

        fun fullDate(millis: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                .format(Date(millis))

        fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            return String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }

        fun formatDuration(ms: Long): String {
            val totalSec = (ms / 1000).coerceAtLeast(0)
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

        fun trashCountdown(expiresAt: Long): String {
            if (expiresAt <= 0L) return "In bin"
            val days = TimeUnit.MILLISECONDS.toDays(expiresAt - System.currentTimeMillis())
            return when {
                days <= 0L -> "Deletes soon"
                days == 1L -> "1 day left"
                else -> "$days days left"
            }
        }

        private fun sameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
