package com.staticradio.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentColor { REBAR, SIGNAL_BLUE, HAZARD_LIME }
enum class ImageShape { CIRCLE, SQUARE, ROUNDED }

const val DEFAULT_GRID_SPACING_DP = 28f
const val DEFAULT_GRID_LINE_WIDTH_DP = 1f
const val DEFAULT_GRID_OPACITY = 1f
const val DEFAULT_BUFFER_SECONDS = 10

private val Context.settingsDataStore by preferencesDataStore(name = "static_settings")

/**
 * App-level appearance settings — DataStore, not Room, per PROJECT_CONTEXT.md
 * ("Settings ... belong in DataStore, NOT Room — app-level singleton state,
 * not per-station data").
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val IMAGE_SHAPE = stringPreferencesKey("image_shape")
        val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")
        val SHOW_BACKGROUND_GRID = booleanPreferencesKey("show_background_grid")
        val GRID_SPACING_DP = floatPreferencesKey("grid_spacing_dp")
        val GRID_LINE_WIDTH_DP = floatPreferencesKey("grid_line_width_dp")
        val GRID_OPACITY = floatPreferencesKey("grid_opacity")
        val BUFFER_SECONDS = intPreferencesKey("buffer_seconds")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val accentColor: Flow<AccentColor> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ACCENT_COLOR]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.REBAR
    }

    val imageShape: Flow<ImageShape> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.IMAGE_SHAPE]?.let { runCatching { ImageShape.valueOf(it) }.getOrNull() } ?: ImageShape.SQUARE
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.settingsDataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    suspend fun setImageShape(shape: ImageShape) {
        context.settingsDataStore.edit { it[Keys.IMAGE_SHAPE] = shape.name }
    }

    val normalizeVolume: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.NORMALIZE_VOLUME] ?: false
    }

    suspend fun setNormalizeVolume(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NORMALIZE_VOLUME] = enabled }
    }

    val showBackgroundGrid: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.SHOW_BACKGROUND_GRID] ?: true
    }

    suspend fun setShowBackgroundGrid(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_BACKGROUND_GRID] = enabled }
    }

    val gridSpacingDp: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.GRID_SPACING_DP] ?: DEFAULT_GRID_SPACING_DP
    }

    suspend fun setGridSpacingDp(value: Float) {
        context.settingsDataStore.edit { it[Keys.GRID_SPACING_DP] = value }
    }

    val gridLineWidthDp: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.GRID_LINE_WIDTH_DP] ?: DEFAULT_GRID_LINE_WIDTH_DP
    }

    suspend fun setGridLineWidthDp(value: Float) {
        context.settingsDataStore.edit { it[Keys.GRID_LINE_WIDTH_DP] = value }
    }

    val gridOpacity: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.GRID_OPACITY] ?: DEFAULT_GRID_OPACITY
    }

    suspend fun setGridOpacity(value: Float) {
        context.settingsDataStore.edit { it[Keys.GRID_OPACITY] = value }
    }

    suspend fun resetGridDefaults() {
        context.settingsDataStore.edit {
            it[Keys.GRID_SPACING_DP] = DEFAULT_GRID_SPACING_DP
            it[Keys.GRID_LINE_WIDTH_DP] = DEFAULT_GRID_LINE_WIDTH_DP
            it[Keys.GRID_OPACITY] = DEFAULT_GRID_OPACITY
        }
    }

    val bufferSeconds: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.BUFFER_SECONDS] ?: DEFAULT_BUFFER_SECONDS
    }

    suspend fun setBufferSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.BUFFER_SECONDS] = value }
    }
}
