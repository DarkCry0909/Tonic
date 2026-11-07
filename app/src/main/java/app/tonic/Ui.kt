package app.tonic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

sealed interface Screen {
    data object Search : Screen
    data object Library : Screen
    data class PlaylistView(val id: String) : Screen
    data object Settings : Screen
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun tracksWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "трек"
        m10 in 2..4 && m100 !in 12..14 -> "трека"
        else -> "треков"
    }
}

// ============================ Корень ============================

@Composable
fun AppRoot(vm: AppViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Search) }
    var playerOpen by remember { mutableStateOf(false) }
    val playlists by vm.playlists.collectAsState()

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && screen != Screen.Search) {
        screen = if (screen is Screen.PlaylistView) Screen.Library else Screen.Search
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (screen == Screen.Settings) {
                SettingsScreen(vm, onBack = { screen = Screen.Search })
            } else {
                Row(Modifier.fillMaxSize()) {
                    SideRail(screen) { screen = it }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f)) {
                            when (val s = screen) {
                                Screen.Search -> SearchScreen(vm, playlists)
                                Screen.Library -> LibraryScreen(vm, playlists) { screen = Screen.PlaylistView(it) }
                                is Screen.PlaylistView -> PlaylistScreen(
                                    vm,
                                    playlists.firstOrNull { it.id == s.id },
                                    playlists,
                                    onBack = { screen = Screen.Library }
                                )
                                else -> {}
                            }
                        }
                        if (vm.now != null) MiniPlayer(vm) { playerOpen = true }
                    }
                }
            }

            AnimatedVisibility(
                visible = playerOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                PlayerScreen(vm, playlists) { playerOpen = false }
            }
        }
    }
}

