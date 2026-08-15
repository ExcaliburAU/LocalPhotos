package dev.exau.photos.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ExifShare {
    fun copyWithoutExif(context: Context, uri: Uri, displayName: String): Uri {
        val dest = File(context.cacheDir, "share_clean/${System.currentTimeMillis()}_$displayName.jpg")
        dest.parentFile?.mkdirs()
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: error("Could not read photo")
        try {
            dest.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    error("Could not strip location")
                }
            }
        } finally {
            bitmap.recycle()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", dest)
    }
}
