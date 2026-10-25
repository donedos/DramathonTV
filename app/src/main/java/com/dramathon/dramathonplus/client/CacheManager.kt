package com.dramathon.dramathonplus.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CacheManager {
    private const val TAG = "CacheManager"
    private val apiCache = ConcurrentHashMap<String, CacheEntry>()
    private const val DEFAULT_TTL = 30 * 24 * 60 * 60 * 1000L // 30 days
    private var prefs: SharedPreferences? = null
    private var cacheDir: File? = null

    data class CacheEntry(val data: Any, val timestamp: Long)

    fun init(context: Context) {
        val appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences("api_cache", Context.MODE_PRIVATE)
        cacheDir = File(appCtx.cacheDir, "api_json_cache").apply { if (!exists()) mkdirs() }
        Log.d(TAG, "Initialized. Cache dir: ${cacheDir?.absolutePath}")
    }

    fun put(key: String, data: Any) {
        val now = System.currentTimeMillis()
        apiCache[key] = CacheEntry(data, now)

        val stringData = when (data) {
            is JSONObject -> "obj:" + data.toString()
            is JSONArray -> "arr:" + data.toString()
            else -> data.toString()
        }

        // For large strings (like episode lists), save to file instead of SharedPreferences
        if (stringData.length > 2048) {
            saveToFile(key, stringData)
            prefs?.edit()?.apply {
                putString(key, "file_ref")
                putLong(key + "_ts", now)
                apply()
            }
        } else {
            prefs?.edit()?.apply {
                putString(key, stringData)
                putLong(key + "_ts", now)
                apply()
            }
            // Cleanup file if it exists
            getFile(key).delete()
        }
    }

    fun get(key: String): Any? {
        val now = System.currentTimeMillis()

        // 1. Memory Cache
        val entry = apiCache[key]
        if (entry != null) {
            if (now - entry.timestamp > DEFAULT_TTL) {
                apiCache.remove(key)
                removeFromPrefs(key)
                return null
            }
            return entry.data
        }

        // 2. Disk (Prefs or File)
        val savedData = prefs?.getString(key, null)
        val savedTs = prefs?.getLong(key + "_ts", 0) ?: 0

        if (savedData != null && savedTs > 0) {
            if (now - savedTs > DEFAULT_TTL) {
                removeFromPrefs(key)
                return null
            }

            val content = if (savedData == "file_ref") {
                readFromFile(key)
            } else {
                savedData
            }

            if (content == null) return null

            val parsedData = try {
                when {
                    content.startsWith("obj:") -> JSONObject(content.substring(4))
                    content.startsWith("arr:") -> JSONArray(content.substring(4))
                    else -> content
                }
            } catch (e: Exception) {
                Log.e(TAG, "Parse error for $key", e)
                null
            }

            if (parsedData != null) {
                apiCache[key] = CacheEntry(parsedData, savedTs)
                return parsedData
            }
        }

        return null
    }

    private fun saveToFile(key: String, data: String) {
        try {
            val file = getFile(key)
            file.writeText(data)
        } catch (e: Exception) {
            Log.e(TAG, "File write error for $key", e)
        }
    }

    private fun readFromFile(key: String): String? {
        return try {
            val file = getFile(key)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "File read error for $key", e)
            null
        }
    }

    private fun getFile(key: String): File {
        // Sanitize key for filename
        val safeKey = key.replace("[^a-zA-Z0-9]".toRegex(), "_")
        return File(cacheDir, "$safeKey.json")
    }

    private fun removeFromPrefs(key: String) {
        prefs?.edit()?.apply {
            remove(key)
            remove(key + "_ts")
            apply()
        }
        getFile(key).delete()
    }

    fun clear() {
        apiCache.clear()
        prefs?.edit()?.clear()?.apply()
        cacheDir?.listFiles()?.forEach { it.delete() }
    }
}
