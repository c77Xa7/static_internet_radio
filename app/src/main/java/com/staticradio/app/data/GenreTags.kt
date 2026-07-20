package com.staticradio.app.data

import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.StationTagCrossRef
import com.staticradio.app.data.local.TagEntity
import com.staticradio.app.data.local.TagType

/**
 * Comma-delimited "smart tag" entry point for genre (see StationEntity's field
 * table). Typing "Dub, Post-Punk" becomes two GENRE tags in the vocabulary
 * join table — shared by Add Station and Edit Station so both stay in sync.
 */
object GenreTags {

    fun parse(text: String): List<String> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    suspend fun replaceStationGenres(stationDao: StationDao, stationId: String, genreNames: List<String>) {
        stationDao.clearStationTags(stationId)
        for (name in genreNames) {
            val insertedTagId = stationDao.insertTag(TagEntity(name = name, type = TagType.GENRE))
            val tagId = if (insertedTagId != -1L) insertedTagId else stationDao.findTagId(name, TagType.GENRE)
            tagId?.let { stationDao.insertStationTagCrossRef(StationTagCrossRef(stationId = stationId, tagId = it)) }
        }
    }
}
