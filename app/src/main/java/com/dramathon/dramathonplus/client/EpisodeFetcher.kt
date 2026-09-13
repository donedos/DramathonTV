package com.dramathon.dramathonplus.client

import android.util.Log
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
            
            if (data == null || !data.has("chapterList") || data.getJSONArray("chapterList").length() == 0) {
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

            if (limit > 0 && result.size >= limit) {
                // Limit reached after first batch
            } else {
                var currentIdx = 6
                var retryCount = 0
                var consecutiveSkips = 0
                val MAX_RETRIES = 3

                while (currentIdx <= totalChapters) {
                    if (limit > 0 && result.size >= limit) break
                    
                    val batchData = fetchBatch(currentIdx)
                    val batchChapters = batchData?.optJSONArray("chapterList")
                    
                    if (batchChapters != null && batchChapters.length() > 0) {
                        val receivedIndex = batchChapters.getJSONObject(0).optInt("chapterIndex")
                        if (receivedIndex >= (currentIdx - 5)) {
                            for (i in 0 until batchChapters.length()) {
                                val c = batchChapters.getJSONObject(i)
                                result[c.optLong("chapterId")] = c
                            }
                            currentIdx += 5
                            retryCount = 0
                            consecutiveSkips = 0
                        } else {
                            retryCount++
                            if (retryCount >= MAX_RETRIES) {
                                currentIdx += 5
                                retryCount = 0
                                consecutiveSkips++
                                if (consecutiveSkips >= 2) break
                            } else {
                                delay(2000)
                            }
                        }
                    } else {
                        retryCount++
                        if (retryCount >= MAX_RETRIES) {
                            currentIdx += 5
                            retryCount = 0
                            consecutiveSkips++
                            if (consecutiveSkips >= 2) break
                        } else {
                            delay(2000)
                        }
                    }
                    delay(800)
                }
            }
        }

        val finalResult = result.values.toList()
            .sortedBy { it.optInt("chapterIndex") }
            .take(if (limit > 0) limit else Int.MAX_VALUE)

        val finalArray = JSONArray()
        finalResult.forEach { ep -> 
            val pickedPath = pickVideoPath(ep)
            if (pickedPath.isNotEmpty()) {
                ep.put("videoPath", pickedPath)
            }
            finalArray.put(ep) 
        }
        return finalArray
    }

    private fun pickVideoPath(ep: JSONObject): String {
        val cdnList = ep.optJSONArray("cdnList") ?: return ep.optString("videoPath")
        val allPaths = mutableListOf<JSONObject>()
        val seenQualities = mutableSetOf<Int>()

        for (i in 0 until cdnList.length()) {
            val cdn = cdnList.getJSONObject(i)
            val pathList = cdn.optJSONArray("videoPathList") ?: continue
            for (j in 0 until pathList.length()) {
                val pathObj = pathList.getJSONObject(j)
                val quality = pathObj.optInt("quality")
                if (!seenQualities.contains(quality)) {
                    seenQualities.add(quality)
                    allPaths.add(pathObj)
                }
            }
        }

        val usablePaths = allPaths.filter {
            val url = it.optString("videoPath")
            url.contains("dramaboxdb.com") || url.contains("dramabox.com")
        }

        val finalPaths = if (usablePaths.isNotEmpty()) usablePaths else allPaths
        return finalPaths.maxByOrNull { it.optInt("quality") }?.optString("videoPath") ?: ep.optString("videoPath")
    }
}
