package com.staticradio.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.MixEntity
import com.staticradio.app.data.local.MixSource
import com.staticradio.app.data.local.MixTrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class MixTrackExport(
    val position: Int,
    val artist: String? = null,
    val trackTitle: String? = null,
    val timestampSeconds: Int? = null
)

@Serializable
private data class MixExport(
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
    val tracks: List<MixTrackExport> = emptyList()
)

@Serializable
private data class MixBackupFile(val mixes: List<MixExport>, val version: Int = 1)

/**
 * Own zip format (own-schema mixes.json + images/<id>.jpg) — mirrors the
 * station backup's overall shape (zip via Storage Access Framework, bundled
 * local images) but there's no third-party mix format to stay compatible
 * with, so this is a self-contained STATIC-only format.
 */
class MixBackupManager(private val mixDao: MixDao) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(contentResolver: ContentResolver, uri: Uri) {
        // observeMixesWithTracks is a Flow — take a single snapshot for export.
        val snapshot = mixDao.observeMixesWithTracks().first()

        val exportList = snapshot.map { mwt ->
            val m = mwt.mix
            MixExport(
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
                    MixTrackExport(it.position, it.artist, it.trackTitle, it.timestampSeconds)
                }
            )
        }

        val backupJson = json.encodeToString(MixBackupFile.serializer(), MixBackupFile(exportList))

        contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("mixes.json"))
                zip.write(backupJson.toByteArray())
                zip.closeEntry()

                snapshot.forEach { mwt ->
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

    suspend fun import(context: Context, uri: Uri): Int {
        var backup: MixBackupFile? = null
        val imageBytesByOriginalId = mutableMapOf<String, ByteArray>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name.endsWith("mixes.json") -> {
                            backup = json.decodeFromString(MixBackupFile.serializer(), zip.readBytes().decodeToString())
                        }
                        name.startsWith("images/") -> {
                            val originalId = name.removePrefix("images/").removeSuffix(".jpg")
                            imageBytesByOriginalId[originalId] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val mixes = backup?.mixes ?: return 0
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }

        for (m in mixes) {
            if (m.url.isBlank()) continue
            val mixId = UUID.randomUUID().toString()

            val localImage = imageBytesByOriginalId[m.id]?.let { bytes ->
                val file = File(imagesDir, "$mixId.jpg")
                file.writeBytes(bytes)
                "file://${file.absolutePath}"
            } ?: m.image

            mixDao.insertMix(
                MixEntity(
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
        }

        return mixes.size
    }
}
