package com.dramathon.dramathonplus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dramathon.dramathonplus.client.CacheManager
import com.dramathon.dramathonplus.client.DramaBoxApi
import com.dramathon.dramathonplus.client.DramaCategories
import com.dramathon.dramathonplus.client.EpisodeFetcher
import com.dramathon.dramathonplus.client.NativeReelShortSecretProvider
import com.dramathon.dramathonplus.client.ReelShortApi
import com.dramathon.dramathonplus.client.ReelShortClient
import com.dramathon.dramathonplus.client.ReelShortSession
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.dramathon.dramathonplus.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object DeviceIdManager {
    private const val PREF_NAME = "device_prefs"
    private const val KEY_STABLE_ID = "stable_device_id"

    fun getStableId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_STABLE_ID, null)
        
        if (id == null) {
            // Migration: Try to use the current ANDROID_ID first so existing users aren't logged out
            id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            // Fallback to UUID if ANDROID_ID is missing or "unknown"
            if (id.isNullOrEmpty() || id == "unknown") {
                id = UUID.randomUUID().toString()
            }
            
            prefs.edit().putString(KEY_STABLE_ID, id).apply()
        }
        return id
    }
}

class DramaViewModel : ViewModel() {
    private companion object {
        const val TAG = "DramaViewModel"
    }

    private val client = HttpClients.get()
    private val reelShortSession = ReelShortSession(NativeReelShortSecretProvider())
    private val reelShortClient = ReelShortClient(client, reelShortSession)
    private val reelShortApi = ReelShortApi(reelShortSession, reelShortClient)
    private val RTDB_URL = "https://dramathonkey-default-rtdb.asia-southeast1.firebasedatabase.app/"

    private val _dramas = MutableStateFlow<JSONArray>(JSONArray())
    val dramas: StateFlow<JSONArray> = _dramas

    private val _selectedDrama = MutableStateFlow<JSONObject?>(null)
    val selectedDrama: StateFlow<JSONObject?> = _selectedDrama
    
    private val _selectedDramaDetail = MutableStateFlow<JSONObject?>(null)
    val selectedDramaDetail: StateFlow<JSONObject?> = _selectedDramaDetail

    private val _episodes = MutableStateFlow<JSONArray>(JSONArray())
    val episodes: StateFlow<JSONArray> = _episodes

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    
    private val _currentTab = MutableStateFlow("explore")
    val currentTab: StateFlow<String> = _currentTab

    private val _watchedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val watchedEpisodes: StateFlow<Set<String>> = _watchedEpisodes

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private val _unlockCode = MutableStateFlow<String?>(null)
    val unlockCode: StateFlow<String?> = _unlockCode

    private val _favorites = MutableStateFlow<Map<Long, JSONObject>>(emptyMap())
    val favorites: StateFlow<Map<Long, JSONObject>> = _favorites

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _pipMode = MutableStateFlow(false)
    val pipMode: StateFlow<Boolean> = _pipMode

    private val _playingEpisode = MutableStateFlow<JSONObject?>(null)
    val playingEpisode: StateFlow<JSONObject?> = _playingEpisode

    private val _updateAvailable = MutableStateFlow<JSONObject?>(null)
    val updateAvailable: StateFlow<JSONObject?> = _updateAvailable

    private val _subscriptionExpiry = MutableStateFlow<String?>(null)
    val subscriptionExpiry: StateFlow<String?> = _subscriptionExpiry

    private val _subtitleLanguage = MutableStateFlow("English")
    val subtitleLanguage: StateFlow<String> = _subtitleLanguage.asStateFlow()

    private val _globalUserCount = MutableStateFlow(0)
    val globalUserCount: StateFlow<Int> = _globalUserCount

    private val _liveStatus = MutableStateFlow(true)
    val liveStatus: StateFlow<Boolean> = _liveStatus

    private val _isVersionKilled = MutableStateFlow(false)
    val isVersionKilled: StateFlow<Boolean> = _isVersionKilled

    private val _reelShortError = MutableStateFlow<String?>(null)
    val reelShortError: StateFlow<String?> = _reelShortError
    private val _episodesError = MutableStateFlow<String?>(null)
    val episodesError: StateFlow<String?> = _episodesError

    var currentPage = 1
        private set
    
    private val _isEndReached = MutableStateFlow(false)
    val isEndReached: StateFlow<Boolean> = _isEndReached

    private var _currentSearchKeyword: String? = null

    private var codeRef: DatabaseReference? = null
    private var codeListener: ValueEventListener? = null

    init {
        viewModelScope.launch {
            loadTab("explore")
        }
    }

    fun initUnlockStatus(context: Context, deviceId: String) {
        loadWatchedEpisodes(context)
        loadFavorites(context)
        
        // Report global device activity
        reportDeviceActivity(deviceId)

        // Monitor app live status
        monitorLiveStatus()

        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        _isDarkMode.value = prefs.getBoolean("is_dark_mode", true)
        _subtitleLanguage.value = prefs.getString("subtitle_lang", "English") ?: "English"
        
        val isUnlocked = prefs.getBoolean("is_unlocked", false)
        
        if (isUnlocked) {
            val code = prefs.getString("unlock_code", null)
            if (code != null) {
                _unlockCode.value = code
                startListening(code, deviceId, context)
            } else {
                lockApp(context)
            }
        } else {
            _isUnlocked.value = false
        }
    }

    fun setSubtitleLanguage(context: Context, language: String) {
        _subtitleLanguage.value = language
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("subtitle_lang", language).apply()
    }

