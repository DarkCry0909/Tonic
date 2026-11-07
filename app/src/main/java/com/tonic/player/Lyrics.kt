package com.tonic.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val synced: List<LyricLine>, val plain: String?)

/** Тексты песен из открытой базы LRCLIB (с синхронизацией по времени, если есть). */
object LyricsApi {
    private val http = OkHttpClient()
    private val lrc = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?](.*)""")

    private fun clean(s: String): String = s
        .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(" - Topic", "")
        .replace("VEVO", "", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", "Tonic/1.0").build()
        return http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

    private fun parseLrc(s: String): List<LyricLine> = s.lines().flatMap { line ->
        val m = lrc.matchEntire(line.trim()) ?: return@flatMap emptyList<LyricLine>()
        val sec = m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
        listOf(LyricLine(sec * 1000 + ms, m.groupValues[4].trim()))
    }.sortedBy { it.timeMs }

    private fun parse(o: JSONObject): Lyrics? {
        val lines = parseLrc(o.str("syncedLyrics"))
        val plain = o.str("plainLyrics").ifBlank { null }
        return if (lines.isEmpty() && plain == null) null else Lyrics(lines, plain)
    }

    suspend fun fetch(title: String, artist: String, durationSec: Long): Lyrics? = withContext(Dispatchers.IO) {
        val t = clean(title)
        val a = clean(artist)
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val dur = if (durationSec > 0) "&duration=$durationSec" else ""
        val direct = get("https://lrclib.net/api/get?track_name=${enc(t)}&artist_name=${enc(a)}$dur")
        if (direct != null) {
            val l = try { parse(JSONObject(direct)) } catch (e: Exception) { null }
            if (l != null) return@withContext l
        }
        val res = get("https://lrclib.net/api/search?q=${enc("$a $t")}") ?: return@withContext null
        val arr = JSONArray(res)
        for (i in 0 until arr.length()) {
            val l = parse(arr.getJSONObject(i))
            if (l != null) return@withContext l
        }
        null
    }
}
