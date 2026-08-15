package dev.exau.photos.files

data class FileRoot(
    val name: String,
    val path: String,
    val removable: Boolean,
)

data class SambaShare(
    val id: String,
    val host: String,
    val share: String,
    val username: String,
    val password: String,
    val domain: String,
    val port: Int = 445,
) {
    val title: String get() = if (share.isBlank()) host else "$share on $host"
}

data class FileEntry(
    val name: String,
    val relative: String,
    val isDir: Boolean,
    val isImage: Boolean,
    val isVideo: Boolean,
    val size: Long,
    val lastModified: Long,
) {
    val isMedia: Boolean get() = isImage || isVideo
}

sealed interface FileLocation {
    data object Roots : FileLocation
    data class Local(val rootPath: String, val rootName: String, val relative: String) : FileLocation
    data class Samba(val shareId: String, val relative: String) : FileLocation
}

data class SambaImage(
    val shareId: String,
    val path: String,
)

data class FileClipboard(
    val scopeKey: String,
    val items: List<FileEntry>,
    val cut: Boolean,
) {
    val label: String
        get() {
            val noun = if (items.size == 1) items.first().name else "${items.size} items"
            return if (cut) "Move $noun" else "Copy $noun"
        }
}

object FileKinds {
    private val previewImages = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "wbmp",
        "svg", "jxl", "dng",
    )
    private val previewVideos = setOf(
        "mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v", "mpg", "mpeg",
    )
    private val extraMime = mapOf(
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "jxl" to "image/jxl",
        "dng" to "image/x-adobe-dng",
        "cr2" to "image/x-canon-cr2",
        "nef" to "image/x-nikon-nef",
        "arw" to "image/x-sony-arw",
        "mkv" to "video/x-matroska",
        "m4v" to "video/mp4",
        "mts" to "video/mp2t",
        "m2ts" to "video/mp2t",
        "flac" to "audio/flac",
        "opus" to "audio/opus",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "wma" to "audio/x-ms-wma",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "pages" to "application/vnd.apple.pages",
        "numbers" to "application/vnd.apple.numbers",
        "key" to "application/vnd.apple.keynote",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "rtf" to "application/rtf",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz",
        "apk" to "application/vnd.android.package-archive",
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "json" to "application/json",
        "xml" to "application/xml",
        "csv" to "text/csv",
        "md" to "text/markdown",
        "yml" to "application/x-yaml",
        "yaml" to "application/x-yaml",
        "log" to "text/plain",
        "ics" to "text/calendar",
        "vcf" to "text/vcard",
        "gpx" to "application/gpx+xml",
        "kml" to "application/vnd.google-earth.kml+xml",
        "iso" to "application/x-iso9660-image",
    )

    fun isImage(name: String) = ext(name) in previewImages
    fun isVideo(name: String) = ext(name) in previewVideos
    fun isAudio(name: String) = mime(name).startsWith("audio/")

    fun mime(name: String): String {
        val e = ext(name)
        if (e.isBlank()) return "application/octet-stream"
        extraMime[e]?.let { return it }
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(e)
            ?: "application/octet-stream"
    }

    fun kindLabel(name: String): String {
        val m = mime(name)
        val e = ext(name)
        return when {
            isImage(name) || m.startsWith("image/") -> "Photo"
            isVideo(name) || m.startsWith("video/") -> "Video"
            m.startsWith("audio/") -> "Audio"
            m == "application/pdf" -> "PDF"
            m.startsWith("text/") -> "Text"
            m.contains("zip") || m.contains("rar") || m.contains("7z") ||
                m.contains("tar") || m.contains("gzip") || m.contains("compressed") -> "Archive"
            e.isNotBlank() -> e.uppercase()
            else -> "File"
        }
    }

    fun extLabel(name: String): String = ext(name).uppercase().ifBlank { "FILE" }

    fun join(parent: String, name: String): String =
        if (parent.isBlank()) name else "$parent/$name"

    fun parentOf(relative: String): String {
        val cut = relative.trim('/').substringBeforeLast('/', missingDelimiterValue = "")
        return cut
    }

    private fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()
}
