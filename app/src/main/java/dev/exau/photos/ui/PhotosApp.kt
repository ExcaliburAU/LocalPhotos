package dev.exau.photos.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import dev.exau.photos.PhotosApplication
import dev.exau.photos.PhotosViewModel
import dev.exau.photos.R
import dev.exau.photos.data.ExifShare
import dev.exau.photos.data.MediaItem
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.data.MediaWrites
import dev.exau.photos.files.FileKinds
import dev.exau.photos.files.FileLocation
import dev.exau.photos.files.TextFiles
import dev.exau.photos.files.SambaImage
import dev.exau.photos.immich.ImmichAsset
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Chip
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist
import dev.exau.photos.ui.theme.NavBar
import kotlinx.coroutines.launch

private enum class Tab { Albums, Files }

private const val LikedAlbumId = -1L

private sealed interface Route {
    data object Library : Route
    data class Album(val bucketId: Long, val name: String) : Route
    data class Viewer(val ids: List<Long>, val startId: Long, val back: Route) : Route
    data class Editor(val itemId: Long, val viewer: Viewer) : Route
    data object Immich : Route
    data object Bin : Route
    data class FileViewer(val startRelative: String) : Route
    data class ImmichViewer(val assetId: String) : Route
    data class UriEditor(val uri: Uri, val displayName: String, val back: Route) : Route
    data object Storage : Route
    data class TextEditor(val path: String) : Route
    data object LockSetup : Route
}

