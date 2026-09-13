package com.dramathon.dramathonplus.server

import com.dramathon.dramathonplus.client.DramaBoxApi
import com.dramathon.dramathonplus.client.EpisodeFetcher
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class ApiServer(port: Int) : NanoHTTPD("127.0.0.1", port) {
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri.startsWith("/api/dramabox/") -> handleApi(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun handleApi(session: IHTTPSession): Response {
        val path = session.uri.removePrefix("/api/dramabox/")
        val params = session.parameters
        
        return runBlocking {
            try {
                val result = when (path) {
                    "latest" -> DramaBoxApi.latest()
                    "search" -> {
                        val keyword = params["keyword"]?.firstOrNull() ?: ""
                        DramaBoxApi.search(keyword)
                    }
                    "trendings" -> DramaBoxApi.trendings()
                    "foryou" -> DramaBoxApi.foryou()
                    "vip" -> DramaBoxApi.vip()
                    "dubindo" -> DramaBoxApi.dubindo()
                    "populersearch" -> DramaBoxApi.populersearch()
                    "randomdrama" -> DramaBoxApi.randomDrama()
                    "detail" -> {
                        val bookId = params["bookId"]?.firstOrNull()?.toLongOrNull() ?: 0L
                        DramaBoxApi.detail(bookId)
                    }
                    "allepisode" -> {
                        val bookId = params["bookId"]?.firstOrNull()?.toLongOrNull() ?: 0L
                        val eps = EpisodeFetcher.scrapeProcess(bookId)
                        JSONObject().apply {
                            put("success", true)
                            put("data", eps)
                        }
                    }
                    else -> JSONObject().apply { 
                        put("success", false)
                        put("message", "Unknown endpoint: $path")
                    }
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message)
            }
        }
    }
}
