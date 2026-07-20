package com.staticradio.app.data

import com.staticradio.app.data.local.StationEntity
import com.staticradio.app.data.local.StationWithTags
import com.staticradio.app.data.local.TagEntity
import com.staticradio.app.data.local.TagType

/**
 * The rest of the app (UI, playback) should only ever see resolved values —
 * override ?? source — never the raw *Source / *Override pairs on StationEntity.
 *
 * genres is multi-valued ("smart tags", comma-delimited on entry) and backed
 * by the TagEntity/StationTagCrossRef join table, not a single string column —
 * genreOverride on StationEntity still holds the raw comma-joined text so edit
 * forms can prefill it. Genre is always user-defined (written into
 * genreOverride only) — genreSource is legacy/unused.
 */
data class ResolvedStation(
    val id: String,
    val streamUrl: String,
    val name: String,
    val imageUrl: String?,
    val genres: List<String>,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val bitrate: Int?,
    val description: String?,
    val language: String?,
    val websiteUrl: String?,
    val isFavorite: Boolean,
    val clickCountSnapshot: Long?,
    val popularityTier: String?,
    val nowPlayingCache: String?,
    val mood: String?,
    val style: String?,
    val liveTimesFrom: String?,
    val liveTimesTo: String?,
    val is24x7: Boolean
)

fun StationEntity.toResolved(tags: List<TagEntity> = emptyList()): ResolvedStation = ResolvedStation(
    id = id,
    streamUrl = streamUrl,
    name = nameOverride ?: nameSource ?: "Unknown station",
    imageUrl = imageOverride ?: imageSource,
    genres = tags.filter { it.type == TagType.GENRE }.map { it.name },
    countryCode = countryCodeOverride ?: countryCodeSource,
    latitude = latitudeOverride ?: latitudeSource,
    longitude = longitudeOverride ?: longitudeSource,
    bitrate = bitrateOverride ?: bitrateSource,
    description = descriptionOverride ?: descriptionSource,
    language = languageOverride ?: languageSource,
    websiteUrl = websiteUrl,
    isFavorite = isFavorite,
    clickCountSnapshot = clickCountSnapshot,
    popularityTier = popularityTier,
    nowPlayingCache = nowPlayingCache,
    mood = mood,
    style = style,
    liveTimesFrom = liveTimesFrom,
    liveTimesTo = liveTimesTo,
    is24x7 = is24x7
)

fun StationWithTags.toResolved(): ResolvedStation = station.toResolved(tags)
