package com.tonic.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.io.File

/** Загрузки для офлайна: отдельный кэш, который никогда не очищается сам. */
@OptIn(UnstableApi::class)
object Offline {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private var cache: SimpleCache? = null
    private val state = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = state
    val progress = mutableStateMapOf<String, Float>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private var loaded = false

    @Synchronized
    fun cache(ctx: Context): SimpleCache {
        cache?.let { return it }
        val app = ctx.applicationContext
        val c = SimpleCache(File(app.filesDir, "offline"), NoOpCacheEvictor(), StandaloneDatabaseProvider(app))
        cache = c
        return c
    }

    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = File(ctx.filesDir, "offline.json")
        state.value = try {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                (0 until a.length()).map { trackFromJson(a.getJSONObject(it)) }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context) {
        File(ctx.filesDir, "offline.json").writeText(JSONArray(state.value.map { trackToJson(it) }).toString())
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    fun isDownloaded(url: String): Boolean = state.value.any { it.url == url }

    fun download(ctx: Context, t: Track) {
        if (isDownloaded(t.url) || progress.containsKey(t.url)) return
        progress[t.url] = 0f
        scope.launch {
            gate.withPermit {
                try {
                    val stream = Yt.audioUrl(t.url)
                    val http = DefaultHttpDataSource.Factory().setUserAgent(UA).setAllowCrossProtocolRedirects(true)
                    val ds = CacheDataSource.Factory()
                        .setCache(cache(ctx))
                        .setUpstreamDataSourceFactory(http)
                        .createDataSource()
                    val spec = DataSpec.Builder().setUri(stream).setKey(t.url).build()
                    CacheWriter(ds, spec, null) { length, done, _ ->
                        if (length > 0) progress[t.url] = done.toFloat() / length
                    }.cache()
                    state.value = state.value.filter { it.url != t.url } + t
                    save(ctx)
                } catch (e: Exception) {
                    toast(ctx, "Не удалось скачать «${t.title}»: ${e.message}")
                } finally {
                    progress.remove(t.url)
                }
            }
        }
    }

    fun remove(ctx: Context, t: Track) {
        try { cache(ctx).removeResource(t.url) } catch (e: Exception) { }
        state.value = state.value.filter { it.url != t.url }
        save(ctx)
    }

    fun removeAll(ctx: Context) {
        val c = cache(ctx)
        c.keys.toList().forEach { try { c.removeResource(it) } catch (e: Exception) { } }
        state.value = emptyList()
        save(ctx)
    }

    fun sizeMb(ctx: Context): Long = cache(ctx).cacheSpace / (1024 * 1024)
}
