package com.staticradio.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.MixSource
import com.staticradio.app.data.local.MixTrackEntity
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.TagEntity
import com.staticradio.app.data.local.TagType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class FullStationExport(
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
    val style: String? = null,
    val liveTimesFrom: String? = null,
    val liveTimesTo: String? = null,
    val is24x7: Boolean = false
)

@Serializable
private data class FullMixTrackExport(
    val position: Int,
    val artist: String? = null,
    val trackTitle: String? = null,
    val timestampSeconds: Int? = null
)

@Serializable
private data class FullMixExport(
    val id: String,
    val url: String,
    val fullTitle: String? = null,
    val artist: String? = null,
    val mixTitle: String? = null,
    val sourceRadio: String? = null,
    val genre: String? = null,
    val mood: String? = null,
    val style: String? = null,
    val image: String? = null,
    val releasedDate: String? = null,
    val sourceStreamingSite: String,
    val isFavorite: Boolean,
    val description: String? = null,
    val dateAddedEpochMillis: Long,
    val tracks: List<FullMixTrackExport> = emptyList()
)

/**
 * One backup, everything: stations (every field incl. live times/24x7 and
 * genre tags), mixes (every field + tracklists), the full Genre/Mood/Style
 * vocabularies, and every locally-stored image. Zip entries: stations.json,
 * mixes.json, images/<sourceId>.jpg.
 *
 * Import also transparently accepts the older split backups (stations.json
 * only / mixes.json only) — each part restores independently — plus the
 * stations.json zip's Transistor interop path stays in BackupManager.
 */
@Serializable
private data class FullBackupFile(
    val stations: List<FullStationExport> = emptyList(),
    val mixes: List<FullMixExport> = emptyList(),
    val genreVocabulary: List<String> = emptyList(),
    val moodVocabulary: List<String> = emptyList(),
    val styleVocabulary: List<String> = emptyList(),
    val version: Int = 2
)

