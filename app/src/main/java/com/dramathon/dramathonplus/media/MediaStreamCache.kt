package com.dramathon.dramathonplus.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaStreamCache {
    const val MAX_STREAM_CACHE_BYTES = 200L * 1024L * 1024L

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDir = File(context.cacheDir, "media_stream_cache").apply { mkdirs() }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_STREAM_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            SimpleCache(cacheDir, evictor, databaseProvider).also {
                simpleCache = it
            }
        }
    }

    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("DramaThonPlus/1.6.1 (Linux; Android)")

        val cache = getSimpleCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Synchronized
    fun releaseForTesting() {
        simpleCache?.release()
        simpleCache = null
    }
}
