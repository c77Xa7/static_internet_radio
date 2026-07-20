package com.staticradio.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.ui.common.AppTopBar
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import com.staticradio.app.ui.common.TopBarMode
import com.staticradio.app.ui.common.squareGridBackground

@Composable
fun HomeScreen(
    stationDao: StationDao,
    onStationClick: (StationListItem) -> Unit,
    onEditStation: (StationListItem) -> Unit,
    onAddStation: () -> Unit,
    onMapClick: () -> Unit,
    onMixesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    nowPlayingStationId: String? = null,
    isPlaying: Boolean = false,
    gridImageShape: Shape = RoundedCornerShape(4.dp),
    showBackgroundGrid: Boolean = true,
    gridSpacing: Dp = 28.dp,
    gridLineWidth: Dp = 1.dp,
    gridOpacity: Float = 1f,
    modifier: Modifier = Modifier
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(stationDao))
    val viewMode by viewModel.viewMode.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val countryCodes by viewModel.countryCodes.collectAsState()
    val moods by viewModel.moods.collectAsState()
    val styles by viewModel.styles.collectAsState()
    val stations by viewModel.stations.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    if (showFilterDialog) {
        StationFilterDialog(
            filter = filter,
            genres = genres,
            countryCodes = countryCodes,
            moods = moods,
            styles = styles,
            onGenreClick = viewModel::toggleGenreFilter,
            onCountryClick = viewModel::toggleCountryFilter,
            onMoodClick = viewModel::toggleMoodFilter,
            onStyleClick = viewModel::toggleStyleFilter,
            onFavoritesClick = viewModel::toggleFavoritesOnly,
            onDismiss = { showFilterDialog = false }
        )
    }

    val gridLineColor = MaterialTheme.colorScheme.primary
    val bottomInset = LocalPlayerBarBottomInset.current
    val filterActive = filter.favoritesOnly || filter.genre != null || filter.countryCode != null ||
        filter.mood != null || filter.style != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            mode = TopBarMode.HOME,
            activeViewMode = viewMode,
            onListClick = { viewModel.setViewMode(StationViewMode.LIST) },
            onGridClick = { viewModel.setViewMode(StationViewMode.GRID) },
            onMapClick = onMapClick,
            onMixesClick = onMixesClick,
            onFilterClick = { showFilterDialog = true },
            filterActive = filterActive,
            onAddClick = onAddStation,
            onSettingsClick = onSettingsClick
        )

        val gridBackgroundModifier = if (showBackgroundGrid) {
            Modifier.squareGridBackground(gridLineColor, cellSize = gridSpacing, lineWidth = gridLineWidth, opacity = gridOpacity)
        } else {
            Modifier
        }

        if (stations.isEmpty() && !filterActive) {
            EmptyStateMessage(
                title = "No stations yet",
                body = "Tap the + button above to add one — paste a stream URL directly, or search Radio Browser for stations to import.",
                modifier = Modifier.fillMaxSize().then(gridBackgroundModifier)
            )
            return@Column
        }

        when (viewMode) {
            StationViewMode.LIST -> LazyColumn(
                modifier = Modifier.fillMaxSize().then(gridBackgroundModifier),
                contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp + bottomInset)
            ) {
                items(stations, key = { it.station.id }) { item ->
                    StationListRow(
                        item = item,
                        onClick = { onStationClick(item) },
                        onLongClick = { onEditStation(item) },
                        isLive = isPlaying && item.station.id == nowPlayingStationId,
                        modifier = Modifier.padding(bottom = 9.dp)
                    )
                }
            }

            StationViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().then(gridBackgroundModifier),
                contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp + bottomInset),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(stations, key = { it.station.id }) { item ->
                    StationGridTile(
                        item = item,
                        imageShape = gridImageShape,
                        onClick = { onStationClick(item) },
                        onLongClick = { onEditStation(item) },
                        isLive = isPlaying && item.station.id == nowPlayingStationId
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 16.dp).padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.0.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun StationFilterDialog(
    filter: HomeFilter,
    genres: List<String>,
    countryCodes: List<String>,
    moods: List<String>,
    styles: List<String>,
    onGenreClick: (String) -> Unit,
    onCountryClick: (String) -> Unit,
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
                FilterSection(title = "Favourites") {
                    Chip(label = "★ Favourites", active = filter.favoritesOnly, onClick = onFavoritesClick)
                }
                FilterSection(title = "Genre") {
                    if (genres.isEmpty()) {
                        Text("No genres yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    genres.forEach { genre ->
                        Chip(label = genre, active = genre == filter.genre, onClick = { onGenreClick(genre) })
                    }
                }
                FilterSection(title = "Country") {
                    if (countryCodes.isEmpty()) {
                        Text("No countries yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    countryCodes.forEach { code ->
                        Chip(label = code, active = code == filter.countryCode, onClick = { onCountryClick(code) })
                    }
                }
                FilterSection(title = "Mood") {
                    if (moods.isEmpty()) {
                        Text("No moods yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    moods.forEach { mood ->
                        Chip(label = mood, active = mood == filter.mood, onClick = { onMoodClick(mood) })
                    }
                }
                FilterSection(title = "Style") {
                    if (styles.isEmpty()) {
                        Text("No styles yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    styles.forEach { style ->
                        Chip(label = style, active = style == filter.style, onClick = { onStyleClick(style) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
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
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
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
