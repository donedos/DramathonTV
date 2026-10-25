package com.dramathon.dramathonplus.image

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File

object AppImageLoader {
    const val DISK_CACHE_BYTES = 50L * 1024L * 1024L
    private const val MEMORY_CACHE_PERCENT = 0.20
    private const val CROSSFADE_MILLIS = 250

    fun create(context: Context, httpClient: OkHttpClient): ImageLoader {
        val directory = File(context.cacheDir, "image_cache").apply { mkdirs() }
        return ImageLoader.Builder(context)
            .okHttpClient { httpClient }
            .crossfade(CROSSFADE_MILLIS)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .build()
    }
}
