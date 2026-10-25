package com.dramathon.dramathonplus.client

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReelShortSession(
    private val provider: ReelShortSecretProvider
) {
    private companion object {
        const val TAG = "ReelShortSession"
    }

    private val initializationMutex = Mutex()

    @Volatile
    private var bundle: ReelShortSecretBundle? = null

    suspend fun ensureReady(forceRefresh: Boolean = false): ReelShortSecretBundle {
        if (!forceRefresh) {
            bundle?.let { return it }
        }
        return initializationMutex.withLock {
            if (!forceRefresh) {
                bundle?.let { return@withLock it }
            }
            try {
                provider.load().also { bundle = it }
            } catch (e: Exception) {
                // The runtime source is optional: fall back to the bootstrapping
                // configuration so ReelShort keeps working offline or when the
                // node is missing.
                Log.w(TAG, "ReelShort configuration fetch failed, using defaults", e)
                provider.default().also { bundle = it }
            }
        }
    }

    fun current(): ReelShortSecretBundle = bundle ?: provider.default()

    fun currentOrNull(): ReelShortSecretBundle? = bundle
}
