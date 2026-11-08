package com.tonic.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Одна база на все кэши и загрузки. */
@OptIn(UnstableApi::class)
object ExoDb {
    private var db: StandaloneDatabaseProvider? = null

    @Synchronized
    fun get(ctx: Context): StandaloneDatabaseProvider =
        db ?: StandaloneDatabaseProvider(ctx.applicationContext).also { db = it }
}

/** Дисковый кэш аудио: прослушанные треки играют повторно без интернета. */
@OptIn(UnstableApi::class)
object AudioCache {
    private var cache: SimpleCache? = null

    fun limitMb(ctx: Context): Int = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("cacheMb", 512)

    @Synchronized
    fun get(ctx: Context): SimpleCache {
        cache?.let { return it }
        val app = ctx.applicationContext
        val c = SimpleCache(
            File(app.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(maxOf(limitMb(app), 16) * 1024L * 1024L),
            ExoDb.get(app)
        )
        cache = c
        return c
    }

    fun usedMb(ctx: Context): Long = get(ctx).cacheSpace / (1024 * 1024)

    @Synchronized
    fun clear(ctx: Context) {
        val c = get(ctx)
        c.keys.toList().forEach { c.removeResource(it) }
    }
}
