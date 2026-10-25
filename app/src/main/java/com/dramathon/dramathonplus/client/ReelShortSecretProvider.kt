package com.dramathon.dramathonplus.client

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

interface ReelShortSecretProvider {
    suspend fun load(): ReelShortSecretBundle

    /**
     * Bootstrapping configuration used when the runtime source cannot be reached
     * (fresh install, missing or misconfigured Realtime Database node).
     */
    fun default(): ReelShortSecretBundle
}

/**
 * Request-signing material. It ships inside the native library so the runtime
 * configuration only has to carry the per-user session data.
 */
data class ReelShortNativeSecrets(
    val signSecretKey: String,
    val aesKey: String,
    val aesIv: String,
    val apkSignatureHash: String
)

data class ReelShortSecretBundle(
    val uid: String,
    val session: String,
    val devId: String,
    val lang: String,
    val channelId: String,
    val clientVer: String,
    val apiVersion: String,
    val signSecretKey: String,
    val aesKey: String,
    val aesIv: String,
    val apkSignatureHash: String
) {
    init {
        require(uid.isNotBlank()) { "ReelShort uid is required" }
        require(session.isNotBlank()) { "ReelShort session is required" }
        require(devId.isNotBlank()) { "ReelShort devId is required" }
        require(lang.isNotBlank()) { "ReelShort lang is required" }
        require(channelId.isNotBlank()) { "ReelShort channelId is required" }
        require(clientVer.isNotBlank()) { "ReelShort clientVer is required" }
        require(apiVersion.isNotBlank()) { "ReelShort apiVersion is required" }
        require(signSecretKey.toByteArray(Charsets.US_ASCII).size == 32) {
            "ReelShort signing key has an invalid length"
        }
        require(aesKey.toByteArray(Charsets.US_ASCII).size == 16) {
            "ReelShort AES key has an invalid length"
        }
        require(aesIv.toByteArray(Charsets.US_ASCII).size == 16) {
            "ReelShort AES IV has an invalid length"
        }
        require(apkSignatureHash.length == 32 && apkSignatureHash.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "ReelShort APK signature hash is invalid"
        }
    }

    companion object {
        /**
         * Builds a bundle from the runtime node. The node only carries the
         * user/session configuration; the signing material comes from [native].
         */
        fun fromJson(json: JSONObject, native: ReelShortNativeSecrets): ReelShortSecretBundle =
            ReelShortSecretBundle(
                uid = json.required("uid"),
                session = json.required("session"),
                devId = json.required("devId"),
                lang = json.required("lang"),
                channelId = json.required("channelId"),
                clientVer = json.required("clientVer"),
                apiVersion = json.required("apiVersion"),
                signSecretKey = native.signSecretKey,
                aesKey = native.aesKey,
                aesIv = native.aesIv,
                apkSignatureHash = native.apkSignatureHash
            )

        private fun JSONObject.required(key: String): String = optString(key).trim().also {
            require(it.isNotEmpty()) { "ReelShort configuration is missing $key" }
        }
    }
}

class NativeReelShortSecretProvider(
    private val endpoint: String = DEFAULT_ENDPOINT
) : ReelShortSecretProvider {
    init {
        System.loadLibrary("native-lib")
    }

    override suspend fun load(): ReelShortSecretBundle = withContext(Dispatchers.IO) {
        val appCheckToken = FirebaseAppCheck.getInstance().getToken(false).await().token
        val json = JSONObject(fetchJson(endpoint, systemTrustedCertificates(), appCheckToken))
        ReelShortSecretBundle.fromJson(json, nativeSecrets())
    }

    override fun default(): ReelShortSecretBundle = ReelShortSecretBundle(
        uid = DEFAULT_UID,
        session = DEFAULT_SESSION,
        devId = DEFAULT_DEV_ID,
        lang = "en",
        channelId = DEFAULT_CHANNEL_ID,
        clientVer = DEFAULT_CLIENT_VER,
        apiVersion = DEFAULT_API_VERSION,
        signSecretKey = getNativeSignSecretKey(),
        aesKey = getNativeAesKey(),
        aesIv = getNativeAesIv(),
        apkSignatureHash = getNativeApkSignatureHash()
    )

    private fun nativeSecrets(): ReelShortNativeSecrets = ReelShortNativeSecrets(
        signSecretKey = getNativeSignSecretKey(),
        aesKey = getNativeAesKey(),
        aesIv = getNativeAesIv(),
        apkSignatureHash = getNativeApkSignatureHash()
    )

    private external fun getNativeSignSecretKey(): String

    private external fun getNativeAesKey(): String

    private external fun getNativeAesIv(): String

    private external fun getNativeApkSignatureHash(): String

    private external fun fetchJson(
        endpoint: String,
        caCertificates: Array<ByteArray>,
        appCheckToken: String
    ): String

    private fun systemTrustedCertificates(): Array<ByteArray> {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { manager -> manager.acceptedIssuers.asList() }
            .distinctBy { certificate -> certificate.encoded.contentHashCode() }
            .map { certificate -> certificate.encoded }
            .toTypedArray()
            .also { certificates ->
                require(certificates.isNotEmpty()) { "System trust anchors are unavailable" }
            }
    }

    companion object {
        private const val TAG = "ReelShortSecrets"

        const val DEFAULT_ENDPOINT =
            "https://dramathonkey-default-rtdb.asia-southeast1.firebasedatabase.app/reelshort.json"

        private const val DEFAULT_UID = "592831694"
        private const val DEFAULT_SESSION = "44d040d1b12b31b5d29337696a994723"
        private const val DEFAULT_DEV_ID = "1eb06d3de357be4f"
        private const val DEFAULT_CHANNEL_ID = "AVG10003"
        private const val DEFAULT_CLIENT_VER = "3.5.20"
        private const val DEFAULT_API_VERSION = "1.4.8"

        init {
            Log.d(TAG, "ReelShort secret provider ready")
        }
    }
}
