package dev.exau.photos.edit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.max

object PhotoEditor {

    fun load(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth.coerceAtLeast(1)
        val h = bounds.outHeight.coerceAtLeast(1)
        val sample = sampleSize(max(w, h), maxSide)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Could not decode image")
        return applyExif(context, uri, decoded)
    }

    fun render(source: Bitmap, state: EditState): Bitmap {
        val oriented = transform(source, state.rotationDegrees, state.flipH, state.flipV)
        val cropped = crop(oriented, state.crop)
        if (oriented !== source && oriented !== cropped) oriented.recycle()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(state.colorMatrixValues())
        }
        var out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        if (state.strokes.isNotEmpty()) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            state.strokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                strokePaint.color = stroke.color.toInt()
                strokePaint.strokeWidth = stroke.width * out.width
                val path = android.graphics.Path()
                path.moveTo(stroke.points.first().first * out.width, stroke.points.first().second * out.height)
                stroke.points.drop(1).forEach { (x, y) ->
                    path.lineTo(x * out.width, y * out.height)
                }
                canvas.drawPath(path, strokePaint)
            }
        }
        if (cropped !== source && cropped !== out) cropped.recycle()
        val max = state.maxSide
        if (max > 0) {
            val longest = max(out.width, out.height)
            if (longest > max) {
                val scale = max.toFloat() / longest
                val resized = Bitmap.createScaledBitmap(
                    out,
                    (out.width * scale).toInt().coerceAtLeast(1),
                    (out.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (resized !== out) out.recycle()
                out = resized
            }
        }
        return out
    }

    fun save(context: Context, bitmap: Bitmap, suggestedName: String): Uri {
        val name = suggestedName
            .substringBeforeLast('.')
            .ifBlank { "Photo" }
            .plus("_edit.jpg")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Files",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values)
            ?: error("Could not create file")
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    error("Could not write JPEG")
                }
            } ?: error("Could not open output")
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun sampleSize(longest: Int, maxSide: Int): Int {
        var sample = 1
        while (longest / sample > maxSide * 2) sample *= 2
        return sample
    }

    private fun applyExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun transform(source: Bitmap, rotation: Int, flipH: Boolean, flipV: Boolean): Bitmap {
        if (rotation == 0 && !flipH && !flipV) return source
        val matrix = Matrix()
        val cx = source.width / 2f
        val cy = source.height / 2f
        if (flipH || flipV) matrix.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f, cx, cy)
        if (rotation != 0) matrix.postRotate(rotation.toFloat(), cx, cy)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun crop(source: Bitmap, crop: CropRect): Bitmap {
        val rect = crop.coerced()
        val left = (rect.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (rect.top * source.height).toInt().coerceIn(0, source.height - 1)
        val width = ((rect.right - rect.left) * source.width).toInt().coerceIn(1, source.width - left)
        val height = ((rect.bottom - rect.top) * source.height).toInt().coerceIn(1, source.height - top)
        if (left == 0 && top == 0 && width == source.width && height == source.height) return source
        return Bitmap.createBitmap(source, left, top, width, height)
    }
}
