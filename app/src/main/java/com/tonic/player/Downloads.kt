package com.tonic.player

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class DlInfo(val state: Int, val percent: Float)

/** Офлайн-загрузки: треки хранятся в отдельном кэше без автоматического удаления. */
@OptIn(UnstableApi::class)
object Downloads {
    const val CHANNEL = "tonic_downloads"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    /** url трека -> состояние загрузки */
    val states = mutableStateMapOf<String, DlInfo>()

    /** Полностью скачанные треки */
    val tracks = mutableStateMapOf<String, Track>()

    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null
    private var helper: DownloadNotificationHelper? = null

    @Synchronized
    fun cache(ctx: Context): SimpleCache {
        cache?.let { return it }
        val app = ctx.applicationContext
        val c = SimpleCache(File(app.filesDir, "downloads"), NoOpCacheEvictor(), ExoDb.get(app))
        cache = c
        return c
    }

    /** Источник данных для плеера: сначала загрузки (только чтение), потом сеть/кэш. */
    fun playbackFactory(ctx: Context, upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Synchronized
    fun manager(ctx: Context): DownloadManager {
        manager?.let { return it }
        val app = ctx.applicationContext
        val http = DefaultHttpDataSource.Factory().setUserAgent(UA).setAllowCrossProtocolRedirects(true)
        val upstream = ResolvingDataSource.Factory(http, StreamResolver)
        val dsFactory = CacheDataSource.Factory().setCache(cache(app)).setUpstreamDataSourceFactory(upstream)
        val m = DownloadManager(app, ExoDb.get(app), cache(app), dsFactory, Executors.newFixedThreadPool(2))
        m.maxParallelDownloads = 2
        m.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                apply(download)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                states.remove(download.request.id)
                tracks.remove(download.request.id)
            }
        })
        val cursor = m.downloadIndex.getDownloads()
        while (cursor.moveToNext()) apply(cursor.download)
        cursor.close()
        manager = m
        return m
    }

    @Synchronized
    fun helper(ctx: Context): DownloadNotificationHelper =
        helper ?: DownloadNotificationHelper(ctx.applicationContext, CHANNEL).also { helper = it }

    private fun apply(d: Download) {
        val id = d.request.id
        states[id] = DlInfo(d.state, d.percentDownloaded.coerceAtLeast(0f))
        if (d.state == Download.STATE_COMPLETED) {
            try {
                tracks[id] = trackFromJson(JSONObject(String(d.request.data, Charsets.UTF_8)))
            } catch (e: Exception) {
                // метаданных нет — трек играет, но в списке «Оффлайн» не покажется
            }
        } else {
            tracks.remove(id)
        }
    }

    fun isDone(url: String) = states[url]?.state == Download.STATE_COMPLETED
    fun isActive(url: String): Boolean {
        val s = states[url]?.state ?: return false
        return s == Download.STATE_QUEUED || s == Download.STATE_DOWNLOADING || s == Download.STATE_RESTARTING
    }

    fun add(ctx: Context, t: Track) {
        if (isDone(t.url) || isActive(t.url)) return
        manager(ctx)
        val req = DownloadRequest.Builder(t.url, Uri.parse(t.url))
            .setCustomCacheKey(t.url)
            .setData(trackToJson(t).toString().toByteArray(Charsets.UTF_8))
            .build()
        DownloadService.sendAddDownload(ctx, TonicDownloadService::class.java, req, false)
    }

    fun remove(ctx: Context, url: String) {
        manager(ctx)
        DownloadService.sendRemoveDownload(ctx, TonicDownloadService::class.java, url, false)
    }

    fun removeAll(ctx: Context) {
        manager(ctx)
        DownloadService.sendRemoveAllDownloads(ctx, TonicDownloadService::class.java, false)
    }

    fun usedMb(ctx: Context): Long = cache(ctx).cacheSpace / (1024 * 1024)
}

@OptIn(UnstableApi::class)
class TonicDownloadService : DownloadService(
    1001, 1000L, Downloads.CHANNEL, R.string.download_channel, 0
) {
    override fun getDownloadManager(): DownloadManager = Downloads.manager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        Downloads.helper(this).buildProgressNotification(
            this, android.R.drawable.stat_sys_download, null, null, downloads, notMetRequirements
        )
}