    private fun startListening(code: String, deviceId: String, context: Context) {
        // Clear previous listener if any
        stopListening()

        val db = FirebaseDatabase.getInstance(RTDB_URL)
        val ref = db.getReference("master_codes").child(code)
        codeRef = ref

        // One-time update of last_active when starting to listen
        viewModelScope.launch {
            try {
                val snapshot = ref.get().await()
                if (snapshot.exists()) {
                    val maxUsers = snapshot.child("user_count").getValue(Int::class.java) ?: 3
                    for (i in 1..maxUsers) {
                        val slotName = "device_id_$i"
                        if (snapshot.child("$slotName/id").value == deviceId) {
                            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                            val updates = mutableMapOf<String, Any>("last_active" to timestamp)
                            if (!snapshot.child(slotName).hasChild("date_unlocked")) {
                                updates["date_unlocked"] = timestamp
                            }
                            ref.child(slotName).updateChildren(updates)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val isActive = snapshot.child("active").getValue(Boolean::class.java) ?: true
                    val expireDateStr = snapshot.child("date_expire").getValue(String::class.java)

                    if (!isActive) {
                        lockApp(context)
                        return
                    }

                    if (expireDateStr != null) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val expireDate = sdf.parse(expireDateStr)
                            if (expireDate != null && Date().after(expireDate)) {
                                // Mark as inactive in DB before locking local app
                                ref.child("active").setValue(false)
                                lockApp(context)
                                return
                            }
                            _subscriptionExpiry.value = expireDateStr
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    var isDeviceAuthorized = false
                    val maxUsers = snapshot.child("user_count").getValue(Int::class.java) ?: 3

                    for (i in 1..maxUsers) {
                        val slotName = "device_id_$i"
                        val slotSnapshot = snapshot.child(slotName)
                        if (slotSnapshot.exists()) {
                            val id = slotSnapshot.child("id").value as? String
                            if (id == deviceId) {
                                isDeviceAuthorized = true
                                break
                            }
                        }
                    }

                    if (isDeviceAuthorized) {
                        _isUnlocked.value = true
                    } else {
                        lockApp(context)
                    }
                } else {
                    lockApp(context)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Ignore for now
            }
        }
        
        codeListener = listener
        ref.addValueEventListener(listener)
    }

    private fun reportDeviceActivity(deviceId: String) {
        val db = FirebaseDatabase.getInstance(RTDB_URL)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val deviceRef = db.getReference("active_devices").child(deviceId)
        
        val updates = mapOf(
            "last_active" to timestamp,
            "version" to BuildConfig.VERSION_NAME,
            "platform" to "Android TV"
        )
        
        deviceRef.updateChildren(updates)
        
        db.getReference("active_devices").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.childrenCount.toInt()
                _globalUserCount.value = count
                db.getReference("user_count").setValue(count)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("RTDB", "Failed to count active devices: ${error.message}")
            }
        })
    }

