package app.tonic

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.IOException

/**
 * Фоновое воспроизведение. Треки приходят как tonic://track/<videoId>;
 * настоящая ссылка (или скачанный файл) подставляется в момент загрузки.
 * Скорость и тональность задаются через PlaybackParameters самого ExoPlayer.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Yt.UA)
            .setAllowCrossProtocolRedirects(true)
        val base = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(base) { spec -> resolve(spec) }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        session = MediaSession.Builder(this, player).build()
    }

    private fun resolve(spec: DataSpec): DataSpec {
        val uri = spec.uri
        if (uri.scheme != "tonic") return spec
        val id = uri.lastPathSegment ?: throw IOException("Некорректный трек")
        val local = Downloads.file(applicationContext, id)
        if (local.exists()) return spec.withUri(Uri.fromFile(local))
        try {
            return spec.withUri(Uri.parse(Yt.streamUrl(id)))
        } catch (e: Exception) {
            throw IOException(e.message ?: "Не удалось получить поток", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
