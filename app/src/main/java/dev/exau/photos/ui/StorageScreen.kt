package dev.exau.photos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.files.StorageItem
import dev.exau.photos.files.StorageReport
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist

@Composable
fun StorageScreen(
    report: StorageReport?,
    busy: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onOpenPath: (StorageItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { if (report == null && !busy) onScan() }
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
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Storage", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("What’s using space on this phone", color = Mist, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when {
            busy && report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Could not read storage.", color = Mist)
            }
            else -> {
                val usedFrac = if (report.totalBytes > 0) report.usedBytes.toFloat() / report.totalBytes else 0f
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(InkRaised)
                                .padding(16.dp),
                        ) {
                            Text(
                                "${MediaRepository.formatSize(report.usedBytes)} used · ${MediaRepository.formatSize(report.freeBytes)} free",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { usedFrac.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                                color = Amber,
                                trackColor = InkSoft,
                            )
                        }
                    }
                    items(report.buckets, key = { it.label }) { bucket ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(InkRaised)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(bucket.label, color = Color.White, modifier = Modifier.weight(1f))
                            Text(MediaRepository.formatSize(bucket.bytes), color = Amber)
                        }
                    }
                    item {
                        Text("Largest", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(report.largest, key = { it.path }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(InkRaised)
                                .clickable { onOpenPath(item) }
                                .padding(14.dp),
                        ) {
                            Text(item.name, color = Color.White, maxLines = 1)
                            Text(
                                "${if (item.isDir) "Folder · " else ""}${MediaRepository.formatSize(item.bytes)}",
                                color = Mist,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
