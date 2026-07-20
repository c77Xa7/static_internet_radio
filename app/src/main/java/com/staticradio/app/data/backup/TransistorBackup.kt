package com.staticradio.app.data.backup

import kotlinx.serialization.Serializable

/** Matches org.y20k.transistor's collection.json schema (Transistor app). */
@Serializable
data class TransistorStation(
    val bitrate: Int = 0,
    val codec: String = "",
    val homepage: String = "",
    val image: String = "",
    val imageColor: Int = 0,
    val imageManuallySet: Boolean = false,
    val isPlaying: Boolean = false,
    val modificationDate: String = "",
    val name: String = "",
    val nameManuallySet: Boolean = false,
    val radioBrowserChangeUuid: String = "",
    val radioBrowserStationUuid: String = "",
    val remoteImageLocation: String = "",
    val remoteStationLocation: String = "",
    val smallImage: String = "",
    val starred: Boolean = false,
    val stream: Int = 0,
    val streamContent: String = "",
    val streamUris: List<String> = emptyList(),
    val uuid: String = ""
)

@Serializable
data class TransistorCollection(
    val modificationDate: String = "",
    val stations: List<TransistorStation> = emptyList(),
    val version: Int = 0
)
