package com.dramathon.dramathonplus.client

import org.json.JSONObject
import kotlin.random.Random

object DramaBoxApi {
    suspend fun latest(page: Int = 1): JSONObject {
        val payload = JSONObject().apply {
            put("rankType", 3)
            put("pageNo", page)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/rank", payload)
    }

    suspend fun search(keyword: String): JSONObject {
        val payload = JSONObject().apply {
            put("keyword", keyword)
        }
        return DramaBoxClient.post("/drama-box/search/suggest", payload)
    }

    suspend fun trendings(page: Int = 1): JSONObject {
        val payload = JSONObject().apply {
            put("rankType", 1)
            put("pageNo", page)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/rank", payload)
    }

    suspend fun foryou(page: Int = 1): JSONObject {
        val payload = JSONObject().apply {
            put("isNeedRank", 1)
            put("specialColumnId", 0)
            put("pageNo", page)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/recommendBook", payload)
    }

    suspend fun vip(): JSONObject {
        val payload = JSONObject().apply {
            put("homePageStyle", 0)
            put("isNeedRank", 1)
            put("index", 4)
            put("type", 0)
            put("channelId", 205)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/theater", payload)
    }

    suspend fun dubindo(classify: String = "1", page: Int = 1): JSONObject {
        val typeList = org.json.JSONArray().apply {
            put(JSONObject().apply { put("type", 1); put("value", "") })
            put(JSONObject().apply { put("type", 2); put("value", "1") })
            put(JSONObject().apply { put("type", 3); put("value", "") })
            put(JSONObject().apply { put("type", 4); put("value", "") })
            put(JSONObject().apply { put("type", 5); put("value", classify) })
        }
        val payload = JSONObject().apply {
            put("typeList", typeList)
            put("showLabels", false)
            put("pageNo", page)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/classify", payload)
    }

    suspend fun populersearch(): JSONObject {
        val payload = JSONObject().apply {
            put("rankType", 2)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/rank", payload)
    }

    suspend fun randomDrama(): JSONObject {
        val payload = JSONObject().apply {
            put("pageNo", Random.nextInt(2) + 1)
            put("pageSize", 50)
            put("pageFlag", "")
            put("startType", 0)
            put("firstStartUp", false)
            put("channelId", 1) // default to some channel
        }
        return DramaBoxClient.post("/drama-box/he001/recommendChannel", payload)
    }

    suspend fun detail(bookId: Long): JSONObject {
        val payload = JSONObject().apply {
            put("bookId", bookId)
        }
        return DramaBoxClient.post("/drama-box/he001/reserveBookDetail", payload)
    }

    suspend fun categorySearch(classifyId: String, page: Int = 1): JSONObject {
        val typeList = org.json.JSONArray().apply {
            put(JSONObject().apply { put("type", 1); put("value", "") })
            put(JSONObject().apply { put("type", 2); put("value", "") })
            put(JSONObject().apply { put("type", 3); put("value", "") })
            put(JSONObject().apply { put("type", 4); put("value", "") })
            put(JSONObject().apply { put("type", 5); put("value", classifyId) })
        }
        val payload = JSONObject().apply {
            put("typeList", typeList)
            put("showLabels", false)
            put("pageNo", page)
            put("pageSize", 50)
        }
        return DramaBoxClient.post("/drama-box/he001/classify", payload)
    }
}
