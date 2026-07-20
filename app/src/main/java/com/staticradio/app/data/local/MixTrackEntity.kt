package com.staticradio.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One tracklist row — timestamp is optional (not every mix is timestamped). */
@Entity(tableName = "mix_tracks", indices = [Index("mixId")])
data class MixTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mixId: String,
    val position: Int,
    val artist: String?,
    val trackTitle: String?,
    val timestampSeconds: Int?
)
