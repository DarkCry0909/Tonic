package com.tonic.player

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

/** Хранение библиотеки в скрытой папке приложения на Google Drive (appDataFolder). */
object Drive {
    private const val FILE = "tonic_library.json"
    private val http = OkHttpClient()

    fun sync(token: String, local: Library): Library {
        val auth = "Bearer $token"
        val q = URLEncoder.encode("name='$FILE'", "UTF-8")
        val listReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id)")
            .header("Authorization", auth).build()
        val listBody = http.newCall(listReq).execute().use {
            check(it.isSuccessful) { "Drive: список файлов, код ${it.code}" }
            it.body!!.string()
        }
        val files = JSONObject(listBody).getJSONArray("files")

        var id: String? = null
        var remote = Library()
        if (files.length() > 0) {
            id = files.getJSONObject(0).getString("id")
            val getReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$id?alt=media")
                .header("Authorization", auth).build()
            val text = http.newCall(getReq).execute().use {
                check(it.isSuccessful) { "Drive: чтение, код ${it.code}" }
                it.body!!.string()
            }
            remote = try { libFromJson(text) } catch (e: Exception) { Library() }
        }

        val merged = mergeLibraries(local, remote)
        val json = libToJson(merged)

        val writeReq = if (id == null) {
            val boundary = "tonic_boundary_7d9f"
            val meta = """{"name":"$FILE","parents":["appDataFolder"]}"""
            val payload = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$boundary--"
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", auth)
                .post(payload.toByteArray().toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
        } else {
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media")
                .header("Authorization", auth)
                .patch(json.toByteArray().toRequestBody("application/json".toMediaType()))
                .build()
        }
        http.newCall(writeReq).execute().use {
            check(it.isSuccessful) { "Drive: запись, код ${it.code}" }
        }
        return merged
    }
}
