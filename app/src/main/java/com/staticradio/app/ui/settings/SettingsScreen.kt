package com.staticradio.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.staticradio.app.BuildConfig
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.settings.AccentColor
import com.staticradio.app.data.settings.DEFAULT_BUFFER_SECONDS
import com.staticradio.app.data.settings.ImageShape
import com.staticradio.app.data.settings.SettingsRepository
import com.staticradio.app.data.settings.ThemeMode
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import com.staticradio.app.playback.PlaybackRepository
import com.staticradio.app.playback.RadioController
import com.staticradio.app.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val MAX_SLEEP_TIMER_MINUTES = 24 * 60
private const val KOFI_URL = "https://ko-fi.com/W4T623HDPA"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    stationDao: StationDao,
    mixDao: MixDao,
    radioController: RadioController,
    playbackRepository: PlaybackRepository,
    onManageGenres: () -> Unit,
    onManageMoods: () -> Unit,
    onManageStyles: () -> Unit,
    onReorderStations: () -> Unit = {},
    onReorderMixes: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(settingsRepository, stationDao, mixDao, radioController, playbackRepository)
    )
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val imageShape by viewModel.imageShape.collectAsState()
    val genreVocabulary by viewModel.genreVocabulary.collectAsState()
    val message by viewModel.importExportMessage.collectAsState()
    val sleepTimerEndAtMillis by viewModel.sleepTimerEndAtMillis.collectAsState()
    val normalizeVolume by viewModel.normalizeVolume.collectAsState()
    val showBackgroundGrid by viewModel.showBackgroundGrid.collectAsState()
    val gridSpacingDp by viewModel.gridSpacingDp.collectAsState()
    val gridLineWidthDp by viewModel.gridLineWidthDp.collectAsState()
    val gridOpacity by viewModel.gridOpacity.collectAsState()
    val bufferSeconds by viewModel.bufferSeconds.collectAsState()
    val castEnabled by viewModel.castEnabled.collectAsState()
    val castPreBuffer by viewModel.castPreBuffer.collectAsState()
    val timeZoneId by viewModel.timeZoneId.collectAsState()
    val defaultDestination by viewModel.defaultDestination.collectAsState()
    val moodVocabulary by stationDao.observeTagsByType(com.staticradio.app.data.local.TagType.MOOD).collectAsState(initial = emptyList())
    val styleVocabulary by stationDao.observeTagsByType(com.staticradio.app.data.local.TagType.STYLE).collectAsState(initial = emptyList())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.export(context.contentResolver, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.import(context, it) } }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalPlayerBarBottomInset.current)
    ) {
        if (message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = viewModel::clearMessage) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
        }

        // ---- Appearance ----
        SettingsCategory(title = "Appearance") {
            // Switch enforces a 48dp minimum touch target by default, which pads
            // it well beyond its visual size and was the real source of the extra
            // gap between these two adjacent rows — suppressed just here.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
            ) {
                SettingsRow(label = "Dark mode") {
                    Switch(
                        checked = themeMode == ThemeMode.DARK,
                        onCheckedChange = { viewModel.setThemeMode(if (it) ThemeMode.DARK else ThemeMode.LIGHT) }
                    )
                }
                SettingsRow(label = "Follow system") {
                    Switch(
                        checked = themeMode == ThemeMode.SYSTEM,
                        onCheckedChange = { if (it) viewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                }
            }
            SettingsRow(label = "Accent") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentColor.entries.forEach { accent ->
                        ColorSwatch(
                            color = accent.toComposeColor(),
                            selected = accent == accentColor,
                            onClick = { viewModel.setAccentColor(accent) }
                        )
                    }
                }
            }
            SettingsRow(label = "Station artwork shape") {
                ImageShapeDropdown(selected = imageShape, onSelect = viewModel::setImageShape)
            }
            SettingsRow(label = "Show background grid") {
                Switch(
                    checked = showBackgroundGrid,
                    onCheckedChange = viewModel::setShowBackgroundGrid
                )
            }
            if (showBackgroundGrid) {
                var gridControlsExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                SettingsRow(label = "Adjust grid") {
                    OutlinedButton(onClick = { gridControlsExpanded = !gridControlsExpanded }) {
                        Text(if (gridControlsExpanded) "Hide" else "Show")
                    }
                }
                if (gridControlsExpanded) {
                    GridCustomizationControls(
                        spacing = gridSpacingDp,
                        lineWidth = gridLineWidthDp,
                        opacity = gridOpacity,
                        onSpacingChange = viewModel::setGridSpacingDp,
                        onLineWidthChange = viewModel::setGridLineWidthDp,
                        onOpacityChange = viewModel::setGridOpacity,
                        onReset = viewModel::resetGridDefaults
                    )
                }
            }
        }

        // ---- Playback ----
        SettingsCategory(title = "Playback") {
            SettingsRow(label = "Normalize volume") {
                Switch(
                    checked = normalizeVolume,
                    onCheckedChange = viewModel::setNormalizeVolume
                )
            }
            Text(
                "Smooths out loud/quiet stations in real time by measuring the signal and slowly adjusting gain toward a consistent level — there's no loudness data in internet radio streams to read, so this listens and adapts instead.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsRow(label = "Stream buffer: ${bufferSeconds}s") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionButton(label = "-", active = false, onClick = { viewModel.setBufferSeconds((bufferSeconds - 5).coerceAtLeast(5)) })
                    OptionButton(label = "+", active = false, onClick = { viewModel.setBufferSeconds((bufferSeconds + 5).coerceAtMost(120)) })
                }
            }
            Text(
                "A bigger buffer holds more audio ahead of playback, trading a slightly slower start for better resilience against network drops — same idea as Transistor's buffer setting. Takes effect next time a station starts playing.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Cast ----
        SettingsCategory(title = "Cast") {
            SettingsRow(label = "Chromecast") {
                Switch(
                    checked = castEnabled,
                    onCheckedChange = viewModel::setCastEnabled
                )
            }
            Text(
                "Adds a cast button to the player bar for streaming to a Chromecast device on your network. Off by default — enabling this is the only thing in the app that touches Google Play Services.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsRow(label = "Pre-buffer for casting") {
                Switch(
                    checked = castPreBuffer,
                    onCheckedChange = viewModel::setCastPreBuffer
                )
            }
            Text(
                "Warns: uses more data. Keeps a ready-made buffer of the next 3 stations (the ones 'Next' would play) and the next 3 random stations (the ones 'Shuffle' would play) — so casting to a Chromecast starts instantly instead of stalling ~15 seconds while the receiver fills up. Uses extra data: each upcoming station is fetched a second time in the background alongside whatever you're listening to.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Region & time ----
        SettingsCategory(title = "Region & time") {
            SettingsRow(label = "Time zone") {
                TimeZonePicker(
                    selectedZoneId = timeZoneId,
                    onSelect = viewModel::setTimeZoneId
                )
            }
            Text(
                "Used to work out whether a station's defined broadcast hours mean it's currently Online or Offline — the app converts the station's hours from its own country's clock into yours. Leave on \"Device default\" unless you live somewhere your phone doesn't.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Ordering ----
        SettingsCategory(title = "Ordering") {
            SettingsRow(label = "Reorder stations") {
                OutlinedButton(onClick = onReorderStations) { Text("Open") }
            }
            Text(
                "Favourites stay pinned as their own top section — you can reorder within favourites, and within everything else below them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsRow(label = "Reorder mixes") {
                OutlinedButton(onClick = onReorderMixes) { Text("Open") }
            }
        }

        // ---- Startup ----
        SettingsCategory(title = "Startup") {
            SettingsRow(label = "Open on") {
                DefaultDestinationDropdown(
                    selected = defaultDestination,
                    onSelect = viewModel::setDefaultDestination
                )
            }
            Text(
                "Which screen the app opens on: the station list, the station grid, the station map, or your saved mixes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Sleep timer ----
        SettingsCategory(title = "Sleep timer") {
            SleepTimerControls(
                endAtMillis = sleepTimerEndAtMillis,
                onStart = viewModel::startSleepTimer,
                onCancel = viewModel::cancelSleepTimer
            )
        }

        // ---- Vocabularies ----
        SettingsCategory(title = "Vocabularies") {
            SettingsRow(label = "${genreVocabulary.size} genre(s) defined") {
                OutlinedButton(onClick = onManageGenres) { Text("Manage") }
            }
            SettingsRow(label = "${moodVocabulary.size} mood(s) defined") {
                OutlinedButton(onClick = onManageMoods) { Text("Manage") }
            }
            SettingsRow(label = "${styleVocabulary.size} style(s) defined") {
                OutlinedButton(onClick = onManageStyles) { Text("Manage") }
            }
        }

        // ---- Backup ----
        SettingsCategory(title = "Backup") {
            Text(
                "One backup, everything: all stations (every field — genre/mood/style, live broadcast hours, coordinates, description, language, popularity), all saved mixes with their tracklists, the full Genre/Mood/Style vocabularies, and any locally-uploaded images.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportLauncher.launch("static-backup_$timestamp.zip")
                }) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Text("Import")
                }
            }
            Text(
                "Imports from older STATIC backups (stations-only or mixes-only) still work — everything each of those files contains is restored.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        KofiButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL))) }
        })

        Text(
            text = "Build ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
    }
}

@Composable
private fun SettingsCategory(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.0.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Uniform vertical rhythm inside every category card: rows and
            // helper texts contribute no external padding of their own — the
            // Card's internal Column enforces the spacing, so no section can
            // end up visually denser or airier than another.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun GridCustomizationControls(
    spacing: Float,
    lineWidth: Float,
    opacity: Float,
    onSpacingChange: (Float) -> Unit,
    onLineWidthChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Grid spacing: ${spacing.toInt()}dp", style = MaterialTheme.typography.labelMedium)
        Slider(value = spacing, onValueChange = onSpacingChange, valueRange = 12f..64f)

        Text("Grid line width: ${"%.1f".format(lineWidth)}dp", style = MaterialTheme.typography.labelMedium)
        Slider(value = lineWidth, onValueChange = onLineWidthChange, valueRange = 0.5f..4f)

        Text("Grid opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(value = opacity, onValueChange = onOpacityChange, valueRange = 0f..1f)

        OutlinedButton(onClick = onReset) { Text("Reset to default") }
    }
}

@Composable
private fun KofiButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF525252))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Support me on Ko-fi", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SettingsRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        trailing()
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val keyline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(1.0.dp, keyline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(if (selected) 14.dp else 16.dp)
    )
}

