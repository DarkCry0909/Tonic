package com.tonic.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ===================== Шторка плеера (как в ViMusic) =====================

/** Свёрнутая полоса 64dp внизу, которую можно вытянуть вверх до полноэкранного плеера. */
@Composable
fun PlayerSheet(vm: AppVm, progress: Animatable<Float, AnimationVector1D>) {
    val t = vm.currentTrack ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val navPx = WindowInsets.navigationBars.getBottom(density)
        val collapsedPx = with(density) { 64.dp.toPx() } + navPx
        val fullPx = constraints.maxHeight.toFloat()
        val travel = (fullPx - collapsedPx).coerceAtLeast(1f)
        val scope = rememberCoroutineScope()
        val dragState = rememberDraggableState { d ->
            scope.launch { progress.snapTo((progress.value - d / travel).coerceIn(0f, 1f)) }
        }
        val dragMod = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { v ->
                scope.launch {
                    progress.animateTo(if (v < -600f || (v < 600f && progress.value > 0.5f)) 1f else 0f)
                }
            }
        )
        val p = progress.value
        Box(
            Modifier
                .offset { IntOffset(0, (travel * (1f - progress.value)).roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { fullPx.toDp() })
        ) {
            if (p > 0.001f) {
                Box(Modifier.fillMaxSize().alpha(p)) {
                    ExpandedPlayer(vm, dragMod) { scope.launch { progress.animateTo(0f) } }
                }
            }
            if (p < 0.999f) {
                CollapsedBar(
                    vm, t,
                    Modifier.alpha(1f - p).then(dragMod).clickable { scope.launch { progress.animateTo(1f) } }
                )
            }
        }
    }
}

@Composable
fun CollapsedBar(vm: AppVm, t: Track, modifier: Modifier) {
    val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
    val line = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxWidth()
            .panel(RectangleShape, MaterialTheme.colorScheme.surface, dark = 0.4f)
            .drawBehind {
                drawLine(line, Offset(0f, 1.dp.toPx()), Offset(size.width * frac, 1.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(8.dp))
            Art(t.thumb, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = muted
                )
            }
            if (vm.ui.miniPrev.value) {
                IconButton(onClick = { vm.prev() }) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(22.dp)) }
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(24.dp))
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, null, Modifier.size(22.dp)) }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ===================== Развёрнутый плеер =====================

