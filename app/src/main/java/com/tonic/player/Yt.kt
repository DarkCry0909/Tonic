package com.tonic.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ArtistItem(val name: String, val url: String, val thumb: String?, val subs: Long)
data class AlbumItem(val name: String, val url: String, val thumb: String?, val artist: String, val count: Long)
data class ArtistPage(val name: String, val avatar: String?, val songs: List<Track>, val albums: List<AlbumItem>)

object Yt {
    @Volatile private var ready = false
    @Volatile var lowQuality = false

    private fun ensure() {
        if (!ready) synchronized(this) {
            if (!ready) {
                NewPipe.init(DownloaderImpl)
                ready = true
            }
        }
    }

    private fun StreamInfoItem.toTrack() = Track(
        url = url,
        title = name,
        artist = uploaderName ?: "",
        thumb = thumbnails.maxByOrNull { it.height }?.url,
        duration = duration
    )

    private fun searchWith(q: String, filter: String): List<Track> {
        val ex = ServiceList.YouTube.getSearchExtractor(q, listOf(filter), "")
        ex.fetchPage()
        return ex.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toTrack() }
    }

    suspend fun search(q: String): List<Track> = withContext(Dispatchers.IO) {
        ensure()
        val songs = try {
            searchWith(q, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
        } catch (e: Exception) {
            emptyList()
        }
        songs.ifEmpty { searchWith(q, YoutubeSearchQueryHandlerFactory.VIDEOS) }
    }

    /** Прямая ссылка на лучший аудиопоток (вызывается из потока загрузки плеера). */
    fun audioUrl(videoUrl: String): String {
        ensure()
        val info = StreamInfo.getInfo(videoUrl)
        val streams = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val best = (if (lowQuality) streams.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }
        else streams.maxByOrNull { it.averageBitrate })
            ?: streams.firstOrNull()
            ?: throw IllegalStateException("Аудиопоток не найден")
        return best.content
    }

    private fun InfoItem.bestThumb(): String? = thumbnails.maxByOrNull { it.height }?.url

    suspend fun searchArtists(q: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        ensure()
        val ex = ServiceList.YouTube.getSearchExtractor(q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS), "")
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<ChannelInfoItem>()
            .map { ArtistItem(it.name, it.url, it.bestThumb(), it.subscriberCount) }
    }

    suspend fun searchAlbums(q: String): List<AlbumItem> = withContext(Dispatchers.IO) {
        ensure()
        val ex = ServiceList.YouTube.getSearchExtractor(q, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "")
        ex.fetchPage()
        ex.initialPage.items.filterIsInstance<PlaylistInfoItem>()
            .map { AlbumItem(it.name, it.url, it.bestThumb(), it.uploaderName ?: "", it.streamCount) }
    }

    /** Страница исполнителя: треки и альбомы из вкладок канала. */
    suspend fun artist(url: String): ArtistPage = withContext(Dispatchers.IO) {
        ensure()
        val info = ChannelInfo.getInfo(url)
        fun tab(vararg names: String): List<InfoItem> {
            val h = info.tabs.firstOrNull { t -> names.any { n -> n in t.contentFilters } } ?: return emptyList()
            return try {
                ChannelTabInfo.getInfo(ServiceList.YouTube, h).relatedItems
            } catch (e: Exception) {
                emptyList()
            }
        }
        val songs = tab("tracks", "videos").filterIsInstance<StreamInfoItem>().map { it.toTrack() }
        val albums = tab("albums", "playlists").filterIsInstance<PlaylistInfoItem>()
            .map { AlbumItem(it.name, it.url, it.bestThumb(), it.uploaderName ?: info.name, it.streamCount) }
        ArtistPage(info.name, info.avatars.maxByOrNull { it.height }?.url, songs, albums)
    }

    /** Публичный плейлист YouTube / YouTube Music по ссылке (аккаунт не нужен). */
    suspend fun playlist(url: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        ensure()
        val info = PlaylistInfo.getInfo(url)
        val out = info.relatedItems.map { it.toTrack() }.toMutableList()
        var page = info.nextPage
        var n = 0
        while (page != null && n < 5) {
            val p = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            out += p.items.map { it.toTrack() }
            page = p.nextPage
            n++
        }
        info.name to out.toList()
    }
}