@Composable
private fun SleepTimerControls(
    endAtMillis: Long?,
    onStart: (Long) -> Unit,
    onCancel: () -> Unit
) {
    if (endAtMillis != null) {
        var remainingMillis by remember(endAtMillis) { mutableLongStateOf(endAtMillis - System.currentTimeMillis()) }
        LaunchedEffect(endAtMillis) {
            while (remainingMillis > 0) {
                delay(1000)
                remainingMillis = endAtMillis - System.currentTimeMillis()
            }
        }
        val clamped = remainingMillis.coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(clamped)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(clamped) % 60
        SettingsRow(label = "Stops in %02d:%02d".format(minutes, seconds)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    } else {
        var hours by remember { mutableIntStateOf(0) }
        var minutes by remember { mutableIntStateOf(30) }

        SettingsRow(label = "Hours") {
            Stepper(
                value = hours,
                onDecrement = { hours = (hours - 1).coerceAtLeast(0) },
                onIncrement = {
                    val next = hours + 1
                    if (next * 60 + minutes <= MAX_SLEEP_TIMER_MINUTES) hours = next
                }
            )
        }
        SettingsRow(label = "Minutes") {
            Stepper(
                value = minutes,
                onDecrement = { minutes = (minutes - 15).coerceAtLeast(0) },
                onIncrement = {
                    val next = minutes + 15
                    if (hours * 60 + next <= MAX_SLEEP_TIMER_MINUTES) minutes = next
                }
            )
        }
        SettingsRow(label = "Playback stops automatically") {
            val totalMinutes = hours * 60 + minutes
            OutlinedButton(
                enabled = totalMinutes > 0,
                onClick = { onStart(TimeUnit.MINUTES.toMillis(totalMinutes.toLong())) }
            ) { Text("Start") }
        }
    }
}

@Composable
private fun Stepper(value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionButton(label = "-", active = false, onClick = onDecrement)
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        OptionButton(label = "+", active = false, onClick = onIncrement)
    }
}

