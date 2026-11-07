package com.tonic.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class Track(
    val url: String,
    val title: String,
    val artist: String,
    val thumb: String?,
    val duration: Long
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(url)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumb?.let { Uri.parse(it) })
                .build()
        )
        .build()
}

data class Playlist(val id: String, val name: String, val tracks: List<Track>)

data class Library(
    val favorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val history: List<Track> = emptyList(),
    val plays: Map<String, Int> = emptyMap()
)

private fun trackToJson(t: Track): JSONObject = JSONObject()
    .put("url", t.url).put("title", t.title).put("artist", t.artist)
    .put("thumb", t.thumb ?: "").put("duration", t.duration)

private fun trackFromJson(o: JSONObject) = Track(
    o.getString("url"), o.optString("title"), o.optString("artist"),
    o.optString("thumb").ifEmpty { null }, o.optLong("duration")
)

fun libToJson(l: Library): String {
    val pls = JSONArray()
    l.playlists.forEach { p ->
        pls.put(
            JSONObject().put("id", p.id).put("name", p.name)
                .put("tracks", JSONArray(p.tracks.map { trackToJson(it) }))
        )
    }
    return JSONObject()
        .put("favorites", JSONArray(l.favorites.map { trackToJson(it) }))
        .put("playlists", pls)
        .put("history", JSONArray(l.history.map { trackToJson(it) }))
        .put("plays", JSONObject(l.plays as Map<*, *>))
        .toString()
}

fun libFromJson(s: String): Library {
    val o = JSONObject(s)
    fun tracks(a: JSONArray?): List<Track> =
        (0 until (a?.length() ?: 0)).map { trackFromJson(a!!.getJSONObject(it)) }
    val pa = o.optJSONArray("playlists")
    val pls = (0 until (pa?.length() ?: 0)).map { i ->
        val p = pa!!.getJSONObject(i)
        Playlist(p.getString("id"), p.optString("name"), tracks(p.optJSONArray("tracks")))
    }
    val plays = o.optJSONObject("plays")?.let { j -> j.keys().asSequence().associateWith { k -> j.getInt(k) } } ?: emptyMap()
    return Library(tracks(o.optJSONArray("favorites")), pls, tracks(o.optJSONArray("history")), plays)
}

/** Объединение локальной и облачной библиотеки (без дублей). */
fun mergeLibraries(a: Library, b: Library): Library {
    val fav = (a.favorites + b.favorites).distinctBy { it.url }
    val ids = (a.playlists.map { it.id } + b.playlists.map { it.id }).distinct()
    val pls = ids.map { id ->
        val x = a.playlists.find { it.id == id }
        val y = b.playlists.find { it.id == id }
        Playlist(
            id,
            (x ?: y)!!.name,
            ((x?.tracks ?: emptyList()) + (y?.tracks ?: emptyList())).distinctBy { it.url }
        )
    }
    val hist = (a.history + b.history).distinctBy { it.url }.take(100)
    val plays = (a.plays.keys + b.plays.keys).associateWith { k -> maxOf(a.plays[k] ?: 0, b.plays[k] ?: 0) }
    return Library(fav, pls, hist, plays)
}

fun allTracks(l: Library): List<Track> =
    (l.favorites + l.playlists.flatMap { it.tracks } + l.history).distinctBy { it.url }

fun topTracks(l: Library): List<Track> =
    allTracks(l).filter { (l.plays[it.url] ?: 0) > 0 }.sortedByDescending { l.plays[it.url] ?: 0 }.take(50)
