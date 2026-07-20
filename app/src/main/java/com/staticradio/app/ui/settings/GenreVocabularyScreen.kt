package com.staticradio.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.settings.SettingsRepository
import com.staticradio.app.playback.PlaybackRepository
import com.staticradio.app.playback.RadioController
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreVocabularyScreen(
    settingsRepository: SettingsRepository,
    stationDao: StationDao,
    mixDao: MixDao,
    radioController: RadioController,
    playbackRepository: PlaybackRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(settingsRepository, stationDao, mixDao, radioController, playbackRepository)
    )
    val genreVocabulary by viewModel.genreVocabulary.collectAsState()
    val newTagName by viewModel.newTagName.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Genre Vocabulary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Text(
                "Constrains genre smart tags to a known list — stations can still enter free text, but it shows up here for cleanup.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = viewModel::setNewTagName,
                    label = { Text("New genre") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = viewModel::addGenreTag, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Add")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalPlayerBarBottomInset.current)
            ) {
                items(genreVocabulary, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tag.name)
                        IconButton(onClick = { viewModel.deleteGenreTag(tag) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove ${tag.name}")
                        }
                    }
                }
            }
        }
    }
}
