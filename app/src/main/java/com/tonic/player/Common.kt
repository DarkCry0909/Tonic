package com.tonic.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Поворот текста на -90° (использовать вместе с .rotate(-90f)). */
fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    layout(p.height, p.width) {
        p.place(x = -(p.width / 2 - p.height / 2), y = -(p.height / 2 - p.width / 2))
    }
}

@Composable
fun Art(url: String?, modifier: Modifier) {
    val r = when (LocalPrefs.current.radius.value) {
        0 -> 0.dp
        1 -> 6.dp
        2 -> 10.dp
        else -> 18.dp
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(r)).background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun TrackRow(t: Track, vm: AppVm, onPlay: () -> Unit, onRemove: (() -> Unit)? = null) {
    val lib by vm.store.lib.collectAsState()
    val fav = lib.favorites.any { it.url == t.url }
    val downloaded by Offline.tracks.collectAsState()
    val isDone = downloaded.any { it.url == t.url }
    val prog = Offline.progress[t.url]
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onPlay() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(t.thumb, Modifier.size(54.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val sub = if (t.duration > 0) "${t.artist} · ${fmt(t.duration * 1000)}" else t.artist
            val dl = Downloads.states[t.url]
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Downloads.isDone(t.url)) {
                    Icon(Icons.Default.DownloadDone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                } else if (dl != null && Downloads.isActive(t.url)) {
                    Text(
                        "${dl.percent.toInt()}% · ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isDone) {
            Icon(Icons.Default.DownloadDone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        } else if (prog != null) {
            CircularProgressIndicator(progress = { prog }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (fav) "Убрать из избранного" else "В избранное") },
                    onClick = { vm.toggleFav(t); menu = false }
                )
                DropdownMenuItem(
                    text = { Text(if (isDone) "Удалить загрузку" else "Скачать") },
                    onClick = { if (isDone) vm.removeDownload(t) else vm.download(t); menu = false }
                )
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
                DropdownMenuItem(
                    text = { Text("Играть следующим") },
                    onClick = { vm.playNext(t); menu = false }
                )
                DropdownMenuItem(
                    text = { Text("В очередь") },
                    onClick = { vm.addToQueue(t); menu = false }
                )
                DropdownMenuItem(
                    text = { Text("Добавить в плейлист") },
                    onClick = { menu = false; pick = true }
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("Убрать из списка") },
                        onClick = { onRemove(); menu = false }
                    )
                }
            }
        }
    }
    if (pick) PlaylistPicker(vm, t) { pick = false }
}

@Composable
fun PlaylistPicker(vm: AppVm, t: Track, onClose: () -> Unit) {
    val lib by vm.store.lib.collectAsState()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
        title = { Text("В плейлист") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lib.playlists.forEach { p ->
                    TextButton(onClick = { vm.addToPlaylist(p.id, t); onClose() }) { Text(p.name) }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Новый плейлист") }, singleLine = true
                )
                TextButton(onClick = {
                    if (name.isNotBlank()) { vm.createPlaylist(name.trim(), t); onClose() }
                }) { Text("Создать и добавить") }
            }
        }
    )
}

@Composable
fun SeekBar(value: Long, max: Long, onDrag: (Long) -> Unit, onDragEnd: () -> Unit, onTap: (Long) -> Unit) {
    var width by remember { mutableIntStateOf(1) }
    fun at(x: Float): Long = if (max > 0) (x / width * max).toLong().coerceIn(0L, max) else 0L
    val frac = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f
    val fg = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(max) { detectTapGestures { o -> onTap(at(o.x)) } }
            .pointerInput(max) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onDrag(at(o.x)) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onHorizontalDrag = { c, _ -> onDrag(at(c.position.x)) }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(fg))
        }
    }
}
