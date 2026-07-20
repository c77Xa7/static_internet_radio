package com.staticradio.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "station_tag_cross_ref",
    primaryKeys = ["stationId", "tagId"],
    indices = [Index("tagId")]
)
data class StationTagCrossRef(
    val stationId: String,
    val tagId: Long
)
