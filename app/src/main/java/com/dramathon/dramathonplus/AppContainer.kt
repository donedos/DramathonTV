package com.dramathon.dramathonplus

import android.content.Context
import com.dramathon.dramathonplus.client.DramaBoxClient
import com.dramathon.dramathonplus.client.NativeReelShortSecretProvider
import com.dramathon.dramathonplus.client.ReelShortApi
import com.dramathon.dramathonplus.client.ReelShortClient
import com.dramathon.dramathonplus.client.ReelShortSession
import com.dramathon.dramathonplus.client.TokenManager
import com.dramathon.dramathonplus.network.HttpClients
import com.dramathon.dramathonplus.network.NetworkClient

class AppContainer(context: Context) {
    // Shared client with generous timeouts and a connection pool. The DramaBox
    // API can take well over ten seconds to answer on a cold connection, and a
    // plain OkHttpClient() (10s default) turns that into an empty result.
    val okHttpClient = NetworkClient(context.applicationContext).okHttpClient
        .also { HttpClients.install(it) }

    init {
        TokenManager.install(okHttpClient)
        DramaBoxClient.install(okHttpClient)
    }

    val reelShortSession = ReelShortSession(NativeReelShortSecretProvider())
    private val reelShortClient = ReelShortClient(okHttpClient, reelShortSession)
    val reelShortApi = ReelShortApi(reelShortSession, reelShortClient)
}
