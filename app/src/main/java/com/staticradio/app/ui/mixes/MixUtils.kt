package com.staticradio.app.ui.mixes

import com.staticradio.app.data.local.MixSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

fun detectMixSource(url: String): MixSource = when {
    url.contains("soundcloud.com", ignoreCase = true) -> MixSource.SOUNDCLOUD
    url.contains("mixcloud.com", ignoreCase = true) -> MixSource.MIXCLOUD
    else -> MixSource.OTHER
}

@Serializable
data class OEmbedResponse(
    val title: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

/**
 * Official oEmbed endpoints — legitimate, ToS-compliant metadata source for
 * both platforms (unlike scraping a raw stream/download URL, which their
 * terms explicitly prohibit). Only gives title/author/thumbnail; tracklist,
 * genre, release date etc. aren't available this way and stay manual.
 */
private val httpClient = OkHttpClient()
private val json = Json { ignoreUnknownKeys = true }

suspend fun fetchOEmbed(url: String, source: MixSource): OEmbedResponse? = withContext(Dispatchers.IO) {
    val endpoint = when (source) {
        MixSource.SOUNDCLOUD -> "https://soundcloud.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
        MixSource.MIXCLOUD -> "https://www.mixcloud.com/oembed/?format=json&url=" + URLEncoder.encode(url, "UTF-8")
        MixSource.OTHER -> return@withContext null
    }
    runCatching {
        val request = Request.Builder().url(endpoint).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body?.string() ?: return@runCatching null
            json.decodeFromString<OEmbedResponse>(body)
        }
    }.getOrNull()
}

/** "mm:ss" or "h:mm:ss" — matches how people normally write mix timestamps. */
fun formatTimestamp(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun parseTimestamp(text: String): Int? {
    val parts = text.trim().split(":").mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        0 -> null
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}
