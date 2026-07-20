package com.staticradio.app.ui.mixes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.local.MixDao
import com.staticradio.app.data.local.MixEntity
import com.staticradio.app.data.local.MixSource
import com.staticradio.app.data.local.MixWithTracks
import com.staticradio.app.ui.common.AppTopBar
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import com.staticradio.app.ui.common.TopBarMode
import com.staticradio.app.ui.common.verticalLinesBackground
import com.staticradio.app.ui.home.StationArt
import com.staticradio.app.ui.home.StationPlate

@Composable
fun MixesScreen(
    mixDao: MixDao,
    onEditMix: (String) -> Unit,
    onAddMix: () -> Unit,
    onSettingsClick: () -> Unit,
    onBack: () -> Unit,
    showBackgroundGrid: Boolean = true,
    gridSpacing: androidx.compose.ui.unit.Dp = 28.dp,
    gridLineWidth: androidx.compose.ui.unit.Dp = 1.dp,
    gridOpacity: Float = 1f,
    modifier: Modifier = Modifier
) {
    val viewModel: MixesViewModel = viewModel(factory = MixesViewModel.Factory(mixDao))
    val filter by viewModel.filter.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val moods by viewModel.moods.collectAsState()
    val styles by viewModel.styles.collectAsState()
    val mixes by viewModel.mixes.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }
    val filterActive = filter.favoritesOnly || filter.genre != null || filter.mood != null || filter.style != null
    val context = LocalContext.current

    if (showFilterDialog) {
        MixFilterDialog(
            filter = filter,
            genres = genres,
            moods = moods,
            styles = styles,
            onGenreClick = viewModel::toggleGenreFilter,
            onMoodClick = viewModel::toggleMoodFilter,
            onStyleClick = viewModel::toggleStyleFilter,
            onFavoritesClick = viewModel::toggleFavoritesOnly,
            onDismiss = { showFilterDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            mode = TopBarMode.MIXES,
            activeViewMode = null,
            onListClick = {},
            onGridClick = {},
            onMapClick = {},
            onMixesClick = {},
            onFilterClick = { showFilterDialog = true },
            filterActive = filterActive,
            onAddClick = onAddMix,
            onSettingsClick = onSettingsClick,
            subtitle = "// saved mixes",
            onBackClick = onBack
        )

        val lineColor = MaterialTheme.colorScheme.primary
        val backgroundModifier = if (showBackgroundGrid) {
            Modifier.verticalLinesBackground(lineColor, cellSize = gridSpacing, lineWidth = gridLineWidth, opacity = gridOpacity)
        } else {
            Modifier
        }

        if (mixes.isEmpty() && !filterActive) {
            com.staticradio.app.ui.home.EmptyStateMessage(
                title = "No mixes yet",
                body = "Tap the + button above to add one — or share a link directly from the SoundCloud or Mixcloud app and STATIC will pre-fill the details for you.",
                modifier = Modifier.fillMaxSize().then(backgroundModifier)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().then(backgroundModifier),
            contentPadding = PaddingValues(
                start = 14.dp, top = 14.dp, end = 14.dp,
                bottom = 14.dp + LocalPlayerBarBottomInset.current
            )
        ) {
            items(mixes, key = { it.mix.id }) { mixWithTracks ->
                MixListRow(
                    mix = mixWithTracks.mix,
                    onClick = { openMixExternally(context, mixWithTracks.mix.url) },
                    onLongClick = { onEditMix(mixWithTracks.mix.id) },
                    modifier = Modifier.padding(bottom = 9.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MixListRow(
    mix: MixEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StationPlate(
            popularityEmoji = null,
            mood = mix.mood,
            isFavorite = mix.isFavorite,
            genres = mix.genre?.let { GenreTags.parse(it) } ?: emptyList(),
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StationArt(name = mix.fullTitle ?: mix.url, imageUrl = mix.image, size = 52.dp, shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = true)) {
                    Text(
                        text = mix.fullTitle ?: "Untitled mix",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    val artistLine = listOfNotNull(mix.artist, mix.mixTitle).joinToString(" - ")
                    if (artistLine.isNotEmpty()) {
                        Text(
                            text = artistLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (!mix.sourceRadio.isNullOrBlank()) {
                        Text(
                            text = mix.sourceRadio,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                SourceBadge(source = mix.sourceStreamingSite)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: MixSource) {
    when (source) {
        MixSource.SOUNDCLOUD -> SoundCloudLogo()
        MixSource.MIXCLOUD -> MixcloudLogo()
        MixSource.OTHER -> Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.background)
        }
    }
}

@Composable
private fun MixFilterDialog(
    filter: MixFilter,
    genres: List<String>,
    moods: List<String>,
    styles: List<String>,
    onGenreClick: (String) -> Unit,
    onMoodClick: (String) -> Unit,
    onStyleClick: (String) -> Unit,
    onFavoritesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Filter") },
        text = {
            Column {
                MixFilterSection(title = "Favourites") {
                    MixFilterChip(label = "★ Favourites", active = filter.favoritesOnly, onClick = onFavoritesClick)
                }
                MixFilterSection(title = "Genre") {
                    if (genres.isEmpty()) {
                        Text("No genres yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    genres.forEach { genre -> MixFilterChip(label = genre, active = genre == filter.genre, onClick = { onGenreClick(genre) }) }
                }
                MixFilterSection(title = "Mood") {
                    if (moods.isEmpty()) {
                        Text("No moods yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    moods.forEach { mood -> MixFilterChip(label = mood, active = mood == filter.mood, onClick = { onMoodClick(mood) }) }
                }
                MixFilterSection(title = "Style") {
                    if (styles.isEmpty()) {
                        Text("No styles yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    styles.forEach { style -> MixFilterChip(label = style, active = style == filter.style, onClick = { onStyleClick(style) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun MixFilterSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MixFilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
            .border(1.0.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
        )
    }
}
