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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.TagEntity
import com.staticradio.app.data.local.TagType
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Generic settings-managed vocabulary list — same pattern as genre's own
 * vocabulary screen, reused for Mood and Style since both are "always
 * user-defined, pick from a known list" fields with no free text.
 */
class TagVocabularyViewModel(
    private val stationDao: StationDao,
    private val tagType: TagType
) : ViewModel() {

    val vocabulary: StateFlow<List<TagEntity>> = stationDao.observeTagsByType(tagType)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            stationDao.insertTag(TagEntity(name = trimmed, type = tagType))
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            stationDao.clearCrossRefsForTag(tag.id)
            stationDao.deleteTag(tag.id)
        }
    }

    class Factory(
        private val stationDao: StationDao,
        private val tagType: TagType
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TagVocabularyViewModel(stationDao, tagType) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagVocabularyScreen(
    stationDao: StationDao,
    tagType: TagType,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: TagVocabularyViewModel = viewModel(
        key = tagType.name,
        factory = TagVocabularyViewModel.Factory(stationDao, tagType)
    )
    val vocabulary by viewModel.vocabulary.collectAsState()
    var newTagName by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                "Every station's $title is picked from this list — manage the options here.",
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
                    onValueChange = { newTagName = it },
                    label = { Text("New $title") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.addTag(newTagName); newTagName = "" },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Add") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalPlayerBarBottomInset.current)
            ) {
                items(vocabulary, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tag.name)
                        IconButton(onClick = { viewModel.deleteTag(tag) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove ${tag.name}")
                        }
                    }
                }
            }
        }
    }
}