    private fun isVersionAtLeast(version: String, target: String): Boolean {
        try {
            val vParts = version.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val tParts = target.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(vParts.size, tParts.size)
            for (i in 0 until maxLen) {
                val v = vParts.getOrElse(i) { 0 }
                val t = tParts.getOrElse(i) { 0 }
                if (v > t) return true
                if (v < t) return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun monitorLiveStatus() {
        val db = FirebaseDatabase.getInstance(RTDB_URL)
        val currentVersion = BuildConfig.VERSION_NAME

        // Versions 1.6.1 onwards no longer use global "live" boolean; controlled solely by "killed_versions"
        if (!isVersionAtLeast(currentVersion, "1.6.1")) {
            // Legacy versions (< 1.6.1): Listen to global "live" switch
            db.getReference("live").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val value = snapshot.value
                        _liveStatus.value = when (value) {
                            is Boolean -> value
                            is String -> value.toBoolean()
                            is Long -> value == 1L
                            else -> true
                        }
                    } else {
                        _liveStatus.value = true
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("RTDB", "Failed to monitor live status: ${error.message}")
                }
            })
        } else {
            // Version 1.6.1+: Always live (legacy "live" boolean ignored)
            _liveStatus.value = true
        }

        // Listen to version-specific kill switches (killed_versions/<version_key>)
        db.getReference("killed_versions").child(currentVersion.replace(".", "_"))
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val isKilled = when (val value = snapshot.value) {
                            is Boolean -> value
                            is String -> value.toBoolean()
                            is Long -> value == 1L
                            else -> false
                        }
                        _isVersionKilled.value = isKilled
                        if (isKilled) {
                            _dramas.value = JSONArray()
                        }
                    } else {
                        _isVersionKilled.value = false
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("RTDB", "Failed to monitor version kill switch: ${error.message}")
                }
            })
    }

    private fun stopListening() {
        codeListener?.let { codeRef?.removeEventListener(it) }
        codeListener = null
        codeRef = null
    }

    private fun lockApp(context: Context) {
        _isUnlocked.value = false
        _unlockCode.value = null
        _subscriptionExpiry.value = null
        stopListening()
        
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_unlocked", false)
            .remove("unlock_code")
            .apply()
        
        _episodes.value = filterEpisodes(_episodes.value)
    }

    override fun onCleared() {
        stopListening()
    }

    fun unlock(code: String, deviceId: String, context: Context, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val db = FirebaseDatabase.getInstance(RTDB_URL)
                val codeRef = db.getReference("master_codes").child(code)
                val snapshot = codeRef.get().await()

                if (snapshot.exists()) {
                    val isActive = snapshot.child("active").getValue(Boolean::class.java) ?: true
                    val expireDateStr = snapshot.child("date_expire").getValue(String::class.java)

                    if (expireDateStr != null) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val expireDate = sdf.parse(expireDateStr)
                            if (expireDate != null && Date().after(expireDate)) {
                                codeRef.child("active").setValue(false)
                                onResult(false, "This code has expired.")
                                _loading.value = false
                                return@launch
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (!isActive) {
                        onResult(false, "This code is no longer active.")
                        _loading.value = false
                        return@launch
                    }

                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    var targetSlot: String? = null
                    var firstEmptySlot: String? = null
                    
                    val maxUsers = snapshot.child("user_count").getValue(Int::class.java) ?: 3

                    for (i in 1..maxUsers) {
                        val slotName = "device_id_$i"
                        val slotSnapshot = snapshot.child(slotName)
                        
                        if (slotSnapshot.exists()) {
                            val id = slotSnapshot.child("id").value as? String
                            if (id == deviceId) {
                                targetSlot = slotName
                                break
                            }
                        } else if (firstEmptySlot == null) {
                            firstEmptySlot = slotName
                        }
                    }
                    
                    val slotToUpdate = targetSlot ?: firstEmptySlot
                    
                    if (slotToUpdate != null) {
                        val deviceEntry = mutableMapOf(
                            "id" to deviceId,
                            "last_active" to timestamp
                        )
                        
                        if (targetSlot == null) {
                            deviceEntry["date_unlocked"] = timestamp
                            
                            // Default 1 week expiry if not set
                            if (!snapshot.hasChild("date_expire")) {
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    val date = sdf.parse(timestamp)
                                    val cal = Calendar.getInstance()
                                    cal.time = date!!
                                    cal.add(Calendar.DAY_OF_YEAR, 7)
                                    val expireDate = sdf.format(cal.time)
                                    codeRef.child("date_expire").setValue(expireDate)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            val existingDate = snapshot.child(slotToUpdate).child("date_unlocked").value as? String
                            deviceEntry["date_unlocked"] = existingDate ?: timestamp
                        }

                        codeRef.child(slotToUpdate).setValue(deviceEntry).await()
                        setUnlocked(context, code, deviceId)
                        onResult(true, null)
                    } else {
                        onResult(false, "Maximum limit of $maxUsers devices reached for this code.")
                    }
                } else {
                    onResult(false, "Invalid 6-character code.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Verification failed: ${e.message}")
            }
            _loading.value = false
        }
    }

    private fun setUnlocked(context: Context, code: String, deviceId: String) {
        _isUnlocked.value = true
        _unlockCode.value = code
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_unlocked", true)
            .putString("unlock_code", code)
            .apply()
        
        startListening(code, deviceId, context)
        
        // Force a re-fetch of episodes to remove the 7-episode limit immediately
        _selectedDrama.value?.let { drama ->
            viewModelScope.launch {
                selectDrama(drama, forceRefresh = true)
            }
        }
    }

    private fun filterEpisodes(eps: JSONArray): JSONArray {
        if (_isUnlocked.value) return eps
        val arr = JSONArray()
        for (i in 0 until minOf(eps.length(), 7)) {
            arr.put(eps.getJSONObject(i))
        }
        return arr
    }

    fun toggleTheme(context: Context) {
        val newVal = !_isDarkMode.value
        _isDarkMode.value = newVal
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_dark_mode", newVal).apply()
    }

    private fun loadWatchedEpisodes(context: Context) {
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        val watched = prefs.getStringSet("watched_episodes", emptySet()) ?: emptySet()
        _watchedEpisodes.value = watched
    }

    private fun saveWatchedEpisodes(context: Context) {
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("watched_episodes", _watchedEpisodes.value).apply()
    }

    private fun loadFavorites(context: Context) {
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        val favsString = prefs.getString("favorites_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(favsString)
            val map = mutableMapOf<Long, JSONObject>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                map[obj.optLong("bookId")] = obj
            }
            _favorites.value = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveFavorites(context: Context) {
        val prefs = context.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        _favorites.value.values.forEach { arr.put(it) }
        prefs.edit().putString("favorites_json", arr.toString()).apply()
    }

    fun toggleFavorite(drama: JSONObject, context: Context) {
        val bookId = drama.optLong("bookId")
        val current = _favorites.value.toMutableMap()
        if (current.containsKey(bookId)) {
            current.remove(bookId)
        } else {
            current[bookId] = drama
        }
        _favorites.value = current
        saveFavorites(context)
        
        // If we are currently in favorites tab, refresh the list
        if (_currentTab.value == "myfavorite") {
            viewModelScope.launch {
                loadTab("myfavorite")
            }
        }
    }

    fun markAsWatched(bookId: Long, chapterIndex: Int, context: Context) {
        val id = "${bookId}_$chapterIndex"
        if (!_watchedEpisodes.value.contains(id)) {
            _watchedEpisodes.value = _watchedEpisodes.value + id
            saveWatchedEpisodes(context)
        }
    }

    private fun sanitizeDramas(arr: JSONArray): JSONArray {
        val filtered = JSONArray()
        for (i in 0 until arr.length()) {
            val drama = arr.optJSONObject(i) ?: continue
            val name = drama.optString("bookName").ifEmpty { drama.optString("title") }
            val cover = drama.optString("cover").ifEmpty { 
                drama.optString("coverUrl").ifEmpty { 
                    drama.optString("coverWap") 
                } 
            }
            // Only include if it has a name and a cover image
            if (name.isNotEmpty() && cover.isNotEmpty()) {
                if (!drama.has("bookName") && drama.has("title")) {
                    drama.put("bookName", drama.getString("title"))
                }
                if (!drama.has("chapterCount") && drama.has("totalEpisodes")) {
                    drama.put("chapterCount", drama.getInt("totalEpisodes"))
                }
                filtered.put(drama)
            }
        }
        return filtered
    }

    private fun collectReelShortBooks(data: JSONObject): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        fun walk(obj: JSONObject) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val value = obj.get(keys.next())
                when (value) {
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            val item = value.optJSONObject(i) ?: continue
                            val id = item.optString("book_id").ifEmpty {
                                item.optString("bookId").ifEmpty { item.optString("t_book_id") }
                            }
                            if (id.isNotEmpty()) {
                                if (seen.add(id)) out.add(item)
                            } else {
                                walk(item)
                            }
                        }
                    }
                    is JSONObject -> walk(value)
                }
            }
        }
        walk(data)
        return out
    }

    private fun applyReelShortVideoPaths(ep: JSONObject, videoList: JSONArray) {
        val allPaths = JSONArray()
        for (j in 0 until videoList.length()) {
            val v = videoList.getJSONObject(j)
            val url = v.optString("url")
            if (url.isEmpty()) continue
            allPaths.put(JSONObject().apply {
                put("videoPath", url)
                put("quality", v.optInt("quality"))
                put("encode", v.optString("encode"))
            })
        }
        val best = pickBestRendition(allPaths)
        ep.put("allVideoPaths", allPaths)
        ep.put("videoPath", best?.optString("videoPath") ?: "")
        // Fallback candidates, best first: if the preferred rendition fails to load
        // the player retries with the next one instead of showing an endless spinner.
        ep.put("videoPaths", JSONArray(renditionFallbacks(allPaths, best)))
    }

    /**
     * Builds the ordered list of alternative stream URLs for an episode.
     * H.264 renditions come first (HEVC clips stall on many devices), then the
     * remaining renditions by descending quality.
     */
    private fun renditionFallbacks(allPaths: JSONArray, best: JSONObject?): List<String> {
        val paths = (0 until allPaths.length()).map { allPaths.getJSONObject(it) }
        val ordered = paths.sortedWith(
            compareByDescending<JSONObject> { it.optString("encode").contains("H264", ignoreCase = true) }
                .thenByDescending { it.optInt("quality") }
        )
        val urls = LinkedHashSet<String>()
        best?.optString("videoPath")?.takeIf { it.isNotEmpty() }?.let { urls.add(it) }
        ordered.forEach { urls.add(it.optString("videoPath")) }
        return urls.filter { it.isNotEmpty() }
    }

    /**
     * ReelShort serves every clip twice, as H.265 and as H.264. The H.265 renditions
     * hang on devices whose HEVC decoder is unreliable, so H.264 is used whenever it
     * is offered; only the highest quality rendition of that codec is picked.
     */
    private fun pickBestRendition(allPaths: JSONArray): JSONObject? {
        val paths = (0 until allPaths.length()).map { allPaths.getJSONObject(it) }
        val avc = paths.filter { it.optString("encode").contains("H264", ignoreCase = true) }
        return avc.maxByOrNull { it.optInt("quality") }
            ?: paths.maxByOrNull { it.optInt("quality") }
    }

    suspend fun loadTab(tab: String, forceRefresh: Boolean = false) {
        if (!_liveStatus.value || _isVersionKilled.value) {
            _dramas.value = JSONArray()
            return
        }
        _currentTab.value = tab
        _currentSearchKeyword = null
        currentPage = 1
        _isEndReached.value = false
        
        if (tab == "myfavorite") {
            val arr = JSONArray()
            _favorites.value.values.forEach { arr.put(it) }
            _dramas.value = arr
            return
        }

        if (tab == "more") {
            _dramas.value = JSONArray()
            return
        }

        if (tab == "settings") {
            _dramas.value = JSONArray()
            return
        }

        if (!forceRefresh) {
            val cached = CacheManager.get("tab_$tab") as? JSONArray
            // An empty cached array only means the previous fetch failed. Serving it
            // would pin the tab to "No dramas found" for the whole TTL and never
            // trigger another request, so only a non-empty cache is a valid hit.
            if (cached != null && cached.length() > 0) {
                _dramas.value = cached
                return
            }
            if (cached != null) {
                Log.w(TAG, "Ignoring empty cache for tab '$tab', refetching")
            }
        }

        _loading.value = true
        try {
            coroutineScope {
                val dbDeferred = async {
                    try {
                        when (tab) {
                            "explore" -> DramaBoxApi.latest()
                            "vip" -> DramaBoxApi.vip()
                            else -> DramaBoxApi.latest()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DramaBox request failed for tab '$tab'", e)
                        null
                    }
                }

                val rsDeferred = async {
                    try {
                        if (tab != "settings" && tab != "myfavorite" && tab != "more") {
                            reelShortApi.homepage().also {
                                if (it.optBoolean("success", false)) _reelShortError.value = null
                            }
                        } else null
                    } catch (e: Exception) {
                        Log.e(TAG, "ReelShort homepage failed for tab '$tab'", e)
                        _reelShortError.value = "ReelShort is temporarily unavailable"
                        null
                    }
                }

                val res = dbDeferred.await()

                val list = JSONArray()
                val seenIds = mutableSetOf<Long>()

                if (res != null) {
                    val data = res.optJSONObject("data")
                    if (data != null) {
                        val keys = listOf("rankList", "recommendList", "classifyBookList", "newTheaterList")
                        keys.forEach { key ->
                            val arr = when (key) {
                                "rankList" -> data.optJSONArray(key)
                                else -> data.optJSONObject(key)?.optJSONArray("records")
                            }
                            
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val drama = arr.optJSONObject(i) ?: continue
                                    val id = drama.optLong("bookId")
                                    if (id != 0L && seenIds.add(id)) {
                                        list.put(drama)
                                    }
                                }
                            }
                        }
                        
                        if (list.length() == 0) {
                            val recommendRecords = data.optJSONObject("recommendList")?.optJSONArray("records")
                            if (recommendRecords != null) {
                                for (i in 0 until recommendRecords.length()) {
                                    val drama = recommendRecords.optJSONObject(i) ?: continue
                                    val id = drama.optLong("bookId")
                                    if (id != 0L && seenIds.add(id)) list.put(drama)
                                }
                            }
                        }
                    }
                }

                if (list.length() < 50) {
                    try {
                        val supRes = DramaBoxApi.foryou()
                        val supData = supRes.optJSONObject("data")
                        val supRecs = supData?.optJSONObject("recommendList")?.optJSONArray("records")
                        if (supRecs != null) {
                            for (i in 0 until supRecs.length()) {
                                val drama = supRecs.optJSONObject(i) ?: continue
                                val id = drama.optLong("bookId")
                                if (id != 0L && seenIds.add(id)) list.put(drama)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supplemental 'for you' fetch failed for tab '$tab'", e)
                    }
                }

                if (list.length() < 50) {
                    try {
                        val rndRes = DramaBoxApi.randomDrama()
                        val rndData = rndRes.optJSONObject("data")
                        val rndRecs = rndData?.optJSONArray("recommendList")
                        if (rndRecs != null) {
                            for (i in 0 until rndRecs.length()) {
                                val drama = rndRecs.optJSONObject(i) ?: continue
                                val id = drama.optLong("bookId")
                                if (id != 0L && seenIds.add(id)) list.put(drama)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Supplemental random-drama fetch failed for tab '$tab'", e)
                    }
                }

                var sanitized = sanitizeDramas(list)
                // Never cache an empty result: an empty cached array is treated as a
                // valid cache hit on the next launch and would blank the tab for
                // the whole TTL. Keep whatever the user is already looking at.
                if (sanitized.length() > 0) {
                    _dramas.value = sanitized
                    CacheManager.put("tab_$tab", sanitized)
                } else {
                    Log.w(TAG, "Empty result for tab '$tab', not caching")
                    if (_dramas.value.length() == 0) {
                        val cached = CacheManager.get("tab_$tab") as? JSONArray
                        if (cached != null && cached.length() > 0) {
                            _dramas.value = cached
                        }
                    }
                }
                // The ReelShort leg can take tens of seconds (native TLS + secrets fetch),
                // so publish the DramaBox results and drop the spinner before awaiting it.
                _loading.value = false

                val rsRes = rsDeferred.await()
                if (rsRes != null && rsRes.optBoolean("success", false)) {
                    val data = rsRes.optJSONObject("data")
                    if (data != null) {
                        val current = _dramas.value
                        val merged = JSONArray()
                        val mergedIds = mutableSetOf<Long>()
                        for (i in 0 until current.length()) {
                            val drama = current.getJSONObject(i)
                            val id = drama.optLong("bookId")
                            if (id != 0L && mergedIds.add(id)) merged.put(drama)
                        }
                        var added = 0
                        collectReelShortBooks(data).forEach { item ->
                            val sId = item.optString("book_id", item.optString("bookId", item.optString("t_book_id")))
                            if (sId.isNotEmpty()) {
                                val derivedId = sId.hashCode().toLong()
                                if (mergedIds.add(derivedId)) {
                                    merged.put(JSONObject().apply {
                                        put("bookId", derivedId)
                                        put("realBookId", sId)
                                        put("bookName", item.optString("book_title", item.optString("title", item.optString("bookName"))))
                                        put("cover", item.optString("book_pic", item.optString("cover", item.optString("coverUrl"))))
                                        put("chapterCount", item.optInt("chapter_count", item.optInt("totalEpisodes", item.optInt("chapterCount"))))
                                        put("is_reelshort", true)
                                    })
                                    added++
                                }
                            }
                        }
                        if (added > 0) {
                            sanitized = sanitizeDramas(merged)
                            if (sanitized.length() > 0) {
                                _dramas.value = sanitized
                                CacheManager.put("tab_$tab", sanitized)
                            }
                        }
                    }
                }

                // loadMore() bails out while _loading is true, so clear it first.
                _loading.value = false
                if (sanitized.length() < 30 && sanitized.length() > 0) {
                    loadMore()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tab '$tab'", e)
            // Do not wipe the list on failure: fall back to a non-empty cache
            // (or keep whatever is already on screen) instead of blanking the UI.
            if (_dramas.value.length() == 0) {
                val cached = CacheManager.get("tab_$tab") as? JSONArray
                if (cached != null && cached.length() > 0) {
                    _dramas.value = cached
                }
            }
        }
        _loading.value = false
    }
    
    suspend fun search(keyword: String) {
        if (!_liveStatus.value || _isVersionKilled.value || keyword.isBlank()) return
        
        _currentSearchKeyword = keyword
        currentPage = 1
        _isEndReached.value = false

        _loading.value = true
        try {
            coroutineScope {
                val dbJob = async {
                    try {
                        DramaBoxApi.search(keyword)
                    } catch (e: Exception) {
                        Log.e(TAG, "DramaBox search failed for '$keyword'", e)
                        null
                    }
                }
                val rsJob = async {
                    try {
                        reelShortApi.search(keyword).also {
                            if (it.optBoolean("success", false)) _reelShortError.value = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ReelShort search failed for '$keyword'", e)
                        _reelShortError.value = "ReelShort is temporarily unavailable"
                        null
                    }
                }

                val res = dbJob.await()
                val rsRes = rsJob.await()

                val merged = JSONArray()
                val seenIds = mutableSetOf<Long>()

                if (res != null) {
                    val suggestList = res.optJSONObject("data")?.optJSONArray("suggestList") ?: JSONArray()
                    for (i in 0 until suggestList.length()) {
                        val drama = suggestList.getJSONObject(i)
                        val id = drama.optLong("bookId")
                        if (id != 0L && seenIds.add(id)) {
                            merged.put(drama)
                        }
                    }
                    
                    val matchedCategory = DramaCategories.ALL.find { 
                        it.equals(keyword, ignoreCase = true) || 
                        it.contains(keyword, ignoreCase = true) ||
                        keyword.contains(it, ignoreCase = true)
                    }
                    
                    if (matchedCategory != null) {
                        val categoryList = when (matchedCategory) {
                            "Trending" -> {
                                val resTrend = DramaBoxApi.trendings()
                                resTrend.optJSONObject("data")?.optJSONArray("rankList") ?: JSONArray()
                            }
                            "Dub in" -> {
                                val resDub = DramaBoxApi.dubindo()
                                resDub.optJSONObject("data")?.optJSONObject("classifyBookList")?.optJSONArray("records") ?: JSONArray()
                            }
                            else -> {
                                val id = (DramaCategories.ALL.indexOf(matchedCategory) - 1).toString()
                                val catRes = DramaBoxApi.categorySearch(id)
                                val classifyData = catRes.optJSONObject("data")?.optJSONObject("classifyBookList")
                                classifyData?.optJSONArray("records") ?: JSONArray()
                            }
                        }
                        for (i in 0 until categoryList.length()) {
                            val drama = categoryList.getJSONObject(i)
                            val id = drama.optLong("bookId")
                            if (id != 0L && seenIds.add(id)) merged.put(drama)
                        }
                    }
                }

                if (rsRes != null && rsRes.optBoolean("success", false)) {
                    val results = rsRes.optJSONArray("results") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val sId = item.optString("bookId", item.optString("book_id", item.optString("t_book_id")))
                        if (sId.isNotEmpty()) {
                            val derivedId = sId.hashCode().toLong()
                            if (seenIds.add(derivedId)) {
                                merged.put(JSONObject().apply {
                                    put("bookId", derivedId)
                                    put("realBookId", sId)
                                    put("bookName", item.optString("title", item.optString("book_title", item.optString("bookName"))))
                                    put("cover", item.optString("cover", item.optString("book_pic", item.optString("coverUrl"))))
                                    put("chapterCount", item.optInt("chapterCount", item.optInt("chapter_count", item.optInt("totalEpisodes"))))
                                    put("is_reelshort", true)
                                })
                            }
                        }
                    }
                }
                
                _dramas.value = sanitizeDramas(merged)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$keyword'", e)
        }
        _loading.value = false
    }

    fun loadMore() {
        if (_loading.value || _isEndReached.value) return
        
        viewModelScope.launch {
            _loading.value = true
            val pageToLoad = currentPage + 1
            
            try {
                val list = if (_currentSearchKeyword != null) {
                    val keyword = _currentSearchKeyword!!
                    val matchedCategory = DramaCategories.ALL.find { 
                        it.equals(keyword, ignoreCase = true) || 
                        it.contains(keyword, ignoreCase = true) ||
                        keyword.contains(it, ignoreCase = true)
                    }
                    
                    if (matchedCategory != null) {
                        when (matchedCategory) {
                            "Trending" -> {
                                val resTrend = DramaBoxApi.trendings(pageToLoad)
                                resTrend.optJSONObject("data")?.optJSONArray("rankList") ?: JSONArray()
                            }
                            "Dub in" -> {
                                val resDub = DramaBoxApi.dubindo(page = pageToLoad)
                                resDub.optJSONObject("data")?.optJSONObject("classifyBookList")?.optJSONArray("records") ?: JSONArray()
                            }
                            else -> {
                                val id = (DramaCategories.ALL.indexOf(matchedCategory) - 1).toString()
                                val catRes = DramaBoxApi.categorySearch(id, pageToLoad)
                                val classifyData = catRes.optJSONObject("data")?.optJSONObject("classifyBookList")
                                classifyData?.optJSONArray("records") ?: JSONArray()
                            }
                        }
                    } else {
                        JSONArray()
                    }
                } else {
                    val tab = _currentTab.value
                    val res = when (tab) {
                        "explore" -> DramaBoxApi.latest(pageToLoad)
                        else -> null
                    }
                    
                    if (res != null) {
                        val data = res.optJSONObject("data")
                        val mergedList = JSONArray()
                        val seenForPage = mutableSetOf<Long>()
                        if (data != null) {
                            val keys = listOf("rankList", "recommendList", "classifyBookList", "newTheaterList")
                            keys.forEach { key ->
                                val arr = when (key) {
                                    "rankList" -> data.optJSONArray(key)
                                    else -> data.optJSONObject(key)?.optJSONArray("records")
                                }
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        val drama = arr.optJSONObject(i) ?: continue
                                        val id = drama.optLong("bookId")
                                        if (id != 0L && seenForPage.add(id)) {
                                            mergedList.put(drama)
                                        }
                                    }
                                }
                            }
                        }
                        mergedList
                    } else {
                        JSONArray()
                    }
                }
                
                if (list.length() == 0) {
                    if (_currentSearchKeyword == null && _currentTab.value == "explore") {
                        try {
                            val supRes = DramaBoxApi.foryou(pageToLoad)
                            val supData = supRes.optJSONObject("data")
                            val supRecs = supData?.optJSONObject("recommendList")?.optJSONArray("records")
                            if (supRecs != null) {
                                val currentList = _dramas.value
                                val merged = JSONArray()
                                val seenIds = mutableSetOf<Long>()
                                for (i in 0 until currentList.length()) {
                                    val drama = currentList.getJSONObject(i)
                                    val id = drama.optLong("bookId")
                                    if (id != 0L && seenIds.add(id)) merged.put(drama)
                                }
                                var newCount = 0
                                for (i in 0 until supRecs.length()) {
                                    val drama = supRecs.optJSONObject(i) ?: continue
                                    val id = drama.optLong("bookId")
                                    if (id != 0L && seenIds.add(id)) {
                                        merged.put(drama)
                                        newCount++
                                    }
                                }
                                if (newCount > 0) {
                                    _dramas.value = merged
                                    currentPage = pageToLoad
                                    _loading.value = false
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Supplemental 'for you' pagination failed", e)
                        }
                    }
                    _isEndReached.value = true
                } else {
                    val sanitized = sanitizeDramas(list)
                    val currentList = _dramas.value
                    val merged = JSONArray()
                    val seenIds = mutableSetOf<Long>()

                    for (i in 0 until currentList.length()) {
                        val drama = currentList.getJSONObject(i)
                        val id = drama.optLong("bookId")
                        if (id != 0L && seenIds.add(id)) merged.put(drama)
                    }

                    var newCount = 0
                    for (i in 0 until sanitized.length()) {
                        val drama = sanitized.getJSONObject(i)
                        val id = drama.optLong("bookId")
                        if (id != 0L && seenIds.add(id)) {
                            merged.put(drama)
                            newCount++
                        }
                    }

                    if (newCount > 0) {
                        _dramas.value = merged
                        currentPage = pageToLoad
                    } else if (_currentSearchKeyword == null && _currentTab.value == "explore") {
                        try {
                            val supRes = DramaBoxApi.foryou(pageToLoad)
                            val supData = supRes.optJSONObject("data")
                            val supRecs = supData?.optJSONObject("recommendList")?.optJSONArray("records")
                            if (supRecs != null) {
                                var supNewCount = 0
                                for (i in 0 until supRecs.length()) {
                                    val drama = supRecs.optJSONObject(i) ?: continue
                                    val id = drama.optLong("bookId")
                                    if (id != 0L && seenIds.add(id)) {
                                        merged.put(drama)
                                        supNewCount++
                                    }
                                }
                                if (supNewCount > 0) {
                                    _dramas.value = merged
                                    currentPage = pageToLoad
                                } else {
                                    _isEndReached.value = true
                                }
                            } else {
                                _isEndReached.value = true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Supplemental 'for you' pagination failed", e)
                            _isEndReached.value = true
                        }
                    } else {
                        _isEndReached.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore failed", e)
            }
            _loading.value = false
        }
    }

    suspend fun selectDrama(drama: JSONObject, forceRefresh: Boolean = false) {
        _selectedDrama.value = drama
        val bookId = drama.optLong("bookId")

        if (drama.optBoolean("is_reelshort", false)) {
            val realBookId = drama.optString("realBookId")
            _loading.value = true
            try {
                val res = reelShortApi.detail(realBookId)
                if (res.optBoolean("success", false)) {
                    val chapters = res.optJSONArray("chapters") ?: JSONArray()
                    val mappedEps = JSONArray()
                    for (i in 0 until chapters.length()) {
                        val ch = chapters.getJSONObject(i) ?: continue
                        mappedEps.put(JSONObject().apply {
                            put("chapterIndex", ch.optInt("index") - 1)
                            put("chapterName", ch.optString("title"))
                            put("realChapterId", ch.optString("chapterId"))
                            put("chapterId", ch.optString("chapterId").hashCode().toLong())
                            put("realBookId", realBookId)
                            put("is_reelshort", true)
                            put("isLocked", ch.optBoolean("isLocked", false))
                            // Episode still frame, used by the episode row artwork.
                            put(
                                "chapterImg",
                                ch.optString("videoPic").ifEmpty { ch.optString("thumbnailUrl") }
                            )
                            put("allVideoPaths", JSONArray())
                            put("videoPath", "")
                        })
                    }
                    val description = res.optString("description")
                    val updatedDrama = JSONObject(drama.toString()).apply {
                        put("introduction", description)
                        put("chapterCount", chapters.length())
                    }
                    _selectedDramaDetail.value = updatedDrama
                    _episodes.value = filterEpisodes(mappedEps)
                    _reelShortError.value = null
                    _episodesError.value = null
                } else {
                    _reelShortError.value = "ReelShort is temporarily unavailable"
                    _episodesError.value = "Couldn't load episodes. Check your connection and try again."
                }
            } catch (_: Exception) {
                _reelShortError.value = "ReelShort is temporarily unavailable"
                _episodesError.value = "Couldn't load episodes. Check your connection and try again."
            }
            _loading.value = false
            return
        }

        if (!forceRefresh) {
            val cachedEps = CacheManager.get("eps_$bookId") as? JSONArray
            // An empty cached list is a failed fetch, never a valid answer.
            if (cachedEps != null && cachedEps.length() > 0) {
                _episodes.value = filterEpisodes(cachedEps)
                _episodesError.value = null
                // Still fetch detail if missing
                if (_selectedDramaDetail.value == null) {
                    val detailRes = DramaBoxApi.detail(bookId)
                    _selectedDramaDetail.value = detailRes.optJSONObject("data")
                }
                return
            }
        }

        _loading.value = true
        try {
            // Fetch detail and episodes in parallel
            val detailRes = DramaBoxApi.detail(bookId)
            _selectedDramaDetail.value = detailRes.optJSONObject("data")
            
            val limit = if (_isUnlocked.value) 0 else 7
            val epsRes = EpisodeFetcher.scrapeProcess(bookId, limit)
            _episodes.value = filterEpisodes(epsRes)
            if (epsRes.length() == 0) {
                _episodesError.value = "Couldn't load episodes. Check your connection and try again."
            } else {
                _episodesError.value = null
                // Only a successful list is worth caching, an empty one would
                // keep the drama empty on every later visit.
                CacheManager.put("eps_$bookId", epsRes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _episodesError.value = "Couldn't load episodes. Check your connection and try again."
        }
        _loading.value = false
    }
    
    fun backToList() {
        _selectedDrama.value = null
        _selectedDramaDetail.value = null
        _episodes.value = JSONArray()
        _episodesError.value = null
    }

    fun setPipMode(active: Boolean) {
        _pipMode.value = active
    }

    fun setPlayingEpisode(episode: JSONObject?) {
        _playingEpisode.value = episode
        if (episode != null &&
            episode.optBoolean("is_reelshort", false) &&
            episode.optJSONArray("allVideoPaths")?.length() == 0
        ) {
            viewModelScope.launch { fetchReelShortEpisode(episode) }
        }
    }

    private fun fetchReelShortEpisode(ep: JSONObject) {
        val realBookId = ep.optString("realBookId")
        val realChapterId = ep.optString("realChapterId")
        if (realBookId.isEmpty() || realChapterId.isEmpty()) return
        viewModelScope.launch {
            try {
                val res = reelShortApi.episodeByChapter(realBookId, realChapterId, true)
                if (!res.optBoolean("success", false)) {
                    markEpisodeUnavailable(ep, res.optString("message", "This episode is unavailable right now."))
                    return@launch
                }
                val videoList = res.optJSONArray("videoList") ?: JSONArray()
                if (videoList.length() == 0) {
                    markEpisodeUnavailable(
                        ep,
                        res.optString(
                            "errorReason",
                            if (res.optBoolean("isLocked", false)) "This episode is locked. Unlock to watch it."
                            else "This episode is not available for playback right now."
                        ),
                        res.optBoolean("isLocked", false)
                    )
                    return@launch
                }

                val updated = JSONObject()
                val keys = ep.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    updated.put(k, ep.get(k))
                }
                updated.put("isLocked", false)
                updated.put("playbackError", "")
                val art = res.optString("videoPic").ifEmpty { res.optString("thumbnailUrl") }
                if (art.isNotEmpty()) updated.put("chapterImg", art)
                applyReelShortVideoPaths(updated, videoList)

                val current = _episodes.value
                val newArr = JSONArray()
                for (i in 0 until current.length()) {
                    val item = current.optJSONObject(i)
                    if (item != null && item.optString("realChapterId") == realChapterId) {
                        newArr.put(updated)
                    } else if (item != null) {
                        newArr.put(item)
                    }
                }
                _episodes.value = newArr

                if (_playingEpisode.value?.optString("realChapterId") == realChapterId) {
                    _playingEpisode.value = updated
                }
                _reelShortError.value = null
            } catch (_: Exception) {
                markEpisodeUnavailable(ep, "This episode is unavailable right now.")
            }
        }
    }

    /**
     * Flags an episode that cannot be played (locked, not yet released, upstream error)
     * so the UI can explain it instead of showing an endless loading spinner.
     */
    private fun markEpisodeUnavailable(ep: JSONObject, reason: String, locked: Boolean = false) {
        val realChapterId = ep.optString("realChapterId")
        val updated = JSONObject()
        val keys = ep.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            updated.put(k, ep.get(k))
        }
        updated.put("isLocked", locked || ep.optBoolean("isLocked", false))
        updated.put("playbackError", reason)
        updated.put("videoPath", "")
        updated.put("videoPaths", JSONArray())

        val current = _episodes.value
        val newArr = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i)
            if (item != null && item.optString("realChapterId") == realChapterId) {
                newArr.put(updated)
            } else if (item != null) {
                newArr.put(item)
            }
        }
        _episodes.value = newArr
        if (_playingEpisode.value?.optString("realChapterId") == realChapterId) {
            _playingEpisode.value = updated
        }
        Log.w(TAG, "Episode $realChapterId unavailable: $reason")
    }

    fun checkForUpdatesAuto(currentVersion: String) {
        viewModelScope.launch {
            val latest = checkUpdate()
            if (latest != null) {
                val latestTag = latest.optString("tag_name").removePrefix("v")
                val currentUpdateTag = _updateAvailable.value?.optString("tag_name")?.removePrefix("v")
                if (latestTag != currentVersion && latestTag != currentUpdateTag) {
                    _updateAvailable.value = latest
                } else if (latestTag == currentVersion) {
                    _updateAvailable.value = null
                }
            } else {
                _updateAvailable.value = null
            }
        }
    }

    suspend fun checkUpdate(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/iskoaxie22t03-lang/DramaThonPlus/releases/latest")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                return@withContext JSONObject(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun retryReelShort() {
        viewModelScope.launch {
            _reelShortError.value = null
            val result = runCatching { reelShortApi.homepage() }
            if (result.getOrNull()?.optBoolean("success", false) != true) {
                _reelShortError.value = "ReelShort is temporarily unavailable"
            }
        }
    }
}
