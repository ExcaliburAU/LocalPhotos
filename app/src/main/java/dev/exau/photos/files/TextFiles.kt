package dev.exau.photos.files

import java.io.File
import java.nio.charset.Charset

object TextFiles {
    private val textExt = setOf(
        "txt", "md", "markdown", "json", "xml", "csv", "log", "yml", "yaml",
        "html", "htm", "css", "js", "kt", "kts", "java", "py", "sh", "ini",
        "conf", "cfg", "properties", "gradle", "gitignore", "ics", "vcf", "gpx", "kml",
    )

    fun isText(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in textExt) return true
        val mime = FileKinds.mime(name)
        return mime.startsWith("text/") || mime == "application/json" || mime == "application/xml"
    }

    fun read(file: File, maxBytes: Int = 1_500_000): String {
        if (!file.isFile) error("File not found")
        if (file.length() > maxBytes) error("This file is too large to edit here")
        val bytes = file.readBytes()
        return String(bytes, charsetOf(bytes))
    }

    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun charsetOf(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        return Charsets.UTF_8
    }
}
