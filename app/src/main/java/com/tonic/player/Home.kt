package com.tonic.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

enum class HomeTab(val title: String) {
    Home("Главное"), Explore("Исследуйте"), Music("Музыка"), Playlists("Плейлисты"), Artists("Исполнители")
}

/** Боковая панель с вертикальными вкладками (как в ViMusic). */
@Composable
fun Rail(
    titles: List<String>, selected: Int, topIcon: ImageVector,
    onTop: () -> Unit, onSelect: (Int) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.width(48.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onTop) {
            Icon(topIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            titles.forEachIndexed { i, title ->
                val on = i == selected
                Box(Modifier.clickable { onSelect(i) }.padding(vertical = 14.dp)) {
                    Text(
                        title, softWrap = false, maxLines = 1,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.onBackground else accent.copy(alpha = 0.6f),
                        modifier = Modifier.vertical().rotate(-90f)
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(end = 20.dp, top = 4.dp, bottom = 8.dp),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.headlineLarge
    )
}

@Composable
fun HomeScreen(vm: AppVm, onSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Home) }
    var open by remember { mutableStateOf<String?>(null) }
    val lib by vm.store.lib.collectAsState()
    val cur = open

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Rail(
                HomeTab.values().map { it.title }, tab.ordinal, Icons.Default.Settings, onSettings
            ) { tab = HomeTab.values()[it]; open = null }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (cur != null && cur.startsWith("yartist:")) {
                    BackHandler { open = null }
                    ArtistPageScreen(vm, cur.removePrefix("yartist:"), { open = null }) { open = "yalbum:$it" }
                } else if (cur != null && cur.startsWith("yalbum:")) {
                    BackHandler { open = null }
                    AlbumPageScreen(vm, cur.removePrefix("yalbum:")) { open = null }
                } else if (cur != null) {
                    val pl = lib.playlists.find { "pl:${it.id}" == cur }
                    val (title, tracks) = when {
                        cur == "fav" -> "Избранное" to lib.favorites
                        cur == "history" -> "История" to lib.history
                        cur == "offline" -> "Оффлайн" to Downloads.tracks.values.toList()
                        cur == "top" -> "Мой топ 50" to topTracks(lib)
                        cur.startsWith("artist:") -> {
                            val a = cur.removePrefix("artist:")
                            a to allTracks(lib).filter { it.artist == a }
                        }
                        else -> (pl?.name ?: "") to (pl?.tracks ?: emptyList())
                    }
                    BackHandler { open = null }
                    TrackListScreen(
                        vm, title, tracks, onBack = { open = null },
                        onDelete = if (pl != null) {
                            { vm.deletePlaylist(pl.id); open = null }
                        } else null,
                        onRemove = if (cur == "offline") {
                            { t: Track -> vm.removeDownload(t) }
                        } else if (cur == "fav") {
                            { t: Track -> vm.toggleFav(t) }
                        } else if (pl != null) {
                            { t: Track -> vm.removeFromPlaylist(pl.id, t.url) }
                        } else null
                    )
                } else when (tab) {
                    HomeTab.Home -> HistoryHome(vm, lib)
                    HomeTab.Explore -> ExploreScreen(vm) { open = it }
                    HomeTab.Music -> {
                        val all = allTracks(lib)
                        TrackListScreen(vm, "Музыка", all, onBack = null, onDelete = null, onRemove = null)
                    }
                    HomeTab.Playlists -> PlaylistsScreen(vm, lib) { open = it }
                    HomeTab.Artists -> ArtistsScreen(lib) { open = "artist:$it" }
                }
            }
        }

        if (tab != HomeTab.Explore && cur == null) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp)
                    .panel(RoundedCornerShape(16.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    .clickable { tab = HomeTab.Explore },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Search, null) }
        }
    }
}

@Composable
fun HistoryHome(vm: AppVm, lib: Library) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Главное")
        if (lib.history.isEmpty()) {
            Text(
                "Здесь появятся треки, которые вы слушали. Найдите музыку во вкладке «Исследуйте».",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val list = lib.history.take(30)
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Недавно слушали", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                itemsIndexed(list) { i, t -> TrackRow(t, vm, { vm.play(list, i) }) }
            }
        }
    }
}

fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс".format(n / 1_000.0)
    else -> "$n"
}

