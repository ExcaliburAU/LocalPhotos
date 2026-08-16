package dev.exau.photos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.exau.photos.R
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.files.FavoriteFolder
import dev.exau.photos.files.FileClipboard
import dev.exau.photos.files.FileEntry
import dev.exau.photos.files.FileKinds
import dev.exau.photos.files.FileLocation
import dev.exau.photos.files.FileRoot
import dev.exau.photos.files.FileSort
import dev.exau.photos.files.SambaImage
import dev.exau.photos.files.SambaShare
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Danger
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist
import java.io.File

@Composable
fun FilesScreen(
    location: FileLocation,
    roots: List<FileRoot>,
    shares: List<SambaShare>,
    entries: List<FileEntry>,
    title: String,
    subtitle: String,
    busy: Boolean,
    message: String?,
    canManageFiles: Boolean,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onGrantFiles: () -> Unit,
    onOpenRoot: (FileRoot) -> Unit,
    onOpenShare: (SambaShare) -> Unit,
    onRemoveShare: (SambaShare) -> Unit,
    onOpenDir: (FileEntry) -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    onAddSamba: (host: String, share: String, user: String, password: String) -> Unit,
    clipboard: FileClipboard? = null,
    canPaste: Boolean = false,
    onNewFolder: (String) -> Unit = {},
    onRename: (FileEntry, String) -> Unit = { _, _ -> },
    onDelete: (List<FileEntry>) -> Unit = {},
    onCopy: (List<FileEntry>) -> Unit = {},
    onMove: (List<FileEntry>) -> Unit = {},
    onPaste: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    immichConnected: Boolean = false,
    onOpenSettings: () -> Unit = {},
    favorites: List<FavoriteFolder> = emptyList(),
    onOpenFavorite: (FavoriteFolder) -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenLock: () -> Unit = {},
    showHidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
    sort: FileSort = FileSort.Name,
    onSort: (FileSort) -> Unit = {},
    folderFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onZip: (List<FileEntry>) -> Unit = {},
    onUnzip: (FileEntry) -> Unit = {},
    onHide: (List<FileEntry>, Boolean) -> Unit = { _, _ -> },
    onShare: (List<FileEntry>) -> Unit = {},
    onSetCover: (FileEntry) -> Unit = {},
) {
    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var naming by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeShare by remember { mutableStateOf<SambaShare?>(null) }
    var picking by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val selecting = picking || selected.isNotEmpty()
    val query = search.trim()
    val shownRoots = if (query.isEmpty()) roots else roots.filter { it.name.contains(query, ignoreCase = true) }
    val shownShares = if (query.isEmpty()) {
        shares
    } else {
        shares.filter { it.title.contains(query, ignoreCase = true) || it.host.contains(query, ignoreCase = true) }
    }
    val shownEntries = if (query.isEmpty()) entries else entries.filter { it.name.contains(query, ignoreCase = true) }
    val chosen = shownEntries.filter { it.relative in selected }
    val searchColors = OutlinedTextFieldDefaults.colors(
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
    LaunchedEffect(location) {
        if (location is FileLocation.Samba && adding) adding = false
        selected = emptySet()
        picking = false
        naming = null
        confirmDelete = false
        removeShare = null
        searching = false
        search = ""
    }
    BackHandler(enabled = selecting || searching || location !is FileLocation.Roots || adding) {
        when {
            selecting -> {
                selected = emptySet()
                picking = false
            }
            searching -> {
                searching = false
                search = ""
            }
            adding -> adding = false
            else -> onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            FileSelectBar(
                count = chosen.size,
                allSelected = shownEntries.isNotEmpty() && selected.size == shownEntries.size,
                busy = busy,
                onClose = {
                    selected = emptySet()
                    picking = false
                },
                onToggleAll = {
                    selected = if (selected.size == shownEntries.size) emptySet() else shownEntries.map { it.relative }.toSet()
                },
                onRename = {
                    val one = chosen.singleOrNull() ?: return@FileSelectBar
                    naming = NameDialog.Rename(one)
                },
                onCopy = {
                    onCopy(chosen)
                    selected = emptySet()
                    picking = false
                },
                onMove = {
                    onMove(chosen)
                    selected = emptySet()
                    picking = false
                },
                onDelete = { confirmDelete = true },
                onZip = { onZip(chosen) },
                onUnzip = {
                    val one = chosen.singleOrNull() ?: return@FileSelectBar
                    onUnzip(one)
                },
                onHide = { onHide(chosen, true) },
                onUnhide = { onHide(chosen, false) },
                onShare = { onShare(chosen) },
                onCover = {
                    val one = chosen.singleOrNull() ?: return@FileSelectBar
                    onSetCover(one)
                    selected = emptySet()
                    picking = false
                },
                canCover = chosen.size == 1 && chosen.first().isImage,
                canUnzip = chosen.size == 1 && chosen.first().name.endsWith(".zip", true),
            )
        } else if (location is FileLocation.Roots && !adding) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searching) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search files") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = searchColors,
                        modifier = Modifier.weight(1f).padding(end = 8.dp).height(56.dp),
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Files", style = MaterialTheme.typography.displayMedium, color = Color.White)
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Mist)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(InkSoft)
                        .clickable {
                            searching = !searching
                            if (!searching) search = ""
                        },
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
                            tint = if (immichConnected) Amber else Mist,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (adding) adding = false else onBack()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    if (searching && !adding) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search this folder") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = searchColors,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        )
                    } else {
                        Text(if (adding) "Add Samba" else title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            if (adding) "SMB share on your network" else subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!adding) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                searching = !searching
                                if (!searching) search = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (searching) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searching) "Close search" else "Search",
                            tint = Amber,
                        )
                    }
                    Text(
                        "Select",
                        color = Amber,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { picking = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !busy) { naming = NameDialog.Create },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New folder", tint = Amber)
                    }
                }
            }
            if (!adding && location !is FileLocation.Roots) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FileActionChip(if (showHidden) "Hide dots" else "Hidden", enabled = true, onClick = onToggleHidden)
                    FileActionChip(
                        when (sort) {
                            FileSort.Name -> "Name"
                            FileSort.Date -> "Date"
                            FileSort.Size -> "Size"
                        },
                        enabled = true,
                        onClick = {
                            onSort(
                                when (sort) {
                                    FileSort.Name -> FileSort.Date
                                    FileSort.Date -> FileSort.Size
                                    FileSort.Size -> FileSort.Name
                                },
                            )
                        },
                    )
                    if (location is FileLocation.Local) {
                        FileActionChip(if (folderFavorite) "Unstar" else "Star", enabled = true, onClick = onToggleFavorite)
                    }
                }
            }
        }
        if (clipboard != null && !adding && location !is FileLocation.Roots) {
            PasteBar(
                label = clipboard.label,
                enabled = canPaste && !busy,
                onPaste = onPaste,
                onCancel = onClearClipboard,
            )
        }

        Box(modifier = Modifier.weight(1f, fill = true)) {
            when {
                adding -> AddSambaForm(
                    busy = busy,
                    message = message,
                    onAdd = onAddSamba,
                )
                !canManageFiles && location is FileLocation.Roots -> Column(Modifier.fillMaxSize()) {
                    FilesAccessPrompt(onGrantFiles)
                    RootsGrid(
                        roots = emptyList(),
                        shares = shownShares,
                        favorites = emptyList(),
                        onOpenRoot = onOpenRoot,
                        onOpenShare = onOpenShare,
                        onRemoveShare = { removeShare = it },
                        onAddSamba = { adding = true },
                        onOpenFavorite = onOpenFavorite,
                        onOpenStorage = onOpenStorage,
                        onOpenLock = onOpenLock,
                    )
                }
                busy && entries.isEmpty() && location !is FileLocation.Roots -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Amber) }
                location is FileLocation.Roots -> RootsGrid(
                    roots = shownRoots,
                    shares = shownShares,
                    favorites = if (query.isEmpty()) favorites else favorites.filter {
                        it.name.contains(query, ignoreCase = true)
                    },
                    onOpenRoot = onOpenRoot,
                    onOpenShare = onOpenShare,
                    onRemoveShare = { removeShare = it },
                    onAddSamba = { adding = true },
                    onOpenFavorite = onOpenFavorite,
                    onOpenStorage = onOpenStorage,
                    onOpenLock = onOpenLock,
                )
                else -> FolderGrid(
                    entries = shownEntries,
                    location = location,
                    imageLoader = imageLoader,
                    message = message,
                    selected = selected,
                    selecting = selecting,
                    onOpenDir = onOpenDir,
                    onOpenFile = onOpenFile,
                    onToggle = { entry ->
                        selected = if (entry.relative in selected) selected - entry.relative else selected + entry.relative
                    },
                    onLongSelect = { entry ->
                        picking = true
                        selected = selected + entry.relative
                    },
                )
            }
            if (busy && location !is FileLocation.Roots && entries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Amber)
                }
            }
        }
    }

    val dialog = naming
    if (dialog != null) {
        FileNameDialog(
            title = if (dialog is NameDialog.Create) "New folder" else "Rename",
            initial = (dialog as? NameDialog.Rename)?.entry?.name.orEmpty(),
            confirm = if (dialog is NameDialog.Create) "Create" else "Rename",
            onDismiss = { naming = null },
            onConfirm = { value ->
                when (dialog) {
                    NameDialog.Create -> onNewFolder(value)
                    is NameDialog.Rename -> onRename(dialog.entry, value)
                }
                naming = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (chosen.size == 1) "Delete ${chosen.first().name}?" else "Delete ${chosen.size} items?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(chosen)
                        selected = emptySet()
                        picking = false
                    },
                ) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            containerColor = Ink,
        )
    }
    val shareToRemove = removeShare
    if (shareToRemove != null) {
        AlertDialog(
            onDismissRequest = { removeShare = null },
            title = { Text("Remove ${shareToRemove.title}?") },
            text = { Text("This only forgets the share on this phone. Files on the server stay.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveShare(shareToRemove)
                        removeShare = null
                    },
                ) { Text("Remove", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { removeShare = null }) { Text("Cancel") }
            },
            containerColor = Ink,
        )
    }
}

