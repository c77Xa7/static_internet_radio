package com.staticradio.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.StationEntity
import com.staticradio.app.data.local.TagEntity
import com.staticradio.app.data.local.TagType
import com.staticradio.app.data.toResolved
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class StationExport(
    val id: String,
    val streamUrl: String,
    val radioBrowserUuid: String? = null,
    val nameSource: String? = null,
    val nameOverride: String? = null,
    val imageSource: String? = null,
    val imageOverride: String? = null,
    val countryCodeSource: String? = null,
    val countryCodeOverride: String? = null,
    val latitudeSource: Double? = null,
    val latitudeOverride: Double? = null,
    val longitudeSource: Double? = null,
    val longitudeOverride: Double? = null,
    val genres: List<String> = emptyList(),
    val bitrateSource: Int? = null,
    val bitrateOverride: Int? = null,
    val descriptionSource: String? = null,
    val descriptionOverride: String? = null,
    val languageSource: String? = null,
    val languageOverride: String? = null,
    val websiteUrl: String? = null,
    val isFavorite: Boolean = false,
    val clickCountSnapshot: Long? = null,
    val popularityTier: String? = null,
    val dateAddedEpochMillis: Long,
    val mood: String? = null,
    val style: String? = null
)

@Serializable
private data class StationBackupFile(
    val stations: List<StationExport>,
    val genreVocabulary: List<String> = emptyList(),
    val moodVocabulary: List<String> = emptyList(),
    val styleVocabulary: List<String> = emptyList(),
    val version: Int = 1
)

/**
 * Local file backup/restore via the system file picker (Storage Access
 * Framework). Export uses STATIC's own zip format (stations.json,
 * images/<id>.jpg) so every field round-trips — coordinates, genre/mood/
 * style, description, language, popularity tier, and the full Genre/Mood/
 * Style vocabularies (including entries not currently attached to any
 * station). Import accepts either that own format or a Transistor
 * collection.json export for one-way interop with Transistor — a Transistor
 * import only carries the fields Transistor itself tracks (name, stream URL,
 * image, homepage, starred, bitrate); it doesn't have genre/coordinates/
 * language/description/mood/style to bring in.
 */
class BackupManager(private val stationDao: StationDao) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(contentResolver: ContentResolver, uri: Uri) {
        val withTagsList = stationDao.getAllStationsWithTagsOnce()
        val allTags = stationDao.getAllTagsOnce()

        val stationExports = withTagsList.map { withTags ->
            val entity = withTags.station
            StationExport(
                id = entity.id,
                streamUrl = entity.streamUrl,
                radioBrowserUuid = entity.radioBrowserUuid,
                nameSource = entity.nameSource,
                nameOverride = entity.nameOverride,
                imageSource = entity.imageSource,
                imageOverride = entity.imageOverride,
                countryCodeSource = entity.countryCodeSource,
                countryCodeOverride = entity.countryCodeOverride,
                latitudeSource = entity.latitudeSource,
                latitudeOverride = entity.latitudeOverride,
                longitudeSource = entity.longitudeSource,
                longitudeOverride = entity.longitudeOverride,
                genres = withTags.tags.filter { it.type == TagType.GENRE }.map { it.name },
                bitrateSource = entity.bitrateSource,
                bitrateOverride = entity.bitrateOverride,
                descriptionSource = entity.descriptionSource,
                descriptionOverride = entity.descriptionOverride,
                languageSource = entity.languageSource,
                languageOverride = entity.languageOverride,
                websiteUrl = entity.websiteUrl,
                isFavorite = entity.isFavorite,
                clickCountSnapshot = entity.clickCountSnapshot,
                popularityTier = entity.popularityTier,
                dateAddedEpochMillis = entity.dateAddedEpochMillis,
                mood = entity.mood,
                style = entity.style
            )
        }

        val backupJson = json.encodeToString(
            StationBackupFile.serializer(),
            StationBackupFile(
                stations = stationExports,
                genreVocabulary = allTags.filter { it.type == TagType.GENRE }.map { it.name },
                moodVocabulary = allTags.filter { it.type == TagType.MOOD }.map { it.name },
                styleVocabulary = allTags.filter { it.type == TagType.STYLE }.map { it.name }
            )
        )

        contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("stations.json"))
                zip.write(backupJson.toByteArray())
                zip.closeEntry()

                // Bundle images we already have as local files (from the in-app image
                // picker). Remote favicon URLs aren't fetched during export — they're
                // still carried as imageSource/imageOverride in the JSON.
                withTagsList.forEach { withTags ->
                    val resolved = withTags.toResolved()
                    val imageUrl = resolved.imageUrl
                    if (imageUrl != null && imageUrl.startsWith("file://")) {
                        val file = File(imageUrl.removePrefix("file://"))
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("images/${withTags.station.id}.jpg"))
                            zip.write(file.readBytes())
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }

    suspend fun import(context: Context, uri: Uri): Int {
        var ownBackup: StationBackupFile? = null
        var transistorCollection: TransistorCollection? = null
        val imageBytesById = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name.endsWith("stations.json") -> {
                            ownBackup = json.decodeFromString(StationBackupFile.serializer(), zip.readBytes().decodeToString())
                        }
                        name.endsWith("collection.json") -> {
                            transistorCollection = json.decodeFromString(TransistorCollection.serializer(), zip.readBytes().decodeToString())
                        }
                        name.startsWith("images/") -> {
                            val id = name.removePrefix("images/").substringBefore("/").removeSuffix(".jpg")
                            imageBytesById[id] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

        ownBackup?.let { backup ->
            for (name in backup.genreVocabulary) stationDao.insertTag(TagEntity(name = name, type = TagType.GENRE))
            for (name in backup.moodVocabulary) stationDao.insertTag(TagEntity(name = name, type = TagType.MOOD))
            for (name in backup.styleVocabulary) stationDao.insertTag(TagEntity(name = name, type = TagType.STYLE))

            for (s in backup.stations) {
                if (s.streamUrl.isBlank()) continue
                val stationId = UUID.randomUUID().toString()

                val localImage = imageBytesById[s.id]?.let { bytes ->
                    val file = File(imagesDir, "$stationId.jpg")
                    file.writeBytes(bytes)
                    "file://${file.absolutePath}"
                }

                stationDao.insertStation(
                    StationEntity(
                        id = stationId,
                        streamUrl = s.streamUrl,
                        radioBrowserUuid = s.radioBrowserUuid,
                        nameSource = s.nameSource,
                        nameOverride = s.nameOverride,
                        imageSource = s.imageSource,
                        imageOverride = localImage ?: s.imageOverride,
                        countryCodeSource = s.countryCodeSource,
                        countryCodeOverride = s.countryCodeOverride,
                        latitudeSource = s.latitudeSource,
                        latitudeOverride = s.latitudeOverride,
                        longitudeSource = s.longitudeSource,
                        longitudeOverride = s.longitudeOverride,
                        genreSource = null,
                        genreOverride = s.genres.joinToString(", ").ifBlank { null },
                        bitrateSource = s.bitrateSource,
                        bitrateOverride = s.bitrateOverride,
                        descriptionSource = s.descriptionSource,
                        descriptionOverride = s.descriptionOverride,
                        languageSource = s.languageSource,
                        languageOverride = s.languageOverride,
                        websiteUrl = s.websiteUrl,
                        isFavorite = s.isFavorite,
                        clickCountSnapshot = s.clickCountSnapshot,
                        popularityTier = s.popularityTier,
                        nowPlayingCache = null,
                        dateAddedEpochMillis = s.dateAddedEpochMillis,
                        mood = s.mood,
                        style = s.style
                    )
                )

                if (s.genres.isNotEmpty()) {
                    GenreTags.replaceStationGenres(stationDao, stationId, s.genres)
                }
            }

            return backup.stations.size
        }

        val stations = transistorCollection?.stations ?: return 0

        for (s in stations) {
            val streamUrl = s.streamUris.getOrNull(s.stream) ?: s.streamUris.firstOrNull() ?: s.remoteStationLocation
            if (streamUrl.isBlank()) continue

            val stationId = UUID.randomUUID().toString()
            var localImagePath: String? = null
            imageBytesById[s.uuid]?.let { bytes ->
                val file = File(imagesDir, "$stationId.jpg")
                file.writeBytes(bytes)
                localImagePath = "file://${file.absolutePath}"
            }

            stationDao.insertStation(
                StationEntity(
                    id = stationId,
                    streamUrl = streamUrl,
                    radioBrowserUuid = s.radioBrowserStationUuid.ifBlank { null },
                    nameSource = s.name.ifBlank { null },
                    nameOverride = null,
                    imageSource = localImagePath ?: s.remoteImageLocation.ifBlank { null },
                    imageOverride = null,
                    countryCodeSource = null,
                    countryCodeOverride = null,
                    latitudeSource = null,
                    latitudeOverride = null,
                    longitudeSource = null,
                    longitudeOverride = null,
                    genreSource = null,
                    genreOverride = null,
                    bitrateSource = s.bitrate.takeIf { it > 0 },
                    bitrateOverride = null,
                    descriptionSource = null,
                    descriptionOverride = null,
                    languageSource = null,
                    languageOverride = null,
                    websiteUrl = s.homepage.ifBlank { null },
                    isFavorite = s.starred,
                    clickCountSnapshot = null,
                    popularityTier = null,
                    nowPlayingCache = null,
                    dateAddedEpochMillis = System.currentTimeMillis()
                )
            )
        }

        return stations.size
    }
}
