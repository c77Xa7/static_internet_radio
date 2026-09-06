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

class HomeViewModel(
    private val stationDao: StationDao,
    private val settingsRepository: com.staticradio.app.data.settings.SettingsRepository? = null
) : ViewModel() {

    private val _viewMode = MutableStateFlow(StationViewMode.LIST)
    val viewMode: StateFlow<StationViewMode> = _viewMode

    private val _filter = MutableStateFlow(HomeFilter())
    val filter: StateFlow<HomeFilter> = _filter

    // User's saved reorder (Settings -> Ordering) — empty until they've used
    // the reorder screen at least once.
    private val savedOrder = settingsRepository?.stationOrder
        ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    // Free-text search over the station NAME (top bar magnifier) — separate
    // from the Filter dialog, which filters by attributes. Substring,
    // case-insensitive; blank means no search.
    private val _nameSearch = MutableStateFlow("")
    val nameSearch: StateFlow<String> = _nameSearch

    fun setNameSearch(query: String) {
        _nameSearch.value = query
    }

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

    val stations: StateFlow<List<StationListItem>> = combine(
        allStations, _filter, savedOrder, _nameSearch
    ) { stations, filter, order, search ->
        stations
            .filter { filter.genre == null || filter.genre in it.genres }
            .filter { filter.countryCode == null || it.countryCode == filter.countryCode }
            .filter { filter.mood == null || it.mood == filter.mood }
            .filter { filter.style == null || it.style == filter.style }
            .filter { !filter.favoritesOnly || it.isFavorite }
            .filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
            // Favourites float to the top as their own block; within each
            // block, the user's saved reorder order wins (Settings ->
            // Ordering -> Reorder stations), falling back to alphabetical
            // (favourites) / most-recently-added-first (the rest) for
            // anything not in the saved list.
            .let { list ->
                val rank = order.withIndex().associate { (i, id) -> id to i }
                list.sortedWith(
                    compareByDescending<ResolvedStation> { it.isFavorite }
                        .thenBy { rank[it.id] ?: Int.MAX_VALUE }
                        .thenBy { if (it.isFavorite) it.name.lowercase() else "" }
                        .thenByDescending { it.id } // stable-ish fallback for unsaved ties
                )
            }
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

    class Factory(
        private val stationDao: StationDao,
        private val settingsRepository: com.staticradio.app.data.settings.SettingsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(stationDao, settingsRepository) as T
    }
}

data class StationListItem(
    val station: ResolvedStation
)