private sealed interface NameDialog {
    data object Create : NameDialog
    data class Rename(val entry: FileEntry) : NameDialog
}

@Composable
private fun FilesAccessPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(InkRaised)
            .padding(16.dp),
    ) {
        Text(
            "Allow All files access to browse folders on this phone.",
            color = Mist,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        ) { Text("Allow file access") }
    }
}

@Composable
private fun RootsGrid(
    roots: List<FileRoot>,
    shares: List<SambaShare>,
    favorites: List<FavoriteFolder>,
    onOpenRoot: (FileRoot) -> Unit,
    onOpenShare: (SambaShare) -> Unit,
    onRemoveShare: (SambaShare) -> Unit,
    onAddSamba: () -> Unit,
    onOpenFavorite: (FavoriteFolder) -> Unit,
    onOpenStorage: () -> Unit,
    onOpenLock: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(roots, key = { it.path }) { root ->
            SourceCard(
                title = root.name,
                subtitle = if (root.removable) "SD / USB" else "This phone",
                onClick = { onOpenRoot(root) },
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder_cover),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        items(favorites, key = { "fav-${it.key}" }) { folder ->
            SourceCard(
                title = folder.name,
                subtitle = folder.subtitle,
                onClick = { onOpenFavorite(folder) },
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(32.dp))
            }
        }
        items(shares, key = { it.id }) { share ->
            SourceCard(
                title = share.title,
                subtitle = "Samba",
                onClick = { onOpenShare(share) },
                onLongClick = { onRemoveShare(share) },
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = Amber, modifier = Modifier.size(32.dp))
            }
        }
        item(key = "add-samba") {
            SourceCard(title = "Add Samba", subtitle = "SMB share", onClick = onAddSamba) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Amber, modifier = Modifier.size(36.dp))
            }
        }
        item(key = "storage") {
            SourceCard(title = "Storage", subtitle = "What's using space", onClick = onOpenStorage) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Amber, modifier = Modifier.size(32.dp))
            }
        }
        item(key = "lock") {
            SourceCard(title = "App lock", subtitle = "PIN for Files", onClick = onOpenLock) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Amber, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(InkRaised)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(14.dp))
                .background(InkSoft),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Mist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderGrid(
    entries: List<FileEntry>,
    location: FileLocation,
    imageLoader: ImageLoader,
    message: String?,
    selected: Set<String>,
    selecting: Boolean,
    onOpenDir: (FileEntry) -> Unit,
    onOpenFile: (FileEntry) -> Unit,
    onToggle: (FileEntry) -> Unit,
    onLongSelect: (FileEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                message ?: "This folder is empty.\nTap + to add a folder.",
                color = Mist,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    val folders = entries.filter { it.isDir }
    val files = entries.filter { !it.isDir }
    val shareId = (location as? FileLocation.Samba)?.shareId
    val rootPath = (location as? FileLocation.Local)?.rootPath
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (folders.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Text("Folders", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 8.dp))
            }
            items(folders, key = { "d-${it.relative}" }) { entry ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(InkRaised)
                        .combinedClickable(
                            onClick = { if (selecting) onToggle(entry) else onOpenDir(entry) },
                            onLongClick = { onLongSelect(entry) },
                        ),
                ) {
                    val cover = entry.coverRelative
                    if (cover != null && (shareId != null || rootPath != null)) {
                        val model: Any = when {
                            shareId != null -> SambaImage(shareId, cover)
                            rootPath != null -> File(rootPath, cover)
                            else -> cover
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(model)
                                .size(512)
                                .crossfade(false)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = entry.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_folder_cover),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 6.dp),
                    ) {
                        Text(
                            entry.name,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SelectMark(selected = entry.relative in selected, selecting = selecting)
                }
            }
        }
        if (files.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Text("Files", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 8.dp))
            }
            items(files, key = { "f-${it.relative}" }) { entry ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(InkRaised)
                        .combinedClickable(
                            onClick = { if (selecting) onToggle(entry) else onOpenFile(entry) },
                            onLongClick = { onLongSelect(entry) },
                        ),
                ) {
                    if (entry.isImage || entry.isVideo) {
                        val model: Any = when {
                            shareId != null -> SambaImage(shareId, entry.relative)
                            rootPath != null -> File(rootPath, entry.relative)
                            else -> entry.name
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(model)
                                .size(512)
                                .crossfade(false)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = entry.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                                .padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 6.dp),
                        ) {
                            Text(
                                entry.name,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (entry.isVideo) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xAA000000))
                                    .padding(2.dp)
                                    .size(16.dp),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(InkSoft)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    FileKinds.extLabel(entry.name),
                                    color = Amber,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                entry.name,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.size > 0L) {
                                Text(
                                    MediaRepository.formatSize(entry.size),
                                    color = Mist,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (entry.relative in selected) {
                        Box(Modifier.fillMaxSize().background(Color(0x55000000)))
                    }
                    SelectMark(selected = entry.relative in selected, selecting = selecting)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SelectMark(selected: Boolean, selecting: Boolean) {
    if (!selecting) return
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
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Ink, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun FileSelectBar(
    count: Int,
    allSelected: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onZip: () -> Unit,
    onUnzip: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onShare: () -> Unit,
    onCover: () -> Unit,
    canCover: Boolean,
    canUnzip: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    if (count == 0) "Select items" else if (count == 1) "1 selected" else "$count selected",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Tap to choose", color = Mist, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (allSelected) "None" else "All",
                color = Amber,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleAll)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FileActionChip("Rename", enabled = !busy && count == 1, onClick = onRename)
            FileActionChip("Copy", enabled = !busy && count > 0, onClick = onCopy)
            FileActionChip("Move", enabled = !busy && count > 0, onClick = onMove)
            FileActionChip("Zip", enabled = !busy && count > 0, onClick = onZip)
            FileActionChip("Unzip", enabled = !busy && canUnzip, onClick = onUnzip)
            FileActionChip("Hide", enabled = !busy && count > 0, onClick = onHide)
            FileActionChip("Unhide", enabled = !busy && count > 0, onClick = onUnhide)
            FileActionChip("Share", enabled = !busy && count > 0, onClick = onShare)
            FileActionChip("Cover", enabled = !busy && canCover, onClick = onCover)
            FileActionChip("Delete", enabled = !busy && count > 0, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun FileActionChip(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = when {
            !enabled -> Mist
            danger -> Danger
            else -> Amber
        },
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun PasteBar(
    label: String,
    enabled: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Paste",
            color = if (enabled) Amber else Mist,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onPaste)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            "Cancel",
            color = Mist,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FileNameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (value.isNotBlank()) onConfirm(value) },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    focusedLabelColor = Amber,
                    cursorColor = Amber,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value) },
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = Ink,
    )
}

@Composable
private fun AddSambaForm(
    busy: Boolean,
    message: String?,
    onAdd: (String, String, String, String) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Amber,
        focusedLabelColor = Amber,
        cursorColor = Amber,
        unfocusedBorderColor = Color.Transparent,
        unfocusedLabelColor = Mist,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = InkSoft,
        unfocusedContainerColor = InkSoft,
        focusedPlaceholderColor = Mist,
        unfocusedPlaceholderColor = Mist,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(InkRaised)
                .padding(20.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                placeholder = { Text("192.168.1.10 or nas.home") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = share,
                onValueChange = { share = it },
                label = { Text("Share name") },
                placeholder = { Text("photos") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username") },
                placeholder = { Text("Leave blank for guest") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onAdd(host, share, user, password) },
                enabled = !busy && host.isNotBlank() && (share.isNotBlank() || host.contains('/') || host.contains('\\')),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink, disabledContainerColor = InkSoft, disabledContentColor = Mist),
            ) {
                Text(if (busy) "Connecting…" else "Connect")
            }
            if (busy) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
            }
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(message, color = Amber, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Host is an IP or hostname. Share is the share name, not a folder inside it.",
            color = Mist,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
    }
}
