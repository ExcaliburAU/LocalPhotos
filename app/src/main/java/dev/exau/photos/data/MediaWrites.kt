package dev.exau.photos.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings

object MediaWrites {
    val canTrash: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun canManage(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun manageFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    fun trash(resolver: ContentResolver, uris: List<Uri>, toTrash: Boolean): PendingIntent? {
        if (!canTrash || uris.isEmpty()) return null
        return MediaStore.createTrashRequest(resolver, uris, toTrash)
    }

    fun delete(resolver: ContentResolver, uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, uris)
        } else {
            null
        }
    }

    fun setDateTaken(resolver: ContentResolver, uri: Uri, takenMillis: Long): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATE_TAKEN, takenMillis)
            put(MediaStore.MediaColumns.DATE_MODIFIED, takenMillis / 1000)
        }
        return try {
            val changed = resolver.update(uri, values, null, null)
            if (changed > 0) resolver.notifyChange(uri, null)
            changed > 0
        } catch (_: SecurityException) {
            false
        }
    }

    fun deleteLegacy(resolver: ContentResolver, uris: List<Uri>) {
        uris.forEach { resolver.delete(it, null, null) }
    }

    fun apply(resolver: ContentResolver, uris: List<Uri>, action: String): Boolean {
        if (uris.isEmpty()) return true
        return try {
            uris.all { uri -> applyOne(resolver, uri, action) }
        } catch (_: SecurityException) {
            false
        }
    }

    private fun applyOne(resolver: ContentResolver, uri: Uri, action: String): Boolean {
        val changed = when (action) {
            "trash" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                resolver.update(uri, trashValues(true), null, null)
            } else {
                resolver.delete(uri, null, null)
            }
            "restore" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                resolver.update(uri, trashValues(false), null, null)
            } else {
                0
            }
            "delete" -> resolver.delete(uri, null, null)
            else -> 0
        }
        if (changed > 0) resolver.notifyChange(uri, null)
        return changed > 0
    }

    private fun trashValues(toTrash: Boolean) = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_TRASHED, if (toTrash) 1 else 0)
    }
}
