package com.staticradio.app.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.data.backup.BackupManager
import com.staticradio.app.data.backup.MixBackupManager
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.TagEntity
import com.staticradio.app.data.local.TagType
import com.staticradio.app.data.settings.AccentColor
import com.staticradio.app.data.settings.DefaultDestination
import com.staticradio.app.data.settings.ImageShape
import com.staticradio.app.data.settings.SettingsRepository
import com.staticradio.app.data.settings.ThemeMode
import com.staticradio.app.playback.PlaybackRepository
import com.staticradio.app.playback.RadioController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Applies a saved ID order: known IDs first (in saved order), then the rest in natural order. */
private fun <T> applySavedOrder(list: List<T>, savedOrder: List<String>, idOf: (T) -> String): List<T> {
    if (savedOrder.isEmpty()) return list
    val rank = savedOrder.withIndex().associate { (i, id) -> id to i }
    return list.sortedBy { rank[idOf(it)] ?: Int.MAX_VALUE }
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val stationDao: StationDao,
    private val mixDao: MixDao,
    private val radioController: RadioController,
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val backupManager = BackupManager(stationDao)
    private val mixBackupManager = MixBackupManager(mixDao)

    val sleepTimerEndAtMillis: StateFlow<Long?> = playbackRepository.sleepTimerEndAtMillis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    val accentColor: StateFlow<AccentColor> = settingsRepository.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccentColor.REBAR)
    val imageShape: StateFlow<ImageShape> = settingsRepository.imageShape
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImageShape.SQUARE)
    val normalizeVolume: StateFlow<Boolean> = settingsRepository.normalizeVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showBackgroundGrid: StateFlow<Boolean> = settingsRepository.showBackgroundGrid
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridSpacingDp: StateFlow<Float> = settingsRepository.gridSpacingDp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.staticradio.app.data.settings.DEFAULT_GRID_SPACING_DP)
    val gridLineWidthDp: StateFlow<Float> = settingsRepository.gridLineWidthDp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.staticradio.app.data.settings.DEFAULT_GRID_LINE_WIDTH_DP)
    val gridOpacity: StateFlow<Float> = settingsRepository.gridOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.staticradio.app.data.settings.DEFAULT_GRID_OPACITY)
    val bufferSeconds: StateFlow<Int> = settingsRepository.bufferSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.staticradio.app.data.settings.DEFAULT_BUFFER_SECONDS)
    val castEnabled: StateFlow<Boolean> = settingsRepository.castEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val castPreBuffer: StateFlow<Boolean> = settingsRepository.castPreBuffer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val timeZoneId: StateFlow<String> = settingsRepository.timeZoneId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val defaultDestination: StateFlow<DefaultDestination> = settingsRepository.defaultDestination
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultDestination.STATION_LIST)

    val genreVocabulary: StateFlow<List<TagEntity>> = stationDao.observeTagsByType(TagType.GENRE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _newTagName = MutableStateFlow("")
    val newTagName: StateFlow<String> = _newTagName

    private val _importExportMessage = MutableStateFlow<String?>(null)
    val importExportMessage: StateFlow<String?> = _importExportMessage

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { settingsRepository.setAccentColor(color) }
    fun setImageShape(shape: ImageShape) = viewModelScope.launch { settingsRepository.setImageShape(shape) }
    fun setNormalizeVolume(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNormalizeVolume(enabled) }
    fun setShowBackgroundGrid(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowBackgroundGrid(enabled) }
    fun setGridSpacingDp(value: Float) = viewModelScope.launch { settingsRepository.setGridSpacingDp(value) }
    fun setGridLineWidthDp(value: Float) = viewModelScope.launch { settingsRepository.setGridLineWidthDp(value) }
    fun setGridOpacity(value: Float) = viewModelScope.launch { settingsRepository.setGridOpacity(value) }
    fun resetGridDefaults() = viewModelScope.launch { settingsRepository.resetGridDefaults() }
    fun setBufferSeconds(value: Int) = viewModelScope.launch { settingsRepository.setBufferSeconds(value) }
    fun setCastEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setCastEnabled(enabled) }
    fun setCastPreBuffer(enabled: Boolean) = viewModelScope.launch { settingsRepository.setCastPreBuffer(enabled) }
    fun setTimeZoneId(value: String) = viewModelScope.launch { settingsRepository.setTimeZoneId(value) }
    fun setDefaultDestination(value: DefaultDestination) = viewModelScope.launch { settingsRepository.setDefaultDestination(value) }

    // ---- Reordering ----

    // Raw DAO snapshots for the reorder screens, exposed as one-shot loads —
    // the reorder UI holds its own working copy and commits the whole order
    // on save, so a live Flow would fight with in-flight edits.
    private val _stationReorderList = MutableStateFlow<List<StationReorderItem>>(emptyList())
    val stationReorderList: StateFlow<List<StationReorderItem>> = _stationReorderList

    private val _mixReorderList = MutableStateFlow<List<MixReorderItem>>(emptyList())
    val mixReorderList: StateFlow<List<MixReorderItem>> = _mixReorderList

    data class StationReorderItem(val id: String, val title: String, val isFavorite: Boolean)
    data class MixReorderItem(val id: String, val title: String, val isFavorite: Boolean)

    fun loadStationOrder() {
        viewModelScope.launch {
            val savedOrder = settingsRepository.stationOrder.first()
            _stationReorderList.value = stationDao.getAllStationsOnce()
                .map { StationReorderItem(it.id, it.nameOverride ?: it.nameSource ?: "Unknown station", it.isFavorite) }
                // Favourites block first, then everything else — within each
                // block the saved order wins. Without the favourite-first sort
                // the raw DAO order interleaved the two sections. The final
                // stable re-sort also guards against saved ranks pulling a
                // rank-less favourite below a ranked non-favourite.
                .let { applySavedOrder(it, savedOrder) { item: StationReorderItem -> item.id } }
                .sortedByDescending { it.isFavorite }
        }
    }

    fun loadMixOrder() {
        viewModelScope.launch {
            val savedOrder = settingsRepository.mixOrder.first()
            _mixReorderList.value = mixDao.getAllMixesOnce()
                .map { MixReorderItem(it.id, it.fullTitle ?: it.url, it.isFavorite) }
                .let { applySavedOrder(it, savedOrder) { item: MixReorderItem -> item.id } }
                .sortedByDescending { it.isFavorite }
        }
    }

    fun moveStation(fromIndex: Int, toIndex: Int) {
        _stationReorderList.value = _stationReorderList.value.toMutableList()
            .apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun moveMix(fromIndex: Int, toIndex: Int) {
        _mixReorderList.value = _mixReorderList.value.toMutableList()
            .apply { add(toIndex, removeAt(fromIndex)) }
    }

    /**
     * Commits the working order. Favourites stay a contiguous top block and
     * non-favourites stay below — the UI only lets the user reorder *within*
     * each section, so this just persists both sections' internal orders.
     */
    fun saveStationOrder() {
        viewModelScope.launch {
            val items = _stationReorderList.value
            settingsRepository.setStationOrder(items.map { it.id })
            _importExportMessage.value = "Station order saved"
        }
    }

    fun saveMixOrder() {
        viewModelScope.launch {
            val items = _mixReorderList.value
            settingsRepository.setMixOrder(items.map { it.id })
            _importExportMessage.value = "Mix order saved"
        }
    }

    fun setNewTagName(value: String) { _newTagName.value = value }

    fun addGenreTag() {
        val name = _newTagName.value.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            stationDao.insertTag(TagEntity(name = name, type = TagType.GENRE))
            _newTagName.value = ""
        }
    }

    fun deleteGenreTag(tag: TagEntity) {
        viewModelScope.launch {
            stationDao.clearCrossRefsForTag(tag.id)
            stationDao.deleteTag(tag.id)
        }
    }

    fun export(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _importExportMessage.value = try {
                backupManager.export(contentResolver, uri)
                "Exported successfully"
            } catch (e: Exception) {
                "Export failed"
            }
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importExportMessage.value = try {
                val count = backupManager.import(context, uri)
                "Imported $count station(s)"
            } catch (e: Exception) {
                "Import failed — check the file is a Transistor or STATIC backup zip"
            }
        }
    }

    fun exportMixes(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _importExportMessage.value = try {
                mixBackupManager.export(contentResolver, uri)
                "Mixes exported successfully"
            } catch (e: Exception) {
                "Mix export failed"
            }
        }
    }

    fun importMixes(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importExportMessage.value = try {
                val count = mixBackupManager.import(context, uri)
                "Imported $count mix(es)"
            } catch (e: Exception) {
                "Mix import failed — check the file is a STATIC mixes backup zip"
            }
        }
    }

    fun clearMessage() { _importExportMessage.value = null }

    fun startSleepTimer(durationMillis: Long) = radioController.startSleepTimer(durationMillis)

    fun cancelSleepTimer() = radioController.cancelSleepTimer()

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val stationDao: StationDao,
        private val mixDao: MixDao,
        private val radioController: RadioController,
        private val playbackRepository: PlaybackRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, stationDao, mixDao, radioController, playbackRepository) as T
    }
}