@Composable
fun ExpandedPlayer(vm: AppVm, dragMod: Modifier = Modifier, onCollapse: (() -> Unit)? = null) {
    val t = vm.currentTrack
    val ui = vm.ui
    val lib by vm.store.lib.collectAsState()
    val fav = t != null && lib.favorites.any { it.url == t.url }
    var showLyrics by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var tempo by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(t?.url, showLyrics) { if (showLyrics && t != null) vm.loadLyrics(t) }
    BackHandler(enabled = queueOpen) { queueOpen = false }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (ui.glass.value) GlassBackdrop(vm, strong = true)
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(bottom = 64.dp)) {
                    Box(Modifier.weight(0.66f).fillMaxHeight().padding(16.dp)) {
                        Cover(vm, t, showLyrics) { showLyrics = !showLyrics }
                    }
                    Controls(vm, t, Modifier.weight(1f).fillMaxHeight().padding(horizontal = 32.dp, vertical = 16.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(bottom = 64.dp)) {
                    Row(
                        dragMod.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onCollapse != null) {
                            IconButton(onClick = onCollapse) {
                                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(30.dp), tint = muted)
                            }
                        }
                    }
                    Box(Modifier.weight(1.25f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) {
                        Cover(vm, t, showLyrics) { showLyrics = !showLyrics }
                    }
                    Controls(vm, t, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp))
                }
            }

            // Нижняя полоса «Очередь» + меню
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
                    .panel(RectangleShape, MaterialTheme.colorScheme.surface, dark = 0.4f)
                    .clickable { queueOpen = true }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(48.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = muted)
                    Spacer(Modifier.width(6.dp))
                    Text("Очередь · ${vm.queue.size}", fontWeight = FontWeight.Medium)
                }
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
                        if (t != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            Downloads.isDone(t.url) -> "Удалить загрузку"
                                            Downloads.isActive(t.url) -> "Загрузка идёт…"
                                            else -> "Скачать для офлайна"
                                        }
                                    )
                                },
                                onClick = {
                                    if (Downloads.isDone(t.url)) vm.removeDownload(t) else vm.download(t)
                                    menu = false
                                }
                            )
                        }
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

            AnimatedVisibility(
                visible = queueOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) { QueuePanel(vm) { queueOpen = false } }
        }
    }

    if (pick && t != null) PlaylistPicker(vm, t) { pick = false }
    if (sleep) {
        AlertDialog(
            onDismissRequest = { sleep = false },
            title = { Text("Таймер сна") },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
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

/** Обложка: смена трека свайпом влево/вправо, нажатие показывает текст песни. */
@Composable
fun Cover(vm: AppVm, t: Track?, showLyrics: Boolean, onToggle: () -> Unit) {
    var lastIndex by remember { mutableIntStateOf(vm.index) }
    val forward = vm.index >= lastIndex
    SideEffect { lastIndex = vm.index }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth, maxHeight)
        AnimatedContent(
            targetState = t?.url ?: "",
            modifier = Modifier.size(s),
            transitionSpec = {
                val dir = if (forward) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right
                (slideIntoContainer(dir, tween(400)) + fadeIn(tween(400)) + scaleIn(tween(400), 0.85f)) togetherWith
                    (slideOutOfContainer(dir, tween(400)) + fadeOut(tween(400)) + scaleOut(tween(400), 0.85f))
            },
            label = "cover"
        ) { url ->
            val tr = vm.queue.find { it.url == url } ?: t
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onToggle() } }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -120f) vm.next() else if (total > 120f) vm.prev()
                            },
                            onHorizontalDrag = { _, d -> total += d }
                        )
                    }
            ) {
                Art(tr?.thumb, Modifier.fillMaxSize())
                if (showLyrics) LyricsPanel(vm)
            }
        }
    }
}

/** Название, исполнитель, перемотка и кнопки — как в ViMusic. */
@Composable
fun Controls(vm: AppVm, t: Track?, modifier: Modifier) {
    val lib by vm.store.lib.collectAsState()
    val fav = t != null && lib.favorites.any { it.url == t.url }
    var drag by remember { mutableStateOf<Long?>(null) }
    val round by animateDpAsState(if (vm.isPlaying) 32.dp else 16.dp, tween(100), label = "round")
    val shown = drag ?: vm.positionMs
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier, horizontalAlignment = Alignment.Start) {
        Spacer(Modifier.weight(1f))
        Text(
            t?.title ?: "", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            t?.artist ?: "", color = muted, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        SeekBar(
            value = shown, max = vm.durationMs,
            onDrag = { drag = it },
            onDragEnd = { drag?.let { vm.controller?.seekTo(it) }; drag = null },
            onTap = { vm.controller?.seekTo(it); vm.positionMs = it }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(shown), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(fmt(vm.durationMs), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { t?.let { vm.toggleFav(it) } }, modifier = Modifier.weight(1f)) {
                Icon(
                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                    Modifier.size(24.dp),
                    tint = if (fav) MaterialTheme.colorScheme.secondary else muted
                )
            }
            IconButton(onClick = { vm.prev() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.SkipPrevious, null, Modifier.size(28.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(64.dp)
                    .panel(RoundedCornerShape(round), MaterialTheme.colorScheme.surfaceVariant, tint = 0.14f)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                if (vm.buffering) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                else Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(32.dp))
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.next() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(28.dp))
            }
            IconButton(onClick = { vm.toggleRepeat() }, modifier = Modifier.weight(1f)) {
                Icon(
                    if (vm.repeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat, null,
                    Modifier.size(24.dp),
                    tint = if (vm.repeatOne) MaterialTheme.colorScheme.onSurface else muted
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ===================== Очередь =====================

@Composable
fun QueuePanel(vm: AppVm, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {}
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClose).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Очередь · ${vm.queue.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            QueueRows(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun QueueRows(vm: AppVm) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
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
                IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Default.Close, null, tint = muted) }
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
