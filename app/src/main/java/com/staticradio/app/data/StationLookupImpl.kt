package com.staticradio.app.data

import com.staticradio.app.data.local.StationDao
import com.staticradio.app.playback.RadioPlaybackService

class StationLookupImpl(private val stationDao: StationDao) : RadioPlaybackService.StationLookup {

    override suspend fun getRandomStation(excludeId: String?): RadioPlaybackService.StationRef? {
        val entity = stationDao.getRandomStation(excludeId) ?: return null
        return RadioPlaybackService.StationRef(
            id = entity.id,
            streamUrl = entity.streamUrl,
            title = entity.nameOverride ?: entity.nameSource ?: "Unknown station",
            imageUrl = entity.imageOverride ?: entity.imageSource
        )
    }

    override suspend fun updateNowPlayingCache(stationId: String, text: String) {
        stationDao.updateNowPlayingCache(stationId, text)
    }

    override suspend fun updateBitrateFromStream(stationId: String, bitrate: Int) {
        stationDao.updateBitrateFromStream(stationId, bitrate)
    }
}
