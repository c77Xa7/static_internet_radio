package com.staticradio.app.ui.editstation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.PopularityTiers
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.toResolved
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Field table pinned down with the user (see StationEntity.kt for the full
 * source/overwritable breakdown). Editing a *Source / *Override field writes
 * into *Override — clearing it back to blank reverts to *Source, it doesn't
 * delete the original data. streamUrl/websiteUrl/isFavorite have no source
 * split (direct fields). genre is always user-defined (written straight into
 * genreOverride, see AddStationViewModel). clickCountSnapshot/popularityTier/
 * nowPlayingCache are read-only here — they're system-maintained, not user
 * fields.
 */
class EditStationViewModel(
    private val stationId: String,
    private val stationDao: StationDao
) : ViewModel() {

    private val _streamUrl = MutableStateFlow("")
    val streamUrl: StateFlow<String> = _streamUrl
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _imageUrl = MutableStateFlow("")
    val imageUrl: StateFlow<String> = _imageUrl
    private val _country = MutableStateFlow("")
    val country: StateFlow<String> = _country
    private val _latitude = MutableStateFlow("")
    val latitude: StateFlow<String> = _latitude
    private val _longitude = MutableStateFlow("")
    val longitude: StateFlow<String> = _longitude
    private val _genre = MutableStateFlow("")
    val genre: StateFlow<String> = _genre
    private val _bitrate = MutableStateFlow("")
    val bitrate: StateFlow<String> = _bitrate
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description
    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language
    private val _websiteUrl = MutableStateFlow("")
    val websiteUrl: StateFlow<String> = _websiteUrl
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite
    private val _mood = MutableStateFlow<String?>(null)
    val mood: StateFlow<String?> = _mood
    private val _style = MutableStateFlow<String?>(null)
    val style: StateFlow<String?> = _style
    private val _liveTimesFrom = MutableStateFlow("")
    val liveTimesFrom: StateFlow<String> = _liveTimesFrom
    private val _liveTimesTo = MutableStateFlow("")
    val liveTimesTo: StateFlow<String> = _liveTimesTo
    private val _is24x7 = MutableStateFlow(false)
    val is24x7: StateFlow<Boolean> = _is24x7

    private val _clickCountSnapshot = MutableStateFlow<Long?>(null)
    val clickCountSnapshot: StateFlow<Long?> = _clickCountSnapshot
    private val _popularityTier = MutableStateFlow<String?>(null)
    val popularityTier: StateFlow<String?> = _popularityTier

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _liveTimeError = MutableStateFlow<String?>(null)
    val liveTimeError: StateFlow<String?> = _liveTimeError

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val withTags = stationDao.getStationWithTags(stationId)
            val resolved = withTags?.toResolved()
            if (resolved != null) {
                _streamUrl.value = resolved.streamUrl
                _name.value = resolved.name
                _imageUrl.value = resolved.imageUrl.orEmpty()
                _country.value = resolved.countryCode.orEmpty()
                _latitude.value = resolved.latitude?.let { formatCoordinate(it) }.orEmpty()
                _longitude.value = resolved.longitude?.let { formatCoordinate(it) }.orEmpty()
                _genre.value = resolved.genres.joinToString(", ")
                _bitrate.value = resolved.bitrate?.toString().orEmpty()
                _description.value = resolved.description.orEmpty()
                _language.value = resolved.language.orEmpty()
                _websiteUrl.value = resolved.websiteUrl.orEmpty()
                _isFavorite.value = resolved.isFavorite
                _mood.value = resolved.mood
                _style.value = resolved.style
                _liveTimesFrom.value = resolved.liveTimesFrom.orEmpty()
                _liveTimesTo.value = resolved.liveTimesTo.orEmpty()
                _is24x7.value = resolved.is24x7
                _clickCountSnapshot.value = resolved.clickCountSnapshot
                _popularityTier.value = resolved.popularityTier
            }
            _isLoaded.value = true
        }
    }

    fun setStreamUrl(value: String) { _streamUrl.value = value; _error.value = null }
    fun setName(value: String) { _name.value = value }
    fun setImageUrl(value: String) { _imageUrl.value = value }
    fun setCountry(value: String) { _country.value = value }
    fun setLatitude(value: String) { _latitude.value = value; _error.value = null }
    fun setLongitude(value: String) { _longitude.value = value; _error.value = null }
    fun setPickedLocation(lat: Double, lon: Double) {
        _latitude.value = formatCoordinate(lat)
        _longitude.value = formatCoordinate(lon)
        _error.value = null
    }
    fun setManualPopularityTier(emoji: String) {
        _popularityTier.value = if (_popularityTier.value == emoji) null else emoji
    }
    fun setGenre(value: String) { _genre.value = value }
    fun setBitrate(value: String) { _bitrate.value = value; _error.value = null }
    fun setDescription(value: String) { _description.value = value }
    fun setLanguage(value: String) { _language.value = value }
    fun setWebsiteUrl(value: String) { _websiteUrl.value = value }
    fun setIsFavorite(value: Boolean) { _isFavorite.value = value }
    fun setMood(value: String?) { _mood.value = value }
    fun setStyle(value: String?) { _style.value = value }
    fun setLiveTimesFrom(value: String) { _liveTimesFrom.value = value; _liveTimeError.value = null }
    fun setLiveTimesTo(value: String) { _liveTimesTo.value = value; _liveTimeError.value = null }
    fun setIs24x7(value: Boolean) { _is24x7.value = value }

    fun save() {
        val url = _streamUrl.value.trim()
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            _error.value = "Enter a valid stream URL (must start with http:// or https://)"
            return
        }
        val bitrateText = _bitrate.value.trim()
        val bitrateValue = bitrateText.toIntOrNull()
        if (bitrateText.isNotEmpty() && bitrateValue == null) {
            _error.value = "Bitrate must be a number"
            return
        }
        val latText = _latitude.value.trim()
        val latValue = latText.toDoubleOrNull()?.let { roundToFiveDecimals(it) }
        if (latText.isNotEmpty() && latValue == null) {
            _error.value = "Latitude must be a number"
            return
        }
        val lonText = _longitude.value.trim()
        val lonValue = lonText.toDoubleOrNull()?.let { roundToFiveDecimals(it) }
        if (lonText.isNotEmpty() && lonValue == null) {
            _error.value = "Longitude must be a number"
            return
        }
        val liveFromText = _liveTimesFrom.value.trim()
        val liveToText = _liveTimesTo.value.trim()
        val timeRegex = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
        if (!_is24x7.value) {
            if (liveFromText.isNotEmpty() && !timeRegex.matches(liveFromText)) {
                _liveTimeError.value = "Live from time must be 24h HH:mm (e.g. 23:59)"
                return
            }
            if (liveToText.isNotEmpty() && !timeRegex.matches(liveToText)) {
                _liveTimeError.value = "Live to time must be 24h HH:mm (e.g. 23:59)"
                return
            }
        }

        viewModelScope.launch {
            val existing = stationDao.getStationWithTags(stationId)?.station ?: return@launch
            val genreNames = GenreTags.parse(_genre.value)

            stationDao.updateStation(
                existing.copy(
                    streamUrl = url,
                    nameOverride = _name.value.trim().ifBlank { null },
                    imageOverride = _imageUrl.value.trim().ifBlank { null },
                    countryCodeOverride = _country.value.trim().ifBlank { null },
                    latitudeOverride = latValue,
                    longitudeOverride = lonValue,
                    genreOverride = genreNames.joinToString(", ").ifBlank { null },
                    bitrateOverride = bitrateValue,
                    descriptionOverride = _description.value.trim().ifBlank { null },
                    languageOverride = _language.value.trim().ifBlank { null },
                    websiteUrl = _websiteUrl.value.trim().ifBlank { null },
                    isFavorite = _isFavorite.value,
                    mood = _mood.value,
                    style = _style.value,
                    liveTimesFrom = liveFromText.ifBlank { null },
                    liveTimesTo = liveToText.ifBlank { null },
                    is24x7 = _is24x7.value,
                    // Only ever user-settable when there's no click count to derive it from —
                    // PopularityTiers.recompute() owns the tier for every station that has one.
                    popularityTier = if (existing.clickCountSnapshot == null) _popularityTier.value else existing.popularityTier
                )
            )

            GenreTags.replaceStationGenres(stationDao, stationId, genreNames)

            _done.trySend(Unit)
        }
    }

    fun delete() {
        viewModelScope.launch {
            stationDao.deleteStation(stationId)
            PopularityTiers.recompute(stationDao)
            _done.trySend(Unit)
        }
    }

    class Factory(
        private val stationId: String,
        private val stationDao: StationDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditStationViewModel(stationId, stationDao) as T
    }
}

private fun roundToFiveDecimals(value: Double): Double =
    kotlin.math.round(value * 100000.0) / 100000.0

private fun formatCoordinate(value: Double): String =
    roundToFiveDecimals(value).toString()
