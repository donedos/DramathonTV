package com.dramathon.dramathonplus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dramathon.dramathonplus.client.CacheManager
import com.dramathon.dramathonplus.client.DramaBoxApi
import com.dramathon.dramathonplus.client.DramaCategories
import com.dramathon.dramathonplus.client.EpisodeFetcher
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import android.provider.Settings
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
    private val client = OkHttpClient()
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
    
    private val _currentTab = MutableStateFlow("latest")
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

    private val _globalUserCount = MutableStateFlow(0)
    val globalUserCount: StateFlow<Int> = _globalUserCount

    private val _liveStatus = MutableStateFlow(true)
    val liveStatus: StateFlow<Boolean> = _liveStatus

    private val _isVersionKilled = MutableStateFlow(false)
    val isVersionKilled: StateFlow<Boolean> = _isVersionKilled

    var currentPage = 1
        private set
    
    private val _isEndReached = MutableStateFlow(false)
    val isEndReached: StateFlow<Boolean> = _isEndReached

    private var _currentSearchKeyword: String? = null

    private var codeRef: DatabaseReference? = null
    private var codeListener: ValueEventListener? = null

    init {
        viewModelScope.launch {
            loadTab("latest")
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

    private fun monitorLiveStatus() {
        val db = FirebaseDatabase.getInstance(RTDB_URL)
        
        // Listen to global live switch
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

        // Listen to version-specific kill switches
        db.getReference("killed_versions").child(BuildConfig.VERSION_NAME.replace(".", "_"))
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
            val name = drama.optString("bookName")
            val cover = drama.optString("cover").ifEmpty { 
                drama.optString("coverUrl").ifEmpty { 
                    drama.optString("coverWap") 
                } 
            }
            // Only include if it has a name and a cover image
            if (name.isNotEmpty() && cover.isNotEmpty()) {
                filtered.put(drama)
            }
        }
        return filtered
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
            if (cached != null) {
                _dramas.value = cached
                return
            }
        }

        _loading.value = true
        try {
            val res = when (tab) {
                "latest" -> DramaBoxApi.latest()
                "trending" -> DramaBoxApi.trendings()
                "foryou" -> DramaBoxApi.foryou()
                "vip" -> DramaBoxApi.vip()
                "dubindo" -> DramaBoxApi.dubindo()
                else -> DramaBoxApi.latest()
            }
            
            val data = res.optJSONObject("data")
            val list = JSONArray()
            val seenIds = mutableSetOf<Long>()
            
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

            // Improve density by merging with foryou and randomDrama if initial results are low
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
                } catch (_: Exception) {}
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
                } catch (_: Exception) {}
            }

            val sanitized = sanitizeDramas(list)
            _dramas.value = sanitized
            CacheManager.put("tab_$tab", sanitized)
            
            // Trigger load more if density is still low
            if (sanitized.length() < 30 && sanitized.length() > 0) {
                loadMore()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _dramas.value = JSONArray()
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
            // 1. Regular search (by title/keyword)
            val res = DramaBoxApi.search(keyword)
            val suggestList = res.optJSONObject("data")?.optJSONArray("suggestList") ?: JSONArray()
            
            // 2. Category search if keyword matches or contains a known category
            val matchedCategory = DramaCategories.ALL.find { 
                it.equals(keyword, ignoreCase = true) || 
                it.contains(keyword, ignoreCase = true) ||
                keyword.contains(it, ignoreCase = true)
            }
            
            val categoryList = if (matchedCategory != null) {
                when (matchedCategory) {
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
            } else {
                JSONArray()
            }

            // Merge results
            val merged = JSONArray()
            val seenIds = mutableSetOf<Long>()
            
            // Prioritize results that have the keyword in their title or are from the matched category
            for (i in 0 until suggestList.length()) {
                val drama = suggestList.getJSONObject(i)
                val id = drama.optLong("bookId")
                if (id != 0L && seenIds.add(id)) {
                    merged.put(drama)
                }
            }
            
            for (i in 0 until categoryList.length()) {
                val drama = categoryList.getJSONObject(i)
                val id = drama.optLong("bookId")
                if (id != 0L && seenIds.add(id)) {
                    merged.put(drama)
                }
            }
            
            _dramas.value = sanitizeDramas(merged)
        } catch (e: Exception) {
            e.printStackTrace()
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
                        "latest" -> DramaBoxApi.latest(pageToLoad)
                        "trending" -> DramaBoxApi.trendings(pageToLoad)
                        "foryou" -> DramaBoxApi.foryou(pageToLoad)
                        "dubindo" -> DramaBoxApi.dubindo(page = pageToLoad)
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
                    if (_currentSearchKeyword == null && _currentTab.value != "foryou") {
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
                        } catch (_: Exception) {}
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
                    } else {
                        _isEndReached.value = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _loading.value = false
        }
    }

    suspend fun selectDrama(drama: JSONObject, forceRefresh: Boolean = false) {
        _selectedDrama.value = drama
        val bookId = drama.optLong("bookId")

        if (!forceRefresh) {
            val cachedEps = CacheManager.get("eps_$bookId") as? JSONArray
            if (cachedEps != null) {
                _episodes.value = filterEpisodes(cachedEps)
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
            CacheManager.put("eps_$bookId", epsRes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _loading.value = false
    }
    
    fun backToList() {
        _selectedDrama.value = null
        _selectedDramaDetail.value = null
        _episodes.value = JSONArray()
    }

    fun setPipMode(active: Boolean) {
        _pipMode.value = active
    }

    fun setPlayingEpisode(episode: JSONObject?) {
        _playingEpisode.value = episode
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
}
