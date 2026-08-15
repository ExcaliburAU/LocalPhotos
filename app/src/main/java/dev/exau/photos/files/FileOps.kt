package dev.exau.photos.files

object FileOps {
    fun sanitizeName(raw: String): String {
        val name = raw.trim().replace('/', '_').replace('\\', '_').replace('\u0000', '_')
        if (name.isBlank() || name == "." || name == "..") error("Enter a name")
        return name
    }

    fun uniqueName(existing: Collection<String>, desired: String): String {
        val taken = existing.map { it.lowercase() }.toHashSet()
        if (desired.lowercase() !in taken) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var index = 2
        while ("$base ($index)$ext".lowercase() in taken) index++
        return "$base ($index)$ext"
    }

    fun scopeKey(location: FileLocation): String? = when (location) {
        FileLocation.Roots -> null
        is FileLocation.Local -> "local:${location.rootPath}"
        is FileLocation.Samba -> "smb:${location.shareId}"
    }

    fun currentRelative(location: FileLocation): String = when (location) {
        FileLocation.Roots -> ""
        is FileLocation.Local -> location.relative
        is FileLocation.Samba -> location.relative
    }

    fun wouldNest(source: String, destDir: String): Boolean {
        val src = source.trim('/')
        val dest = destDir.trim('/')
        if (src.isBlank()) return true
        return dest == src || dest.startsWith("$src/")
    }

    fun newFolder(
        location: FileLocation,
        share: SambaShare?,
        browser: SambaBrowser,
        name: String,
        existing: List<FileEntry>,
    ) {
        val clean = uniqueName(existing.map { it.name }, sanitizeName(name))
        val relative = FileKinds.join(currentRelative(location), clean)
        when (location) {
            FileLocation.Roots -> error("Open a folder first")
            is FileLocation.Local -> LocalFiles.mkdir(location.rootPath, relative)
            is FileLocation.Samba -> {
                val smb = share ?: error("Samba share missing")
                browser.mutate(smb) { it.mkdir(relative) }
            }
        }
    }

    fun rename(
        location: FileLocation,
        share: SambaShare?,
        browser: SambaBrowser,
        entry: FileEntry,
        newName: String,
        existing: List<FileEntry>,
    ) {
        val clean = sanitizeName(newName)
        if (clean == entry.name) return
        val others = existing.filter { it.relative != entry.relative }.map { it.name }
        val finalName = uniqueName(others, clean)
        val dest = FileKinds.join(FileKinds.parentOf(entry.relative), finalName)
        when (location) {
            FileLocation.Roots -> error("Open a folder first")
            is FileLocation.Local -> LocalFiles.rename(location.rootPath, entry.relative, dest)
            is FileLocation.Samba -> {
                val smb = share ?: error("Samba share missing")
                browser.mutate(smb) { it.rename(entry.relative, dest) }
            }
        }
    }

    fun delete(
        location: FileLocation,
        share: SambaShare?,
        browser: SambaBrowser,
        entries: List<FileEntry>,
    ) {
        when (location) {
            FileLocation.Roots -> error("Open a folder first")
            is FileLocation.Local -> entries.forEach { LocalFiles.delete(location.rootPath, it.relative) }
            is FileLocation.Samba -> {
                val smb = share ?: error("Samba share missing")
                browser.mutate(smb) { session ->
                    entries.forEach { session.delete(it.relative, it.isDir) }
                }
            }
        }
    }

    fun paste(
        location: FileLocation,
        share: SambaShare?,
        browser: SambaBrowser,
        clipboard: FileClipboard,
        existing: List<FileEntry>,
    ) {
        if (scopeKey(location) != clipboard.scopeKey) {
            error("Paste in the same storage you copied from")
        }
        val destDir = currentRelative(location)
        clipboard.items.forEach { item ->
            if (item.isDir && wouldNest(item.relative, destDir)) {
                error("Can't move a folder into itself")
            }
        }
        when (location) {
            FileLocation.Roots -> error("Open a folder first")
            is FileLocation.Local -> LocalFiles.paste(location.rootPath, clipboard.items, destDir, clipboard.cut, existing)
            is FileLocation.Samba -> {
                val smb = share ?: error("Samba share missing")
                browser.mutate(smb) { session ->
                    pasteSamba(session, clipboard.items, destDir, clipboard.cut, existing)
                }
            }
        }
    }

    private fun pasteSamba(
        session: SambaMutator,
        items: List<FileEntry>,
        destDir: String,
        cut: Boolean,
        existing: List<FileEntry>,
    ) {
        val names = existing.map { it.name }.toMutableSet()
        items.forEach { item ->
            val sameParent = FileKinds.parentOf(item.relative) == destDir
            if (cut && sameParent) return@forEach
            val name = uniqueName(names, item.name)
            names += name
            val dest = FileKinds.join(destDir, name)
            copyTree(session, item, dest)
            if (cut) session.delete(item.relative, item.isDir)
        }
    }

    private fun copyTree(session: SambaMutator, source: FileEntry, dest: String) {
        if (source.isDir) {
            session.mkdir(dest)
            session.list(source.relative).forEach { child ->
                copyTree(session, child, FileKinds.join(dest, child.name))
            }
        } else {
            session.copyFile(source.relative, dest)
        }
    }

    fun zip(location: FileLocation, entries: List<FileEntry>) {
        val loc = location as? FileLocation.Local ?: error("Zip works on this phone’s storage")
        val name = if (entries.size == 1) {
            uniqueName(existingNames(loc), entries.first().name.substringBeforeLast('.') + ".zip")
        } else {
            uniqueName(existingNames(loc), "Archive.zip")
        }
        val dest = FileKinds.join(currentRelative(loc), name)
        Archives.zip(loc.rootPath, entries, dest)
    }

    fun unzip(location: FileLocation, entry: FileEntry) {
        val loc = location as? FileLocation.Local ?: error("Unzip works on this phone’s storage")
        if (!Archives.isZip(entry.name)) error("Pick a zip file")
        Archives.unzip(loc.rootPath, entry.relative)
    }

    fun hide(
        location: FileLocation,
        share: SambaShare?,
        browser: SambaBrowser,
        entries: List<FileEntry>,
        hidden: Boolean,
        existing: List<FileEntry>,
    ) {
        entries.forEach { entry ->
            val next = if (hidden) {
                if (entry.name.startsWith('.')) entry.name else ".${entry.name}"
            } else {
                entry.name.trimStart('.')
            }
            if (next.isBlank() || next == entry.name) return@forEach
            rename(location, share, browser, entry, next, existing)
        }
    }

    private fun existingNames(location: FileLocation.Local): List<String> =
        LocalFiles.list(location.rootPath, location.relative).map { it.name }
}
