package com.staticradio.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Talks to the public Radio Browser API (radio-browser.info), a community-run
 * directory of internet radio streams. No API key required. de1.api.* is one
 * fixed mirror — fine for a solo/weekend-scope app; a production app would
 * resolve the current mirror list from all.api.radio-browser.info first.
 */
class RadioBrowserApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://de1.api.radio-browser.info"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchStations(query: String, limit: Int = 30): List<RadioBrowserStation> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/json/stations/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("hidebroken", "true")
                .addQueryParameter("order", "clickcount")
                .addQueryParameter("reverse", "true")
                .build()

            val request = Request.Builder()
                .url(url)
                // radio-browser.info's usage guidelines ask for a descriptive User-Agent.
                .header("User-Agent", "StaticRadioApp/0.1 (Android; personal weekend project)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString(body)
            }
        }

    /** Same advanced-search endpoint, filtered by community-provided tag instead of station name. */
    suspend fun searchStationsByTag(tag: String, limit: Int = 30): List<RadioBrowserStation> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/json/stations/search".toHttpUrl().newBuilder()
                .addQueryParameter("tag", tag)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("hidebroken", "true")
                .addQueryParameter("order", "clickcount")
                .addQueryParameter("reverse", "true")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StaticRadioApp/0.1 (Android; personal weekend project)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                json.decodeFromString(body)
            }
        }
}
