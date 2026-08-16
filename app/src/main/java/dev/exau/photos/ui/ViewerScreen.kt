package dev.exau.photos.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.exau.photos.data.MediaItem
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.Mist

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startId: Long,
    favorites: Set<Long>,
    onBack: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onRequestDelete: (MediaItem) -> Unit,
    onEdit: ((MediaItem) -> Unit)? = null,
    onRestore: ((MediaItem) -> Unit)? = null,
    onSetCover: ((MediaItem) -> Unit)? = null,
) {
    val startIndex = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { items.size })
    val current = items.getOrNull(pagerState.currentPage) ?: return
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed && !scrubbing,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                if (page == pagerState.currentPage) {
                    AppVideoPlayer(
                        uri = item.uri,
                        chromeVisible = chrome,
                        onToggleChrome = { chrome = !chrome },
                        onScrubbing = { scrubbing = it },
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
            } else {
                ZoomableAsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    onTap = { chrome = !chrome },
                    onZoomed = { zoomed = it },
                )
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
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent),
                            ),
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
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            MediaRepository.dayTitle(current.dateTaken),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${pagerState.currentPage + 1} / ${items.size}",
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
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerAction(
                        icon = if (current.id in favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = "Like",
                        tint = if (current.id in favorites) Amber else Color.White,
                        onClick = { onToggleFavorite(current.id) },
                    )
                    if (onRestore != null) {
                        ViewerAction(
                            icon = Icons.Filled.Check,
                            label = "Restore",
                            tint = Amber,
                            onClick = { onRestore(current) },
                        )
                    }
                    if (onEdit != null && !current.isVideo) {
                        ViewerAction(
                            icon = Icons.Filled.Edit,
                            label = "Edit",
                            onClick = { onEdit(current) },
                        )
                    }
                    ViewerAction(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = {
                            val stream = if (!current.isVideo && current.mimeType.startsWith("image/")) {
                                runCatching {
                                    dev.exau.photos.data.ExifShare.copyWithoutExif(context, current.uri, current.displayName)
                                }.getOrDefault(current.uri)
                            } else {
                                current.uri
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = if (stream != current.uri) "image/jpeg" else current.mimeType
                                putExtra(Intent.EXTRA_STREAM, stream)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share"))
                        },
                    )
                    ViewerAction(
                        icon = Icons.Filled.Info,
                        label = "Info",
                        onClick = { showInfo = true },
                    )
                    ViewerAction(
                        icon = Icons.Filled.Delete,
                        label = if (onRestore != null) "Delete" else "Bin",
                        onClick = { confirmDelete = true },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val forever = onRestore != null
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (forever) "Delete forever?" else "Move to bin?") },
            text = {
                Text(
                    if (forever) {
                        "This cannot be undone."
                    } else {
                        "You can restore it from Photos → Bin for 30 days."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onRequestDelete(current)
                    },
                ) { Text(if (forever) "Delete forever" else "Move to bin") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            containerColor = Ink,
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(current.displayName) },
            text = {
                Column {
                    InfoRow("Taken", MediaRepository.fullDate(current.dateTaken))
                    InfoRow("Folder", current.bucketName.ifBlank { "Other" })
                    InfoRow("Type", current.mimeType)
                    if (current.width > 0) InfoRow("Size", "${current.width} × ${current.height}")
                    InfoRow("File", MediaRepository.formatSize(current.sizeBytes))
                    if (current.isVideo) InfoRow("Length", MediaRepository.formatDuration(current.durationMs))
                    Spacer(Modifier.height(8.dp))
                    Text("Stored only on this device.", color = Mist, style = MaterialTheme.typography.bodyMedium)
                    if (onSetCover != null && !current.isVideo) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            showInfo = false
                            onSetCover(current)
                        }) { Text("Use as album cover", color = Amber) }
                    }
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
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Mist, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}
