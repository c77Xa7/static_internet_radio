package com.staticradio.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Field table (per PROJECT_CONTEXT.md's data model, as pinned down with the user):
 *
 * item                | source            | user overwritable
 * --------------------|-------------------|-------------------
 * streamUrl           | Radio Browser API | yes (direct field, no source/override split)
 * name (radio title)  | Radio Browser API | yes
 * image               | Radio Browser API | yes
 * countryCode         | Radio Browser API | yes
 * latitude/longitude  | Radio Browser API | yes
 * clickCountSnapshot  | Radio Browser API | no
 * genre               | always user-defined | yes (smart tags, written into genreOverride;
 *                      |                      genreSource is unused/legacy)
 * bitrate             | stream URL (ICY)  | yes, DISPLAY-ONLY — never read by the playback layer
 * nowPlayingCache     | stream URL (ICY)  | no
 * description         | stream URL        | yes — Media3 doesn't expose icy-description, so this
 *                      |                   |   is populated by the user until that's hand-parsed
 * websiteUrl          | user defined      | yes
 * language            | Radio Browser API | yes
 * isFavorite          | user defined      | yes
 *
 * Resolved value for every *Source / *Override pair is override ?? source — see
 * ResolvedStation.kt. popularityTier is derived (percentile-bucketed from
 * clickCountSnapshot across all stations), not sourced directly.
 */
@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val streamUrl: String,
    val radioBrowserUuid: String?,

    val nameSource: String?,
    val nameOverride: String?,

    val imageSource: String?,
    val imageOverride: String?,

    val countryCodeSource: String?,
    val countryCodeOverride: String?,

    val latitudeSource: Double?,
    val latitudeOverride: Double?,
    val longitudeSource: Double?,
    val longitudeOverride: Double?,

    val genreSource: String?,
    val genreOverride: String?,

    val bitrateSource: Int?,
    val bitrateOverride: Int?,

    val descriptionSource: String?,
    val descriptionOverride: String?,

    val languageSource: String?,
    val languageOverride: String?,

    val websiteUrl: String?,
    val isFavorite: Boolean,

    val clickCountSnapshot: Long?,
    val popularityTier: String?,
    val nowPlayingCache: String?,

    val dateAddedEpochMillis: Long,

    // Always user-defined — no source/override split, same as websiteUrl/isFavorite.
    // Single value each, picked from a Settings-managed vocabulary (see TagType.MOOD/STYLE).
    val mood: String? = null,
    val style: String? = null,

    // Live broadcast hours, station-only. "HH:mm" 24-hour strings. is24x7 overrides
    // both times when set — the app treats the station as always-live regardless
    // of what liveTimesFrom/liveTimesTo hold.
    val liveTimesFrom: String? = null,
    val liveTimesTo: String? = null,
    val is24x7: Boolean = false
)
