package com.dramathon.dramathonplus.client

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ReelShortClient(
    private val client: OkHttpClient,
    private val session: ReelShortSession
) {
    private fun aesCipher(mode: Int, secrets: ReelShortSecretBundle): Cipher {
        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(
                mode,
                SecretKeySpec(secrets.aesKey.toByteArray(Charsets.US_ASCII), "AES"),
                IvParameterSpec(secrets.aesIv.toByteArray(Charsets.US_ASCII))
            )
        }
    }

    private fun generateTs(): String = (System.currentTimeMillis() / 1000).toString()

    internal fun generateClientTraceId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (0 until 10000000).random()
        return "$timestamp$random"
    }

    internal fun generateRequestTime(secrets: ReelShortSecretBundle): String {
        val timestamp = System.currentTimeMillis()
        val plaintext = "${timestamp}_${secrets.apkSignatureHash}"
        val encrypted = aesCipher(Cipher.ENCRYPT_MODE, secrets)
            .doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    internal fun generateSign(
        params: Map<String, String>,
        secrets: ReelShortSecretBundle
    ): String {
        val signString = params.keys.sorted()
            .joinToString("&") { key -> "$key=${params[key]}" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secrets.signSecretKey.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        return mac.doFinal(signString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    internal fun decryptPlayUrl(
        encryptedData: String,
        secrets: ReelShortSecretBundle
    ): String {
        require(encryptedData.isNotBlank()) { "ReelShort play URL payload is empty" }
        val plaintext = aesCipher(Cipher.DECRYPT_MODE, secrets)
            .doFinal(Base64.getDecoder().decode(encryptedData))
        val compressed = Base64.getDecoder().decode(String(plaintext, Charsets.US_ASCII))
        return if (isGzip(compressed)) {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } else {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }
    }

    private fun isGzip(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
    }

    suspend fun request(endpoint: String, bodyParams: Map<String, String>): JSONObject {
        val secrets = session.current()
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = generateTs()
                val clientTraceId = generateClientTraceId()
                val requestTime = generateRequestTime(secrets)
                val headerParams = linkedMapOf<String, String>(
                    "apiVersion" to secrets.apiVersion,
                    "channelId" to secrets.channelId,
                    "clientTraceId" to clientTraceId,
                    "clientVer" to secrets.clientVer,
                    "devId" to secrets.devId,
                    "lang" to secrets.lang,
                    "requestTime" to requestTime,
                    "session" to secrets.session,
                    "ts" to timestamp,
                    "uid" to secrets.uid
                )
                val allParams = bodyParams + headerParams
                val sign = generateSign(allParams, secrets)
                val body = bodyParams.entries.joinToString("&") { (key, value) ->
                    "$key=${URLEncoder.encode(value, "UTF-8").replace("+", "%20")}"
                }
                val request = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .header("User-Agent", "okhttp/4.11.0")
                    .header("Accept-Encoding", "gzip")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("requesttime", requestTime)
                    .header("uid", secrets.uid)
                    .header("channelid", secrets.channelId)
                    .header("ts", timestamp)
                    .header("apiversion", secrets.apiVersion)
                    .header("session", secrets.session)
                    .header("lang", secrets.lang)
                    .header("devid", secrets.devId)
                    .header("clientver", secrets.clientVer)
                    .header("clienttraceid", clientTraceId)
                    .header("sign", sign)
                    .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext JSONObject().apply {
                            put("code", response.code)
                            put("msg", "Server returned HTTP status ${response.code}")
                        }
                    }
                    val bytes = response.body?.bytes() ?: "{}".toByteArray(Charsets.UTF_8)
                    val text = if (isGzip(bytes)) {
                        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
                    } else {
                        String(bytes, Charsets.UTF_8)
                    }
                    val json = JSONObject(if (text.isBlank()) "{}" else text)
                    if (json.optInt("code") != 0) {
                        Log.w(TAG, "ReelShort $endpoint returned code=${json.optInt("code")} msg=${json.optString("msg")}")
                    }
                    json
                }
            } catch (exception: Exception) {
                Log.e(TAG, "ReelShort request failed for $endpoint", exception)
                JSONObject().apply {
                    put("code", -1)
                    put("msg", exception.message ?: "network error")
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReelShortClient"
        private const val BASE_URL = "https://v-api.crazymaplestudios.com"
    }
}
