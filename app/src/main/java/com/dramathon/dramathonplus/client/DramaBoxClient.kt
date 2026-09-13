package com.dramathon.dramathonplus.client

import android.util.Log
import com.dramathon.dramathonplus.sign.DeviceHeaders
import com.dramathon.dramathonplus.sign.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DramaBoxClient {
    private const val TAG = "DramaBoxClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://sapi.dramaboxdb.com"

    suspend fun post(path: String, payload: JSONObject, retry: Int = 1): JSONObject = withContext(Dispatchers.IO) {
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
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                
                if (!json.optBoolean("success") && retry > 0) {
                    val msg = json.optString("message", "")
                    Log.w(TAG, "Request failed: $msg. Attempting token refresh...")
                    TokenManager.bootstrap()
                    return@withContext post(path, payload, retry - 1)
                }
                json
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post failed for $path", e)
            JSONObject().apply { 
                put("success", false)
                put("message", e.message)
            }
        }
    }
}
