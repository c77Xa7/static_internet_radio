package com.staticradio.app.ui.mixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.MixEntity
import com.staticradio.app.data.local.MixTrackEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class TrackDraft(val artist: String = "", val trackTitle: String = "", val timestamp: String = "")

private val RELEASE_DATE_REGEX = Regex("""^(0[1-9]|[12]\d|3[01])/(0[1-9]|1[0-2])/\d{4}$""")

/**
 * Shared form for both add and edit — mixId == null means "creating new".
 * prefillUrl (share-intent capture) only applies in add mode and triggers a
 * best-effort oEmbed fetch to prepopulate title/artist/image.
 */
class MixFormViewModel(
    private val mixId: String?,
    private val mixDao: MixDao,
    prefillUrl: String? = null
) : ViewModel() {

    val isEditMode = mixId != null

    private var dateAddedEpochMillis = System.currentTimeMillis()

    private val _url = MutableStateFlow(prefillUrl.orEmpty())
    val url: StateFlow<String> = _url
    private val _fullTitle = MutableStateFlow("")
    val fullTitle: StateFlow<String> = _fullTitle
    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist
    private val _mixTitle = MutableStateFlow("")
    val mixTitle: StateFlow<String> = _mixTitle
    private val _sourceRadio = MutableStateFlow("")
    val sourceRadio: StateFlow<String> = _sourceRadio
    private val _genre = MutableStateFlow<String?>(null)
    val genre: StateFlow<String?> = _genre
    private val _mood = MutableStateFlow<String?>(null)
    val mood: StateFlow<String?> = _mood
    private val _style = MutableStateFlow<String?>(null)
    val style: StateFlow<String?> = _style
    private val _image = MutableStateFlow("")
    val image: StateFlow<String> = _image
    private val _releasedDate = MutableStateFlow("")
    val releasedDate: StateFlow<String> = _releasedDate
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite
    private val _tracks = MutableStateFlow(listOf<TrackDraft>())
    val tracks: StateFlow<List<TrackDraft>> = _tracks

    private val _isLoaded = MutableStateFlow(mixId == null)
    val isLoaded: StateFlow<Boolean> = _isLoaded
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _releasedDateError = MutableStateFlow<String?>(null)
    val releasedDateError: StateFlow<String?> = _releasedDateError

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done = _done.receiveAsFlow()

    init {
        if (mixId != null) {
            viewModelScope.launch {
                val existing = mixDao.getMixWithTracks(mixId)
                if (existing != null) {
                    dateAddedEpochMillis = existing.mix.dateAddedEpochMillis
                    _url.value = existing.mix.url
                    _fullTitle.value = existing.mix.fullTitle.orEmpty()
                    _artist.value = existing.mix.artist.orEmpty()
                    _mixTitle.value = existing.mix.mixTitle.orEmpty()
                    _sourceRadio.value = existing.mix.sourceRadio.orEmpty()
                    _genre.value = existing.mix.genre
                    _mood.value = existing.mix.mood
                    _style.value = existing.mix.style
                    _image.value = existing.mix.image.orEmpty()
                    _releasedDate.value = existing.mix.releasedDate.orEmpty()
                    _description.value = existing.mix.description.orEmpty()
                    _isFavorite.value = existing.mix.isFavorite
                    _tracks.value = existing.tracks.sortedBy { it.position }.map {
                        TrackDraft(
                            artist = it.artist.orEmpty(),
                            trackTitle = it.trackTitle.orEmpty(),
                            timestamp = it.timestampSeconds?.let(::formatTimestamp).orEmpty()
                        )
                    }
                }
                _isLoaded.value = true
            }
        } else if (!prefillUrl.isNullOrBlank()) {
            viewModelScope.launch {
                val source = detectMixSource(prefillUrl)
                val oembed = fetchOEmbed(prefillUrl, source)
                if (oembed != null) {
                    _fullTitle.value = oembed.title.orEmpty()
                    _artist.value = oembed.authorName.orEmpty()
                    _image.value = oembed.thumbnailUrl.orEmpty()
                }
            }
        }
    }

    fun setUrl(value: String) { _url.value = value; _error.value = null }
    fun setFullTitle(value: String) { _fullTitle.value = value }
    fun setArtist(value: String) { _artist.value = value }
    fun setMixTitle(value: String) { _mixTitle.value = value }
    fun setSourceRadio(value: String) { _sourceRadio.value = value }
    fun setGenre(value: String?) { _genre.value = value }
    fun setMood(value: String?) { _mood.value = value }
    fun setStyle(value: String?) { _style.value = value }
    fun setImage(value: String) { _image.value = value }
    fun setReleasedDate(value: String) { _releasedDate.value = value; _releasedDateError.value = null }
    fun setDescription(value: String) { _description.value = value }
    fun setIsFavorite(value: Boolean) { _isFavorite.value = value }

    fun addTrackRow() { _tracks.value = _tracks.value + TrackDraft() }
    fun updateTrackRow(index: Int, track: TrackDraft) {
        _tracks.value = _tracks.value.toMutableList().apply { this[index] = track }
    }
    fun removeTrackRow(index: Int) {
        _tracks.value = _tracks.value.toMutableList().apply { removeAt(index) }
    }

    fun save() {
        val urlValue = _url.value.trim()
        if (urlValue.isEmpty()) {
            _error.value = "Enter a URL"
            return
        }
        val releasedDateText = _releasedDate.value.trim()
        if (releasedDateText.isNotEmpty() && !RELEASE_DATE_REGEX.matches(releasedDateText)) {
            _releasedDateError.value = "Must be DD/MM/YYYY (e.g. 01/01/2020)"
            return
        }
        viewModelScope.launch {
            val id = mixId ?: UUID.randomUUID().toString()
            val entity = MixEntity(
                id = id,
                url = urlValue,
                fullTitle = _fullTitle.value.trim().ifBlank { null },
                artist = _artist.value.trim().ifBlank { null },
                mixTitle = _mixTitle.value.trim().ifBlank { null },
                sourceRadio = _sourceRadio.value.trim().ifBlank { null },
                genre = _genre.value,
                mood = _mood.value,
                style = _style.value,
                image = _image.value.trim().ifBlank { null },
                releasedDate = _releasedDate.value.trim().ifBlank { null },
                sourceStreamingSite = detectMixSource(urlValue),
                isFavorite = _isFavorite.value,
                description = _description.value.trim().ifBlank { null },
                dateAddedEpochMillis = dateAddedEpochMillis
            )
            if (mixId != null) mixDao.updateMix(entity) else mixDao.insertMix(entity)
            mixDao.clearTracksForMix(id)
            _tracks.value.forEachIndexed { index, track ->
                if (track.artist.isNotBlank() || track.trackTitle.isNotBlank()) {
                    mixDao.insertTrack(
                        MixTrackEntity(
                            mixId = id,
                            position = index,
                            artist = track.artist.trim().ifBlank { null },
                            trackTitle = track.trackTitle.trim().ifBlank { null },
                            timestampSeconds = parseTimestamp(track.timestamp)
                        )
                    )
                }
            }
            _done.trySend(Unit)
        }
    }

    fun delete() {
        val id = mixId ?: return
        viewModelScope.launch {
            mixDao.deleteMix(id)
            _done.trySend(Unit)
        }
    }

    class Factory(
        private val mixId: String?,
        private val mixDao: MixDao,
        private val prefillUrl: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MixFormViewModel(mixId, mixDao, prefillUrl) as T
    }
}
