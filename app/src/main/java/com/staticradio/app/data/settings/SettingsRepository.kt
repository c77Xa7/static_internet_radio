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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentColor { REBAR, SIGNAL_BLUE, HAZARD_LIME }
enum class ImageShape { CIRCLE, SQUARE, ROUNDED }
enum class DefaultDestination { STATION_LIST, STATION_GRID, STATION_MAP, MIXES }

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
        val CAST_ENABLED = booleanPreferencesKey("cast_enabled")
        val CAST_PRE_BUFFER = booleanPreferencesKey("cast_pre_buffer")
        val TIME_ZONE_ID = stringPreferencesKey("time_zone_id")
        val DEFAULT_DESTINATION = stringPreferencesKey("default_destination")
        val STATION_ORDER = stringPreferencesKey("station_order_json")
        val MIX_ORDER = stringPreferencesKey("mix_order_json")
        val SHUFFLE_QUEUE = stringPreferencesKey("shuffle_queue_json")
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

    // Off by default and opt-in only — Play Services / the Cast SDK are never
    // touched unless the user explicitly turns this on (see RadioPlaybackService).
    val castEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.CAST_ENABLED] ?: false
    }

    suspend fun setCastEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CAST_ENABLED] = enabled }
    }

    // Opt-in burst-buffer proxy for casting (see BurstBufferProxy) — keeps a
    // warm shadow-fetch ring buffer of upcoming stations so a Chromecast
    // receiver's ~256KB appetite is cleared at LAN speed instead of 1x
    // realtime. Doubles the bandwidth for buffered stations while active,
    // hence off by default.
    val castPreBuffer: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.CAST_PRE_BUFFER] ?: false
    }

    suspend fun setCastPreBuffer(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CAST_PRE_BUFFER] = enabled }
    }

    // IANA zone the user says they're in. Empty string = follow the device
    // default (ZoneId.systemDefault()). Used by StationLiveWindow to decide
    // whether a station's defined live hours mean it's currently Online.
    val timeZoneId: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.TIME_ZONE_ID] ?: ""
    }

    suspend fun setTimeZoneId(value: String) {
        context.settingsDataStore.edit { it[Keys.TIME_ZONE_ID] = value }
    }

    val defaultDestination: Flow<DefaultDestination> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_DESTINATION]?.let { runCatching { DefaultDestination.valueOf(it) }.getOrNull() }
            ?: DefaultDestination.STATION_LIST
    }

    suspend fun setDefaultDestination(value: DefaultDestination) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_DESTINATION] = value.name }
    }

    // User reorderings, persisted as ordered ID lists. IDs not present in the
    // list (newly added items) sort after the known ones by their natural
    // order — no schema change, no Room version bump, no data wipe.
    val stationOrder: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        decodeIdList(prefs[Keys.STATION_ORDER])
    }

    suspend fun setStationOrder(ids: List<String>) {
        context.settingsDataStore.edit { it[Keys.STATION_ORDER] = Json.encodeToString(ids) }
    }

    val mixOrder: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        decodeIdList(prefs[Keys.MIX_ORDER])
    }

    suspend fun setMixOrder(ids: List<String>) {
        context.settingsDataStore.edit { it[Keys.MIX_ORDER] = Json.encodeToString(ids) }
    }

    // The pre-decided random queue backing the shuffle button: when pre-buffer
    // is on, the next stations are known ahead of time so they can be warmed
    // before the user ever presses Shuffle. Recomputed by the service when it
    // runs out. Empty = decide randomly at tap time (pre-buffer off).
    val shuffleQueue: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        decodeIdList(prefs[Keys.SHUFFLE_QUEUE])
    }

    suspend fun setShuffleQueue(ids: List<String>) {
        context.settingsDataStore.edit { it[Keys.SHUFFLE_QUEUE] = Json.encodeToString(ids) }
    }

    private fun decodeIdList(raw: String?): List<String> =
        raw?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
}