@Composable
fun SideRail(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf<Triple<Screen, ImageVector, String>>(
        Triple(Screen.Search, Icons.Filled.Search, "Поиск"),
        Triple(Screen.Library, Icons.Filled.LibraryMusic, "Библиотека"),
        Triple(Screen.Settings, Icons.Filled.Settings, "Настройки")
    )
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Spacer(Modifier.weight(1f))
        items.forEach { (s, icon, label) ->
            val selected = current == s || (s == Screen.Library && current is Screen.PlaylistView)
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(s) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

// ============================ Общие элементы ============================

@Composable
fun Cover(vm: AppViewModel, url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url.ifEmpty { null },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(vm.corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun TrackRow(
    vm: AppViewModel,
    t: Track,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    val active = vm.now?.id == t.id
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(vm, t.thumb, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
            val dur = if (t.durationSec > 0) " • " + fmtTime(t.durationSec * 1000) else ""
            Text(
                t.artist + dur,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val p = vm.progress[t.id]
        if (p != null) {
            CircularProgressIndicator(progress = { p }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (t.id in vm.downloadedIds) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Скачано",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Меню") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("В очередь") }, onClick = { menu = false; vm.enqueue(t) })
                DropdownMenuItem(text = { Text("Добавить в плейлист") }, onClick = { menu = false; onAdd() })
                if (t.id !in vm.downloadedIds && vm.progress[t.id] == null) {
                    DropdownMenuItem(text = { Text("Скачать") }, onClick = { menu = false; vm.download(t) })
                }
                if (onRemove != null) {
                    DropdownMenuItem(text = { Text("Убрать из плейлиста") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Название") }
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists) { p ->
                        Text(
                            p.name,
                            Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(vertical = 14.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("Создать и добавить") }
            } else {
                TextButton(onClick = { creating = true }) { Text("Новый плейлист") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый плейлист") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Название") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ============================ Поиск ============================

@Composable
fun SearchScreen(vm: AppViewModel, playlists: List<Playlist>) {
    var query by remember { mutableStateOf("") }
    var addTo by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Песня, исполнитель или ссылка на плейлист") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit(query) })
        )
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (vm.results.isEmpty() && !vm.busy) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Введите название песни или вставьте ссылку на плейлист YouTube / YouTube Music — он появится в библиотеке.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(vm.results) { i, t ->
                    TrackRow(vm, t, onPlay = { vm.play(vm.results, i) }, onAdd = { addTo = t })
                }
            }
        }
    }

    addTo?.let { t ->
        AddToPlaylistDialog(
            playlists,
            onPick = { vm.addToPlaylist(it, t); addTo = null },
            onCreate = { vm.createAndAdd(it, t); addTo = null },
            onDismiss = { addTo = null }
        )
    }
}

// ============================ Библиотека ============================

@Composable
fun LibraryScreen(vm: AppViewModel, playlists: List<Playlist>, onOpen: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Библиотека", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "Новый плейлист") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(playlists) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val thumb = p.tracks.firstOrNull()?.thumb ?: ""
                    if (thumb.isNotEmpty()) {
                        Cover(vm, thumb, Modifier.size(56.dp))
                    } else {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(vm.corner))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.MusicNote, contentDescription = null) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.tracks.size} ${tracksWord(p.tracks.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (creating) {
        NewPlaylistDialog(onCreate = { vm.createPlaylist(it); creating = false }, onDismiss = { creating = false })
    }
}

@Composable
fun PlaylistScreen(vm: AppViewModel, playlist: Playlist?, playlists: List<Playlist>, onBack: () -> Unit) {
    LaunchedEffect(playlist == null) { if (playlist == null) onBack() }
    if (playlist == null) return
    var addTo by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.play(playlist.tracks, 0) }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Играть") }
            IconButton(onClick = { vm.downloadAll(playlist.tracks) }) { Icon(Icons.Filled.Download, contentDescription = "Скачать все") }
            if (playlist.id != Library.FAVORITES) {
                IconButton(onClick = { vm.deletePlaylist(playlist.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить плейлист") }
            }
        }
        if (playlist.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Плейлист пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(playlist.tracks) { i, t ->
                    TrackRow(
                        vm, t,
                        onPlay = { vm.play(playlist.tracks, i) },
                        onAdd = { addTo = t },
                        onRemove = { vm.removeFromPlaylist(playlist.id, t.id) }
                    )
                }
            }
        }
    }

    addTo?.let { t ->
        AddToPlaylistDialog(
            playlists,
            onPick = { vm.addToPlaylist(it, t); addTo = null },
            onCreate = { vm.createAndAdd(it, t); addTo = null },
            onDismiss = { addTo = null }
        )
    }
}

// ============================ Плеер ============================

@Composable
fun MiniPlayer(vm: AppViewModel, onOpen: () -> Unit) {
    val t = vm.now ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(vm, t.thumb, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    t.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (vm.showPrevInMini) {
                IconButton(onClick = { vm.prev() }) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущий") }
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Пауза / играть")
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Filled.SkipNext, contentDescription = "Следующий") }
        }
    }
}

@Composable
fun PlayerScreen(vm: AppViewModel, playlists: List<Playlist>, onClose: () -> Unit) {
    val t = vm.now
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            pos = vm.position()
            dur = vm.duration()
            delay(400)
        }
    }
    LaunchedEffect(t == null) { if (t == null) onClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            if (t != null) {
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Cover(vm, t.thumb, Modifier.fillMaxHeight(0.85f).aspectRatio(1f))
                        }
                        Column(Modifier.weight(1f)) {
                            PlayerControls(vm, t, pos, dur, drag, { drag = it }, { showSpeed = true }, { showAdd = true })
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(56.dp))
                        Cover(vm, t.thumb, Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(24.dp))
                        PlayerControls(vm, t, pos, dur, drag, { drag = it }, { showSpeed = true }, { showAdd = true })
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Свернуть")
            }
        }
    }

    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Скорость и тональность") },
            text = { SpeedPitchControls(vm) },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Готово") } }
        )
    }
    if (showAdd && t != null) {
        AddToPlaylistDialog(
            playlists,
            onPick = { vm.addToPlaylist(it, t); showAdd = false },
            onCreate = { vm.createAndAdd(it, t); showAdd = false },
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
fun PlayerControls(
    vm: AppViewModel,
    t: Track,
    pos: Long,
    dur: Long,
    drag: Float?,
    onDrag: (Float?) -> Unit,
    onSpeed: () -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            t.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            t.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))

        val max = dur.coerceAtLeast(1L).toFloat()
        Slider(
            value = (drag ?: pos.toFloat()).coerceIn(0f, max),
            onValueChange = { onDrag(it) },
            onValueChangeFinished = {
                drag?.let { vm.seekTo(it.toLong()) }
                onDrag(null)
            },
            valueRange = 0f..max
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime((drag ?: pos.toFloat()).toLong()), style = MaterialTheme.typography.bodySmall)
            Text(fmtTime(dur), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { vm.prev() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(32.dp))
            }
            FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Пауза / играть",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onSpeed) {
                Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("×%.2f  %+d".format(vm.speed, vm.semitones))
            }
            val p = vm.progress[t.id]
            when {
                p != null -> CircularProgressIndicator(progress = { p }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                t.id in vm.downloadedIds -> Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Скачано", tint = MaterialTheme.colorScheme.primary
                )
                else -> IconButton(onClick = { vm.download(t) }) {
                    Icon(Icons.Filled.Download, contentDescription = "Скачать")
                }
            }
            IconButton(onClick = onAdd) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "В плейлист") }
        }
    }
}

@Composable
fun SpeedPitchControls(vm: AppViewModel) {
    Column {
        Text("Скорость: ×%.2f".format(vm.speed))
        Slider(
            value = vm.speed,
            onValueChange = { vm.setSpeed(((it * 20).roundToInt()) / 20f) },
            valueRange = 0.5f..2f,
            steps = 29
        )
        Text("Тональность: %+d полутонов".format(vm.semitones))
        Slider(
            value = vm.semitones.toFloat(),
            onValueChange = { vm.setSemitones(it.roundToInt()) },
            valueRange = -12f..12f,
            steps = 23
        )
        TextButton(onClick = { vm.resetParams() }) { Text("Сбросить") }
    }
}
