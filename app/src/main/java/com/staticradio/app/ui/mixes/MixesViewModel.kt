package com.staticradio.app.ui.mixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.MixWithTracks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MixFilter(
    val genre: String? = null,
    val mood: String? = null,
    val style: String? = null,
    val favoritesOnly: Boolean = false
)

class MixesViewModel(private val mixDao: MixDao) : ViewModel() {

    private val _filter = MutableStateFlow(MixFilter())
    val filter: StateFlow<MixFilter> = _filter

    private val allMixes = mixDao.observeMixesWithTracks()

    val genres: StateFlow<List<String>> = allMixes
        .map { list -> list.flatMap { it.mix.genre?.let(GenreTags::parse) ?: emptyList() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val moods: StateFlow<List<String>> = allMixes
        .map { list -> list.mapNotNull { it.mix.mood }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val styles: StateFlow<List<String>> = allMixes
        .map { list -> list.mapNotNull { it.mix.style }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mixes: StateFlow<List<MixWithTracks>> = combine(allMixes, _filter) { list, filter ->
        list
            .filter { filter.genre == null || filter.genre in (it.mix.genre?.let(GenreTags::parse) ?: emptyList()) }
            .filter { filter.mood == null || it.mix.mood == filter.mood }
            .filter { filter.style == null || it.mix.style == filter.style }
            .filter { !filter.favoritesOnly || it.mix.isFavorite }
            .sortedWith(
                compareByDescending<MixWithTracks> { it.mix.isFavorite }
                    .thenByDescending { it.mix.dateAddedEpochMillis }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleGenreFilter(genre: String) {
        _filter.value = _filter.value.copy(genre = if (_filter.value.genre == genre) null else genre)
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

    class Factory(private val mixDao: MixDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MixesViewModel(mixDao) as T
    }
}
