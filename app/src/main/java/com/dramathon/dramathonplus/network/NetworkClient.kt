package com.dramathon.dramathonplus.network

import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkClient(
    context: Context,
    val cacheSizeBytes: Long = DEFAULT_CACHE_SIZE_BYTES
) {
    val okHttpClient: OkHttpClient

    init {
        val cacheDirectory = File(context.cacheDir, "http_cache").apply { mkdirs() }
        okHttpClient = OkHttpClient.Builder()
            .cache(Cache(cacheDirectory, cacheSizeBytes))
            .connectionPool(ConnectionPool())
            .dispatcher(okhttp3.Dispatcher())
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val privateRequest = request.method !in setOf("GET", "HEAD") ||
                    request.header("Authorization") != null ||
                    request.header("Cookie") != null ||
                    request.header("tn") != null ||
                    request.header("session") != null ||
                    request.header("Range") != null
                if (privateRequest) {
                    chain.proceed(
                        request.newBuilder()
                            .cacheControl(CacheControl.FORCE_NETWORK)
                            .header("Cache-Control", "no-store")
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .build()
    }

    companion object {
        const val DEFAULT_CACHE_SIZE_BYTES = 20L * 1024L * 1024L
    }
}

/**
 * Process-wide OkHttpClient holder.
 *
 * The DramaBox API and the ReelShort API both need long timeouts: the DramaBox
 * rank endpoints routinely take more than ten seconds to answer on a cold
 * connection and the ReelShort leg has to reach a native TLS stack first. The
 * OkHttp default of ten seconds turns both into silent empty results, so every
 * component shares one generously configured client instead of building its own.
 */
object HttpClients {
    @Volatile
    private var shared: OkHttpClient? = null

    fun install(client: OkHttpClient) {
        shared = client
    }

    fun get(): OkHttpClient {
        shared?.let { return it }
        return synchronized(this) {
            shared ?: OkHttpClient.Builder()
                .connectionPool(ConnectionPool())
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
                .also { shared = it }
        }
    }
}
