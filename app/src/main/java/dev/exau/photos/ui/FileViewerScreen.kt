package dev.exau.photos.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.files.FileEntry
import dev.exau.photos.files.FileKinds
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist

@Composable
fun FileViewerScreen(
    items: List<FileEntry>,
    startRelative: String,
    imageLoader: ImageLoader,
    modelFor: (FileEntry) -> Any,
    localPathFor: (FileEntry) -> String?,
    busy: Boolean,
    onBack: () -> Unit,
    onShare: (FileEntry) -> Unit,
    onEdit: (FileEntry) -> Unit,
    onOpen: (FileEntry) -> Unit,
    onPrepareVideo: (FileEntry) -> Unit = {},
    onSetCover: ((FileEntry) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        onBack()
        return
    }
    val start = items.indexOfFirst { it.relative == startRelative }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start, pageCount = { items.size })
    val current = items.getOrNull(pager.currentPage) ?: return
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed && !scrubbing,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            when {
                item.isVideo -> {
                    val path = localPathFor(item)
                    if (path == null) {
                        FileStubPage(
                            entry = item,
                            busy = busy,
                            message = "Download this video to play it here.",
                            action = if (busy) "Loading…" else "Play",
                            onOpen = { onPrepareVideo(item) },
                            onTap = { chrome = !chrome },
                        )
                    } else if (page == pager.currentPage) {
                        AppVideoPlayer(
                            uri = Uri.fromFile(java.io.File(path)),
                            chromeVisible = chrome,
                            onToggleChrome = { chrome = !chrome },
                            onScrubbing = { scrubbing = it },
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                }
                item.isImage -> {
                    ZoomableAsyncImage(
                        model = modelFor(item),
                        contentDescription = item.name,
                        imageLoader = imageLoader,
                        onTap = { chrome = !chrome },
                        onZoomed = { zoomed = it },
                    )
                }
                else -> {
                    FileStubPage(
                        entry = item,
                        busy = busy,
                        message = "Open with another app on this phone.",
                        action = if (busy) "Opening…" else "Open",
                        onOpen = { onOpen(item) },
                        onTap = { chrome = !chrome },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x33000000)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            current.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${pager.currentPage + 1} / ${items.size}",
                            color = Mist,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerAction(
                        icon = Icons.Filled.PlayArrow,
                        label = if (busy) "…" else "Open",
                        onClick = { if (!busy) onOpen(current) },
                    )
                    if (current.isImage) {
                        ViewerAction(
                            icon = Icons.Filled.Edit,
                            label = if (busy) "…" else "Edit",
                            onClick = { if (!busy) onEdit(current) },
                        )
                    }
                    if (current.isImage && onSetCover != null) {
                        ViewerAction(
                            icon = Icons.Filled.Star,
                            label = "Cover",
                            onClick = { onSetCover(current) },
                        )
                    }
                    ViewerAction(
                        icon = Icons.Filled.Share,
                        label = if (busy) "…" else "Share",
                        onClick = { if (!busy) onShare(current) },
                    )
                    ViewerAction(
                        icon = Icons.Filled.Info,
                        label = "Info",
                        onClick = { showInfo = true },
                    )
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(current.name) },
            text = {
                Column {
                    if (current.lastModified > 0L) {
                        InfoLine("Modified", MediaRepository.fullDate(current.lastModified))
                    }
                    if (current.size > 0L) {
                        InfoLine("File", MediaRepository.formatSize(current.size))
                    }
                    InfoLine("Type", FileKinds.kindLabel(current.name))
                    InfoLine("Format", FileKinds.mime(current.name))
                    InfoLine("Path", current.relative.ifBlank { current.name })
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close") }
            },
            containerColor = Ink,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Mist, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FileStubPage(
    entry: FileEntry,
    busy: Boolean,
    message: String,
    action: String,
    onOpen: () -> Unit,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(entry.relative) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(InkRaised)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(InkSoft)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(FileKinds.extLabel(entry.name), color = Amber, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(entry.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (entry.size > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(MediaRepository.formatSize(entry.size), color = Mist, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(message, color = Mist, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpen,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(action)
            }
        }
    }
}
