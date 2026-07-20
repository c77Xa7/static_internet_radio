package com.staticradio.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class StationClickCount(
    val id: String,
    val clickCountSnapshot: Long
)

data class StationWithTags(
    @Embedded val station: StationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = StationTagCrossRef::class,
            parentColumn = "stationId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

@Dao
interface StationDao {

    @Transaction
    @Query("SELECT * FROM stations ORDER BY dateAddedEpochMillis DESC")
    fun observeStationsWithTags(): Flow<List<StationWithTags>>

    @Transaction
    @Query("SELECT * FROM stations WHERE id = :stationId")
    suspend fun getStationWithTags(stationId: String): StationWithTags?

    @Transaction
    @Query("SELECT * FROM stations")
    suspend fun getAllStationsWithTagsOnce(): List<StationWithTags>

    @Query("SELECT * FROM stations WHERE (:excludeId IS NULL OR id != :excludeId) ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomStation(excludeId: String?): StationEntity?

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: StationEntity)

    @Update
    suspend fun updateStation(station: StationEntity)

    @Query("DELETE FROM stations WHERE id = :stationId")
    suspend fun deleteStation(stationId: String)

    @Query("UPDATE stations SET nowPlayingCache = :text WHERE id = :stationId")
    suspend fun updateNowPlayingCache(stationId: String, text: String)

    // Live ICY bitrate header from the stream itself — see StationEntity's field
    // table (bitrate is display-only, sourced from the stream, not Radio Browser).
    @Query("UPDATE stations SET bitrateSource = :bitrate WHERE id = :stationId")
    suspend fun updateBitrateFromStream(stationId: String, bitrate: Int)

    @Query("SELECT id, clickCountSnapshot FROM stations WHERE clickCountSnapshot IS NOT NULL")
    suspend fun getClickCounts(): List<StationClickCount>

    @Query("UPDATE stations SET popularityTier = :tier WHERE id = :stationId")
    suspend fun updatePopularityTier(stationId: String, tier: String)

    @Query("SELECT * FROM tags WHERE type = :type ORDER BY name")
    fun observeTagsByType(type: TagType): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT id FROM tags WHERE name = :name AND type = :type LIMIT 1")
    suspend fun findTagId(name: String, type: TagType): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStationTagCrossRef(crossRef: StationTagCrossRef)

    @Query("DELETE FROM station_tag_cross_ref WHERE stationId = :stationId")
    suspend fun clearStationTags(stationId: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Query("DELETE FROM station_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearCrossRefsForTag(tagId: Long)
}
