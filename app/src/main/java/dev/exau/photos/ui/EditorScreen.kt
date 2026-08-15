package dev.exau.photos.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.exau.photos.data.MediaItem
import dev.exau.photos.edit.CropRect
import dev.exau.photos.edit.EditState
import dev.exau.photos.edit.PhotoEditor
import dev.exau.photos.edit.PhotoFilter
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.InkSoft
import dev.exau.photos.ui.theme.Mist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

private enum class EditorTool { Crop, Rotate, Adjust, Filters, Draw, Size }

@Composable
fun EditorScreen(
    item: MediaItem,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    EditorScreen(
        uri = item.uri,
        displayName = item.displayName,
        onCancel = onCancel,
        onSaved = onSaved,
    )
}

@Composable
fun EditorScreen(
    uri: Uri,
    displayName: String,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(EditState()) }
    var tool by remember { mutableStateOf(EditorTool.Adjust) }
    var saving by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(uri) {
        loadError = null
        bitmap = withContext(Dispatchers.IO) {
            runCatching { PhotoEditor.load(context, uri) }.getOrElse {
                loadError = it.message ?: "Could not open photo"
                null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { bitmap?.recycle() }
    }

    BackHandler(onBack = onCancel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Edit", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Saves a copy on this phone", color = Mist, style = MaterialTheme.typography.labelLarge)
            }
            IconButton(
                enabled = bitmap != null && !saving,
                onClick = {
                    val src = bitmap ?: return@IconButton
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                val rendered = PhotoEditor.render(src, state)
                                PhotoEditor.save(context, rendered, displayName)
                                if (rendered !== src) rendered.recycle()
                            }.isSuccess
                        }
                        saving = false
                        if (ok) {
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            Toast.makeText(context, "Could not save", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Save copy", tint = Amber)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            when {
                loadError != null -> Text(loadError ?: "", color = Mist)
                bmp == null || saving -> CircularProgressIndicator(color = Amber)
                else -> EditorPreview(
                    bitmap = bmp,
                    state = state,
                    showCrop = tool == EditorTool.Crop,
                    showDraw = tool == EditorTool.Draw,
                    strokes = state.strokes,
                    onStroke = { stroke -> state = state.copy(strokes = state.strokes + stroke) },
                    lockedAspect = aspect,
                    onCropChange = { state = state.copy(crop = it) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(InkRaised)
                .padding(bottom = 8.dp),
        ) {
            when (tool) {
                EditorTool.Crop -> CropTools(
                    aspect = aspect,
                    onAspect = { next ->
                        aspect = next
                        val src = bitmap ?: return@CropTools
                        val rot = state.rotationDegrees
                        val w = if (rot == 90 || rot == 270) src.height else src.width
                        val h = if (rot == 90 || rot == 270) src.width else src.height
                        state = state.copy(crop = cropForAspect(w, h, next))
                    },
                    onReset = {
                        aspect = null
                        state = state.copy(crop = CropRect())
                    },
                )
                EditorTool.Rotate -> RotateTools(
                    onCcw = { state = state.rotateCcw() },
                    onCw = { state = state.rotateCw() },
                    onFlipH = { state = state.copy(flipH = !state.flipH, crop = CropRect()) },
                    onFlipV = { state = state.copy(flipV = !state.flipV, crop = CropRect()) },
                )
                EditorTool.Adjust -> AdjustTools(
                    state = state,
                    onChange = { state = it },
                )
                EditorTool.Filters -> FilterTools(
                    selected = state.filter,
                    onSelect = { state = state.copy(filter = it) },
                )
                EditorTool.Draw -> DrawTools(
                    onUndo = { if (state.strokes.isNotEmpty()) state = state.copy(strokes = state.strokes.dropLast(1)) },
                    onClear = { state = state.copy(strokes = emptyList()) },
                )
                EditorTool.Size -> SizeTools(
                    maxSide = state.maxSide,
                    onChange = { state = state.copy(maxSide = it) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorTool.entries.forEach { t ->
                    val on = t == tool
                    Text(
                        t.name,
                        color = if (on) Amber else Mist,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) InkSoft else Color.Transparent)
                            .clickable { tool = t }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPreview(
    bitmap: Bitmap,
    state: EditState,
    showCrop: Boolean,
    showDraw: Boolean,
    strokes: List<dev.exau.photos.edit.DrawStroke>,
    onStroke: (dev.exau.photos.edit.DrawStroke) -> Unit,
    lockedAspect: Float?,
    onCropChange: (CropRect) -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val matrix = remember(state.filter, state.brightness, state.contrast, state.saturation, state.warmth) {
        ColorFilter.colorMatrix(ColorMatrix(state.colorMatrixValues()))
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val rot = state.rotationDegrees
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val drawnW = if (rot == 90 || rot == 270) srcH else srcW
        val drawnH = if (rot == 90 || rot == 270) srcW else srcH
        val scale = min(boxW / drawnW, boxH / drawnH)
        val dest = Size(drawnW * scale, drawnH * scale)
        val unrotated = Size(srcW * scale, srcH * scale)
        val density = LocalDensity.current

        Box(
            modifier = Modifier.size(
                width = with(density) { dest.width.toDp() },
                height = with(density) { dest.height.toDp() },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = matrix,
                modifier = Modifier
                    .size(
                        width = with(density) { unrotated.width.toDp() },
                        height = with(density) { unrotated.height.toDp() },
                    )
                    .graphicsLayer {
                        rotationZ = rot.toFloat()
                        scaleX = if (state.flipH) -1f else 1f
                        scaleY = if (state.flipV) -1f else 1f
                    },
            )
            if (showCrop) {
                CropOverlay(
                    crop = state.crop,
                    imageAspect = drawnW / drawnH,
                    lockedAspect = lockedAspect,
                    onChange = onCropChange,
                )
            } else if (state.hasCrop) {
                CropDim(crop = state.crop)
            }
            if (showDraw || strokes.isNotEmpty()) {
                DrawOverlay(
                    strokes = strokes,
                    enabled = showDraw,
                    onStroke = onStroke,
                )
            }
        }
    }
}

@Composable
private fun CropDim(crop: CropRect) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val r = Rect(
            crop.left * size.width,
            crop.top * size.height,
            crop.right * size.width,
            crop.bottom * size.height,
        )
        drawRect(Color(0x66000000), topLeft = Offset.Zero, size = Size(size.width, r.top))
        drawRect(Color(0x66000000), topLeft = Offset(0f, r.bottom), size = Size(size.width, size.height - r.bottom))
        drawRect(Color(0x66000000), topLeft = Offset(0f, r.top), size = Size(r.left, r.height))
        drawRect(Color(0x66000000), topLeft = Offset(r.right, r.top), size = Size(size.width - r.right, r.height))
    }
}

private enum class CropHandle { Move, TL, TR, BL, BR }

@Composable
private fun CropOverlay(
    crop: CropRect,
    imageAspect: Float,
    lockedAspect: Float?,
    onChange: (CropRect) -> Unit,
) {
    var active by remember { mutableStateOf<CropHandle?>(null) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(crop, lockedAspect, imageAspect) {
                detectDragGestures(
                    onDragStart = { start ->
                        val sz = Size(size.width.toFloat(), size.height.toFloat())
                        val r = crop.toPx(sz)
                        val hit = 28.dp.toPx()
                        active = when {
                            start.near(r.topLeft, hit) -> CropHandle.TL
                            start.near(r.topRight, hit) -> CropHandle.TR
                            start.near(r.bottomLeft, hit) -> CropHandle.BL
                            start.near(r.bottomRight, hit) -> CropHandle.BR
                            start.x in r.left..r.right && start.y in r.top..r.bottom -> CropHandle.Move
                            else -> null
                        }
                    },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                    onDrag = { change, drag ->
                        change.consume()
                        val handle = active ?: return@detectDragGestures
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        onChange(applyCropDrag(crop, handle, dx, dy, lockedAspect, imageAspect))
                    },
                )
            },
    ) {
        val r = crop.toPx(size)
        drawRect(Color(0x99000000), topLeft = Offset.Zero, size = Size(size.width, r.top))
        drawRect(Color(0x99000000), topLeft = Offset(0f, r.bottom), size = Size(size.width, size.height - r.bottom))
        drawRect(Color(0x99000000), topLeft = Offset(0f, r.top), size = Size(r.left, r.height))
        drawRect(Color(0x99000000), topLeft = Offset(r.right, r.top), size = Size(size.width - r.right, r.height))
        drawRect(Color.White, topLeft = r.topLeft, size = r.size, style = Stroke(width = 2.dp.toPx()))
        val thirdW = r.width / 3f
        val thirdH = r.height / 3f
        for (i in 1..2) {
            drawLine(Color(0x66FFFFFF), Offset(r.left + thirdW * i, r.top), Offset(r.left + thirdW * i, r.bottom), 1.dp.toPx())
            drawLine(Color(0x66FFFFFF), Offset(r.left, r.top + thirdH * i), Offset(r.right, r.top + thirdH * i), 1.dp.toPx())
        }
        val hs = 10.dp.toPx()
        listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { p ->
            drawRect(Amber, topLeft = Offset(p.x - hs / 2, p.y - hs / 2), size = Size(hs, hs))
        }
    }
}

private fun CropRect.toPx(size: Size): Rect = Rect(
    left * size.width,
    top * size.height,
    right * size.width,
    bottom * size.height,
)

private fun Offset.near(other: Offset, hit: Float): Boolean =
    abs(x - other.x) <= hit && abs(y - other.y) <= hit

private fun applyCropDrag(
    crop: CropRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    lockedAspect: Float?,
    imageAspect: Float,
): CropRect {
    var l = crop.left
    var t = crop.top
    var r = crop.right
    var b = crop.bottom
    when (handle) {
        CropHandle.Move -> {
            val w = r - l
            val h = b - t
            l = (l + dx).coerceIn(0f, 1f - w)
            t = (t + dy).coerceIn(0f, 1f - h)
            r = l + w
            b = t + h
        }
        CropHandle.TL -> {
            l = (l + dx).coerceIn(0f, r - 0.08f)
            t = (t + dy).coerceIn(0f, b - 0.08f)
        }
        CropHandle.TR -> {
            r = (r + dx).coerceIn(l + 0.08f, 1f)
            t = (t + dy).coerceIn(0f, b - 0.08f)
        }
        CropHandle.BL -> {
            l = (l + dx).coerceIn(0f, r - 0.08f)
            b = (b + dy).coerceIn(t + 0.08f, 1f)
        }
        CropHandle.BR -> {
            r = (r + dx).coerceIn(l + 0.08f, 1f)
            b = (b + dy).coerceIn(t + 0.08f, 1f)
        }
    }
    var next = CropRect(l, t, r, b).coerced()
    if (lockedAspect != null) {
        next = constrainAspect(next, lockedAspect, imageAspect, handle)
    }
    return next
}

private fun constrainAspect(
    crop: CropRect,
    photoAspect: Float,
    imageAspect: Float,
    handle: CropHandle,
): CropRect {
    val targetNorm = photoAspect / imageAspect
    val w = crop.width
    val h = (w / targetNorm).coerceAtLeast(0.08f)
    return when (handle) {
        CropHandle.Move -> crop
        CropHandle.TL, CropHandle.TR -> {
            val top = (crop.bottom - h).coerceAtLeast(0f)
            crop.copy(top = top, bottom = top + (crop.bottom - top).coerceAtMost(h).let { crop.bottom - top })
                .let {
                    val hh = (it.width / targetNorm)
                    val t = (it.bottom - hh).coerceAtLeast(0f)
                    CropRect(it.left, t, it.right, (t + hh).coerceAtMost(1f)).coerced()
                }
        }
        else -> {
            val hh = crop.width / targetNorm
            val b = (crop.top + hh).coerceAtMost(1f)
            CropRect(crop.left, crop.top, crop.right, b).coerced()
        }
    }
}

private fun cropForAspect(imgW: Int, imgH: Int, aspect: Float?): CropRect {
    if (aspect == null) return CropRect()
    val imgAspect = imgW.toFloat() / imgH.toFloat()
    return if (imgAspect > aspect) {
        val normW = aspect / imgAspect
        val left = (1f - normW) / 2f
        CropRect(left, 0f, left + normW, 1f)
    } else {
        val normH = imgAspect / aspect
        val top = (1f - normH) / 2f
        CropRect(0f, top, 1f, top + normH)
    }
}

@Composable
private fun CropTools(aspect: Float?, onAspect: (Float?) -> Unit, onReset: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AspectChip("Free", aspect == null) { onAspect(null) }
        AspectChip("1:1", aspect == 1f) { onAspect(1f) }
        AspectChip("4:3", aspect == 4f / 3f) { onAspect(4f / 3f) }
        AspectChip("16:9", aspect == 16f / 9f) { onAspect(16f / 9f) }
        TextButton(onClick = onReset) { Text("Reset", color = Amber) }
    }
}

@Composable
private fun AspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Ink else Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Amber else InkSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun RotateTools(
    onCcw: () -> Unit,
    onCw: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = onCcw) { Text("↺ 90°", color = Color.White) }
        TextButton(onClick = onCw) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("90°", color = Color.White)
            }
        }
        TextButton(onClick = onFlipH) { Text("Flip H", color = Color.White) }
        TextButton(onClick = onFlipV) { Text("Flip V", color = Color.White) }
    }
}

@Composable
private fun AdjustTools(state: EditState, onChange: (EditState) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        AdjustSlider("Brightness", state.brightness) { onChange(state.copy(brightness = it)) }
        AdjustSlider("Contrast", state.contrast) { onChange(state.copy(contrast = it)) }
        AdjustSlider("Saturation", state.saturation) { onChange(state.copy(saturation = it)) }
        AdjustSlider("Warmth", state.warmth) { onChange(state.copy(warmth = it)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onChange(EditState.auto().copy(filter = state.filter, crop = state.crop, rotationDegrees = state.rotationDegrees, flipH = state.flipH, flipV = state.flipV)) }) {
                Text("Auto", color = Amber)
            }
            TextButton(onClick = { onChange(state.copy(brightness = 0f, contrast = 0f, saturation = 0f, warmth = 0f)) }) {
                Text("Reset", color = Mist)
            }
        }
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Mist, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(88.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = InkSoft,
            ),
        )
    }
}

