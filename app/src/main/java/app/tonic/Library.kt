package app.tonic

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Локальная библиотека плейлистов (JSON-файл во внутреннем хранилище). */
object Library {
    const val FAVORITES = "favorites"

    private lateinit var file: File
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "library.json")
        var list: List<Playlist> = try {
            parse(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
        if (list.none { it.id == FAVORITES }) {
            list = listOf(Playlist(FAVORITES, "Избранное", emptyList())) + list
        }
        _playlists.value = list
        save()
    }

    @Synchronized
    private fun update(f: (List<Playlist>) -> List<Playlist>) {
        _playlists.value = f(_playlists.value)
        save()
    }

    fun create(name: String, tracks: List<Track> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it + Playlist(id, name, tracks) }
        return id
    }

    fun delete(id: String) {
        if (id == FAVORITES) return
        update { list -> list.filterNot { it.id == id } }
    }

    fun addTrack(playlistId: String, track: Track) {
        update { list ->
            list.map { p ->
                if (p.id == playlistId && p.tracks.none { it.id == track.id }) {
                    p.copy(tracks = p.tracks + track)
                } else p
            }
        }
    }

    fun removeTrack(playlistId: String, trackId: String) {
        update { list ->
            list.map { p ->
                if (p.id == playlistId) p.copy(tracks = p.tracks.filterNot { it.id == trackId }) else p
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (p in _playlists.value) {
            val tracks = JSONArray()
            for (t in p.tracks) {
                tracks.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("title", t.title)
                        .put("artist", t.artist)
                        .put("dur", t.durationSec)
                        .put("thumb", t.thumb)
                )
            }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", tracks))
        }
        try {
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun parse(text: String): List<Playlist> {
        val arr = JSONArray(text)
        val out = ArrayList<Playlist>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ta = o.getJSONArray("tracks")
            val tracks = ArrayList<Track>()
            for (j in 0 until ta.length()) {
                val t = ta.getJSONObject(j)
                tracks.add(
                    Track(
                        t.getString("id"),
                        t.optString("title"),
                        t.optString("artist"),
                        t.optLong("dur"),
                        t.optString("thumb")
                    )
                )
            }
            out.add(Playlist(o.getString("id"), o.getString("name"), tracks))
        }
        return out
    }
}