@Composable
private fun ImageShapeDropdown(selected: ImageShape, onSelect: (ImageShape) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val keyline = MaterialTheme.colorScheme.outline
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.0.dp, keyline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selected.name.lowercase(), style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ImageShape.entries.forEach { shape ->
                DropdownMenuItem(
                    text = { Text(shape.name.lowercase()) },
                    onClick = { onSelect(shape); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun OptionButton(label: String, active: Boolean, onClick: () -> Unit) {
    val keyline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.0.dp, keyline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DefaultDestinationDropdown(
    selected: com.staticradio.app.data.settings.DefaultDestination,
    onSelect: (com.staticradio.app.data.settings.DefaultDestination) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val keyline = MaterialTheme.colorScheme.outline
    val labels = mapOf(
        com.staticradio.app.data.settings.DefaultDestination.STATION_LIST to "Station list",
        com.staticradio.app.data.settings.DefaultDestination.STATION_GRID to "Station grid",
        com.staticradio.app.data.settings.DefaultDestination.STATION_MAP to "Station map",
        com.staticradio.app.data.settings.DefaultDestination.MIXES to "Mixes"
    )
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.0.dp, keyline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(labels[selected] ?: selected.name, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.staticradio.app.data.settings.DefaultDestination.entries.forEach { dest ->
                DropdownMenuItem(
                    text = { Text(labels[dest] ?: dest.name) },
                    onClick = { onSelect(dest); expanded = false }
                )
            }
        }
    }
}

/**
 * Searchable IANA time zone picker. Every JDK-known zone id with its current
 * UTC offset, filtered by a query box — there are ~600, so search is the
 * primary interaction (same pattern as the country picker).
 *
 * Deliberately an AlertDialog, not a DropdownMenu: a LazyColumn nested inside
 * DropdownMenu's own scrollable content column gets measured with infinite
 * height and crashes — the dropdown crashed on open every time.
 */
@Composable
private fun TimeZonePicker(selectedZoneId: String, onSelect: (String) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val keyline = MaterialTheme.colorScheme.outline

    val allZones = remember {
        java.time.ZoneId.getAvailableZoneIds().map { id ->
            val offset = java.time.ZoneId.of(id).rules.getOffset(java.time.Instant.now()).toString()
            id to offset
        }.sortedBy { it.first }
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.0.dp, keyline, RoundedCornerShape(8.dp))
                .clickable { expanded = true; query = "" }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedZoneId.isBlank()) "Device default" else selectedZoneId,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
        }
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = { Text("Time zone") },
                text = {
                    Column {
                        androidx.compose.material3.OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search time zones", style = MaterialTheme.typography.labelMedium) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        // Bounded height: the list scrolls inside its own box,
                        // never inheriting the dialog's constraints.
                        val filtered = remember(query) {
                            if (query.isBlank()) allZones
                            else allZones.filter { it.first.contains(query.trim(), ignoreCase = true) }.take(120)
                        }
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(filtered.size) { index ->
                                val (id, offset) = filtered[index]
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(id, style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                "UTC$offset",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (id == selectedZoneId) Text("✓", color = MaterialTheme.colorScheme.primary)
                                    },
                                    onClick = {
                                        onSelect(id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { expanded = false }) { Text("Cancel") }
                }
            )
        }
    }
}