@Composable
private fun FilterTools(selected: PhotoFilter, onSelect: (PhotoFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhotoFilter.entries.forEach { filter ->
            val on = filter == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(filter) }
                    .padding(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(filterSwatch(filter))
                        .then(
                            if (on) Modifier.border(2.dp, Amber, CircleShape) else Modifier,
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(filter.label, color = if (on) Amber else Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun filterSwatch(filter: PhotoFilter): Color = when (filter) {
    PhotoFilter.None -> Color(0xFFB0B0B0)
    PhotoFilter.Mono -> Color(0xFF6B6B6B)
    PhotoFilter.Warm -> Color(0xFFD4A574)
    PhotoFilter.Cool -> Color(0xFF7BA3C9)
    PhotoFilter.Fade -> Color(0xFFC5B8B0)
    PhotoFilter.Punch -> Color(0xFFC45C3E)
}

@Composable
private fun DrawOverlay(
    strokes: List<dev.exau.photos.edit.DrawStroke>,
    enabled: Boolean,
    onStroke: (dev.exau.photos.edit.DrawStroke) -> Unit,
) {
    var current by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                current = listOf(start.x / size.width to start.y / size.height)
                            },
                            onDragEnd = {
                                if (current.size >= 2) {
                                    onStroke(dev.exau.photos.edit.DrawStroke(current))
                                }
                                current = emptyList()
                            },
                            onDragCancel = { current = emptyList() },
                            onDrag = { change, _ ->
                                change.consume()
                                val next = change.position.x / size.width to change.position.y / size.height
                                current = current + next
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val all = if (current.size >= 2) strokes + dev.exau.photos.edit.DrawStroke(current) else strokes
        all.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            for (i in 1 until stroke.points.size) {
                val a = stroke.points[i - 1]
                val b = stroke.points[i]
                drawLine(
                    Color(stroke.color),
                    Offset(a.first * size.width, a.second * size.height),
                    Offset(b.first * size.width, b.second * size.height),
                    strokeWidth = stroke.width * size.width,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun DrawTools(onUndo: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = onUndo) { Text("Undo", color = Color.White) }
        TextButton(onClick = onClear) { Text("Clear", color = Mist) }
    }
}

@Composable
private fun SizeTools(maxSide: Int, onChange: (Int) -> Unit) {
    val options = listOf(0 to "Full", 2048 to "2048", 1280 to "1280", 720 to "720")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            AspectChip(label, maxSide == value) { onChange(value) }
        }
    }
}
