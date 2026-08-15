package dev.exau.photos.files

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Archives {
    fun zip(rootPath: String, entries: List<FileEntry>, destRelative: String) {
        if (entries.isEmpty()) error("Select files to zip")
        val dest = LocalFiles.file(rootPath, destRelative)
        dest.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(dest.outputStream())).use { zip ->
            entries.forEach { entry ->
                add(zip, LocalFiles.file(rootPath, entry.relative), entry.name)
            }
        }
        if (!dest.isFile || dest.length() == 0L) {
            dest.delete()
            error("Could not create zip")
        }
    }

    fun unzip(rootPath: String, zipRelative: String) {
        val zipFile = LocalFiles.file(rootPath, zipRelative)
        if (!zipFile.isFile) error("Zip not found")
        val destDir = File(
            zipFile.parentFile ?: File(rootPath),
            zipFile.name.substringBeforeLast('.', zipFile.name),
        )
        destDir.mkdirs()
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(destDir, entry.name)
                val canonical = out.canonicalFile
                if (!canonical.path.startsWith(destDir.canonicalPath + File.separator) &&
                    canonical.path != destDir.canonicalPath
                ) {
                    error("Zip has an unsafe path")
                }
                if (entry.isDirectory) {
                    canonical.mkdirs()
                } else {
                    canonical.parentFile?.mkdirs()
                    canonical.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    fun isZip(name: String): Boolean = name.substringAfterLast('.', "").equals("zip", ignoreCase = true)

    private fun add(zip: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            val prefix = if (entryName.endsWith("/")) entryName else "$entryName/"
            zip.putNextEntry(ZipEntry(prefix))
            zip.closeEntry()
            file.listFiles()?.forEach { child ->
                add(zip, child, prefix + child.name)
            }
        } else if (file.isFile) {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