@Composable
fun ExploreScreen(vm: AppVm, onOpen: (String) -> Unit) {
    val kb = LocalSoftwareKeyboardController.current
    val err = vm.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Исследуйте")
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Поиск музыки на YouTube") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { kb?.hide(); vm.search() })
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Песни", "Исполнители", "Альбомы").forEachIndexed { i, label ->
                FilterChip(
                    selected = vm.searchMode == i,
                    onClick = { vm.searchMode = i; if (vm.query.isNotBlank()) vm.search() },
                    label = { Text(label) }
                )
            }
        }
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            err != null -> Text(err, Modifier.padding(16.dp), color = muted)
            vm.searchMode == 0 -> {
                if (vm.results.isEmpty()) {
                    Text("Введите название трека или исполнителя", Modifier.padding(16.dp), color = muted)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(vm.results) { i, t -> TrackRow(t, vm, { vm.play(vm.results, i) }) }
                    }
                }
            }
            vm.searchMode == 1 -> LazyColumn(Modifier.fillMaxSize()) {
                items(vm.artistResults) { a ->
                    ListItem(
                        headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { if (a.subs >= 0) Text("${compact(a.subs)} подписчиков") },
                        leadingContent = {
                            AsyncImage(
                                a.thumb, null,
                                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        },
                        colors = transparent,
                        modifier = Modifier.clickable { onOpen("yartist:" + a.url) }
                    )
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(vm.albumResults) { a -> AlbumRow(a) { onOpen("yalbum:" + a.url) } }
            }
        }
    }
}

@Composable
fun AlbumRow(a: AlbumItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(a.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Art(a.thumb, Modifier.size(54.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun TrackListScreen(
    vm: AppVm, title: String, tracks: List<Track>,
    onBack: (() -> Unit)?, onDelete: (() -> Unit)?, onRemove: ((Track) -> Unit)?,
    extra: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            Text(
                title, Modifier.weight(1f).padding(top = 4.dp, bottom = 8.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.play(tracks, 0) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Слушать (${tracks.size})")
            }
            FilledTonalButton(onClick = { vm.downloadAll(tracks) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(6.dp))
                Text("Скачать")
            }
        }
        extra?.invoke()
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(tracks) { i, t ->
                TrackRow(
                    t, vm, { vm.play(tracks, i) },
                    onRemove = onRemove?.let { f -> { f(t) } }
                )
            }
        }
    }
}

@Composable
fun Tile(title: String, icon: ImageVector, tint: Color, cover: String?, onClick: () -> Unit) {
    Column(Modifier.padding(8.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .panel(RoundedCornerShape(14.dp), MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PlaylistsScreen(vm: AppVm, lib: Library, onOpen: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var byName by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf(false) }
    val list = (if (byName) lib.playlists.sortedBy { it.name.lowercase() } else lib.playlists)
        .let { if (reverse) it.reversed() else it }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Плейлисты")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.panel(RoundedCornerShape(24.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    .clickable { creating = true }.padding(horizontal = 18.dp, vertical = 12.dp)
            ) { Text("Новый плейлист", fontWeight = FontWeight.Medium) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { byName = true }) {
                Icon(Icons.Default.SortByAlpha, null, tint = if (byName) MaterialTheme.colorScheme.primary else muted)
            }
            IconButton(onClick = { byName = false }) {
                Icon(Icons.Default.Schedule, null, tint = if (!byName) MaterialTheme.colorScheme.primary else muted)
            }
            IconButton(onClick = { reverse = !reverse }) {
                Icon(if (reverse) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = muted)
            }
        }
        LazyVerticalGrid(GridCells.Adaptive(120.dp), contentPadding = PaddingValues(8.dp)) {
            item { Tile("Избранное", Icons.Default.Favorite, Color(0xFFBF4040), null) { onOpen("fav") } }
            item { Tile("Оффлайн", Icons.Default.Flight, Color(0xFF4D7CE0), null) { onOpen("offline") } }
            item { Tile("Мой топ 50", Icons.Default.TrendingUp, Color(0xFFBF4040), null) { onOpen("top") } }
            item { Tile("История", Icons.Default.History, muted, null) { onOpen("history") } }
            items(list) { p ->
                Tile(p.name, Icons.Default.LibraryMusic, muted, p.tracks.firstOrNull()?.thumb) { onOpen("pl:${p.id}") }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Новый плейлист") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("Название") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) vm.createPlaylist(newName.trim())
                    newName = ""; creating = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Отмена") } }
        )
    }
}

@Composable
fun ArtistsScreen(lib: Library, onOpen: (String) -> Unit) {
    val groups = allTracks(lib).filter { it.artist.isNotBlank() }.groupBy { it.artist }
        .toList().sortedBy { it.first.lowercase() }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Исполнители")
        if (groups.isEmpty()) {
            Text(
                "Исполнители появятся, когда в библиотеке будут треки.", Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups) { (artist, tracks) ->
                ListItem(
                    headlineContent = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${tracks.size} треков") },
                    leadingContent = { Icon(Icons.Default.Person, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(artist) }
                )
            }
        }
    }
}
