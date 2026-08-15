package dev.exau.photos.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object LocalFiles {
    fun roots(context: Context): List<FileRoot> {
        val result = ArrayList<FileRoot>()
        if (Build.VERSION.SDK_INT >= 24) {
            val sm = context.getSystemService(StorageManager::class.java)
            sm.storageVolumes.forEach { volume ->
                val dir = volumeDirectory(volume) ?: return@forEach
                val name = volume.getDescription(context).ifBlank {
                    if (volume.isPrimary) "Internal storage" else "Storage"
                }
                result += FileRoot(name = name, path = dir.absolutePath, removable = volume.isRemovable)
            }
        }
        if (result.isEmpty()) {
            @Suppress("DEPRECATION")
            val primary = Environment.getExternalStorageDirectory()
            result += FileRoot("Internal storage", primary.absolutePath, removable = false)
        }
        return result.distinctBy { it.path }
    }

    fun list(rootPath: String, relative: String): List<FileEntry> {
        val dir = if (relative.isBlank()) File(rootPath) else File(rootPath, relative)
        val children = dir.listFiles() ?: return emptyList()
        return children
            .map { child ->
                val rel = FileKinds.join(relative, child.name)
                FileEntry(
                    name = child.name,
                    relative = rel,
                    isDir = child.isDirectory,
                    isImage = child.isFile && FileKinds.isImage(child.name),
                    isVideo = child.isFile && FileKinds.isVideo(child.name),
                    size = if (child.isFile) child.length() else 0L,
                    lastModified = child.lastModified(),
                )
            }
            .sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun file(rootPath: String, relative: String): File =
        if (relative.isBlank()) File(rootPath) else File(rootPath, relative)

    fun mkdir(rootPath: String, relative: String) {
        val dir = file(rootPath, relative)
        if (!dir.mkdirs() && !dir.isDirectory) error("Could not create folder")
    }

    fun rename(rootPath: String, from: String, to: String) {
        val src = file(rootPath, from)
        val dest = file(rootPath, to)
        dest.parentFile?.mkdirs()
        if (!src.renameTo(dest)) error("Could not rename")
    }

    fun delete(rootPath: String, relative: String) {
        val target = file(rootPath, relative)
        val ok = if (target.isDirectory) target.deleteRecursively() else target.delete()
        if (!ok && target.exists()) error("Could not delete ${target.name}")
    }

    fun paste(
        rootPath: String,
        items: List<FileEntry>,
        destDir: String,
        cut: Boolean,
        existing: List<FileEntry>,
    ) {
        val names = existing.map { it.name }.toMutableSet()
        items.forEach { item ->
            val sameParent = FileKinds.parentOf(item.relative) == destDir
            if (cut && sameParent) return@forEach
            val name = FileOps.uniqueName(names, item.name)
            names += name
            val dest = FileKinds.join(destDir, name)
            val srcFile = file(rootPath, item.relative)
            val destFile = file(rootPath, dest)
            destFile.parentFile?.mkdirs()
            if (cut && srcFile.renameTo(destFile)) return@forEach
            copyLocal(srcFile, destFile)
            if (cut) delete(rootPath, item.relative)
        }
    }

    private fun copyLocal(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyLocal(child, File(dest, child.name))
            }
        } else {
            src.copyTo(dest, overwrite = false)
        }
    }

    private fun volumeDirectory(volume: android.os.storage.StorageVolume): File? {
        return if (Build.VERSION.SDK_INT >= 30) {
            volume.directory
        } else {
            try {
                val method = volume.javaClass.getMethod("getPathFile")
                method.invoke(volume) as? File
            } catch (_: Exception) {
                if (volume.isPrimary) {
                    @Suppress("DEPRECATION")
                    Environment.getExternalStorageDirectory()
                } else {
                    null
                }
            }
        }
    }
}
