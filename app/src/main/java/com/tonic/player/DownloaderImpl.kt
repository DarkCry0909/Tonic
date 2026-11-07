package com.tonic.player

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** Сетевой слой для NewPipeExtractor. */
object DownloaderImpl : Downloader() {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val b = okhttp3.Request.Builder().url(request.url()).method(method, body).header("User-Agent", UA)
        request.headers().forEach { (name, values) ->
            b.removeHeader(name)
            values.forEach { v -> b.addHeader(name, v) }
        }
        return client.newCall(b.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            Response(
                resp.code, resp.message, resp.headers.toMultimap(),
                resp.body?.string(), resp.request.url.toString()
            )
        }
    }
}