class FullBackupManager(
    private val stationDao: StationDao,
    private val mixDao: MixDao
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(contentResolver: ContentResolver, uri: Uri) {
        val withTagsList = stationDao.getAllStationsWithTagsOnce()
        val allTags = stationDao.getAllTagsOnce()
        val mixes = mixDao.observeMixesWithTracks().first()

        val stationExports = withTagsList.map { withTags ->
            val e = withTags.station
            FullStationExport(
                id = e.id,
                streamUrl = e.streamUrl,
                radioBrowserUuid = e.radioBrowserUuid,
                nameSource = e.nameSource,
                nameOverride = e.nameOverride,
                imageSource = e.imageSource,
                imageOverride = e.imageOverride,
                countryCodeSource = e.countryCodeSource,
                countryCodeOverride = e.countryCodeOverride,
                latitudeSource = e.latitudeSource,
                latitudeOverride = e.latitudeOverride,
                longitudeSource = e.longitudeSource,
                longitudeOverride = e.longitudeOverride,
                genres = withTags.tags.filter { it.type == TagType.GENRE }.map { it.name },
                bitrateSource = e.bitrateSource,
                bitrateOverride = e.bitrateOverride,
                descriptionSource = e.descriptionSource,
                descriptionOverride = e.descriptionOverride,
                languageSource = e.languageSource,
                languageOverride = e.languageOverride,
                websiteUrl = e.websiteUrl,
                isFavorite = e.isFavorite,
                clickCountSnapshot = e.clickCountSnapshot,
                popularityTier = e.popularityTier,
                dateAddedEpochMillis = e.dateAddedEpochMillis,
                mood = e.mood,
                style = e.style,
                liveTimesFrom = e.liveTimesFrom,
                liveTimesTo = e.liveTimesTo,
                is24x7 = e.is24x7
            )
        }

        val mixExports = mixes.map { mwt ->
            val m = mwt.mix
            FullMixExport(
                id = m.id,
                url = m.url,
                fullTitle = m.fullTitle,
                artist = m.artist,
                mixTitle = m.mixTitle,
                sourceRadio = m.sourceRadio,
                genre = m.genre,
                mood = m.mood,
                style = m.style,
                image = m.image,
                releasedDate = m.releasedDate,
                sourceStreamingSite = m.sourceStreamingSite.name,
                isFavorite = m.isFavorite,
                description = m.description,
                dateAddedEpochMillis = m.dateAddedEpochMillis,
                tracks = mwt.tracks.sortedBy { it.position }.map {
                    FullMixTrackExport(it.position, it.artist, it.trackTitle, it.timestampSeconds)
                }
            )
        }

        val backupJson = json.encodeToString(
            FullBackupFile.serializer(),
            FullBackupFile(
                stations = stationExports,
                mixes = mixExports,
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

                zip.putNextEntry(ZipEntry("mixes.json"))
                // Same FullBackupFile JSON in both entries — one document,
                // two conventional entry names so either restore path finds it.
                zip.write(backupJson.toByteArray())
                zip.closeEntry()

                // Locally-stored images (photo-picker uploads) for stations
                // and mixes alike. Remote image URLs stay as URLs in the JSON.
                withTagsList.forEach { withTags ->
                    val imageUrl = withTags.station.imageOverride ?: withTags.station.imageSource
                    if (imageUrl != null && imageUrl.startsWith("file://")) {
                        val file = File(imageUrl.removePrefix("file://"))
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("images/${withTags.station.id}.jpg"))
                            zip.write(file.readBytes())
                            zip.closeEntry()
                        }
                    }
                }
                mixes.forEach { mwt ->
                    val imageUrl = mwt.mix.image
                    if (imageUrl != null && imageUrl.startsWith("file://")) {
                        val file = File(imageUrl.removePrefix("file://"))
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("images/${mwt.mix.id}.jpg"))
                            zip.write(file.readBytes())
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }

    data class ImportResult(val stations: Int, val mixes: Int)

    /**
     * Reads a full-backup zip (or either legacy single-format zip: a
     * stations.json-only export restores its stations; a mixes.json-only
     * export restores its mixes — both keyed off whichever JSON entries and
     * image IDs each part references).
     */
    suspend fun import(context: Context, uri: Uri): ImportResult {
        var backup: FullBackupFile? = null
        val imageBytesByOriginalId = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        // v1.5.1 full backups put the same FullBackupFile JSON
                        // in both entries; older split backups put only their
                        // own payload in one. Parse whichever we find — the
                        // last JSON parsed wins, and each is a superset of the
                        // older formats (ignoreUnknownKeys on).
                        name.endsWith("stations.json") || name.endsWith("mixes.json") -> {
                            backup = json.decodeFromString(FullBackupFile.serializer(), zip.readBytes().decodeToString())
                        }
                        name.startsWith("images/") -> {
                            val id = name.removePrefix("images/").removeSuffix(".jpg")
                            imageBytesByOriginalId[id] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val b = backup ?: return ImportResult(0, 0)
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

        // ---- Vocabularies first, so genre cross-refs can resolve ----
        for (name in b.genreVocabulary) stationDao.insertTag(TagEntity(name = name, type = TagType.GENRE))
        for (name in b.moodVocabulary) stationDao.insertTag(TagEntity(name = name, type = TagType.MOOD))
        for (name in b.styleVocabulary) stationDao.insertTag(TagEntity(name = name, type = TagType.STYLE))

        // ---- Stations ----
        var stationCount = 0
        for (s in b.stations) {
            if (s.streamUrl.isBlank()) continue
            val stationId = UUID.randomUUID().toString()

            val localImage = imageBytesByOriginalId[s.id]?.let { bytes ->
                val file = File(imagesDir, "$stationId.jpg")
                file.writeBytes(bytes)
                "file://${file.absolutePath}"
            }

            stationDao.insertStation(
                com.staticradio.app.data.local.StationEntity(
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
                    style = s.style,
                    liveTimesFrom = s.liveTimesFrom,
                    liveTimesTo = s.liveTimesTo,
                    is24x7 = s.is24x7
                )
            )

            if (s.genres.isNotEmpty()) {
                GenreTags.replaceStationGenres(stationDao, stationId, s.genres)
            }
            stationCount++
        }

        // ---- Mixes ----
        var mixCount = 0
        for (m in b.mixes) {
            if (m.url.isBlank()) continue
            val mixId = UUID.randomUUID().toString()

            val localImage = imageBytesByOriginalId[m.id]?.let { bytes ->
                val file = File(imagesDir, "$mixId.jpg")
                file.writeBytes(bytes)
                "file://${file.absolutePath}"
            } ?: m.image

            mixDao.insertMix(
                com.staticradio.app.data.local.MixEntity(
                    id = mixId,
                    url = m.url,
                    fullTitle = m.fullTitle,
                    artist = m.artist,
                    mixTitle = m.mixTitle,
                    sourceRadio = m.sourceRadio,
                    genre = m.genre,
                    mood = m.mood,
                    style = m.style,
                    image = localImage,
                    releasedDate = m.releasedDate,
                    sourceStreamingSite = runCatching { MixSource.valueOf(m.sourceStreamingSite) }.getOrDefault(MixSource.OTHER),
                    isFavorite = m.isFavorite,
                    description = m.description,
                    dateAddedEpochMillis = m.dateAddedEpochMillis
                )
            )
            m.tracks.forEach { t ->
                mixDao.insertTrack(
                    MixTrackEntity(
                        mixId = mixId,
                        position = t.position,
                        artist = t.artist,
                        trackTitle = t.trackTitle,
                        timestampSeconds = t.timestampSeconds
                    )
                )
            }
            mixCount++
        }

        return ImportResult(stationCount, mixCount)
    }
}
