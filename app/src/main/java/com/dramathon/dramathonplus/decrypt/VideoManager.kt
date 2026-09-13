package com.dramathon.dramathonplus.decrypt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object VideoManager {
    private const val TAG = "VideoManager"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun prepareDecryptedVideo(url: String, context: Context): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading encrypted video: $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "okhttp/4.10.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext null
            }
            
            val data = response.body?.bytes() ?: return@withContext null
            Log.d(TAG, "Downloaded ${data.size / 1024 / 1024} MB")

            val b64 = Mp4Parser.extractAliyunUri(data)
            if (b64 == null) {
                Log.w(TAG, "No Aliyun URI found, video might be unencrypted or unsupported. Saving raw data.")
                return@withContext saveToTempFile(data, context, "raw_${System.currentTimeMillis()}.mp4")
            }

            Log.d(TAG, "Deriving AES key from URI...")
            val aesKey = VideoDecryptor.deriveAESKey(b64)
            Log.d(TAG, "Parsing tracks...")
            val tracks = Mp4Parser.parseTracks(data)
            
            Log.d(TAG, "Decrypting samples: ${tracks.video.size} video, ${tracks.audio.size} audio")
            if (tracks.video.isEmpty() && tracks.audio.isEmpty()) {
                Log.e(TAG, "No tracks parsed! Saving raw data as fallback.")
                return@withContext saveToTempFile(data, context, "error_raw_${System.currentTimeMillis()}.mp4")
            }
            
            val decryptedData = VideoDecryptor.decryptSamples(data, aesKey, tracks)
            Log.d(TAG, "Decryption complete.")
            
            return@withContext saveToTempFile(decryptedData, context, "decrypted_${System.currentTimeMillis()}.mp4")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare video", e)
            null
        }
    }

    private fun saveToTempFile(data: ByteArray, context: Context, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        FileOutputStream(tempFile).use { it.write(data) }
        return tempFile
    }
    
    fun clearCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { 
            if (it.name.endsWith(".mp4")) it.delete() 
        }
    }
}
