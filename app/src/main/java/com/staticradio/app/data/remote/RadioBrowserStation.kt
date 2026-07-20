package com.staticradio.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioBrowserStation(
    @SerialName("stationuuid") val stationUuid: String,
    val name: String,
    @SerialName("url_resolved") val urlResolved: String = "",
    val url: String = "",
    val favicon: String = "",
    val tags: String = "",
    @SerialName("countrycode") val countryCode: String = "",
    val bitrate: Int = 0,
    @SerialName("clickcount") val clickCount: Long = 0,
    @SerialName("geo_lat") val geoLat: Double? = null,
    @SerialName("geo_long") val geoLong: Double? = null,
    val language: String = "",
    val homepage: String = ""
) {
    val streamUrl: String get() = urlResolved.ifBlank { url }
    val primaryGenre: String? get() = tags.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() }
}
