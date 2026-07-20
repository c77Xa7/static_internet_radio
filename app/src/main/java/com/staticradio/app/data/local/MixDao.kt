package com.staticradio.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MixWithTracks(
    @Embedded val mix: MixEntity,
    @Relation(parentColumn = "id", entityColumn = "mixId")
    val tracks: List<MixTrackEntity>
)

@Dao
interface MixDao {

    @Transaction
    @Query("SELECT * FROM mixes ORDER BY dateAddedEpochMillis DESC")
    fun observeMixesWithTracks(): Flow<List<MixWithTracks>>

    @Transaction
    @Query("SELECT * FROM mixes WHERE id = :mixId")
    suspend fun getMixWithTracks(mixId: String): MixWithTracks?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMix(mix: MixEntity)

    @Update
    suspend fun updateMix(mix: MixEntity)

    @Query("DELETE FROM mixes WHERE id = :mixId")
    suspend fun deleteMix(mixId: String)

    @Query("DELETE FROM mix_tracks WHERE mixId = :mixId")
    suspend fun clearTracksForMix(mixId: String)

    @Insert
    suspend fun insertTrack(track: MixTrackEntity)
}
