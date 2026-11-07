package com.tonic.player

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Превращает ссылку на видео YouTube в прямую ссылку на аудио в момент загрузки. */
@OptIn(UnstableApi::class)
object StreamResolver : ResolvingDataSource.Resolver {
    private val cache = ConcurrentHashMap<String, Pair<Long, String>>()
    fun clear() = cache.clear()

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val host = dataSpec.uri.host ?: return dataSpec
        if (!(host.endsWith("youtube.com") || host == "youtu.be")) return dataSpec
        val key = dataSpec.uri.toString()
        val now = System.currentTimeMillis()
        val hit = cache[key]
        val stream = if (hit != null && now - hit.first < 4 * 3600_000L) {
            hit.second
        } else {
            try {
                Yt.audioUrl(key).also { cache[key] = now to it }
            } catch (e: Exception) {
                throw IOException(e)
            }
        }
        return dataSpec.withUri(Uri.parse(stream))
    }
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)
        val factory = ResolvingDataSource.Factory(http, StreamResolver)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
