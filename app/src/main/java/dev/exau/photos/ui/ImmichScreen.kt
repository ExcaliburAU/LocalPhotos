package dev.exau.photos.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.immich.ImmichAsset
import dev.exau.photos.immich.ImmichNetwork
import dev.exau.photos.immich.ImmichPrefs
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist

@Composable
fun ImmichConnectScreen(
    prefs: ImmichPrefs,
    busy: Boolean,
    message: String?,
    onConnect: (String, String, String) -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    var url by remember { mutableStateOf(prefs.serverUrl) }
    var email by remember { mutableStateOf(prefs.email) }
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
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.background(Ink).statusBarsPadding())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Immich", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Same three fields as the Immich app: server address, email, password.",
            color = Mist,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(InkRaised)
                .padding(20.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address") },
                placeholder = { Text("https://immich.example.com") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { Text("you@example.com") },
                supportingText = {
                    Text("The email you type in Immich, not the name on the account", color = Mist)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
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
                onClick = { onConnect(url, email, password) },
                enabled = !busy && url.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Ink,
                    disabledContainerColor = InkSoft,
                    disabledContentColor = Mist,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Ink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (busy) "Signing in…" else "Login")
            }
        }
        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = Amber, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ImmichLibraryScreen(
    assets: List<ImmichAsset>,
    authHeader: Pair<String, String>,
    busy: Boolean,
    progress: String?,
    message: String?,
    onBackupNew: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: (ImmichAsset) -> Unit,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val onWifi = rememberOnWifi()
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                progress ?: message ?: "${assets.size} on your server",
                color = Mist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Backup",
                color = if (busy || !onWifi) Mist else Ink,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (busy || !onWifi) InkSoft else Amber)
                    .clickable(enabled = !busy && onWifi, onClick = onBackupNew)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(
                "Disconnect",
                color = Mist,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(InkSoft)
                    .clickable(onClick = onDisconnect)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Text(
            if (onWifi) "Uploads wait for Wi-Fi so they don’t use mobile data." else "On mobile data · backup waits for Wi-Fi.",
            color = Mist,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        when {
            busy && assets.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            assets.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text("Connected, but no photos on the server yet.\nTap Backup phone to upload from this device.", color = Mist)
            }
            else -> {
                val context = LocalContext.current
                val gridState = rememberLazyGridState()
                val nearEnd = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?.let { it >= assets.lastIndex - 9 } == true
                LaunchedEffect(nearEnd, assets.size, hasMore, loadingMore, busy) {
                    if (nearEnd && hasMore && !loadingMore && !busy) onLoadMore()
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(assets, key = { it.id }) { asset ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(InkRaised)
                                .clickable { onOpen(asset) },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(asset.thumbUrl)
                                    .addHeader(authHeader.first, authHeader.second)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = asset.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (asset.isVideo) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xAA000000))
                                        .padding(2.dp),
                                )
                            }
                        }
                    }
                    if (loadingMore || hasMore) {
                        item(span = { GridItemSpan(3) }, key = "immich-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loadingMore) {
                                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                } else {
                                    Text("More on the server…", color = Mist, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImmichViewerScreen(
    asset: ImmichAsset,
    authHeader: Pair<String, String>,
    busy: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (asset.isVideo) {
            AppVideoPlayer(
                uri = Uri.parse(asset.originalUrl),
                chromeVisible = chrome,
                onToggleChrome = { chrome = !chrome },
                headers = mapOf(authHeader.first to authHeader.second),
            )
        } else {
            ZoomableAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(asset.previewUrl)
                    .addHeader(authHeader.first, authHeader.second)
                    .build(),
                contentDescription = asset.fileName,
                onTap = { chrome = !chrome },
                onZoomed = { },
            )
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
                    Text(
                        asset.fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
                        maxLines = 1,
                    )
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
                    if (!asset.isVideo) {
                        ViewerAction(
                            icon = Icons.Filled.Edit,
                            label = if (busy) "…" else "Edit",
                            onClick = { if (!busy) onEdit() },
                        )
                    }
                    ViewerAction(
                        icon = Icons.Filled.Share,
                        label = if (busy) "…" else "Share",
                        onClick = { if (!busy) onShare() },
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
            title = { Text(asset.fileName) },
            text = {
                Column {
                    if (asset.createdAt > 0L) {
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Taken", color = Mist, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(MediaRepository.fullDate(asset.createdAt), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("Type", color = Mist, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(if (asset.isVideo) "Video" else "Photo", color = Color.White, style = MaterialTheme.typography.bodyMedium)
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
private fun rememberOnWifi(): Boolean {
    val context = LocalContext.current
    var onWifi by remember { mutableStateOf(ImmichNetwork.onWifi(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        fun refresh() {
            main.post { onWifi = ImmichNetwork.onWifi(context) }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return onWifi
}
