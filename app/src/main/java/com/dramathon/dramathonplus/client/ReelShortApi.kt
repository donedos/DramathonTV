package com.dramathon.dramathonplus.client

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ReelShortApi(
    private val session: ReelShortSession,
    private val client: ReelShortClient
) {
    private companion object {
        const val TAG = "ReelShortApi"
    }

    private fun dataOf(result: JSONObject): JSONObject? {
        return if (result.optInt("code") == 0) result.optJSONObject("data") else null
    }

    private fun errorResult(result: JSONObject): JSONObject = JSONObject().apply {
        put("success", false)
        put("message", result.optString("msg", result.optString("message")))
    }

    private fun errorResult(message: String): JSONObject = JSONObject().apply {
        put("success", false)
        put("message", message)
    }

    suspend fun homepage(): JSONObject {
        session.ensureReady()
        val bodyParams = linkedMapOf(
            "abtest_group" to "newHall_4000453",
            "action_type" to "100",
            "is_no_first_req" to "1",
            "no_continue_watch" to "1",
            "source_switch" to "0",
            "tab_id" to "0",
            "widescreen" to "0"
        )
        val result = client.request("/api/ms/hall/infoV4", bodyParams)
        val data = dataOf(result)
        return if (data != null) {
            JSONObject().apply {
                put("success", true)
                put("data", data)
            }
        } else {
            errorResult(result)
        }
    }

    suspend fun detail(bookId: String): JSONObject {
        session.ensureReady()
        val result = client.request(
            "/api/video/book/getBookDetailV2",
            mapOf("book_id" to bookId, "from" to "0", "play_details" to "0")
        )
        val data = dataOf(result)
        if (data != null) {
            val book = data.optJSONObject("retBook") ?: JSONObject()
            val chapterList = data.optJSONObject("chapterList") ?: JSONObject()
            val chapters = chapterList.optJSONArray("chapter_lists")
                ?: data.optJSONArray("chapter_lists") ?: JSONArray()
            Log.d(
                TAG,
                "ReelShort detail $bookId: data keys=${data.keys().asSequence().toList()} " +
                    "chapterList keys=${chapterList.keys().asSequence().toList()} chapters=${chapters.length()}"
            )
            return JSONObject().apply {
                put("success", true)
                put("bookId", book.optString("book_id", book.optString("t_book_id")))
                put("title", book.optString("book_title"))
                put("cover", book.optString("book_pic"))
                put("description", book.optString("special_desc"))
                put("totalEpisodes", chapters.length())
                put("chapters", JSONArray().apply {
                    for (index in 0 until chapters.length()) {
                        val chapter = chapters.optJSONObject(index) ?: continue
                        put(JSONObject().apply {
                            put("index", index + 1)
                            put("chapterId", chapter.optString("chapter_id"))
                            put("title", chapter.optString("chapter_title", "Episode ${index + 1}"))
                            put("isLocked", chapter.optInt("is_lock") == 1)
                            // Episode artwork: the chapter list carries a still frame per episode.
                            put("videoPic", chapter.optString("video_pic"))
                            put("thumbnailUrl", chapter.optString("thumbnail_url"))
                        })
                    }
                })
            }
        }
        return errorResult(result)
    }

    suspend fun episodeByChapter(
        bookId: String,
        chapterId: String,
        allowAutoUpgrade: Boolean
    ): JSONObject {
        session.ensureReady()
        val result = client.request(
            "/api/video/book/getChapterContent",
            mapOf(
                "book_id" to bookId,
                "chapter_id" to chapterId,
                "is_hand_pay" to "0",
                "is_wait_free_unlock" to "0",
                "set_auto" to if (allowAutoUpgrade) "1" else "0"
            )
        )
        val data = dataOf(result)
        if (data != null) {
            val isLocked = data.optInt("is_lock") == 1
            val vipStatus = data.optInt("vip_status", 0)
            val videoList = JSONArray()
            val playInfo = data.optString("play_info")
            if (playInfo.isNotEmpty()) {
                val decrypted = try {
                    client.decryptPlayUrl(playInfo, session.current())
                } catch (error: Exception) {
                    Log.w(TAG, "play_info decrypt failed for $bookId/$chapterId: ${error.message}")
                    ""
                }
                if (decrypted.isNotBlank()) {
                    val parsed = try {
                        JSONArray(decrypted.trim())
                    } catch (error: Exception) {
                        Log.w(TAG, "play_info parse failed for $bookId/$chapterId: ${error.message}")
                        JSONArray()
                    }
                    for (index in 0 until parsed.length()) {
                        val item = parsed.optJSONObject(index) ?: continue
                        val url = item.optString("PlayURL").replace("\\/", "/")
                        if (url.isNotEmpty()) {
                            videoList.put(JSONObject().apply {
                                put("url", url)
                                put("encode", item.optString("Encode"))
                                put("quality", resolveQuality(item))
                                put("bitrate", item.optString("Bitrate"))
                            })
                        }
                    }
                }
            }
            val playable = videoList.length() > 0
            return JSONObject().apply {
                put("success", true)
                put("isLocked", isLocked)
                put("vipStatus", vipStatus)
                put("videoPic", data.optString("video_pic"))
                put("thumbnailUrl", data.optString("thumbnail_url"))
                put("playable", playable)
                if (!playable) {
                    put(
                        "errorReason",
                        if (isLocked) "This episode is locked. Unlock to watch it."
                        else "This episode is not available for playback right now."
                    )
                }
                put("videoList", videoList)
            }
        }
        return errorResult(result)
    }

    /** Upstream reports the rendition size under different keys; normalise it to a height. */
    private fun resolveQuality(item: JSONObject): Int {
        val dpi = item.optInt("Dpi", 0).let { if (it > 0) it else item.optInt("dpi", 0) }
        val quality = item.optInt("Quality", 0).let { if (it > 0) it else item.optInt("quality", 0) }
        val height = item.optInt("Height", 0).let { if (it > 0) it else item.optInt("height", 0) }
        val definition = item.optInt("Definition", 0).let { if (it > 0) it else item.optInt("definition", 0) }
        val raw = when {
            dpi > 0 -> dpi
            quality > 0 -> quality
            height > 0 -> height
            definition > 0 -> definition
            else -> 0
        }
        return when (raw) {
            1 -> 360
            2 -> 540
            3 -> 720
            4 -> 1080
            else -> raw
        }
    }

    suspend fun search(keyword: String, page: Int = 1): JSONObject {
        session.ensureReady()
        val result = client.request(
            "/api/video/search/search",
            mapOf("word" to keyword, "page" to page.toString(), "limit" to "10")
        )
        val data = dataOf(result)
        if (data != null) {
            val items = data.optJSONArray("lists") ?: data.optJSONArray("list") ?: JSONArray()
            return JSONObject().apply {
                put("success", true)
                put("keyword", keyword)
                put("page", page)
                put("total", data.optInt("total", items.length()))
                put("results", JSONArray().apply {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        put(JSONObject().apply {
                            put("bookId", item.optString("book_id", item.optString("t_book_id")))
                            put("title", item.optString("book_title"))
                            put("cover", item.optString("book_pic"))
                            put("description", item.optString("special_desc"))
                            put("chapterCount", item.optInt("chapter_count"))
                            put("tag", item.optString("tag"))
                        })
                    }
                })
            }
        }
        return errorResult(result)
    }
}
