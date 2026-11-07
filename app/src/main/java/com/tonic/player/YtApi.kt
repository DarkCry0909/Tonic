package com.tonic.player

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/** Импорт плейлистов аккаунта через YouTube Data API v3 (только чтение). */
object YtApi {
    private val http = OkHttpClient()

    private fun get(token: String, url: String): JSONObject {
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        return http.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val msg = try {
                    JSONObject(body).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    body.take(200)
                }
                throw IllegalStateException("YouTube API ${r.code}: $msg")
            }
            JSONObject(body)
        }
    }

    private fun items(token: String, base: String, maxPages: Int): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var page: String? = null
        var n = 0
        do {
            val j = get(token, base + (page?.let { "&pageToken=$it" } ?: ""))
            val arr = j.optJSONArray("items")
            for (i in 0 until (arr?.length() ?: 0)) out.add(arr!!.getJSONObject(i))
            page = if (j.has("nextPageToken")) j.getString("nextPageToken") else null
            n++
        } while (page != null && n < maxPages)
        return out
    }

    private fun tracksOf(token: String, playlistId: String): List<Track> {
        val base = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=" +
            URLEncoder.encode(playlistId, "UTF-8")
        return items(token, base, 4).mapNotNull { it.optJSONObject("snippet") }.mapNotNull { s ->
            val vid = s.optJSONObject("resourceId")?.optString("videoId").orEmpty()
            val title = s.optString("title")
            if (vid.isEmpty() || title == "Deleted video" || title == "Private video") return@mapNotNull null
            val th = s.optJSONObject("thumbnails")
            val thumb = (th?.optJSONObject("medium") ?: th?.optJSONObject("default"))
                ?.optString("url")?.ifEmpty { null }
            Track(
                "https://www.youtube.com/watch?v=$vid", title,
                s.optString("videoOwnerChannelTitle").removeSuffix(" - Topic"), thumb, 0
            )
        }
    }

    fun importAll(token: String): Library {
        val result = mutableListOf<Playlist>()
        val lists = items(token, "https://www.googleapis.com/youtube/v3/playlists?part=snippet&mine=true&maxResults=50", 4)
        val liked = try { tracksOf(token, "LL") } catch (e: Exception) { emptyList() }
        if (liked.isNotEmpty()) result.add(Playlist("yt_LL", "Понравившиеся (YouTube)", liked))
        for (p in lists) {
            val id = p.getString("id")
            val name = p.getJSONObject("snippet").optString("title")
            val t = try { tracksOf(token, id) } catch (e: Exception) { emptyList() }
            result.add(Playlist("yt_$id", name, t))
        }
        return Library(playlists = result)
    }
}
