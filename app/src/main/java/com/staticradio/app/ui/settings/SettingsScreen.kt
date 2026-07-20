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
    val moodVocabulary by stationDao.observeTagsByType(com.staticradio.app.data.local.TagType.MOOD).collectAsState(initial = emptyList())
    val styleVocabulary by stationDao.observeTagsByType(com.staticradio.app.data.local.TagType.STYLE).collectAsState(initial = emptyList())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.export(context.contentResolver, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.import(context, it) } }

    val exportMixesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportMixes(context.contentResolver, it) } }

    val importMixesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMixes(context, it) } }

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
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
                "Radio stations",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Backs up every station field — genre/mood/style, coordinates, description, language, popularity — plus your full Genre/Mood/Style vocabularies. A Transistor export can still be imported directly, but Transistor's format doesn't carry those extra fields.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
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
                "Saved mixes",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                "STATIC's own zip format — export bundles the tracklist and any locally-uploaded images.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportMixesLauncher.launch("static-mixes_$timestamp.zip")
                }) { Text("Export") }
                OutlinedButton(onClick = { importMixesLauncher.launch(arrayOf("*/*")) }) {
                    Text("Import")
                }
            }
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
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Column { content() }
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
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text("Grid spacing: ${spacing.toInt()}dp", style = MaterialTheme.typography.labelMedium)
        Slider(value = spacing, onValueChange = onSpacingChange, valueRange = 12f..64f)

        Text("Grid line width: ${"%.1f".format(lineWidth)}dp", style = MaterialTheme.typography.labelMedium)
        Slider(value = lineWidth, onValueChange = onLineWidthChange, valueRange = 0.5f..4f)

        Text("Grid opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(value = opacity, onValueChange = onOpacityChange, valueRange = 0f..1f)

        OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) { Text("Reset to default") }
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
