package com.staticradio.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Reorder UI shared by stations and mixes. Deliberately up/down arrow rows
 * rather than a drag-and-drop surface: the same mechanic works inside both
 * sections, survives configuration changes trivially, and never risks
 * dragging a favourite out of its pinned top block. Favourites are their own
 * contiguous section at the top; non-favourites sit below. Moving an item
 * across the section boundary is impossible — the arrows stop at it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderScreen(
    kind: ReorderKind,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items: List<Any> = when (kind) {
        ReorderKind.STATIONS -> viewModel.stationReorderList.collectAsState().value
        ReorderKind.MIXES -> viewModel.mixReorderList.collectAsState().value
    }
    // Both reorder item types expose id/title/isFavorite, so the row UI works
    // on duck-typed accessors — avoids two near-identical composables.
    val idOf: (Any) -> String = { when (it) {
        is SettingsViewModel.StationReorderItem -> it.id
        is SettingsViewModel.MixReorderItem -> it.id
        else -> ""
    } }
    val titleOf: (Any) -> String = { when (it) {
        is SettingsViewModel.StationReorderItem -> it.title
        is SettingsViewModel.MixReorderItem -> it.title
        else -> ""
    } }
    val favOf: (Any) -> Boolean = { when (it) {
        is SettingsViewModel.StationReorderItem -> it.isFavorite
        is SettingsViewModel.MixReorderItem -> it.isFavorite
        else -> false
    } }

    LaunchedEffect(kind) {
        when (kind) {
            ReorderKind.STATIONS -> viewModel.loadStationOrder()
            ReorderKind.MIXES -> viewModel.loadMixOrder()
        }
    }

    val message by viewModel.importExportMessage.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (kind == ReorderKind.STATIONS) "Reorder stations" else "Reorder mixes") },
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
                .padding(horizontal = 16.dp)
        ) {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (kind == ReorderKind.STATIONS) "No stations yet" else "No mixes yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            val favouriteCount = items.count { favOf(it) }
            val anyFavourites = favouriteCount > 0

            // Headers are rows INSIDE the LazyColumn (emitted immediately
            // before the first row of their section), so each banner sits
            // directly above its own rows: Favourites -> favourite rows ->
            // Everything else -> non-favourite rows. Header text outside the
            // list rendered both banners stacked above all rows, which read
            // as favourites living under the wrong banner.
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (anyFavourites) {
                    item(key = "header_favourites") {
                        ReorderSectionHeader(
                            text = "Favourites",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                itemsIndexed(items, key = { _, item -> idOf(item) }) { index, item ->
                    val inFavourites = favOf(item)
                    val firstInSection = index == 0 || favOf(items[index - 1]) != inFavourites
                    val lastInSection = index == items.size - 1 || favOf(items[index + 1]) != inFavourites

                    if (!inFavourites && (index == 0 || favOf(items[index - 1]))) {
                        // First non-favourite row: emit the section banner
                        // just above it.
                        ReorderSectionHeader(
                            text = if (anyFavourites) "Everything else" else "Stations",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    ReorderRow(
                        title = titleOf(item),
                        isFavorite = inFavourites,
                        canMoveUp = !firstInSection,
                        canMoveDown = !lastInSection,
                        onMoveUp = {
                            if (kind == ReorderKind.STATIONS) viewModel.moveStation(index, index - 1)
                            else viewModel.moveMix(index, index - 1)
                        },
                        onMoveDown = {
                            if (kind == ReorderKind.STATIONS) viewModel.moveStation(index, index + 1)
                            else viewModel.moveMix(index, index + 1)
                        }
                    )
                }
            }

            if (message != null) {
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    when (kind) {
                        ReorderKind.STATIONS -> viewModel.saveStationOrder()
                        ReorderKind.MIXES -> viewModel.saveMixOrder()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
            ) { Text("Save order") }
        }
    }
}

enum class ReorderKind { STATIONS, MIXES }

@Composable
private fun ReorderSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun ReorderRow(
    title: String,
    isFavorite: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val keyline = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.0.dp, keyline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFavorite) {
            Text("★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move up",
                tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move down",
                tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}