@Composable
fun PhotosApp(
    openUri: Uri? = null,
    viewModel: PhotosViewModel = viewModel(),
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var hasAccess by remember { mutableStateOf(hasMediaAccess(context)) }
    var limitedAccess by remember { mutableStateOf(hasLimitedMediaAccess(context)) }
    var tab by remember { mutableStateOf(Tab.Albums) }
    var route by remember { mutableStateOf<Route>(Route.Library) }

    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var pendingWrite by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var cachedFilePaths by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var photoSearch by remember { mutableStateOf("") }
    var photoSearching by remember { mutableStateOf(false) }
    val lock = remember { (context.applicationContext as PhotosApplication).appLock }
    var locked by remember { mutableStateOf(lock.enabled) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAccess = hasMediaAccess(context)
        limitedAccess = hasLimitedMediaAccess(context)
        if (hasAccess) viewModel.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasMediaAccess(context)
                limitedAccess = hasLimitedMediaAccess(context)
                if (hasAccess) viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasAccess) {
        if (hasAccess) viewModel.refresh()
    }

    LaunchedEffect(tab) {
        selecting = false
        selectedIds = emptySet()
        photoSearching = false
        photoSearch = ""
    }

    LaunchedEffect(viewModel.fileToast) {
        val msg = viewModel.fileToast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearFileToast()
    }

    LaunchedEffect(viewModel.immichMessage) {
        val msg = viewModel.immichMessage ?: return@LaunchedEffect
        if (msg.startsWith("Back") || msg.startsWith("Already") || msg.contains("failed") || msg.startsWith("Disconnected") || msg.contains("Wi-Fi")) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(openUri, viewModel.items) {
        val uri = openUri ?: return@LaunchedEffect
        val match = viewModel.itemByUri(uri)
        if (match != null) {
            route = Route.Viewer(listOf(match.id), match.id, Route.Library)
        }
    }

    val filesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshFiles()
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when (pendingWrite) {
                "trash" -> Toast.makeText(context, "Moved to bin", Toast.LENGTH_SHORT).show()
                "restore" -> Toast.makeText(context, "Restored", Toast.LENGTH_SHORT).show()
                "delete" -> Toast.makeText(context, "Deleted forever", Toast.LENGTH_SHORT).show()
            }
            selecting = false
            selectedIds = emptySet()
            viewModel.refresh()
        }
        pendingWrite = null
    }

    fun launchWrite(action: String, items: List<MediaItem>) {
        val uris = items.map { it.uri }
        if (uris.isEmpty()) return
        val pi = when (action) {
            "trash" -> MediaWrites.trash(context.contentResolver, uris, true)
            "restore" -> MediaWrites.trash(context.contentResolver, uris, false)
            "delete" -> MediaWrites.delete(context.contentResolver, uris)
            else -> null
        }
        if (pi != null) {
            pendingWrite = action
            writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else if (action == "delete" || action == "trash") {
            MediaWrites.deleteLegacy(context.contentResolver, uris)
            Toast.makeText(context, if (action == "trash") "Deleted" else "Deleted forever", Toast.LENGTH_SHORT).show()
            selecting = false
            selectedIds = emptySet()
            viewModel.refresh()
        }
    }

    if (locked) {
        LockScreen(lock = lock, setup = false, onUnlocked = { locked = false })
        return
    }

    if (!hasAccess) {
        PermissionScreen(
            onGrant = { permissionLauncher.launch(requiredPermissions()) },
            onSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
        return
    }

    when (val current = route) {
        is Route.Storage -> {
            StorageScreen(
                report = viewModel.storageReport,
                busy = viewModel.storageBusy,
                onBack = { route = Route.Library },
                onScan = viewModel::scanStorage,
                onOpenPath = { item ->
                    viewModel.openAbsolute(item.path)
                    tab = Tab.Files
                    route = Route.Library
                },
            )
        }

        is Route.TextEditor -> {
            TextEditorScreen(
                file = java.io.File(current.path),
                onBack = { route = Route.Library },
                onSaved = {
                    viewModel.refreshFiles()
                    route = Route.Library
                },
            )
        }

        is Route.LockSetup -> {
            LockScreen(
                lock = lock,
                setup = true,
                onUnlocked = { route = Route.Library },
                onCancel = { route = Route.Library },
            )
        }

        is Route.UriEditor -> {
            EditorScreen(
                uri = current.uri,
                displayName = current.displayName,
                onCancel = { route = current.back },
                onSaved = {
                    viewModel.refresh()
                    route = current.back
                },
            )
        }

        is Route.Editor -> {
            val item = viewModel.items.find { it.id == current.itemId }
            if (item == null) {
                route = current.viewer
            } else {
                EditorScreen(
                    item = item,
                    onCancel = { route = current.viewer },
                    onSaved = {
                        viewModel.refresh()
                        route = current.viewer
                    },
                )
            }
        }

        is Route.Immich -> {
            if (!viewModel.immichConnected) {
                ImmichConnectScreen(
                    prefs = viewModel.immichPrefs,
                    busy = viewModel.immichBusy,
                    message = viewModel.immichMessage,
                    onConnect = viewModel::connectImmich,
                    onBack = { route = Route.Library },
                )
            } else {
                ImmichSettingsScreen(
                    user = viewModel.immichUser,
                    assets = viewModel.immichAssets,
                    authHeader = viewModel.immichPrefs.imageAuthHeader,
                    busy = viewModel.immichBusy,
                    progress = viewModel.backupProgress,
                    message = viewModel.immichMessage,
                    onBack = { route = Route.Library },
                    onBackupNew = viewModel::backupNew,
                    onDisconnect = viewModel::disconnectImmich,
                    onOpen = { asset ->
                        route = Route.ImmichViewer(asset.id)
                    },
                    hasMore = viewModel.immichHasMore,
                    loadingMore = viewModel.immichLoadingMore,
                    onLoadMore = viewModel::loadMoreImmich,
                )
            }
        }

        is Route.ImmichViewer -> {
            val asset = viewModel.immichAssets.find { it.id == current.assetId }
            if (asset == null) {
                route = Route.Immich
            } else {
                ImmichViewerScreen(
                    asset = asset,
                    authHeader = viewModel.immichPrefs.imageAuthHeader,
                    busy = preparing,
                    onBack = { route = Route.Immich },
                    onShare = {
                        if (preparing) return@ImmichViewerScreen
                        preparing = true
                        scope.launch {
                            try {
                                shareFile(
                                    context,
                                    viewModel.materializeImmich(asset),
                                    FileKinds.mime(asset.fileName),
                                )
                            } catch (error: Exception) {
                                Toast.makeText(context, error.message ?: "Could not share", Toast.LENGTH_SHORT).show()
                            } finally {
                                preparing = false
                            }
                        }
                    },
                    onEdit = {
                        if (preparing) return@ImmichViewerScreen
                        preparing = true
                        scope.launch {
                            try {
                                val file = viewModel.materializeImmich(asset)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                route = Route.UriEditor(uri, asset.fileName, current)
                            } catch (error: Exception) {
                                Toast.makeText(context, error.message ?: "Could not edit", Toast.LENGTH_SHORT).show()
                            } finally {
                                preparing = false
                            }
                        }
                    },
                )
            }
        }

        is Route.Viewer -> {
            val list = current.ids.mapNotNull { id ->
                viewModel.items.find { it.id == id } ?: viewModel.trashed.find { it.id == id }
            }
            if (list.isEmpty()) {
                route = current.back
            } else {
                val fromBin = current.back is Route.Bin
                ViewerScreen(
                    items = list,
                    startId = current.startId,
                    favorites = viewModel.favorites,
                    onBack = { route = current.back },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onEdit = if (fromBin) null else { item ->
                        route = Route.Editor(item.id, current)
                    },
                    onRestore = if (fromBin) {
                        { item -> launchWrite("restore", listOf(item)) }
                    } else {
                        null
                    },
                    onRequestDelete = { item ->
                        launchWrite(if (fromBin) "delete" else "trash", listOf(item))
                    },
                    onSetCover = if (!fromBin && current.back is Route.Album &&
                        (current.back as Route.Album).bucketId != LikedAlbumId
                    ) {
                        { item -> viewModel.setAlbumCover(item) }
                    } else {
                        null
                    },
                )
            }
        }

        is Route.Bin -> {
            BinScreen(
                items = viewModel.trashed,
                onBack = { route = Route.Library },
                onRestore = { launchWrite("restore", it) },
                onDeleteForever = { launchWrite("delete", it) },
                onOpen = { item ->
                    route = Route.Viewer(viewModel.trashed.map { it.id }, item.id, Route.Bin)
                },
            )
        }

        is Route.FileViewer -> {
            val files = viewModel.fileEntries.filter { !it.isDir }
            FileViewerScreen(
                items = files,
                startRelative = current.startRelative,
                imageLoader = imageLoader,
                modelFor = { entry ->
                    when (val loc = viewModel.fileLocation) {
                        is FileLocation.Local -> java.io.File(loc.rootPath, entry.relative)
                        is FileLocation.Samba -> SambaImage(loc.shareId, entry.relative)
                        FileLocation.Roots -> entry.name
                    }
                },
                localPathFor = { entry ->
                    viewModel.localFile(entry)?.absolutePath ?: cachedFilePaths[entry.relative]
                },
                busy = preparing,
                onBack = { route = Route.Library },
                onShare = { entry ->
                    if (preparing) return@FileViewerScreen
                    preparing = true
                    scope.launch {
                        try {
                            shareFile(context, viewModel.materializeFile(entry), FileKinds.mime(entry.name))
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "Could not share", Toast.LENGTH_SHORT).show()
                        } finally {
                            preparing = false
                        }
                    }
                },
                onEdit = { entry ->
                    if (preparing) return@FileViewerScreen
                    preparing = true
                    scope.launch {
                        try {
                            val file = viewModel.materializeFile(entry)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            route = Route.UriEditor(uri, entry.name, current)
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "Could not edit", Toast.LENGTH_SHORT).show()
                        } finally {
                            preparing = false
                        }
                    }
                },
                onOpen = { entry ->
                    if (preparing) return@FileViewerScreen
                    preparing = true
                    scope.launch {
                        try {
                            viewFile(context, viewModel.materializeFile(entry), FileKinds.mime(entry.name))
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "Could not open", Toast.LENGTH_SHORT).show()
                        } finally {
                            preparing = false
                        }
                    }
                },
                onPrepareVideo = { entry ->
                    if (preparing) return@FileViewerScreen
                    preparing = true
                    scope.launch {
                        try {
                            val file = viewModel.materializeFile(entry)
                            cachedFilePaths = cachedFilePaths + (entry.relative to file.absolutePath)
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "Could not play", Toast.LENGTH_SHORT).show()
                        } finally {
                            preparing = false
                        }
                    }
                },
                onSetCover = viewModel::setFolderCover,
            )
        }

        is Route.Album -> {
            val albumItems = if (current.bucketId == LikedAlbumId) {
                viewModel.favoriteItems.sortedByDescending { it.dateTaken }
            } else {
                viewModel.items.filter { it.bucketId == current.bucketId }.sortedByDescending { it.dateTaken }
            }
            AlbumScreen(
                title = current.name,
                items = albumItems,
                onBack = { route = Route.Library },
                onOpen = { item ->
                    route = Route.Viewer(albumItems.map { it.id }, item.id, current)
                },
                onShare = { shareItems(context, it) },
                onTrash = { launchWrite("trash", it) },
                onCover = if (current.bucketId == LikedAlbumId) null else viewModel::setAlbumCover,
            )
        }

        Route.Library -> {
            BackHandler(enabled = photoSearching) {
                photoSearching = false
                photoSearch = ""
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink)
                    .statusBarsPadding(),
            ) {
                if (tab != Tab.Files) {
                    LibraryHeader(
                        count = viewModel.items.size,
                        tab = tab,
                        connected = viewModel.immichConnected,
                        searching = photoSearching,
                        search = photoSearch,
                        onToggleSearch = {
                            photoSearching = !photoSearching
                            if (!photoSearching) photoSearch = ""
                        },
                        onSearch = { photoSearch = it },
                        onSelect = null,
                        onOpenSettings = {
                            route = Route.Immich
                            if (viewModel.immichConnected) viewModel.refreshImmich()
                        },
                    )
                }
                if (limitedAccess && tab == Tab.Albums) {
                    LimitedAccessBanner(
                        onAllowAll = { permissionLauncher.launch(requiredPermissions()) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        Tab.Albums -> {
                            val query = photoSearch.trim()
                            if (query.isNotEmpty()) {
                                val hits = viewModel.items.filter {
                                    it.displayName.contains(query, ignoreCase = true) ||
                                        it.bucketName.contains(query, ignoreCase = true)
                                }
                                if (hits.isEmpty()) {
                                    EmptyState("No photos matching “$query”.")
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        contentPadding = PaddingValues(1.dp),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(hits, key = { it.id }) { item ->
                                            MediaThumb(
                                                item = item,
                                                onClick = {
                                                    route = Route.Viewer(hits.map { it.id }, item.id, Route.Library)
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                AlbumsList(
                                    albums = viewModel.albums,
                                    binCount = viewModel.trashed.size,
                                    likedCount = viewModel.favoriteItems.size,
                                    onOpen = { album ->
                                        route = Route.Album(album.bucketId, album.name)
                                    },
                                    onOpenBin = { route = Route.Bin },
                                    onOpenLiked = { route = Route.Album(LikedAlbumId, "Liked") },
                                )
                            }
                        }

                        Tab.Files -> {
                            LaunchedEffect(Unit) { viewModel.refreshFiles() }
                            FilesScreen(
                                location = viewModel.fileLocation,
                                roots = viewModel.fileRoots,
                                shares = viewModel.sambaShares,
                                entries = viewModel.fileEntries,
                                title = viewModel.fileTitle(),
                                subtitle = viewModel.fileSubtitle(),
                                busy = viewModel.fileBusy,
                                message = viewModel.fileMessage,
                                canManageFiles = MediaWrites.canManage(context),
                                imageLoader = imageLoader,
                                onBack = { viewModel.fileGoUp() },
                                onGrantFiles = {
                                    filesAccessLauncher.launch(MediaWrites.manageFilesIntent(context))
                                },
                                onOpenRoot = viewModel::openLocalRoot,
                                onOpenShare = viewModel::openSamba,
                                onRemoveShare = { viewModel.removeSamba(it.id) },
                                onOpenDir = viewModel::openFileDir,
                                onOpenFile = { entry ->
                                    if (TextFiles.isText(entry.name) && viewModel.localFile(entry) != null) {
                                        route = Route.TextEditor(viewModel.localFile(entry)!!.absolutePath)
                                    } else {
                                        route = Route.FileViewer(entry.relative)
                                    }
                                },
                                onAddSamba = viewModel::addSamba,
                                clipboard = viewModel.fileClipboard,
                                canPaste = viewModel.canPaste(),
                                onNewFolder = viewModel::newFolder,
                                onRename = viewModel::renameFile,
                                onDelete = viewModel::deleteFiles,
                                onCopy = { viewModel.copyToClipboard(it, cut = false) },
                                onMove = { viewModel.copyToClipboard(it, cut = true) },
                                onPaste = viewModel::pasteFiles,
                                onClearClipboard = viewModel::clearClipboard,
                                immichConnected = viewModel.immichConnected,
                                onOpenSettings = {
                                    route = Route.Immich
                                    if (viewModel.immichConnected) viewModel.refreshImmich()
                                },
                                favorites = viewModel.favoriteFolders,
                                onOpenFavorite = {
                                    viewModel.openFavorite(it)
                                },
                                onOpenStorage = { route = Route.Storage },
                                onOpenLock = { route = Route.LockSetup },
                                showHidden = viewModel.showHidden,
                                onToggleHidden = { viewModel.setHiddenFiles(!viewModel.showHidden) },
                                sort = viewModel.fileSort,
                                onSort = viewModel::setSortMode,
                                folderFavorite = viewModel.currentFolderFavorite(),
                                onToggleFavorite = viewModel::toggleFavoriteFolder,
                                onZip = viewModel::zipFiles,
                                onUnzip = viewModel::unzipFile,
                                onHide = viewModel::hideFiles,
                                onSetCover = viewModel::setFolderCover,
                                onShare = { entries ->
                                    if (preparing) return@FilesScreen
                                    preparing = true
                                    scope.launch {
                                        try {
                                            val files = entries.filter { !it.isDir }.map { viewModel.materializeFile(it) }
                                            if (files.size == 1) {
                                                shareFile(context, files.first(), FileKinds.mime(entries.first { !it.isDir }.name))
                                            } else if (files.isNotEmpty()) {
                                                val uris = ArrayList(files.map {
                                                    FileProvider.getUriForFile(context, "${context.packageName}.files", it)
                                                })
                                                val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                    type = "*/*"
                                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(send, "Share"))
                                            }
                                        } catch (error: Exception) {
                                            Toast.makeText(context, error.message ?: "Could not share", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            preparing = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                NavigationBar(
                    containerColor = NavBar,
                    contentColor = Amber,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Amber,
                        selectedTextColor = Amber,
                        indicatorColor = Chip,
                        unselectedIconColor = Mist,
                        unselectedTextColor = Mist,
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Albums,
                        onClick = { tab = Tab.Albums },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Photos") },
                        label = { Text("Photos") },
                        colors = colors,
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Files,
                        onClick = { tab = Tab.Files },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_folder),
                                contentDescription = "Files",
                            )
                        },
                        label = { Text("Files") },
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    count: Int,
    tab: Tab,
    connected: Boolean,
    searching: Boolean,
    search: String,
    onToggleSearch: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (() -> Unit)?,
    onOpenSettings: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Amber,
        focusedLabelColor = Amber,
        cursorColor = Amber,
        unfocusedBorderColor = Color.Transparent,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = InkSoft,
        unfocusedContainerColor = InkSoft,
        focusedPlaceholderColor = Mist,
        unfocusedPlaceholderColor = Mist,
    )
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                placeholder = { Text("Search photos") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = fieldColors,
                modifier = Modifier.weight(1f).padding(end = 8.dp).height(56.dp),
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (tab) {
                        Tab.Albums -> "Photos"
                        Tab.Files -> "Files"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                )
                Text(
                    text = if (count == 0) "On this device" else "$count on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(InkSoft)
                .clickable(onClick = onToggleSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (searching) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = if (searching) "Close search" else "Search",
                tint = Amber,
                modifier = Modifier.size(20.dp),
            )
        }
        if (!searching) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(InkSoft)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = if (connected) Amber else Mist,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ImmichSettingsScreen(
    user: String,
    assets: List<ImmichAsset>,
    authHeader: Pair<String, String>,
    busy: Boolean,
    progress: String?,
    message: String?,
    onBack: () -> Unit,
    onBackupNew: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: (ImmichAsset) -> Unit,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Column {
                Text("Immich", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    user.ifBlank { "Your server" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ImmichLibraryScreen(
            assets = assets,
            authHeader = authHeader,
            busy = busy,
            progress = progress,
            message = message,
            onBackupNew = onBackupNew,
            onDisconnect = onDisconnect,
            onOpen = onOpen,
            hasMore = hasMore,
            loadingMore = loadingMore,
            onLoadMore = onLoadMore,
        )
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Files", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "Photos, files, and Samba on this phone. Tap Allow all photos — not Select photos — or Camera shots stay hidden.",
            style = MaterialTheme.typography.bodyLarge,
            color = Mist,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        ) {
            Text("Allow access")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSettings) { Text("Open settings", color = Mist) }
    }
}

@Composable
private fun LimitedAccessBanner(onAllowAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised)
            .padding(14.dp),
    ) {
        Text(
            "Camera photos are hidden. Android only showed selected items — choose Allow all photos.",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onAllowAll,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        ) { Text("Allow all photos") }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            color = Mist,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SectionedGrid(
    sections: List<dev.exau.photos.data.MediaSection>,
    onOpen: (MediaItem) -> Unit,
    selecting: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggle: (MediaItem) -> Unit = {},
    onLongSelect: (MediaItem) -> Unit = {},
    onToggleSection: ((List<MediaItem>) -> Unit)? = null,
    subtitleFor: ((MediaItem) -> String?)? = null,
    emptyMessage: String = "No photos or videos on this phone yet.",
) {
    if (sections.isEmpty() || sections.all { it.items.isEmpty() }) {
        EmptyState(emptyMessage)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            item(span = { GridItemSpan(3) }, key = "h-${section.title}") {
                val sectionIds = section.items.map { it.id }.toSet()
                val sectionSelected = sectionIds.isNotEmpty() && sectionIds.all { it in selectedIds }
                Text(
                    text = if (selecting && onToggleSection != null) {
                        if (sectionSelected) "${section.title}  ·  Unselect" else "${section.title}  ·  Select"
                    } else {
                        section.title
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selecting && onToggleSection != null) Amber else Color.White,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
                        .then(
                            if (onToggleSection != null) {
                                Modifier.clickable { onToggleSection(section.items) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            items(section.items, key = { it.id }) { item ->
                MediaThumb(
                    item = item,
                    selected = item.id in selectedIds,
                    selecting = selecting,
                    badge = subtitleFor?.invoke(item),
                    onClick = {
                        if (selecting) onToggle(item) else onOpen(item)
                    },
                    onLongClick = { onLongSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun AlbumsList(
    albums: List<dev.exau.photos.data.Album>,
    binCount: Int,
    likedCount: Int,
    onOpen: (dev.exau.photos.data.Album) -> Unit,
    onOpenBin: () -> Unit,
    onOpenLiked: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.bucketId }) { album ->
            Column(modifier = Modifier.clickable { onOpen(album) }) {
                MediaThumb(
                    item = album.cover,
                    onClick = { onOpen(album) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${album.count} ${if (album.count == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }
        item(key = "library-tools", span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LibraryChip(
                    title = "Liked",
                    subtitle = if (likedCount == 0) "None yet" else "$likedCount",
                    icon = Icons.Filled.Star,
                    onClick = onOpenLiked,
                    modifier = Modifier.weight(1f),
                )
                LibraryChip(
                    title = "Bin",
                    subtitle = if (binCount == 0) "Empty" else "$binCount",
                    icon = Icons.Filled.Delete,
                    onClick = onOpenBin,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LibraryChip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Mist, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AlbumScreen(
    title: String,
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onShare: (List<MediaItem>) -> Unit,
    onTrash: (List<MediaItem>) -> Unit,
    onCover: ((MediaItem) -> Unit)? = null,
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val chosen = items.filter { it.id in selectedIds }
    LaunchedEffect(items) {
        val ids = items.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(ids)
    }
    BackHandler {
        if (selecting) {
            selecting = false
            selectedIds = emptySet()
        } else {
            onBack()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding(),
    ) {
        if (selecting) {
            SelectionBar(
                count = selectedIds.size,
                allSelected = items.isNotEmpty() && selectedIds.size == items.size,
                onClose = {
                    selecting = false
                    selectedIds = emptySet()
                },
                onToggleAll = {
                    selectedIds = if (selectedIds.size == items.size) emptySet() else items.map { it.id }.toSet()
                },
                onShare = { onShare(chosen) },
                onCover = if (onCover != null) {
                    {
                        val one = chosen.singleOrNull()
                        if (one != null && !one.isVideo) onCover.invoke(one)
                        selecting = false
                        selectedIds = emptySet()
                    }
                } else {
                    null
                },
                onTrash = { onTrash(chosen) },
            )
        } else {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        "${items.size} on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                }
                Text(
                    "Select",
                    color = Amber,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selecting = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        SectionedGrid(
            sections = listOf(dev.exau.photos.data.MediaSection(title, items)),
            emptyMessage = if (title == "Liked") {
                "No liked photos yet.\nOpen a photo and tap the star."
            } else {
                "No photos or videos on this phone yet."
            },
            onOpen = onOpen,
            selecting = selecting,
            selectedIds = selectedIds,
            onToggle = { item ->
                selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                selecting = true
            },
            onLongSelect = { item ->
                selecting = true
                selectedIds = selectedIds + item.id
            },
        )
    }
}

@Composable
fun MediaThumb(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    selected: Boolean = false,
    selecting: Boolean = false,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(item.uri, item.isVideo) {
        val builder = ImageRequest.Builder(context)
            .data(item.uri)
            .size(512)
            .precision(Precision.INEXACT)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)
            .memoryCacheKey("thumb-${item.id}")
        if (item.isVideo) {
            builder.decoderFactory(VideoFrameDecoder.Factory())
            builder.videoFrameMillis(0)
        }
        builder.build()
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(InkRaised)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000)),
            )
        }
        if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Amber else Color(0x66000000))
                    .border(1.5.dp, if (selected) Amber else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Ink,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    MediaRepository.formatDuration(item.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            Text(
                badge,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

internal fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun hasLimitedMediaAccess(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    return hasMediaAccess(context) && !granted(Manifest.permission.READ_MEDIA_IMAGES)
}

internal fun hasMediaAccess(context: android.content.Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    return if (Build.VERSION.SDK_INT >= 33) {
        val selected = Build.VERSION.SDK_INT >= 34 &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            selected
    } else {
        granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
