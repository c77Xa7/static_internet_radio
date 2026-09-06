package com.staticradio.app.data

import com.staticradio.app.data.local.StationDao
import com.staticradio.app.playback.RadioPlaybackService

class StationLookupImpl(private val stationDao: StationDao) : RadioPlaybackService.StationLookup {

    override suspend fun getRandomStation(excludeId: String?): RadioPlaybackService.StationRef? {
        return stationDao.getRandomStation(excludeId)?.toStationRef()
    }

    override suspend fun getAllStations(): List<RadioPlaybackService.StationRef> {
        return stationDao.getAllStationsOnce()
            .map { it.toStationRef() }
            .sortedWith(compareByDescending<RadioPlaybackService.StationRef> { it.isFavorite }.thenBy { it.title.lowercase() })
    }

    override suspend fun getStation(stationId: String): RadioPlaybackService.StationRef? {
        return stationDao.getStationOnce(stationId)?.toStationRef()
    }

    private fun com.staticradio.app.data.local.StationEntity.toStationRef() = RadioPlaybackService.StationRef(
        id = id,
        streamUrl = streamUrl,
        title = nameOverride ?: nameSource ?: "Unknown station",
        imageUrl = imageOverride ?: imageSource,
        isFavorite = isFavorite,
        countryCode = countryCodeOverride ?: countryCodeSource,
        liveTimesFrom = liveTimesFrom,
        liveTimesTo = liveTimesTo,
        is24x7 = is24x7
    )

    override suspend fun updateNowPlayingCache(stationId: String, text: String) {
        stationDao.updateNowPlayingCache(stationId, text)
    }

    override suspend fun updateBitrateFromStream(stationId: String, bitrate: Int) {
        stationDao.updateBitrateFromStream(stationId, bitrate)
    }
}
