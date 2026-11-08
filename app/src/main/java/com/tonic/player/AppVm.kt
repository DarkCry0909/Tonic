package com.tonic.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.pow

class AppVm(app: Application) : AndroidViewModel(app) {
    val store = LibraryStore(app)
    val ui = UiPrefs(app)
    private val prefs = app.getSharedPreferences("tonic", Context.MODE_PRIVATE)

    // Поиск
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
    var searchMode by mutableIntStateOf(0)   // 0 песни, 1 исполнители, 2 альбомы
    var artistResults by mutableStateOf<List<ArtistItem>>(emptyList())
    var albumResults by mutableStateOf<List<AlbumItem>>(emptyList())
    var lyrics by mutableStateOf<Lyrics?>(null)
    var lyricsFor by mutableStateOf<String?>(null)
    var lyricsState by mutableIntStateOf(0)  // 1 загрузка, 2 найден, 3 нет
    var error by mutableStateOf<String?>(null)

    // Плеер
    var controller by mutableStateOf<MediaController?>(null)
    var current by mutableStateOf<MediaItem?>(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f))
    var semitones by mutableIntStateOf(prefs.getInt("semis", 0))
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
    var positionMs by mutableLongStateOf(0L)
    var repeatOne by mutableStateOf(false)
    var repeatAll by mutableStateOf(false)
    private var lastRecorded: String? = null
    private var pending = "drive"

    // Аккаунт
    var syncStatus by mutableStateOf("")
    var syncOn by mutableStateOf(prefs.getBoolean("sync", false))
    var account by mutableStateOf(prefs.getString("email", null))

    val currentTrack: Track?
        get() = current?.let { mi ->
            queue.find { it.url == mi.mediaId } ?: Track(
                mi.mediaId,
                mi.mediaMetadata.title?.toString() ?: "",
                mi.mediaMetadata.artist?.toString() ?: "",
                mi.mediaMetadata.artworkUri?.toString(),
                0
            )
        }

    init {
        Yt.lowQuality = ui.lowQuality.value
        try {
            Downloads.manager(app)
            if (Downloads.states.values.any { it.state != androidx.media3.exoplayer.offline.Download.STATE_COMPLETED }) {
                androidx.media3.exoplayer.offline.DownloadService.start(app, TonicDownloadService::class.java)
            }
        } catch (e: Exception) {
            // загрузки возобновятся при следующем запуске
        }
        Offline.init(app)
        val app2 = getApplication<Application>()
        val token = SessionToken(app2, ComponentName(app2, PlaybackService::class.java))
        val future = MediaController.Builder(app2, token).buildAsync()
        future.addListener({
            val c = future.get()
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh(player)
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        app2,
                        "Не удалось воспроизвести: ${error.cause?.message ?: error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
            controller = c
            applyTempo()
            refresh(c)
        }, ContextCompat.getMainExecutor(app2))

        viewModelScope.launch {
            while (true) {
                controller?.let { positionMs = it.currentPosition }
                delay(400)
            }
        }

        if (syncOn) startSync { syncStatus = "Нужен вход — откройте вкладку «Аккаунт»" }
    }

    private fun refresh(p: Player) {
        isPlaying = p.isPlaying
        current = p.currentMediaItem
        buffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.coerceAtLeast(0L)
        index = p.currentMediaItemIndex
        val id = current?.mediaId
        if (id != null && id != lastRecorded) {
            lastRecorded = id
            currentTrack?.let { t ->
                store.update { l ->
                    l.copy(
                        history = (listOf(t) + l.history.filter { it.url != t.url }).take(100),
                        plays = l.plays + (t.url to ((l.plays[t.url] ?: 0) + 1))
                    )
                }
            }
        }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }

    // ---------- Поиск ----------
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                when (searchMode) {
                    0 -> { results = Yt.search(q); if (results.isEmpty()) error = "Ничего не найдено" }
                    1 -> { artistResults = Yt.searchArtists(q); if (artistResults.isEmpty()) error = "Исполнители не найдены" }
                    else -> { albumResults = Yt.searchAlbums(q); if (albumResults.isEmpty()) error = "Альбомы не найдены" }
                }
            } catch (e: Exception) {
                error = "Ошибка поиска: ${e.message}"
            }
            loading = false
        }
    }

    // ---------- Воспроизведение ----------
    fun play(list: List<Track>, index: Int) {
        val c = controller ?: return
        queue = list
        lastRecorded = null
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(t), 0); return }
        val at = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(at, t.toMediaItem())
        queue = queue.toMutableList().also { it.add(at.coerceAtMost(it.size), t) }
        toast("Будет играть следующим")
    }

    fun addToQueue(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(t), 0); return }
        c.addMediaItem(t.toMediaItem())
        queue = queue + t
        toast("Добавлено в очередь")
    }

    fun removeFromQueue(i: Int) {
        val c = controller ?: return
        if (i !in 0 until c.mediaItemCount) return
        c.removeMediaItem(i)
        queue = queue.filterIndexed { idx, _ -> idx != i }
    }

    fun moveQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
        queue = queue.toMutableList().also { val x = it.removeAt(from); it.add(to, x) }
    }

    fun loadLyrics(t: Track) {
        if (lyricsFor == t.url) return
        lyricsFor = t.url
        lyrics = null
        lyricsState = 1
        viewModelScope.launch {
            val l = try {
                LyricsApi.fetch(t.title, t.artist, if (t.duration > 0) t.duration else durationMs / 1000)
            } catch (e: Exception) {
                null
            }
            if (lyricsFor == t.url) {
                lyrics = l
                lyricsState = if (l != null) 2 else 3
            }
        }
    }

    fun savePlaylist(name: String, tracks: List<Track>) {
        val pl = Playlist("alb_" + name.hashCode(), name, tracks)
        store.update { l -> mergeLibraries(l, Library(playlists = listOf(pl))) }
        toast("Сохранено в плейлисты")
    }

    fun download(t: Track) {
        Downloads.add(getApplication<Application>(), t)
        toast("Загрузка начата")
    }

    fun downloadAll(list: List<Track>) {
        val todo = list.filter { !Downloads.isDone(it.url) && !Downloads.isActive(it.url) }
        todo.forEach { Downloads.add(getApplication<Application>(), it) }
        toast(if (todo.isEmpty()) "Всё уже скачано" else "Загрузка начата: ${todo.size}")
    }

    fun removeDownload(t: Track) {
        Downloads.remove(getApplication<Application>(), t.url)
        toast("Загрузка удалена")
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun jumpTo(i: Int) {
        controller?.seekTo(i, 0L)
        controller?.play()
    }

    private fun applyRepeat() {
        controller?.repeatMode = when {
            repeatOne -> Player.REPEAT_MODE_ONE
            repeatAll -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleRepeat() {
        repeatOne = !repeatOne
        if (repeatOne) repeatAll = false
        applyRepeat()
    }

    fun toggleQueueLoop() {
        repeatAll = !repeatAll
        if (repeatAll) repeatOne = false
        applyRepeat()
    }

    // ---------- Загрузки (офлайн) ----------
    fun download(t: Track) {
        Offline.download(getApplication<Application>(), t)
        toast("Загрузка: «${t.title}»")
    }

    fun downloadAll(list: List<Track>) {
        val todo = list.filter { !Offline.isDownloaded(it.url) }
        todo.forEach { Offline.download(getApplication<Application>(), it) }
        toast(if (todo.isEmpty()) "Всё уже скачано" else "Скачивается треков: ${todo.size}")
    }

    fun removeDownload(t: Track) {
        Offline.remove(getApplication<Application>(), t)
        toast("Загрузка удалена")
    }

    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }

    /** speed — скорость, semis — сдвиг тональности в полутонах (-6…+6). */
    fun setTempo(speed: Float, semis: Int) {
        this.speed = speed
        this.semitones = semis
        prefs.edit().putFloat("speed", speed).putInt("semis", semis).apply()
        applyTempo()
    }

    private fun applyTempo() {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        controller?.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    // ---------- Библиотека ----------
    fun toggleFav(t: Track) = store.update { l ->
        if (l.favorites.any { it.url == t.url }) l.copy(favorites = l.favorites.filter { it.url != t.url })
        else l.copy(favorites = l.favorites + t)
    }

    fun createPlaylist(name: String, first: Track? = null) = store.update { l ->
        l.copy(playlists = l.playlists + Playlist(UUID.randomUUID().toString(), name, listOfNotNull(first)))
    }

    fun addToPlaylist(id: String, t: Track) = store.update { l ->
        l.copy(playlists = l.playlists.map { p ->
            if (p.id == id && p.tracks.none { it.url == t.url }) p.copy(tracks = p.tracks + t) else p
        })
    }

    fun removeFromPlaylist(id: String, url: String) = store.update { l ->
        l.copy(playlists = l.playlists.map { p ->
            if (p.id == id) p.copy(tracks = p.tracks.filter { it.url != url }) else p
        })
    }

    fun deletePlaylist(id: String) = store.update { l ->
        l.copy(playlists = l.playlists.filter { it.id != id })
    }

    // ---------- Google ----------
    fun startSync(launch: (IntentSender) -> Unit) = startAuth("drive", launch)
    fun importYoutube(launch: (IntentSender) -> Unit) = startAuth("youtube", launch)

    private fun startAuth(purpose: String, launch: (IntentSender) -> Unit) {
        pending = purpose
        syncStatus = "Подключение…"
        val scope = if (purpose == "youtube") "https://www.googleapis.com/auth/youtube.readonly"
        else "https://www.googleapis.com/auth/drive.appdata"
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope))).build()
        Identity.getAuthorizationClient(getApplication<Application>()).authorize(req)
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launch(pi.intentSender)
                else onAuth(r.accessToken, r.toGoogleSignInAccount()?.email)
            }
            .addOnFailureListener { syncStatus = "Ошибка входа: ${it.message}" }
    }

    fun onAuth(token: String?, email: String?) {
        if (token == null) {
            syncStatus = "Нет доступа к аккаунту Google"
            return
        }
        if (pending == "youtube") {
            syncStatus = "Импорт плейлистов…"
            viewModelScope.launch {
                try {
                    val imp = withContext(Dispatchers.IO) { YtApi.importAll(token) }
                    store.update { mergeLibraries(it, imp) }
                    syncStatus = "Импортировано плейлистов: ${imp.playlists.size}"
                } catch (e: Exception) {
                    syncStatus = "Ошибка импорта: ${e.message}"
                }
            }
            return
        }
        prefs.edit().putBoolean("sync", true).putString("email", email ?: "Google").apply()
        syncOn = true
        account = email ?: "Google"
        syncStatus = "Синхронизация…"
        viewModelScope.launch {
            try {
                val merged = withContext(Dispatchers.IO) { Drive.sync(token, store.lib.value) }
                store.update { merged }
                syncStatus = "Синхронизировано"
            } catch (e: Exception) {
                syncStatus = "Ошибка синхронизации: ${e.message}"
            }
        }
    }

    fun disconnect() {
        prefs.edit().putBoolean("sync", false).remove("email").apply()
        syncOn = false
        account = null
        syncStatus = "Аккаунт отключён"
    }

    // ---------- Импорт по ссылке, файлы, очистка ----------
    private fun toast(t: String) = Toast.makeText(getApplication<Application>(), t, Toast.LENGTH_SHORT).show()

    fun importLink(url: String) {
        syncStatus = "Загрузка плейлиста…"
        viewModelScope.launch {
            try {
                val (name, tracks) = Yt.playlist(url.trim())
                if (tracks.isEmpty()) {
                    syncStatus = "Плейлист пуст или закрыт. Закрытые плейлисты доступны только через вход в аккаунт."
                } else {
                    val pl = Playlist("link_" + url.trim().hashCode(), name, tracks)
                    store.update { l -> mergeLibraries(l, Library(playlists = listOf(pl))) }
                    syncStatus = "Импортировано «$name»: ${tracks.size} треков"
                }
            } catch (e: Exception) {
                syncStatus = "Ошибка: ${e.message}"
            }
        }
    }

    fun exportLibrary(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(libToJson(store.lib.value).toByteArray())
            }
            toast("Библиотека сохранена")
        } catch (e: Exception) {
            toast("Ошибка: ${e.message}")
        }
    }

    fun importLibrary(uri: Uri) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return
            val imp = libFromJson(text)
            store.update { mergeLibraries(it, imp) }
            toast("Библиотека загружена")
        } catch (e: Exception) {
            toast("Не удалось прочитать файл: ${e.message}")
        }
    }

    fun clearHistory() {
        store.update { it.copy(history = emptyList(), plays = emptyMap()) }
        toast("История очищена")
    }

    fun say(text: String) = toast(text)
}
