package com.staticradio.app.ui.addstation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.PopularityTiers
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.StationEntity
import com.staticradio.app.data.remote.RadioBrowserApi
import com.staticradio.app.data.remote.RadioBrowserStation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class AddStationMode { SEARCH, MANUAL }

/**
 * Both entry paths from PROJECT_CONTEXT.md's original scope: paste a stream
 * URL directly, or search Radio Browser and import a result. Manually-entered
 * fields and Radio Browser fields both land in *Source (see StationEntity) —
 * *Override is reserved for a later edit, not the original add. Genre is the
 * one exception: it's always user-defined, so it's written straight into
 * genreOverride and never pulled from Radio Browser's tags. Genre is
 * comma-delimited "smart tags" — see GenreTags.
 */
class AddStationViewModel(
    private val stationDao: StationDao,
    private val radioBrowserApi: RadioBrowserApi
) : ViewModel() {

    private val _mode = MutableStateFlow(AddStationMode.SEARCH)
    val mode: StateFlow<AddStationMode> = _mode

    private val _streamUrl = MutableStateFlow("")
    val streamUrl: StateFlow<String> = _streamUrl
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _genre = MutableStateFlow("")
    val genre: StateFlow<String> = _genre
    private val _country = MutableStateFlow("")
    val country: StateFlow<String> = _country
    private val _mood = MutableStateFlow<String?>(null)
    val mood: StateFlow<String?> = _mood
    private val _style = MutableStateFlow<String?>(null)
    val style: StateFlow<String?> = _style
    private val _manualError = MutableStateFlow<String?>(null)
    val manualError: StateFlow<String?> = _manualError

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _tagQuery = MutableStateFlow("")
    val tagQuery: StateFlow<String> = _tagQuery
    private val _searchResults = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val searchResults: StateFlow<List<RadioBrowserStation>> = _searchResults
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    private val _stationSaved = Channel<Unit>(Channel.CONFLATED)
    val stationSaved = _stationSaved.receiveAsFlow()

    fun setMode(mode: AddStationMode) { _mode.value = mode }
    fun setStreamUrl(value: String) { _streamUrl.value = value; _manualError.value = null }
    fun setName(value: String) { _name.value = value }
    fun setGenre(value: String) { _genre.value = value }
    fun setCountry(value: String) { _country.value = value }
    fun setMood(value: String?) { _mood.value = value }
    fun setStyle(value: String?) { _style.value = value }
    fun setQuery(value: String) { _query.value = value }
    fun setTagQuery(value: String) { _tagQuery.value = value }

    fun saveManualStation() {
        val url = _streamUrl.value.trim()
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            _manualError.value = "Enter a valid stream URL (must start with http:// or https://)"
            return
        }
        viewModelScope.launch {
            insertStation(
                streamUrl = url,
                name = _name.value.trim().ifBlank { null },
                genreText = _genre.value.trim().ifBlank { null },
                country = _country.value.trim().ifBlank { null },
                bitrate = null,
                image = null,
                radioBrowserUuid = null,
                clickCount = null,
                latitude = null,
                longitude = null,
                language = null,
                websiteUrl = null,
                mood = _mood.value,
                style = _style.value
            )
            _streamUrl.value = ""
            _name.value = ""
            _genre.value = ""
            _country.value = ""
            _mood.value = null
            _style.value = null
            _stationSaved.trySend(Unit)
        }
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = radioBrowserApi.searchStations(q)
            } catch (e: Exception) {
                _searchError.value = "Search failed — check your connection."
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun searchByTag() {
        val tag = _tagQuery.value.trim()
        if (tag.isEmpty()) return
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = radioBrowserApi.searchStationsByTag(tag)
            } catch (e: Exception) {
                _searchError.value = "Search failed — check your connection."
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun importStation(result: RadioBrowserStation) {
        viewModelScope.launch {
            insertStation(
                streamUrl = result.streamUrl,
                name = result.name.ifBlank { null },
                genreText = null,
                country = result.countryCode.ifBlank { null },
                bitrate = result.bitrate.takeIf { it > 0 },
                image = result.favicon.ifBlank { null },
                radioBrowserUuid = result.stationUuid,
                clickCount = result.clickCount,
                latitude = result.geoLat,
                longitude = result.geoLong,
                language = result.language.ifBlank { null },
                websiteUrl = result.homepage.ifBlank { null },
                mood = null,
                style = null
            )
            _stationSaved.trySend(Unit)
        }
    }

    private suspend fun insertStation(
        streamUrl: String,
        name: String?,
        genreText: String?,
        country: String?,
        bitrate: Int?,
        image: String?,
        radioBrowserUuid: String?,
        clickCount: Long?,
        latitude: Double?,
        longitude: Double?,
        language: String?,
        websiteUrl: String?,
        mood: String?,
        style: String?
    ) {
        val stationId = UUID.randomUUID().toString()
        stationDao.insertStation(
            StationEntity(
                id = stationId,
                streamUrl = streamUrl,
                radioBrowserUuid = radioBrowserUuid,
                nameSource = name,
                nameOverride = null,
                imageSource = image,
                imageOverride = null,
                countryCodeSource = country,
                countryCodeOverride = null,
                latitudeSource = latitude,
                latitudeOverride = null,
                longitudeSource = longitude,
                longitudeOverride = null,
                genreSource = null,
                genreOverride = genreText,
                bitrateSource = bitrate,
                bitrateOverride = null,
                descriptionSource = null,
                descriptionOverride = null,
                languageSource = language,
                languageOverride = null,
                websiteUrl = websiteUrl,
                isFavorite = false,
                clickCountSnapshot = clickCount,
                popularityTier = null,
                nowPlayingCache = null,
                dateAddedEpochMillis = System.currentTimeMillis(),
                mood = mood,
                style = style
            )
        )

        if (genreText != null) {
            GenreTags.replaceStationGenres(stationDao, stationId, GenreTags.parse(genreText))
        }

        if (clickCount != null) {
            PopularityTiers.recompute(stationDao)
        }
    }

    class Factory(
        private val stationDao: StationDao,
        private val radioBrowserApi: RadioBrowserApi = RadioBrowserApi()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddStationViewModel(stationDao, radioBrowserApi) as T
    }
}
