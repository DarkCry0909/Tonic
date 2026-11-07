package app.tonic

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.pow

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("tonic", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())

    private fun toast(msg: String) {
        ui.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    // ---------- Настройки ----------
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0)); private set      // 0 система, 1 тёмная, 2 светлая
    var rounding by mutableIntStateOf(prefs.getInt("round", 1)); private set       // 0 малая, 1 средняя, 2 большая
    var showPrevInMini by mutableStateOf(prefs.getBoolean("prevMini", true)); private set

    val corner: Dp get() = when (rounding) { 0 -> 4.dp; 2 -> 28.dp; else -> 12.dp }

    fun setTheme(v: Int) { themeMode = v; prefs.edit().putInt("theme", v).apply() }
    fun setRounding(v: Int) { rounding = v; prefs.edit().putInt("round", v).apply() }
    fun setShowPrev(v: Boolean) { showPrevInMini = v; prefs.edit().putBoolean("prevMini", v).apply() }

    // ---------- Поиск / импорт ----------
    var results by mutableStateOf<List<Track>>(emptyList()); private set
    var busy by mutableStateOf(false); private set

    /** Если в строке ссылка с list= — импортируем плейлист, иначе ищем песни. */
    fun submit(input: String) {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (q.contains("list=")) {
                    val p = Yt.importPlaylist(q)
                    if (p.tracks.isEmpty()) {
                        toast("Плейлист пуст или недоступен (закрытые плейлисты не импортируются)")
                    } else {
                        Library.create(p.name, p.tracks)
                        toast("Импортировано: ${p.name} (${p.tracks.size})")
                    }
                } else {
                    results = Yt.search(q)
                    if (results.isEmpty()) toast("Ничего не найдено")
                }
            } catch (e: Exception) {
                toast("Ошибка: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    // ---------- Библиотека ----------
    val playlists = Library.playlists

    fun addToPlaylist(playlistId: String, t: Track) {
        Library.addTrack(playlistId, t)
        toast("Добавлено в плейлист")
    }

    fun createAndAdd(name: String, t: Track) {
        val id = Library.create(name)
        Library.addTrack(id, t)
        toast("Создан плейлист «$name»")
    }

    fun createPlaylist(name: String) { Library.create(name) }
    fun deletePlaylist(id: String) { Library.delete(id) }
    fun removeFromPlaylist(pid: String, tid: String) { Library.removeTrack(pid, tid) }

    // ---------- Загрузки ----------
    var downloadedIds by mutableStateOf(Downloads.ids(ctx)); private set
    val progress = mutableStateMapOf<String, Float>()
    private val limiter = Semaphore(2)

    fun download(t: Track) {
        if (t.id in downloadedIds || progress.containsKey(t.id)) return
        progress[t.id] = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                limiter.withPermit {
                    Downloads.download(ctx, t.id) { progress[t.id] = it }
                }
                toast("Скачано: ${t.title}")
            } catch (e: Exception) {
                toast("Не удалось скачать: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                progress.remove(t.id)
                downloadedIds = Downloads.ids(ctx)
            }
        }
    }

    fun downloadAll(list: List<Track>) { list.forEach { download(it) } }

    fun downloadsSize(): Long = Downloads.sizeBytes(ctx)

    fun deleteDownloads() {
        Downloads.clear(ctx)
        downloadedIds = emptySet()
    }

    // ---------- Плеер ----------
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    var isPlaying by mutableStateOf(false); private set
    var now by mutableStateOf<Track?>(null); private set
    var hasNext by mutableStateOf(false); private set
    var hasPrev by mutableStateOf(false); private set

    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f)); private set
    var semitones by mutableIntStateOf(prefs.getInt("semi", 0)); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync(player) }
        override fun onPlayerError(error: PlaybackException) {
            toast("Ошибка воспроизведения: ${error.cause?.message ?: error.errorCodeName}")
        }
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                c.setPlaybackParameters(PlaybackParameters(speed, pitchRatio()))
                sync(c)
            } catch (e: Exception) {
                toast("Не удалось подключить плеер")
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    private fun sync(p: Player) {
        isPlaying = p.playWhenReady &&
            p.playbackState != Player.STATE_ENDED &&
            p.playbackState != Player.STATE_IDLE
        now = p.currentMediaItem?.toTrack()
        hasNext = p.hasNextMediaItem()
        hasPrev = p.hasPreviousMediaItem()
    }

    private fun MediaItem.toTrack() = Track(
        id = mediaId,
        title = mediaMetadata.title?.toString() ?: "",
        artist = mediaMetadata.artist?.toString() ?: "",
        durationSec = mediaMetadata.extras?.getLong("dur") ?: 0L,
        thumb = mediaMetadata.artworkUri?.toString() ?: ""
    )

    private fun Track.toMediaItem(): MediaItem {
        val extras = Bundle().apply { putLong("dur", durationSec) }
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setExtras(extras)
        if (thumb.isNotEmpty()) meta.setArtworkUri(Uri.parse(thumb))
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri("tonic://track/$id")
            .setMediaMetadata(meta.build())
            .build()
    }

    fun play(list: List<Track>, index: Int) {
        val c = controller ?: return toast("Плеер ещё запускается, попробуйте снова")
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun enqueue(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(t), 0) else {
            c.addMediaItem(t.toMediaItem())
            toast("Добавлено в очередь")
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun position(): Long = controller?.currentPosition ?: 0L
    fun duration(): Long {
        val d = controller?.duration ?: 0L
        return if (d < 0) 0L else d
    }

    // ---------- Скорость и тональность ----------
    private fun pitchRatio(): Float = 2.0.pow(semitones / 12.0).toFloat()

    private fun applyParams() {
        controller?.setPlaybackParameters(PlaybackParameters(speed, pitchRatio()))
    }

    fun setSpeed(v: Float) {
        speed = v
        prefs.edit().putFloat("speed", v).apply()
        applyParams()
    }

    fun setSemitones(v: Int) {
        semitones = v
        prefs.edit().putInt("semi", v).apply()
        applyParams()
    }

    fun resetParams() {
        setSpeed(1f)
        setSemitones(0)
    }
}
