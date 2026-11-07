package app.tonic

import android.content.Context
import android.os.Environment
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Скачивание аудио для офлайн-прослушивания. Файлы лежат в Android/data/app.tonic/files/Music/Tonic. */
object Downloads {
    private val client = OkHttpClient()
    private const val CHUNK = 1024L * 1024L

    fun dir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
        return File(base, "Tonic").also { it.mkdirs() }
    }

    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.audio")

    fun ids(ctx: Context): Set<String> =
        dir(ctx).listFiles()
            ?.filter { it.name.endsWith(".audio") }
            ?.map { it.name.removeSuffix(".audio") }
            ?.toSet() ?: emptySet()

    fun sizeBytes(ctx: Context): Long {
        var sum = 0L
        dir(ctx).listFiles()?.forEach { sum += it.length() }
        return sum
    }

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Качает кусками по 1 МБ (YouTube режет скорость на длинных запросах). */
    fun download(ctx: Context, id: String, onProgress: (Float) -> Unit) {
        val target = file(ctx, id)
        if (target.exists()) return
        val part = File(target.path + ".part")
        try {
            val url = Yt.streamUrl(id)
            var start = 0L
            var total = -1L
            part.outputStream().buffered().use { out ->
                while (total < 0 || start < total) {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", Yt.UA)
                        .header("Range", "bytes=$start-${start + CHUNK - 1}")
                        .build()
                    var got = 0L
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        val body = resp.body ?: throw IOException("Пустой ответ")
                        if (total < 0) {
                            total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                                ?: body.contentLength()
                        }
                        val buf = ByteArray(32 * 1024)
                        body.byteStream().use { input ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                got += n
                            }
                        }
                        if (resp.code == 200) total = got // сервер проигнорировал Range и отдал файл целиком
                    }
                    if (got == 0L) throw IOException("Пустой ответ сервера")
                    start += got
                    if (total > 0) onProgress((start.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить файл")
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }
}
