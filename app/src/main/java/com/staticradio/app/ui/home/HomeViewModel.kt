package com.staticradio.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.data.ResolvedStation
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.toResolved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class StationViewMode { LIST, GRID }

data class HomeFilter(
    val genre: String? = null,
    val countryCode: String? = null,
    val mood: String? = null,
    val style: String? = null,
    val favoritesOnly: Boolean = false
)

class HomeViewModel(private val stationDao: StationDao) : ViewModel() {

    private val _viewMode = MutableStateFlow(StationViewMode.LIST)
    val viewMode: StateFlow<StationViewMode> = _viewMode

    private val _filter = MutableStateFlow(HomeFilter())
    val filter: StateFlow<HomeFilter> = _filter

    private val allStations = stationDao.observeStationsWithTags()
        .map { rows -> rows.map { it.toResolved() } }

    val genres: StateFlow<List<String>> = allStations
        .map { stations -> stations.flatMap { it.genres }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val countryCodes: StateFlow<List<String>> = allStations
        .map { stations -> stations.mapNotNull { it.countryCode }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moods: StateFlow<List<String>> = allStations
        .map { stations -> stations.mapNotNull { it.mood }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val styles: StateFlow<List<String>> = allStations
        .map { stations -> stations.mapNotNull { it.style }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stations: StateFlow<List<StationListItem>> = combine(allStations, _filter) { stations, filter ->
        stations
            .filter { filter.genre == null || filter.genre in it.genres }
            .filter { filter.countryCode == null || it.countryCode == filter.countryCode }
            .filter { filter.mood == null || it.mood == filter.mood }
            .filter { filter.style == null || it.style == filter.style }
            .filter { !filter.favoritesOnly || it.isFavorite }
            // Favourites float to the top, alphabetical among themselves; everyone
            // else keeps the underlying (most-recently-added-first) order.
            .sortedWith(compareByDescending<ResolvedStation> { it.isFavorite }.thenBy { if (it.isFavorite) it.name.lowercase() else "" })
            .map { StationListItem(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setViewMode(mode: StationViewMode) {
        _viewMode.value = mode
    }

    fun toggleGenreFilter(genre: String) {
        _filter.value = _filter.value.copy(genre = if (_filter.value.genre == genre) null else genre)
    }

    fun toggleCountryFilter(countryCode: String) {
        _filter.value = _filter.value.copy(countryCode = if (_filter.value.countryCode == countryCode) null else countryCode)
    }

    fun toggleMoodFilter(mood: String) {
        _filter.value = _filter.value.copy(mood = if (_filter.value.mood == mood) null else mood)
    }

    fun toggleStyleFilter(style: String) {
        _filter.value = _filter.value.copy(style = if (_filter.value.style == style) null else style)
    }

    fun toggleFavoritesOnly() {
        _filter.value = _filter.value.copy(favoritesOnly = !_filter.value.favoritesOnly)
    }

    class Factory(private val stationDao: StationDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(stationDao) as T
    }
}

data class StationListItem(
    val station: ResolvedStation
)
