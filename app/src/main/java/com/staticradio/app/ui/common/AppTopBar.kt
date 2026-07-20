package com.staticradio.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.staticradio.app.ui.home.StationViewMode

enum class TopBarMode { HOME, MAP, MIXES }

/**
 * Shared header — same visual row on Home and Map (Map reuses the whole thing
 * instead of a standalone back button), pared down to Back/Filter/Add/Settings
 * on the Mixes screen.
 */
@Composable
fun AppTopBar(
    mode: TopBarMode,
    activeViewMode: StationViewMode?,
    onListClick: () -> Unit,
    onGridClick: () -> Unit,
    onMapClick: () -> Unit,
    onMixesClick: () -> Unit,
    onFilterClick: () -> Unit,
    filterActive: Boolean,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Static Radio",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.75f)
                )
                if (subtitle != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBackClick != null) {
                        TopBarIconButton(active = false, onClick = onBackClick) { tint ->
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tint)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (mode != TopBarMode.MIXES) {
                        ViewModeSegment(
                            mode = mode,
                            activeViewMode = activeViewMode,
                            onListClick = onListClick,
                            onGridClick = onGridClick,
                            onMapClick = onMapClick
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TopBarIconButton(active = filterActive, onClick = onFilterClick) { tint ->
                        Icon(Icons.Filled.FilterList, contentDescription = "Filter", tint = tint)
                    }
                    if (mode != TopBarMode.MIXES) {
                        Spacer(Modifier.width(8.dp))
                        TopBarIconButton(active = mode == TopBarMode.MIXES, onClick = onMixesClick) { tint ->
                            CassetteIcon(tint = tint)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopBarIconButton(active = true, onClick = onAddClick) { tint ->
                        Icon(Icons.Filled.Add, contentDescription = "Add station", tint = tint)
                    }
                    TopBarIconButton(active = false, onClick = onSettingsClick) { tint ->
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = tint)
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.0.dp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ViewModeSegment(
    mode: TopBarMode,
    activeViewMode: StationViewMode?,
    onListClick: () -> Unit,
    onGridClick: () -> Unit,
    onMapClick: () -> Unit
) {
    val keyline = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.0.dp, keyline, RoundedCornerShape(10.dp))
    ) {
        SegmentHalf(
            active = activeViewMode == StationViewMode.LIST,
            onClick = onListClick
        ) { tint -> Icon(Icons.Filled.Menu, contentDescription = "List view", tint = tint) }

        SegmentDivider(keyline)

        SegmentHalf(
            active = activeViewMode == StationViewMode.GRID,
            onClick = onGridClick
        ) { tint -> Icon(Icons.Filled.GridView, contentDescription = "Grid view", tint = tint) }

        SegmentDivider(keyline)

        SegmentHalf(
            active = mode == TopBarMode.MAP,
            onClick = onMapClick
        ) { tint -> Icon(Icons.Filled.Map, contentDescription = "Map", tint = tint) }
    }
}

@Composable
private fun SegmentDivider(color: Color) {
    Box(
        Modifier
            .width(1.0.dp)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
private fun SegmentHalf(active: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TopBarIconButton(
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val keyline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.0.dp, keyline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}
