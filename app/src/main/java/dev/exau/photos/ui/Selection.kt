package dev.exau.photos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.exau.photos.data.ExifShare
import dev.exau.photos.data.MediaItem
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.data.MediaSection
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist

@Composable
fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onShare: (() -> Unit)? = null,
    onCover: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDeleteForever: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(Icons.Filled.Close, "Cancel", onClose)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                if (count == 0) "Select items" else "$count selected",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text("Long-press or tap to choose", style = MaterialTheme.typography.bodyMedium, color = Mist)
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
        if (onShare != null) {
            CircleIcon(Icons.Filled.Share, "Share", onShare, enabled = count > 0)
        }
        if (onCover != null) {
            Text(
                "Cover",
                color = if (count == 1) Amber else Mist,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = count == 1, onClick = onCover)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (onRestore != null) {
            CircleIcon(Icons.Filled.Check, "Restore", onRestore, enabled = count > 0, tint = Amber)
        }
        if (onTrash != null) {
            CircleIcon(Icons.Filled.Delete, "Move to bin", onTrash, enabled = count > 0)
        }
        if (onDeleteForever != null) {
            CircleIcon(Icons.Filled.Delete, "Delete forever", onDeleteForever, enabled = count > 0, tint = Color(0xFFFF8A80))
        }
    }
}

@Composable
private fun CircleIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(InkSoft)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else Mist,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun BinScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onRestore: (List<MediaItem>) -> Unit,
    onDeleteForever: (List<MediaItem>) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val chosen = items.filter { it.id in selected }

    LaunchedEffect(items) {
        val ids = items.map { it.id }.toSet()
        selected = selected.intersect(ids)
    }

    BackHandler {
        if (selecting) {
            selecting = false
            selected = emptySet()
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
                count = selected.size,
                allSelected = items.isNotEmpty() && selected.size == items.size,
                onClose = {
                    selecting = false
                    selected = emptySet()
                },
                onToggleAll = {
                    selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet()
                },
                onRestore = { if (chosen.isNotEmpty()) onRestore(chosen) },
                onDeleteForever = { if (chosen.isNotEmpty()) confirmDelete = true },
            )
        } else {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Bin", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(
                        if (items.isEmpty()) "Deleted items stay 30 days" else "${items.size} · restore or delete forever",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                }
                if (items.isNotEmpty()) {
                    Text(
                        "Select",
                        color = Amber,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selecting = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Text(
                        "Empty",
                        color = Mist,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { confirmEmpty = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Bin is empty.\nDeleted photos stay here for 30 days, then the system removes them.",
                    color = Mist,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            SectionedGrid(
                sections = listOf(MediaSection("In bin", items)),
                onOpen = onOpen,
                selecting = selecting,
                selectedIds = selected,
                onToggle = { item ->
                    selected = if (item.id in selected) selected - item.id else selected + item.id
                    selecting = true
                },
                onLongSelect = { item ->
                    selecting = true
                    selected = selected + item.id
                },
                subtitleFor = { MediaRepository.trashCountdown(it.dateExpires) },
            )
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty bin?") },
            text = { Text("Permanently delete ${items.size} items. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        onDeleteForever(items)
                    },
                ) { Text("Delete forever", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
            containerColor = Ink,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete forever?") },
            text = { Text("Permanently delete ${chosen.size} items. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteForever(chosen)
                    },
                ) { Text("Delete forever", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = Ink,
        )
    }
}

fun viewFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = mime.ifBlank { "application/octet-stream" }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri(file.name, uri)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open"))
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime.ifBlank { "application/octet-stream" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share"))
}

fun shareItems(context: android.content.Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList<Uri>(
        items.map { item ->
            if (!item.isVideo && item.mimeType.startsWith("image/")) {
                runCatching { ExifShare.copyWithoutExif(context, item.uri, item.displayName) }.getOrDefault(item.uri)
            } else {
                item.uri
            }
        },
    )
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (items.all { it.isVideo }) "video/*" else "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share"))
}
