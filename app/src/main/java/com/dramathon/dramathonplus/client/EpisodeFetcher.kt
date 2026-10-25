package com.dramathon.dramathonplus.client

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

object EpisodeFetcher {
    private const val TAG = "EpisodeFetcher"

    suspend fun scrapeProcess(bookId: Long, limit: Int = 0): JSONArray {
        var savedPayChapterNum = 0
        val result = mutableMapOf<Long, JSONObject>() // chapterId -> JSONObject

        suspend fun fetchBatch(idx: Int, isRetry: Boolean = false): JSONObject? {
            val payload = JSONObject().apply {
                put("boundaryIndex", 0)
                put("comingPlaySectionId", -1)
                put("index", idx)
                put("currencyPlaySourceName", "首页发现_Untukmu_推荐列表")
                put("rid", "")
                put("enterReaderChapterIndex", 0)
                put("loadDirection", 1)
                put("startUpKey", "10942710-5e9e-48f2-8927-7c387e6f5fac")
                put("bookId", bookId)
                put("currencyPlaySource", "discover_175_rec")
                put("needEndRecommend", 0)
                put("preLoad", false)
                put("pullCid", "")
            }

            val res = DramaBoxClient.post("/drama-box/chapterv2/batch/load", payload)
            val data = res.optJSONObject("data")
            
            if (data == null) {
                // The request itself failed, refreshing the token would not help.
                Log.w(TAG, "Chapter request failed for idx $idx: ${res.optString("message")}")
                return null
            }
            if (!data.has("chapterList") || data.getJSONArray("chapterList").length() == 0) {
                if (idx != savedPayChapterNum && !isRetry) {
                    Log.w(TAG, "Empty data for idx $idx. Retrying with token refresh...")
                    TokenManager.bootstrap()
                    if (savedPayChapterNum != 0 && idx != savedPayChapterNum) {
                        fetchBatch(savedPayChapterNum, true)
                        delay(1000)
                    }
                    delay(1500)
                    return fetchBatch(idx, true)
                }
            }
            return data
        }

        val firstBatch = fetchBatch(1)
        if (firstBatch != null) {
            val totalChapters = firstBatch.optInt("chapterCount")
            savedPayChapterNum = firstBatch.optInt("payChapterNum", 0)
            
            val chapters = firstBatch.optJSONArray("chapterList") ?: JSONArray()
            for (i in 0 until chapters.length()) {
                val c = chapters.getJSONObject(i)
                result[c.optLong("chapterId")] = c
            }

            if (!(limit > 0 && result.size >= limit) && totalChapters > 5) {
                // Parallel fetching for speed
                val batchIndexes = (6..totalChapters step 5).toList()
                
                coroutineScope {
                    // Fetch in chunks of 5 parallel requests to balance speed and stability
                    batchIndexes.chunked(5).forEach { chunk ->
                        if (limit > 0 && result.size >= limit) return@forEach
                        
                        val deferreds = chunk.map { idx ->
                            async(Dispatchers.IO) {
                                fetchBatch(idx)
                            }
                        }
                        
                        deferreds.forEach { deferred ->
                            try {
                                val batchData = deferred.await()
                                val batchChapters = batchData?.optJSONArray("chapterList")
                                if (batchChapters != null) {
                                    for (i in 0 until batchChapters.length()) {
                                        val c = batchChapters.getJSONObject(i)
                                        result[c.optLong("chapterId")] = c
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Parallel fetch failed", e)
                            }
                        }
                    }
                }
            }
        }

        val finalResult = result.values.toList()
            .sortedBy { it.optInt("chapterIndex") }
            .take(if (limit > 0) limit else Int.MAX_VALUE)

        val finalArray = JSONArray()
        finalResult.forEach { ep -> 
            val candidates = collectVideoPaths(ep)
            if (candidates.isNotEmpty()) {
                ep.put("videoPath", candidates.first())
            }
            // Fallback candidates, best first, used when the preferred rendition fails.
            ep.put("videoPaths", JSONArray(candidates))
            ep.put(
                "isLocked",
                ep.optInt("isCharge") == 1 || ep.optBoolean("chargeChapter", false)
            )
            finalArray.put(ep) 
        }
        return finalArray
    }

    /**
     * Collects every downloadable rendition of a chapter, ordered from the highest
     * quality down. Each quality lives on its own encrypted object, so a single
     * broken quality must not stop the episode from playing.
     */
    private fun collectVideoPaths(ep: JSONObject): List<String> {
        val cdnList = ep.optJSONArray("cdnList") ?: return listOf(ep.optString("videoPath")).filter { it.isNotEmpty() }
        val perCdn = mutableListOf<List<Pair<Int, String>>>()
        for (i in 0 until cdnList.length()) {
            val cdn = cdnList.optJSONObject(i) ?: continue
            val pathList = cdn.optJSONArray("videoPathList") ?: continue
            val entries = mutableListOf<Pair<Int, String>>()
            for (j in 0 until pathList.length()) {
                val pathObj = pathList.optJSONObject(j) ?: continue
                val url = pathObj.optString("videoPath")
                if (url.isNotEmpty()) entries.add(pathObj.optInt("quality") to url)
            }
            if (entries.isNotEmpty()) perCdn.add(entries.sortedByDescending { it.first })
        }

        val usable = perCdn.filter { cdn ->
            cdn.any { it.second.contains("dramaboxdb.com") || it.second.contains("dramabox.com") }
        }
        val pools = if (usable.isNotEmpty()) usable else perCdn

        val ordered = LinkedHashSet<String>()
        // Highest quality of every CDN first, then step down one quality at a time.
        val maxQuality = pools.maxOf { cdn -> cdn.first().first }
        for (quality in maxQuality downTo 1) {
            pools.forEach { cdn ->
                cdn.firstOrNull { it.first == quality }?.let { ordered.add(it.second) }
            }
        }
        if (ordered.isEmpty()) ep.optString("videoPath").takeIf { it.isNotEmpty() }?.let { ordered.add(it) }
        return ordered.toList()
    }

    private fun pickVideoPath(ep: JSONObject): String {
        return collectVideoPaths(ep).firstOrNull() ?: ep.optString("videoPath")
    }
}
