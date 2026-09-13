package com.dramathon.dramathonplus.client

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object CacheManager {
    private val apiCache = mutableMapOf<String, CacheEntry>()
    private const val DEFAULT_TTL = 24 * 60 * 60 * 1000L // 24 hours
    private var prefs: SharedPreferences? = null

    data class CacheEntry(val data: Any, val timestamp: Long)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("api_cache", Context.MODE_PRIVATE)
    }

    fun put(key: String, data: Any) {
        apiCache[key] = CacheEntry(data, System.currentTimeMillis())
        
        val stringData = when (data) {
            is JSONObject -> "obj:" + data.toString()
            is JSONArray -> "arr:" + data.toString()
            else -> data.toString()
        }
        
        prefs?.edit()?.apply {
            putString(key, stringData)
            putLong(key + "_ts", System.currentTimeMillis())
            apply()
        }
    }

    fun get(key: String): Any? {
        val entry = apiCache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp > DEFAULT_TTL) {
                apiCache.remove(key)
                removeFromPrefs(key)
                return null
            }
            return entry.data
        }

        // Try load from prefs
        val savedData = prefs?.getString(key, null)
        val savedTs = prefs?.getLong(key + "_ts", 0) ?: 0
        
        if (savedData != null && savedTs > 0) {
            if (System.currentTimeMillis() - savedTs > DEFAULT_TTL) {
                removeFromPrefs(key)
                return null
            }
            
            val parsedData = try {
                when {
                    savedData.startsWith("obj:") -> JSONObject(savedData.substring(4))
                    savedData.startsWith("arr:") -> JSONArray(savedData.substring(4))
                    else -> savedData
                }
            } catch (e: Exception) {
                null
            }
            
            if (parsedData != null) {
                apiCache[key] = CacheEntry(parsedData, savedTs)
                return parsedData
            }
        }
        
        return null
    }
    
    private fun removeFromPrefs(key: String) {
        prefs?.edit()?.apply {
            remove(key)
            remove(key + "_ts")
            apply()
        }
    }
    
    fun clear() {
        apiCache.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
