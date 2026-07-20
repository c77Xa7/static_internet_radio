package com.staticradio.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MixSource { SOUNDCLOUD, MIXCLOUD, OTHER }

/**
 * Saved DJ mix bookmark (SoundCloud/Mixcloud). Everything here is always
 * user-defined — genre/mood/style are single values picked from the same
 * Settings-managed vocabularies stations use (see TagType), not multi-tag
 * "smart tags" like a station's genre field.
 */
@Entity(tableName = "mixes")
data class MixEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fullTitle: String?,
    val artist: String?,
    val mixTitle: String?,
    val sourceRadio: String?,
    val genre: String?,
    val mood: String?,
    val style: String?,
    val image: String?,
    val releasedDate: String?,
    val sourceStreamingSite: MixSource,
    val isFavorite: Boolean,
    val description: String?,
    val dateAddedEpochMillis: Long
)
