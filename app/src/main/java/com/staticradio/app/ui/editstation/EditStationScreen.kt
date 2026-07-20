package com.staticradio.app.ui.editstation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.TagType
import com.staticradio.app.ui.common.CountryCodeField
import com.staticradio.app.ui.common.GenreVocabularyField
import com.staticradio.app.ui.common.LanguageField
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import com.staticradio.app.ui.common.TagPickerField
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStationScreen(
    stationId: String,
    stationDao: StationDao,
    onBack: () -> Unit,
    onPickLocation: () -> Unit,
    pickedLatitude: Double? = null,
    pickedLongitude: Double? = null,
    modifier: Modifier = Modifier
) {
    val viewModel: EditStationViewModel = viewModel(
        key = stationId,
        factory = EditStationViewModel.Factory(stationId, stationDao)
    )
    val isLoaded by viewModel.isLoaded.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pickedLatitude, pickedLongitude) {
        if (pickedLatitude != null && pickedLongitude != null) {
            viewModel.setPickedLocation(pickedLatitude, pickedLongitude)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val path = copyImageToInternalStorage(context.filesDir, context.contentResolver, uri, stationId)
                if (path != null) viewModel.setImageUrl(path)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.done.collect { onBack() }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Delete station?") },
            text = { Text("This removes it from your library. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Station") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete station")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (!isLoaded) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val streamUrl by viewModel.streamUrl.collectAsState()
        val name by viewModel.name.collectAsState()
        val imageUrl by viewModel.imageUrl.collectAsState()
        val country by viewModel.country.collectAsState()
        val latitude by viewModel.latitude.collectAsState()
        val longitude by viewModel.longitude.collectAsState()
        val genre by viewModel.genre.collectAsState()
        val bitrate by viewModel.bitrate.collectAsState()
        val description by viewModel.description.collectAsState()
        val language by viewModel.language.collectAsState()
        val websiteUrl by viewModel.websiteUrl.collectAsState()
        val isFavorite by viewModel.isFavorite.collectAsState()
        val mood by viewModel.mood.collectAsState()
        val style by viewModel.style.collectAsState()
        val liveTimesFrom by viewModel.liveTimesFrom.collectAsState()
        val liveTimesTo by viewModel.liveTimesTo.collectAsState()
        val is24x7 by viewModel.is24x7.collectAsState()
        val error by viewModel.error.collectAsState()
        val liveTimeError by viewModel.liveTimeError.collectAsState()
        val clickCountSnapshot by viewModel.clickCountSnapshot.collectAsState()
        val popularityTier by viewModel.popularityTier.collectAsState()

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalPlayerBarBottomInset.current),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = streamUrl,
                onValueChange = viewModel::setStreamUrl,
                label = { Text("Stream URL *") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            SourcedField(name, viewModel::setName, "Name")

            SourcedField(imageUrl, viewModel::setImageUrl, "Image URL")
            Button(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Upload Image") }

            CountryCodeField(
                value = country,
                onValueChange = viewModel::setCountry,
                label = "Country"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SourcedField(latitude, viewModel::setLatitude, "Latitude", modifier = Modifier.weight(1f))
                SourcedField(longitude, viewModel::setLongitude, "Longitude", modifier = Modifier.weight(1f))
            }
            Button(onClick = onPickLocation, modifier = Modifier.fillMaxWidth()) { Text("Pick on Map") }

            if (clickCountSnapshot != null) {
                ReadOnlyField(
                    label = "Click count",
                    value = "$clickCountSnapshot${popularityTier?.let { " · $it" }.orEmpty()}"
                )
            } else {
                PopularityPicker(selected = popularityTier, onSelect = viewModel::setManualPopularityTier)
            }

            GenreVocabularyField(
                stationDao = stationDao,
                value = genre,
                onValueChange = viewModel::setGenre,
                label = "Genre",
                supportingText = "Genres played — multiple allowed"
            )
            TagPickerField(
                stationDao = stationDao,
                tagType = TagType.MOOD,
                value = mood,
                onValueChange = viewModel::setMood,
                label = "Mood",
                supportingText = "What setting you might want to play the radio in"
            )
            TagPickerField(
                stationDao = stationDao,
                tagType = TagType.STYLE,
                value = style,
                onValueChange = viewModel::setStyle,
                label = "Style",
                supportingText = "What type of stream this is, e.g. DJ set, radio show, talk show"
            )
            SourcedField(bitrate, viewModel::setBitrate, "Bitrate (kbps)")
            SourcedField(description, viewModel::setDescription, "Description")
            SourcedField(websiteUrl, viewModel::setWebsiteUrl, "Website URL")
            LanguageField(
                value = language,
                onValueChange = viewModel::setLanguage,
                label = "Language"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Favourite")
                Switch(checked = isFavorite, onCheckedChange = viewModel::setIsFavorite)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Live 24/7")
                Switch(checked = is24x7, onCheckedChange = viewModel::setIs24x7)
            }
            if (!is24x7) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = liveTimesFrom,
                        onValueChange = viewModel::setLiveTimesFrom,
                        label = { Text("Live from") },
                        placeholder = { Text("23:59 (24h)") },
                        isError = liveTimeError != null,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = liveTimesTo,
                        onValueChange = viewModel::setLiveTimesTo,
                        label = { Text("Live to") },
                        placeholder = { Text("23:59 (24h)") },
                        isError = liveTimeError != null,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (liveTimeError != null) {
                    Text(
                        liveTimeError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val localFrom = com.staticradio.app.data.CountryTimeZones.toLocalEquivalent(country, liveTimesFrom)
                val localTo = com.staticradio.app.data.CountryTimeZones.toLocalEquivalent(country, liveTimesTo)
                if (localFrom != null && localTo != null) {
                    Text(
                        "≈ $localFrom–$localTo your time (station country: $country)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}

private fun copyImageToInternalStorage(
    filesDir: File,
    contentResolver: android.content.ContentResolver,
    uri: Uri,
    stationId: String
): String? = try {
    val imagesDir = File(filesDir, "images").apply { mkdirs() }
    val destination = File(imagesDir, "$stationId.jpg")
    contentResolver.openInputStream(uri)?.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    }
    "file://${destination.absolutePath}"
} catch (e: Exception) {
    null
}

@Composable
private fun SourcedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth()
    )
}

private val POPULARITY_EMOJIS = listOf("❄️", "🧊", "😐", "🔥", "🌋")

@Composable
private fun PopularityPicker(selected: String?, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Popularity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "No click count from Radio Browser for this station — pick a tier yourself",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            POPULARITY_EMOJIS.forEach { emoji ->
                val isSelected = emoji == selected
                Box(
                    modifier = Modifier
                        .clickable { onSelect(emoji) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        style = if (isSelected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
