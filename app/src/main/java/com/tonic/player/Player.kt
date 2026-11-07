package com.tonic.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(vm: AppVm, onOpen: () -> Unit) {
    val t = vm.currentTrack ?: return
    val glass = vm.ui.glass.value
    val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
    val shape = if (glass) RoundedCornerShape(24.dp) else RectangleShape
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = if (glass) 14.dp else 0.dp, vertical = if (glass) 8.dp else 0.dp)
            .panel(shape, MaterialTheme.colorScheme.surfaceVariant, dark = 0.38f)
            .clickable { onOpen() }
    ) {
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = Color.Transparent
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t.thumb, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (vm.ui.miniPrev.value) {
                IconButton(onClick = { vm.prev() }) { Icon(Icons.Default.SkipPrevious, null) }
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, null) }
        }
    }
}

@Composable
fun FullPlayer(vm: AppVm, onClose: () -> Unit) {
    val t = vm.currentTrack
    val lib by vm.store.lib.collectAsState()
    val fav = t != null && lib.favorites.any { it.url == t.url }
    var drag by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    var tempo by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t?.url, showLyrics) { if (showLyrics && t != null) vm.loadLyrics(t) }
    val round by animateDpAsState(if (vm.isPlaying) 32.dp else 16.dp, tween(100), label = "round")
    val shown = drag ?: vm.positionMs
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (vm.ui.glass.value) GlassBackdrop(vm, strong = true)
        Column(
            Modifier.systemBarsPadding().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(32.dp)) }
                Spacer(Modifier.weight(1f))
                Text("Сейчас играет", color = muted)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (fav) "Убрать из избранного" else "В избранное") },
                            onClick = { t?.let { vm.toggleFav(it) }; menu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Добавить в плейлист") },
                            onClick = { menu = false; pick = true }
                        )
                        DropdownMenuItem(
                            text = { Text(if (showLyrics) "Показать обложку" else "Текст песни") },
                            onClick = { menu = false; showLyrics = !showLyrics }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (SleepTimer.endsAt > 0) "Таймер сна (ещё ${SleepTimer.minutesLeft()} мин)"
                                    else "Таймер сна"
                                )
                            },
                            onClick = { menu = false; sleep = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Скорость и тональность") },
                            onClick = { menu = false; tempo = true }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clickable { showLyrics = !showLyrics }) {
                if (showLyrics) LyricsPanel(vm) else Art(t?.thumb, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(20.dp))
            Text(
                t?.title ?: "", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(t?.artist ?: "", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))

            SeekBar(
                value = shown, max = vm.durationMs,
                onDrag = { drag = it },
                onDragEnd = { drag?.let { vm.controller?.seekTo(it) }; drag = null },
                onTap = { vm.controller?.seekTo(it); vm.positionMs = it }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(shown), style = MaterialTheme.typography.bodySmall)
                Text(fmt(vm.durationMs), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { t?.let { vm.toggleFav(it) } }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                        tint = if (fav) MaterialTheme.colorScheme.secondary else muted
                    )
                }
                IconButton(onClick = { vm.prev() }) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(32.dp)) }
                Box(
                    Modifier.size(64.dp)
                        .panel(RoundedCornerShape(round), MaterialTheme.colorScheme.surfaceVariant, tint = 0.14f)
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.buffering) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    else Icon(
                        if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                        Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, null, Modifier.size(32.dp)) }
                IconButton(onClick = { vm.toggleRepeat() }) {
                    Icon(
                        if (vm.repeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat, null,
                        tint = if (vm.repeatOne) MaterialTheme.colorScheme.onSurface else muted
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (vm.queue.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Очередь", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
                vm.queue.forEachIndexed { i, q ->
                    key(q.url to vm.queue.take(i).count { it.url == q.url }) {
                        val idx by rememberUpdatedState(i)
                        val dragging = dragIdx == i
                        Row(
                            Modifier.fillMaxWidth().height(52.dp)
                                .offset { IntOffset(0, if (dragging) dragY.roundToInt() else 0) }
                                .zIndex(if (dragging) 1f else 0f)
                                .clickable { vm.jumpTo(i) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Art(q.thumb, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    q.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (i == vm.index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    q.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = muted
                                )
                            }
                            IconButton(onClick = { vm.removeFromQueue(i) }) {
                                Icon(Icons.Default.Close, null, tint = muted)
                            }
                            Icon(
                                Icons.Default.DragHandle, null, tint = muted,
                                modifier = Modifier.size(36.dp).padding(6.dp).pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { dragIdx = idx; dragY = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragY += amount.y
                                            val shift = (dragY / rowPx).roundToInt()
                                            if (shift != 0) {
                                                val from = dragIdx
                                                val to = (from + shift).coerceIn(0, vm.queue.size - 1)
                                                if (from >= 0 && to != from) {
                                                    vm.moveQueue(from, to)
                                                    dragY -= (to - from) * rowPx
                                                    dragIdx = to
                                                }
                                            }
                                        },
                                        onDragEnd = { dragIdx = -1; dragY = 0f },
                                        onDragCancel = { dragIdx = -1; dragY = 0f }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pick && t != null) PlaylistPicker(vm, t) { pick = false }
    if (sleep) {
        val opts = listOf(15, 30, 45, 60, 90)
        AlertDialog(
            onDismissRequest = { sleep = false },
            title = { Text("Таймер сна") },
            text = {
                Column {
                    opts.forEach { m ->
                        TextButton(onClick = { SleepTimer.set(m); sleep = false }) { Text("$m минут") }
                    }
                    if (SleepTimer.endsAt > 0) {
                        TextButton(onClick = { SleepTimer.set(0); sleep = false }) { Text("Выключить таймер") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sleep = false }) { Text("Закрыть") } }
        )
    }
    if (tempo) {
        AlertDialog(
            onDismissRequest = { tempo = false },
            title = { Text("Скорость и тональность") },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Скорость", fontWeight = FontWeight.Medium)
                        Text("%.2f×".format(vm.speed), color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = vm.speed,
                        onValueChange = { vm.setTempo((it * 20).roundToInt() / 20f, vm.semitones) },
                        valueRange = 0.5f..2f,
                        steps = 29
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Тональность", fontWeight = FontWeight.Medium)
                        Text(
                            (if (vm.semitones > 0) "+" else "") + "${vm.semitones} пт",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = vm.semitones.toFloat(),
                        onValueChange = { vm.setTempo(vm.speed, it.roundToInt()) },
                        valueRange = -6f..6f,
                        steps = 11
                    )
                }
            },
            confirmButton = { TextButton(onClick = { tempo = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { vm.setTempo(1f, 0) }) { Text("Сбросить") } }
        )
    }
}

@Composable
fun LyricsPanel(vm: AppVm) {
    val l = vm.lyrics
    val white = Color.White
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            vm.lyricsState == 1 -> CircularProgressIndicator(color = white, modifier = Modifier.size(32.dp))
            l == null -> Text("Текст не найден", color = white.copy(alpha = 0.7f))
            l.synced.isNotEmpty() -> {
                val active = l.synced.indexOfLast { it.timeMs <= vm.positionMs }.coerceAtLeast(0)
                val state = rememberLazyListState()
                LaunchedEffect(active) { state.animateScrollToItem(maxOf(active - 2, 0)) }
                LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    itemsIndexed(l.synced) { i, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            Modifier.fillMaxWidth().clickable { vm.controller?.seekTo(line.timeMs) }.padding(vertical = 6.dp),
                            color = if (i == active) white else white.copy(alpha = 0.5f),
                            fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(l.plain ?: "", color = white, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
