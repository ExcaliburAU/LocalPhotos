package dev.exau.photos.files

import android.os.StatFs
import java.io.File

data class StorageBucket(
    val label: String,
    val bytes: Long,
)

data class StorageItem(
    val name: String,
    val path: String,
    val bytes: Long,
    val isDir: Boolean,
)

data class StorageReport(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val buckets: List<StorageBucket>,
    val largest: List<StorageItem>,
)

object StorageScan {
    fun scan(roots: List<FileRoot>): StorageReport {
        var total = 0L
        var free = 0L
        roots.filterNot { it.removable }.forEach { root ->
            runCatching {
                val stat = StatFs(root.path)
                total = maxOf(total, stat.totalBytes)
                free = maxOf(free, stat.availableBytes)
            }
        }
        if (total == 0L && roots.isNotEmpty()) {
            runCatching {
                val stat = StatFs(roots.first().path)
                total = stat.totalBytes
                free = stat.availableBytes
            }
        }
        val types = LinkedHashMap<String, Long>()
        val largest = ArrayList<StorageItem>()
        roots.forEach { root ->
            walk(File(root.path), types, largest, 0)
        }
        largest.sortByDescending { it.bytes }
        val top = largest.take(40)
        val buckets = types.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { StorageBucket(it.key, it.value) }
        return StorageReport(
            totalBytes = total,
            freeBytes = free,
            usedBytes = (total - free).coerceAtLeast(0L),
            buckets = buckets,
            largest = top,
        )
    }

    private fun walk(
        file: File,
        types: MutableMap<String, Long>,
        largest: MutableList<StorageItem>,
        depth: Int,
    ): Long {
        if (depth > 18) return 0L
        if (file.isFile) {
            val size = runCatching { file.length() }.getOrDefault(0L)
            val label = when {
                FileKinds.isImage(file.name) -> "Photos"
                FileKinds.isVideo(file.name) -> "Videos"
                FileKinds.isAudio(file.name) -> "Audio"
                FileKinds.mime(file.name).contains("zip") || Archives.isZip(file.name) -> "Archives"
                else -> "Other"
            }
            types[label] = (types[label] ?: 0L) + size
            consider(largest, StorageItem(file.name, file.absolutePath, size, false))
            return size
        }
        if (!file.isDirectory) return 0L
        var sum = 0L
        val children = file.listFiles() ?: return 0L
        children.forEach { child ->
            sum += walk(child, types, largest, depth + 1)
        }
        if (depth > 0) {
            consider(largest, StorageItem(file.name, file.absolutePath, sum, true))
        }
        return sum
    }

    private fun consider(largest: MutableList<StorageItem>, item: StorageItem) {
        if (item.bytes < 2L * 1024L * 1024L) return
        largest += item
        if (largest.size > 80) {
            largest.sortByDescending { it.bytes }
            while (largest.size > 50) largest.removeAt(largest.lastIndex)
        }
    }
}
