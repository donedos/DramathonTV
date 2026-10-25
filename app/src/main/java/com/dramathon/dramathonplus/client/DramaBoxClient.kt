package com.dramathon.dramathonplus.client

import android.util.Log
import com.dramathon.dramathonplus.network.HttpClients
import com.dramathon.dramathonplus.sign.DeviceHeaders
import com.dramathon.dramathonplus.sign.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object DramaBoxClient {
    private const val TAG = "DramaBoxClient"

    private var client: OkHttpClient? = null

    fun install(shared: OkHttpClient) {
        client = shared
    }

    private fun getClient(): OkHttpClient {
        return client ?: HttpClients.get().also { client = it }
    }

    private const val BASE_URL = "https://sapi.dramaboxdb.com"

    suspend fun post(path: String, payload: JSONObject, retry: Int = 1): JSONObject = withContext(Dispatchers.IO) {
        val http = getClient()
        val timestamp = System.currentTimeMillis()
        val deviceId = DeviceHeaders.deviceId
        val androidId = DeviceHeaders.androidId
        val tn = DeviceHeaders.tn

        val payloadStr = payload.toString()
        val signStr = "timestamp=$timestamp$payloadStr$deviceId$androidId$tn"
        val signature = Signer.sign(signStr)

        val headers = DeviceHeaders.getBaseHeaders().toMutableMap().apply {
            put("sn", signature)
        }

        val url = "$BASE_URL$path?timestamp=$timestamp"
        
        val request = Request.Builder()
            .url(url)
            .post(payloadStr.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .apply {
                headers.forEach { (k, v) -> 
                    if (v.isNotEmpty()) addHeader(k, v) 
                }
            }
            .build()

        return@withContext try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                
                if (!json.optBoolean("success")) {
                    val msg = json.optString("message", "")
                    if (retry > 0) {
                        Log.w(TAG, "Request failed: $msg. Attempting token refresh...")
                        TokenManager.bootstrap()
                        return@withContext post(path, payload, retry - 1)
                    }
                    Log.w(TAG, "Request failed after retries: $msg. http=${response.code} timestamp=$timestamp body=$body")
                }
                json
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post failed for $path (timestamp=$timestamp)", e)
            JSONObject().apply { 
                put("success", false)
                put("message", e.message)
            }
        }
    }
}
