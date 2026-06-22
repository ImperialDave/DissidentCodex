package com.codex.app.utils

import android.util.Log
import com.codex.app.BuildConfig
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GiphyApi {

    private const val TAG = "GiphyApi"
    private const val BASE = "https://api.giphy.com/v1/gifs"
    private val functions = Firebase.functions

    data class GifItem(val id: String, val previewUrl: String, val fullUrl: String)

    sealed class SearchResult {
        data class Success(val items: List<GifItem>) : SearchResult()
        data class Error(val message: String) : SearchResult()
        data object NotConfigured : SearchResult()
    }

    fun isConfigured(): Boolean = Firebase.auth.currentUser != null

    suspend fun search(query: String, limit: Int = 24): SearchResult {
        if (Firebase.auth.currentUser == null) return SearchResult.NotConfigured

        // Debug builds: prefer direct Giphy when a local key is configured (avoids
        // Cloud Function / App Check permission errors during development).
        if (BuildConfig.DEBUG && BuildConfig.GIPHY_API_KEY.isNotBlank()) {
            return searchDirect(query, limit)
        }

        val cloudResult = searchViaCloudFunction(query, limit)
        if (cloudResult !is SearchResult.NotConfigured) return cloudResult

        if (BuildConfig.DEBUG && BuildConfig.GIPHY_API_KEY.isNotBlank()) {
            Log.w(TAG, "Cloud Function unavailable — using debug-only direct Giphy fallback")
            return searchDirect(query, limit)
        }
        return SearchResult.NotConfigured
    }

    private suspend fun searchViaCloudFunction(query: String, limit: Int): SearchResult {
        return try {
            val payload = hashMapOf(
                "query" to query.trim(),
                "limit" to limit.coerceIn(1, 50)
            )
            val result = functions.getHttpsCallable("searchGiphy").call(payload).await()
            val data = result.getData()
            when (data) {
                is List<*> -> SearchResult.Success(parseItems(data))
                is Map<*, *> -> {
                    val items = data["items"]
                    if (items is List<*>) SearchResult.Success(parseItems(items))
                    else SearchResult.Error("Unexpected Giphy response")
                }
                else -> SearchResult.Error("Unexpected Giphy response")
            }
        } catch (e: FirebaseFunctionsException) {
            Log.w(TAG, "searchGiphy failed: ${e.code}", e)
            when (e.code) {
                FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    SearchResult.NotConfigured
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    SearchResult.Error("Sign in required for GIF search.")
                else -> SearchResult.Error(e.message ?: "GIF search failed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud Giphy request failed", e)
            SearchResult.NotConfigured
        }
    }

    private suspend fun searchDirect(query: String, limit: Int): SearchResult =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val endpoint = if (encoded.isBlank()) {
                "$BASE/trending?api_key=${BuildConfig.GIPHY_API_KEY}&limit=$limit&rating=pg-13"
            } else {
                "$BASE/search?api_key=${BuildConfig.GIPHY_API_KEY}&q=$encoded&limit=$limit&rating=pg-13"
            }
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.e(TAG, "Giphy HTTP $code")
                    SearchResult.Error("Giphy API error ($code)")
                } else {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    SearchResult.Success(parseGiphyJson(body))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct Giphy request failed", e)
                SearchResult.Error(e.message ?: "Network error")
            } finally {
                conn.disconnect()
            }
        }

    private fun parseItems(raw: List<*>): List<GifItem> {
        return raw.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> {
                    val id = entry["id"]?.toString().orEmpty()
                    val previewUrl = entry["previewUrl"]?.toString().orEmpty()
                    val fullUrl = entry["fullUrl"]?.toString().orEmpty()
                    if (id.isBlank() || fullUrl.isBlank()) null
                    else GifItem(id, previewUrl.ifBlank { fullUrl }, fullUrl)
                }
                else -> null
            }
        }
    }

    private fun parseGiphyJson(json: String): List<GifItem> {
        val root = JSONObject(json)
        val data = root.optJSONArray("data") ?: return emptyList()
        val items = mutableListOf<GifItem>()
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val images = obj.optJSONObject("images") ?: continue
            val fixed = images.optJSONObject("fixed_height") ?: continue
            val preview = images.optJSONObject("preview_gif")
                ?: images.optJSONObject("fixed_height_small")
                ?: fixed
            val fullUrl = fixed.optString("url").takeIf { it.isNotBlank() } ?: continue
            val previewUrl = preview.optString("url").takeIf { it.isNotBlank() } ?: fullUrl
            items.add(GifItem(id, previewUrl, fullUrl))
        }
        return items
    }
}