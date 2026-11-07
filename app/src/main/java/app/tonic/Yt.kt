package app.tonic

import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** HTTP-клиент для NewPipeExtractor. */
class NpDownloader(private val client: okhttp3.OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()
        val body = data?.toRequestBody() ?: if (method == "POST") ByteArray(0).toRequestBody() else null

        val builder = okhttp3.Request.Builder()
            .method(method, body)
            .url(url)
            .header("User-Agent", Yt.UA)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (v in values) builder.addHeader(name, v)
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("YouTube просит капчу (429)", url)
            val text = response.body?.string()
            return NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                text,
                response.request.url.toString()
            )
        }
    }
}

class ImportedPlaylist(val name: String, val tracks: List<Track>)

object Yt {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val idRegex = Regex("""[?&]v=([\w-]{11})""")
    private val listRegex = Regex("""[?&]list=([\w-]+)""")
    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    private fun videoId(url: String): String? = idRegex.find(url)?.groupValues?.get(1)

    private fun StreamInfoItem.toTrack(): Track? {
        val id = videoId(url) ?: return null
        val thumb = thumbnails.maxByOrNull { it.height }?.url ?: ""
        return Track(
            id = id,
            title = name ?: "",
            artist = (uploaderName ?: "").removeSuffix(" - Topic"),
            durationSec = duration,
            thumb = thumb
        )
    }

    /** Поиск песен в YouTube Music. */
    fun search(query: String): List<Track> {
        val yt = ServiceList.YouTube
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        val info = SearchInfo.getInfo(yt, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }
    }

    /** Импорт публичного плейлиста по ссылке (YouTube или YouTube Music). */
    fun importPlaylist(input: String, limit: Int = 1000): ImportedPlaylist {
        val listId = listRegex.find(input)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("В ссылке не найден ID плейлиста")
        val url = "https://www.youtube.com/playlist?list=$listId"
        val yt = ServiceList.YouTube

        val info = PlaylistInfo.getInfo(yt, url)
        val tracks = ArrayList<Track>()
        for (item in info.relatedItems) item.toTrack()?.let { tracks.add(it) }

        var page = info.nextPage
        while (page != null && tracks.size < limit) {
            val more = PlaylistInfo.getMoreItems(yt, url, page)
            for (item in more.items) item.toTrack()?.let { tracks.add(it) }
            page = more.nextPage
        }
        return ImportedPlaylist(info.name ?: "Плейлист YouTube", tracks)
    }

    /** Прямая ссылка на лучший аудиопоток. Ссылки живут несколько часов — кэшируем на 20 минут. */
    fun streamUrl(id: String): String {
        val now = System.currentTimeMillis()
        urlCache[id]?.let { if (it.second > now) return it.first }

        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val best = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?: throw IOException("Для этого трека нет доступного аудиопотока")

        urlCache[id] = best.content to (now + 20 * 60 * 1000L)
        return best.content
    }
}
