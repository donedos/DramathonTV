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

object TokenManager {
    private const val TAG = "TokenManager"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://sapi.dramaboxdb.com"

    suspend fun bootstrap(): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        val timestamp = System.currentTimeMillis()
        val deviceId = DeviceHeaders.deviceId
        val androidId = DeviceHeaders.androidId
        val tn = "" // Initial bootstrap has no token

        val payloadStr = payload.toString()
        val signStr = "timestamp=$timestamp$payloadStr$deviceId$androidId$tn"
        val signature = Signer.sign(signStr)

        val headers = DeviceHeaders.getBaseHeaders().toMutableMap().apply {
            put("sn", signature)
            put("tn", "") 
        }

        val url = "$BASE_URL/drama-box/ap001/bootstrap?timestamp=$timestamp"
        
        val requestBody = payloadStr.toRequestBody("application/json; charset=UTF-8".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                headers.forEach { (k, v) -> 
                    if (v.isNotEmpty()) addHeader(k, v) 
                }
            }
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                val bodyBytes = response.body?.bytes()
                val body = bodyBytes?.let { String(it) }
                Log.d(TAG, "Bootstrap response code: ${response.code}")
                Log.d(TAG, "Bootstrap response headers: ${response.headers}")
                
                if (response.isSuccessful) {
                    val json = JSONObject(body ?: "{}")
                    if (json.optBoolean("success")) {
                        val newToken = json.optJSONObject("data")?.optJSONObject("user")?.optString("token")
                        if (newToken != null) {
                            DeviceHeaders.tn = "Bearer $newToken"
                            Log.i(TAG, "Successfully bootstrapped new token")
                            newToken
                        } else {
                            Log.e(TAG, "No token in bootstrap response: $body")
                            null
                        }
                    } else {
                        Log.e(TAG, "Bootstrap unsuccessful: ${json.optString("message")}")
                        null
                    }
                } else {
                    Log.e(TAG, "Bootstrap HTTP error: ${response.code} body: $body")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap exception", e)
            null
        }
    }
}
