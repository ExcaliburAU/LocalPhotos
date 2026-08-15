package dev.exau.photos.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.exau.photos.data.MediaRepository
import dev.exau.photos.ui.theme.Amber
import kotlinx.coroutines.delay

@Composable
fun AppVideoPlayer(
    uri: Uri,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
    headers: Map<String, String> = emptyMap(),
    onScrubbing: (Boolean) -> Unit = {},
    bottomContentPadding: Dp = 104.dp,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val headerKey = headers.entries.joinToString { "${it.key}=${it.value}" }
    val player = remember(uri, headerKey) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val factory = DefaultDataSource.Factory(context, http)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                prepare()
            }
    }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                playing = p.isPlaying
                buffering = p.playbackState == Player.STATE_BUFFERING
                val dur = p.duration
                duration = if (dur > 0) dur else 0L
                if (!seeking) position = p.currentPosition.coerceAtLeast(0L)
                if (p.playbackState == Player.STATE_ENDED) {
                    p.seekTo(0)
                    p.pause()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(playing, seeking, player) {
        while (playing && !seeking) {
            position = player.currentPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isClickable = false
                    isFocusable = false
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uri) {
                    detectTapGestures { onToggleChrome() }
                },
        )
        if (buffering && !playing) {
            CircularProgressIndicator(color = Amber, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        }
        if (!playing && !buffering) {
            IconButton(
                onClick = { player.play() },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        if (chromeVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, bottom = bottomContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (playing) player.pause() else player.play()
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    if (playing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(width = 5.dp, height = 16.dp)
                                    .background(Color.White, CircleShape),
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 5.dp, height = 16.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                        )
                    }
                }
                Text(
                    MediaRepository.formatDuration(position),
                    color = Color.White,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.Start,
                )
                Slider(
                    value = position.toFloat().coerceAtMost(duration.toFloat().coerceAtLeast(1f)),
                    onValueChange = {
                        seeking = true
                        onScrubbing(true)
                        position = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(position)
                        seeking = false
                        onScrubbing(false)
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    enabled = duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Amber,
                        activeTrackColor = Amber,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        disabledThumbColor = Amber.copy(alpha = 0.5f),
                        disabledActiveTrackColor = Amber.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                Text(
                    MediaRepository.formatDuration(duration),
                    color = Color.White,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
