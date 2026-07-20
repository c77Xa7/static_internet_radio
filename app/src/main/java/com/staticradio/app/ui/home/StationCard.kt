package com.staticradio.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.staticradio.app.ui.common.LiveLed

private const val MAX_VISIBLE_GENRE_BUBBLES = 3

// How far the corner/bottom bubbles sit in from the card's true outer edge —
// independent of horizontalInset (which only controls the visible card's
// width) so widening the card doesn't also push the bubbles back out toward
// the edge.
private val BUBBLE_EDGE_INSET = 16.dp

/**
 * Shared "ID plate" chrome from mockup.html: hard keyline border, rivet dots
 * at the top corners, a mood/popularity badge cluster overhanging the top
 * right, a favourite-star bubble mirroring it on the top left, and (when
 * genres are supplied) a row of genre bubbles straddling the bottom edge —
 * all bubbles sit half in/half out of the card, same treatment. Genre
 * bubbles are capped at MAX_VISIBLE_GENRE_BUBBLES with a trailing "…"
 * instead of wrapping/overflowing past where the card's corner radius
 * begins. Long-press opens Edit — there's no separate edit affordance in the
 * mockup, so tap plays and long-press edits.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationPlate(
    popularityEmoji: String? = null,
    mood: String? = null,
    isFavorite: Boolean = false,
    genres: List<String> = emptyList(),
    // Same inset as grid view's tiles, so list/mix cards read the same
    // relative width instead of looking narrower/more inset by comparison.
    horizontalInset: Dp = 6.dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val keyline = MaterialTheme.colorScheme.outline
    val plateColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.padding(top = 10.dp, start = horizontalInset, end = horizontalInset, bottom = 10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(plateColor)
                .border(1.0.dp, keyline, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) { content() }
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(keyline.copy(alpha = 0.55f))
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(keyline.copy(alpha = 0.55f))
        )

        if (isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = BUBBLE_EDGE_INSET, y = (-10).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(plateColor)
                    .border(1.0.dp, keyline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "★",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.offset(y = (-1).dp)
                )
            }
        }

        if (popularityEmoji != null || mood != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -BUBBLE_EDGE_INSET, y = (-10).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (mood != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(plateColor)
                            .border(1.0.dp, keyline, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = mood, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (popularityEmoji != null) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(plateColor)
                            .border(1.0.dp, keyline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = popularityEmoji, fontSize = 13.sp)
                    }
                }
            }
        }

        if (genres.isNotEmpty()) {
            GenreBubbleRow(
                genres = genres,
                plateColor = plateColor,
                keyline = keyline,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = 10.dp)
                    .padding(start = 20.dp, end = 20.dp)
            )
        }
    }
}

@Composable
private fun GenreBubbleRow(genres: List<String>, plateColor: Color, keyline: Color, modifier: Modifier = Modifier) {
    val visible = genres.take(MAX_VISIBLE_GENRE_BUBBLES)
    val overflow = genres.size > MAX_VISIBLE_GENRE_BUBBLES
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEach { genre ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(plateColor)
                    .border(1.0.dp, keyline, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = genre, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (overflow) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(plateColor)
                    .border(1.0.dp, keyline, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StationListRow(
    item: StationListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val station = item.station
    StationPlate(
        popularityEmoji = station.popularityTier,
        mood = station.mood,
        isFavorite = station.isFavorite,
        genres = station.genres,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationArt(name = station.name, imageUrl = station.imageUrl, size = 52.dp, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = true)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        LiveLed()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                }
                val liveTimes = when {
                    station.is24x7 -> "24/7"
                    station.liveTimesFrom != null && station.liveTimesTo != null -> "${station.liveTimesFrom}-${station.liveTimesTo}"
                    else -> null
                }
                val subtitle = listOfNotNull(
                    station.countryCode,
                    station.bitrate?.let { "${it}kbps" },
                    liveTimes
                ).joinToString(" - ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val context = LocalContext.current
            val homepageUrl = station.websiteUrl
            if (!homepageUrl.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(homepageUrl))) }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = "Station homepage",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StationGridTile(
    item: StationListItem,
    imageShape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val station = item.station
    // Grid tiles only show favourite + click-count bubbles — no mood pill,
    // no bottom genre-bubble row. List view is where full metadata shows.
    StationPlate(
        popularityEmoji = station.popularityTier,
        isFavorite = station.isFavorite,
        horizontalInset = 6.dp,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    ) {
        Box(Modifier.padding(8.dp)) {
            StationArt(
                name = station.name,
                imageUrl = station.imageUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = imageShape
            )
            if (isLive) {
                LiveLed(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp), size = 10.dp)
            }
        }
    }
}
