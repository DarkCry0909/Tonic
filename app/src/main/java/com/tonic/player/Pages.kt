package com.tonic.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ArtistPageScreen(vm: AppVm, url: String, onBack: () -> Unit, onAlbum: (String) -> Unit) {
    var page by remember(url) { mutableStateOf<ArtistPage?>(null) }
    var err by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        try {
            page = Yt.artist(url)
        } catch (e: Exception) {
            err = e.message ?: "неизвестная ошибка"
        }
    }
    val p = page
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(
                p?.name ?: "", Modifier.weight(1f).padding(top = 4.dp, bottom = 8.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        when {
            err != null -> Text(
                "Не удалось загрузить: $err", Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            p == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            p.avatar, null,
                            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { vm.play(p.songs, 0) }, enabled = p.songs.isNotEmpty()) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Слушать")
                        }
                    }
                }
                if (p.songs.isNotEmpty()) {
                    item { Text("Треки", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
                }
                itemsIndexed(p.songs) { i, t -> TrackRow(t, vm, { vm.play(p.songs, i) }) }
                if (p.albums.isNotEmpty()) {
                    item { Text("Альбомы", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
                }
                items(p.albums) { a -> AlbumRow(a) { onAlbum(a.url) } }
                if (p.songs.isEmpty() && p.albums.isEmpty()) {
                    item {
                        Text(
                            "На странице исполнителя нет доступных треков.", Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumPageScreen(vm: AppVm, url: String, onBack: () -> Unit) {
    var data by remember(url) { mutableStateOf<Pair<String, List<Track>>?>(null) }
    var err by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        try {
            data = Yt.playlist(url)
        } catch (e: Exception) {
            err = e.message ?: "неизвестная ошибка"
        }
    }
    val d = data
    if (d == null) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            if (err != null) {
                Text(
                    "Не удалось загрузить: $err", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    } else {
        TrackListScreen(
            vm, d.first, d.second, onBack = onBack, onDelete = null, onRemove = null,
            extra = {
                TextButton(
                    onClick = { vm.savePlaylist(d.first, d.second) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("Сохранить в плейлисты") }
            }
        )
    }
}
