package dev.exau.photos.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val dateExpires: Long = 0L,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val deviceAssetId: String get() = "local-$id-$sizeBytes"
}

data class Album(
    val bucketId: Long,
    val name: String,
    val cover: MediaItem,
    val count: Int,
)

data class MediaSection(
    val title: String,
    val items: List<MediaItem>,
)
