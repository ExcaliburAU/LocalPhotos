package dev.exau.photos

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.exau.photos.data.Album
import dev.exau.photos.data.AlbumCovers
import dev.exau.photos.data.FavoritesStore
import dev.exau.photos.data.DateOverrides
import dev.exau.photos.data.MediaItem
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.files.FileClipboard
import dev.exau.photos.files.FileEntry
import dev.exau.photos.files.FileKinds
import dev.exau.photos.files.FileLocation
import dev.exau.photos.files.FileOps
import dev.exau.photos.files.FilePrefs
import dev.exau.photos.files.FileRoot
import dev.exau.photos.files.FileSort
import dev.exau.photos.files.FavoriteFolder
import dev.exau.photos.files.LocalFiles
import dev.exau.photos.files.SambaShare
import dev.exau.photos.files.StorageReport
import dev.exau.photos.files.StorageScan
import dev.exau.photos.immich.ImmichAsset
import dev.exau.photos.immich.ImmichClient
import dev.exau.photos.immich.ImmichNetwork
import dev.exau.photos.immich.ImmichPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotosViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MediaRepository(application)
    private val favoritesStore = FavoritesStore(application)
    private val albumCovers = AlbumCovers(application)
    private val dateOverrides = DateOverrides(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    val immichPrefs = ImmichPrefs(application)
    private val immich = ImmichClient(immichPrefs)
    val sambaBrowser = getApplication<PhotosApplication>().sambaBrowser
    private val sambaPrefs = getApplication<PhotosApplication>().sambaPrefs
    private val filePrefs = FilePrefs(application)

    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var trashed by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var favorites by mutableStateOf<Set<Long>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set

    var immichConnected by mutableStateOf(immichPrefs.connected)
        private set
    var immichUser by mutableStateOf(immichPrefs.userName)
        private set
    var immichAssets by mutableStateOf<List<ImmichAsset>>(emptyList())
        private set
    var immichMessage by mutableStateOf<String?>(null)
        private set
    var immichBusy by mutableStateOf(false)
        private set
    var backupProgress by mutableStateOf<String?>(null)
        private set
    var immichHasMore by mutableStateOf(false)
        private set
    var immichLoadingMore by mutableStateOf(false)
        private set
    private var immichPage = 1
    private var loadMoreJob: Job? = null

    var fileLocation by mutableStateOf<FileLocation>(FileLocation.Roots)
        private set
    var fileRoots by mutableStateOf<List<FileRoot>>(emptyList())
        private set
    var sambaShares by mutableStateOf<List<SambaShare>>(sambaPrefs.shares())
        private set
    var fileEntries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var fileBusy by mutableStateOf(false)
        private set
    var fileMessage by mutableStateOf<String?>(null)
        private set
    var fileClipboard by mutableStateOf<FileClipboard?>(null)
        private set
    var fileToast by mutableStateOf<String?>(null)
        private set
    var showHidden by mutableStateOf(filePrefs.showHidden)
        private set
    var fileSort by mutableStateOf(filePrefs.sort)
        private set
    var favoriteFolders by mutableStateOf(filePrefs.favorites())
        private set
    var storageReport by mutableStateOf<StorageReport?>(null)
        private set
    var storageBusy by mutableStateOf(false)
        private set

    fun clearFileToast() {
        fileToast = null
    }

    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    val favoriteItems: List<MediaItem> get() = items.filter { it.id in favorites }

    private var refreshJob: Job? = null
    private val refreshRunnable = Runnable { refresh() }

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = scheduleRefresh()
        override fun onChange(selfChange: Boolean, uri: Uri?) = scheduleRefresh()
    }

    init {
        favorites = favoritesStore.ids()
        val cr = application.contentResolver
        cr.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        cr.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        if (immichConnected) refreshImmich()
    }

    private fun scheduleRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, 900)
    }

    fun refresh() {
        refreshJob?.cancel()
        val showSpinner = items.isEmpty()
        refreshJob = viewModelScope.launch {
            if (showSpinner) loading = true
            val loaded = withContext(Dispatchers.IO) {
                runCatching { repo.scanCaptures() }
                val library = dateOverrides.apply(
                    runCatching { repo.loadAll() }.getOrDefault(emptyList()),
                )
                val bin = dateOverrides.apply(
                    runCatching { repo.loadTrashed() }.getOrDefault(emptyList()),
                )
                library to bin
            }
            items = loaded.first
            trashed = loaded.second
            albums = applyAlbumCovers(repo.albums(loaded.first))
            favorites = favoritesStore.ids()
            loading = false
        }
    }

    fun toggleFavorite(id: Long) {
        favorites = favoritesStore.toggle(id)
    }

    fun setAlbumCover(item: MediaItem) {
        if (item.isVideo) {
            fileToast = "Pick a photo"
            return
        }
        albumCovers.set(item.bucketId, item.id)
        albums = applyAlbumCovers(repo.albums(items))
        fileToast = "Cover set"
    }

    fun setFolderCover(entry: FileEntry) {
        if (!entry.isImage) {
            fileToast = "Pick a photo"
            return
        }
        val key = folderCoverKey(fileLocation) ?: return
        filePrefs.setFolderCover(key, entry.relative)
        fileToast = "Cover set"
    }

    fun itemByUri(uri: Uri): MediaItem? = items.find { it.uri == uri }

    fun connectImmich(url: String, email: String, password: String) {
        viewModelScope.launch {
            immichBusy = true
            immichMessage = null
            val result = withContext(Dispatchers.IO) {
                runCatching { immich.login(url, email, password) }
            }
            result.onSuccess { user ->
                immichConnected = true
                immichUser = user.name
                immichMessage = "Signed in as ${user.name}"
                refreshImmich()
            }.onFailure {
                immichMessage = it.message ?: "Could not sign in"
            }
            immichBusy = false
        }
    }

    fun disconnectImmich() {
        immichPrefs.clear()
        immichConnected = false
        immichUser = ""
        immichAssets = emptyList()
        backupProgress = null
        immichHasMore = false
        immichPage = 1
        immichMessage = "Disconnected"
    }

    fun refreshImmich() {
        if (!immichPrefs.connected) return
        loadMoreJob?.cancel()
        viewModelScope.launch {
            immichBusy = true
            immichLoadingMore = false
            val result = withContext(Dispatchers.IO) {
                runCatching { immich.listAssets(page = 1) }
            }
            result.onSuccess { page ->
                immichAssets = page.items
                immichPage = 1
                immichHasMore = page.nextPage != null && page.items.isNotEmpty()
            }.onFailure { immichMessage = it.message ?: "Could not load Immich" }
            immichBusy = false
        }
    }

    fun loadMoreImmich() {
        if (!immichPrefs.connected || !immichHasMore || immichBusy || immichLoadingMore) return
        val next = immichPage + 1
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            immichLoadingMore = true
            val result = withContext(Dispatchers.IO) {
                runCatching { immich.listAssets(page = next) }
            }
            result.onSuccess { page ->
                val seen = immichAssets.map { it.id }.toSet()
                immichAssets = immichAssets + page.items.filter { it.id !in seen }
                immichPage = next
                immichHasMore = page.nextPage != null && page.items.isNotEmpty()
            }.onFailure {
                immichHasMore = false
            }
            immichLoadingMore = false
        }
    }

    fun backupOne(item: MediaItem) {
        if (!immichPrefs.connected) {
            immichMessage = "Connect Immich first"
            return
        }
        if (!ImmichNetwork.onWifi(getApplication())) {
            immichMessage = ImmichNetwork.WIFI_ONLY_MESSAGE
            return
        }
        viewModelScope.launch {
            immichBusy = true
            backupProgress = "Checking ${item.displayName}…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    if (item.deviceAssetId in immichPrefs.rememberedAssetIds()) {
                        return@runCatching "Already on Immich"
                    }
                    val existing = immich.existingDeviceAssets(listOf(item.deviceAssetId))
                    if (item.deviceAssetId in existing) {
                        immichPrefs.rememberUploaded(listOf(item.deviceAssetId))
                        return@runCatching "Already on Immich"
                    }
                    val (skipped, sums) = immich.alreadyOnServerByChecksum(
                        resolver,
                        listOf(item),
                        immichPrefs.rememberedChecksums(),
                    )
                    if (item.deviceAssetId in skipped) {
                        immichPrefs.rememberUploaded(listOf(item.deviceAssetId), sums)
                        return@runCatching "Already on Immich"
                    }
                    val status = immich.upload(resolver, item)
                    immichPrefs.rememberUploaded(listOf(item.deviceAssetId), sums)
                    if (status.equals("duplicate", ignoreCase = true)) "Already on Immich" else "Backed up to Immich"
                }
            }
            result.onSuccess {
                immichMessage = it
                refreshImmich()
            }.onFailure {
                immichMessage = it.message ?: "Upload failed"
            }
            backupProgress = null
            immichBusy = false
        }
    }

    fun backupNew() {
        if (!immichPrefs.connected) return
        if (!ImmichNetwork.onWifi(getApplication())) {
            immichMessage = ImmichNetwork.WIFI_ONLY_MESSAGE
            return
        }
        viewModelScope.launch {
            immichBusy = true
            val local = items
            var uploaded = 0
            var skipped = 0
            var failed = 0
            var pausedForWifi = false
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val remembered = immichPrefs.rememberedAssetIds()
                var pending = local.filter { it.deviceAssetId !in remembered }
                skipped = local.size - pending.size
                backupProgress = "Checking Immich…"
                val existing = pending.map { it.deviceAssetId }.chunked(80).flatMap { chunk ->
                    runCatching { immich.existingDeviceAssets(chunk) }.getOrDefault(emptySet())
                }.toSet()
                if (existing.isNotEmpty()) {
                    immichPrefs.rememberUploaded(existing)
                    skipped += pending.count { it.deviceAssetId in existing }
                    pending = pending.filter { it.deviceAssetId !in existing }
                }
                if (pending.isNotEmpty()) {
                    val (byChecksum, sums) = immich.alreadyOnServerByChecksum(
                        resolver,
                        pending,
                        immichPrefs.rememberedChecksums(),
                    ) { done, total ->
                        backupProgress = "Checking $done of $total"
                    }
                    if (byChecksum.isNotEmpty()) {
                        immichPrefs.rememberUploaded(byChecksum, sums)
                        skipped += pending.count { it.deviceAssetId in byChecksum }
                        pending = pending.filter { it.deviceAssetId !in byChecksum }
                    }
                }
                pending.forEachIndexed { index, item ->
                    if (!ImmichNetwork.onWifi(getApplication())) {
                        pausedForWifi = true
                        return@withContext
                    }
                    backupProgress = "Backing up ${index + 1} of ${pending.size}"
                    val status = runCatching { immich.upload(resolver, item) }
                    if (status.isSuccess) {
                        val value = status.getOrNull().orEmpty()
                        immichPrefs.rememberUploaded(listOf(item.deviceAssetId))
                        if (value.equals("duplicate", ignoreCase = true)) skipped++ else uploaded++
                    } else {
                        failed++
                    }
                }
            }
            backupProgress = null
            immichBusy = false
            immichMessage = if (pausedForWifi) {
                buildString {
                    append("Backup paused on mobile data")
                    if (uploaded > 0) append(" · $uploaded new")
                    append(" · waiting for Wi-Fi")
                }
            } else {
                buildString {
                    append("Backup done · $uploaded new")
                    if (skipped > 0) append(" · $skipped already there")
                    if (failed > 0) append(" · $failed failed")
                }
            }
            refreshImmich()
        }
    }

    fun refreshFiles() {
        val app = getApplication<Application>()
        fileRoots = LocalFiles.roots(app)
        sambaShares = sambaPrefs.shares()
        val location = fileLocation
        if (location is FileLocation.Roots) {
            fileEntries = emptyList()
            fileBusy = false
            return
        }
        viewModelScope.launch {
            fileBusy = true
            fileMessage = null
            val result = withContext(Dispatchers.IO) {
                runCatching { listLocation(location) }
            }
            result.onSuccess { fileEntries = present(it) }
                .onFailure { fileMessage = friendlyFileError(it) }
            fileBusy = false
        }
    }

    fun openLocalRoot(root: FileRoot) {
        fileLocation = FileLocation.Local(root.path, root.name, "")
        refreshFiles()
    }

    fun openSamba(share: SambaShare) {
        fileLocation = FileLocation.Samba(share.id, "")
        refreshFiles()
    }

    fun openFileDir(entry: FileEntry) {
        if (!entry.isDir) return
        fileLocation = when (val loc = fileLocation) {
            FileLocation.Roots -> loc
            is FileLocation.Local -> loc.copy(relative = entry.relative)
            is FileLocation.Samba -> loc.copy(relative = entry.relative)
        }
        refreshFiles()
    }

    fun fileGoUp(): Boolean {
        val loc = fileLocation
        if (loc is FileLocation.Roots) return false
        fileLocation = when (loc) {
            FileLocation.Roots -> loc
            is FileLocation.Local -> {
                val parent = FileKinds.parentOf(loc.relative)
                if (loc.relative.isBlank()) FileLocation.Roots
                else loc.copy(relative = parent)
            }
            is FileLocation.Samba -> {
                val parent = FileKinds.parentOf(loc.relative)
                if (loc.relative.isBlank()) FileLocation.Roots
                else loc.copy(relative = parent)
            }
        }
        refreshFiles()
        return true
    }

    fun addSamba(host: String, share: String, username: String, password: String) {
        viewModelScope.launch {
            fileBusy = true
            fileMessage = null
            val candidate = sambaPrefs.newShare(host, share, username, password)
            if (candidate.host.isBlank() || candidate.share.isBlank()) {
                fileMessage = "Enter a host and share name, like 192.168.1.10 and photos"
                fileBusy = false
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { sambaBrowser.list(candidate, "") }
            }
            result.onSuccess {
                sambaShares = sambaPrefs.upsert(candidate)
                fileLocation = FileLocation.Samba(candidate.id, "")
                fileEntries = present(it)
                fileMessage = null
            }.onFailure {
                fileMessage = friendlyFileError(it)
            }
            fileBusy = false
        }
    }

    fun removeSamba(id: String) {
        sambaShares = sambaPrefs.remove(id)
        if ((fileLocation as? FileLocation.Samba)?.shareId == id) {
            fileLocation = FileLocation.Roots
            fileEntries = emptyList()
        }
    }

    fun canPaste(): Boolean {
        val clip = fileClipboard ?: return false
        return FileOps.scopeKey(fileLocation) == clip.scopeKey
    }

    fun copyToClipboard(entries: List<FileEntry>, cut: Boolean) {
        val key = FileOps.scopeKey(fileLocation) ?: return
        if (entries.isEmpty()) return
        fileClipboard = FileClipboard(key, entries, cut)
        fileMessage = null
    }

    fun clearClipboard() {
        fileClipboard = null
    }

    fun newFolder(name: String) = runFileOp(done = "Folder created") {
        FileOps.newFolder(fileLocation, currentSambaShare(), sambaBrowser, name, fileEntries)
    }

    fun renameFile(entry: FileEntry, name: String) = runFileOp(done = "Renamed") {
        FileOps.rename(fileLocation, currentSambaShare(), sambaBrowser, entry, name, fileEntries)
    }

    fun deleteFiles(entries: List<FileEntry>) = runFileOp(done = "Deleted") {
        FileOps.delete(fileLocation, currentSambaShare(), sambaBrowser, entries)
    }

    fun pasteFiles() {
        val clip = fileClipboard ?: return
        runFileOp(clearClip = clip.cut, done = if (clip.cut) "Moved" else "Copied") {
            FileOps.paste(fileLocation, currentSambaShare(), sambaBrowser, clip, fileEntries)
        }
    }

    fun zipFiles(entries: List<FileEntry>) = runFileOp(done = "Zipped") {
        FileOps.zip(fileLocation, entries)
    }

    fun unzipFile(entry: FileEntry) = runFileOp(done = "Unzipped") {
        FileOps.unzip(fileLocation, entry)
    }

    fun hideFiles(entries: List<FileEntry>, hidden: Boolean) = runFileOp(
        done = if (hidden) "Hidden" else "Unhidden",
    ) {
        FileOps.hide(fileLocation, currentSambaShare(), sambaBrowser, entries, hidden, fileEntries)
    }

    fun setHiddenFiles(value: Boolean) {
        showHidden = value
        filePrefs.showHidden = value
        refreshFiles()
    }

    fun setSortMode(sort: FileSort) {
        fileSort = sort
        filePrefs.sort = sort
        refreshFiles()
    }

    fun currentFolderFavorite(): Boolean {
        val loc = fileLocation as? FileLocation.Local ?: return false
        return filePrefs.isFavorite(loc.rootPath, loc.relative)
    }

    fun toggleFavoriteFolder() {
        val loc = fileLocation as? FileLocation.Local ?: return
        val name = loc.relative.substringAfterLast('/').ifBlank { loc.rootName }
        favoriteFolders = filePrefs.toggleFavorite(
            FavoriteFolder(name, loc.rootPath, loc.rootName, loc.relative),
        )
        fileToast = if (filePrefs.isFavorite(loc.rootPath, loc.relative)) "Favorite added" else "Favorite removed"
    }

    fun openFavorite(folder: FavoriteFolder) {
        fileLocation = FileLocation.Local(folder.rootPath, folder.rootName, folder.relative)
        refreshFiles()
    }

    fun openAbsolute(path: String) {
        val file = java.io.File(path)
        val root = fileRoots.find { path.startsWith(it.path) } ?: fileRoots.firstOrNull() ?: return
        val target = if (file.isDirectory) file else file.parentFile ?: file
        val relative = target.absolutePath.removePrefix(root.path).trim('/')
        fileLocation = FileLocation.Local(root.path, root.name, relative)
        refreshFiles()
    }

    fun scanStorage() {
        viewModelScope.launch {
            storageBusy = true
            storageReport = withContext(Dispatchers.IO) {
                runCatching { StorageScan.scan(LocalFiles.roots(getApplication())) }.getOrNull()
            }
            storageBusy = false
        }
    }

    private fun present(entries: List<FileEntry>): List<FileEntry> {
        val visible = if (showHidden) entries else entries.filter { !it.name.startsWith('.') }
        val sorted = when (fileSort) {
            FileSort.Name -> visible.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
            FileSort.Date -> visible.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenByDescending { it.lastModified })
            FileSort.Size -> visible.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenByDescending { it.size })
        }
        return applyFolderCovers(sorted)
    }

    private fun applyFolderCovers(entries: List<FileEntry>): List<FileEntry> {
        val loc = fileLocation
        return entries.map { entry ->
            if (!entry.isDir) return@map entry
            val key = folderCoverKey(loc, entry.relative) ?: return@map entry
            val chosen = filePrefs.folderCover(key) ?: return@map entry
            if (loc is FileLocation.Local && !LocalFiles.file(loc.rootPath, chosen).isFile) return@map entry
            entry.copy(coverRelative = chosen)
        }
    }

    private fun folderCoverKey(location: FileLocation, folderRelative: String? = null): String? = when (location) {
        FileLocation.Roots -> null
        is FileLocation.Local -> FilePrefs.folderKey("local", location.rootPath, folderRelative ?: location.relative)
        is FileLocation.Samba -> FilePrefs.folderKey("smb", location.shareId, folderRelative ?: location.relative)
    }

    private fun applyAlbumCovers(list: List<Album>): List<Album> = list.map { album ->
        val id = albumCovers.idFor(album.bucketId) ?: return@map album
        val cover = items.find { it.id == id && it.bucketId == album.bucketId } ?: return@map album
        album.copy(cover = cover)
    }

    private fun runFileOp(clearClip: Boolean = false, done: String? = null, block: () -> Unit) {
        viewModelScope.launch {
            fileBusy = true
            fileMessage = null
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            result.onFailure { fileToast = friendlyFileError(it) }
                .onSuccess {
                    if (clearClip) fileClipboard = null
                    if (!done.isNullOrBlank()) fileToast = done
                }
            val location = fileLocation
            if (location !is FileLocation.Roots) {
                val listed = withContext(Dispatchers.IO) { runCatching { listLocation(location) } }
                listed.onSuccess { fileEntries = present(it) }
                    .onFailure { if (fileToast == null) fileToast = friendlyFileError(it) }
            }
            fileBusy = false
        }
    }

    fun localFile(entry: FileEntry): java.io.File? {
        val loc = fileLocation as? FileLocation.Local ?: return null
        return LocalFiles.file(loc.rootPath, entry.relative)
    }

    suspend fun materializeFile(entry: FileEntry): java.io.File = withContext(Dispatchers.IO) {
        localFile(entry)?.takeIf { it.isFile }?.let { return@withContext it }
        val share = currentSambaShare() ?: error("Could not open that file")
        val dest = java.io.File(
            getApplication<Application>().cacheDir,
            "share/${entry.relative.hashCode()}_${entry.name}",
        )
        dest.parentFile?.mkdirs()
        sambaBrowser.openStream(share, entry.relative).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        dest
    }

    suspend fun materializeImmich(asset: ImmichAsset): java.io.File = withContext(Dispatchers.IO) {
        if (!ImmichNetwork.onWifi(getApplication())) {
            error(ImmichNetwork.WIFI_ONLY_MESSAGE)
        }
        val dest = java.io.File(
            getApplication<Application>().cacheDir,
            "share/immich_${asset.id}_${asset.fileName}",
        )
        dest.parentFile?.mkdirs()
        runCatching { immich.download(asset.originalUrl, dest) }
            .getOrElse { immich.download(asset.previewUrl, dest) }
        dest
    }

    fun currentSambaShare(): SambaShare? {
        val id = (fileLocation as? FileLocation.Samba)?.shareId ?: return null
        return sambaShares.find { it.id == id }
    }

    fun fileTitle(): String = when (val loc = fileLocation) {
        FileLocation.Roots -> "Files"
        is FileLocation.Local -> loc.relative.substringAfterLast('/').ifBlank { loc.rootName }
        is FileLocation.Samba -> {
            val share = sambaShares.find { it.id == loc.shareId }
            loc.relative.substringAfterLast('/').ifBlank { share?.title ?: "Samba" }
        }
    }

    fun fileSubtitle(): String = when (val loc = fileLocation) {
        FileLocation.Roots -> "This phone and Samba"
        is FileLocation.Local ->
            if (loc.relative.isBlank()) loc.rootPath else "${loc.rootName}/${loc.relative}"
        is FileLocation.Samba -> {
            val share = sambaShares.find { it.id == loc.shareId }
            val base = share?.title ?: "Samba"
            if (loc.relative.isBlank()) "smb://${share?.host}/${share?.share}" else "$base/${loc.relative}"
        }
    }

    private fun listLocation(location: FileLocation): List<FileEntry> = when (location) {
        FileLocation.Roots -> emptyList()
        is FileLocation.Local -> LocalFiles.list(location.rootPath, location.relative)
        is FileLocation.Samba -> {
            val share = sambaShares.find { it.id == location.shareId }
                ?: error("Samba share missing")
            sambaBrowser.list(share, location.relative)
        }
    }

    private fun friendlyFileError(error: Throwable): String {
        val msg = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" · ")
        return when {
            msg.contains("UnknownHost", true) || msg.contains("No address", true) ->
                "Can't resolve that name. Try the server IP, like 192.168.1.10"
            msg.contains("STATUS_LOGON_FAILURE", true) || msg.contains("Logon failure", true) ||
                msg.contains("NtStatus", true) && msg.contains("LOGON", true) ->
                "Wrong username or password"
            msg.contains("STATUS_BAD_NETWORK_NAME", true) || msg.contains("BAD_NETWORK_NAME", true) ->
                "Share not found. Check the share name, not the folder path"
            msg.contains("STATUS_ACCESS_DENIED", true) || msg.contains("Access is denied", true) ->
                "Access denied. This share may be read-only"
            msg.contains("OBJECT_NAME_COLLISION", true) || msg.contains("already exists", true) ->
                "A file with that name already exists"
            msg.contains("timeout", true) || msg.contains("Unable to connect", true) ||
                msg.contains("Connection refused", true) || msg.contains("ECONNREFUSED", true) ->
                "Could not reach the server on port 445. Same Wi‑Fi, and SMB enabled?"
            msg.contains("Connection reset", true) ->
                "The server dropped the connection. It may only allow SMB1 — try again, or check Samba min protocol"
            else -> msg.ifBlank { "Could not connect" }
        }
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(refreshRunnable)
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        sambaBrowser.close()
    }
}
