package com.staticradio.app.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.staticradio.app.playback.PlaybackRepository
import com.staticradio.app.playback.RadioController
import com.staticradio.app.ui.common.LiveLed
import com.staticradio.app.ui.home.StationArt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Bridges PlaybackRepository (service-side state) + station lookup (Room)
 * into a single UI-facing state object for the persistent player bar.
 * No full-screen "now playing" view — the persistent bar is the whole
 * playback UI (see PROJECT_CONTEXT.md revision dropping that requirement).
 */
class PlayerViewModel(
    private val repository: PlaybackRepository,
    private val controller: RadioController,
    private val stationMetadataProvider: suspend (String) -> StationBarInfo?
) : ViewModel() {

    data class StationBarInfo(
        val title: String,
        val imageUrl: String?,
        val countryCode: String?,
        val genres: List<String>,
        val bitrate: Int?,
        val websiteUrl: String?
    )

    data class PlayerBarState(
        val isVisible: Boolean = false,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val nowPlayingText: String? = null,
        val errorMessage: String? = null,
        val stationId: String? = null,
        val station: StationBarInfo? = null
    )

    var barState by mutableStateOf(PlayerBarState())
        private set

    init {
        viewModelScope.launch {
            combine(
                repository.currentStationId,
                repository.isPlaying,
                repository.isBuffering,
                repository.nowPlayingText,
                repository.playbackError
            ) { stationId, isPlaying, isBuffering, nowPlaying, error ->
                StationSnapshot(stationId, isPlaying, isBuffering, nowPlaying, error)
            }
                .collect { snapshot ->
                    val info = snapshot.stationId?.let { stationMetadataProvider(it) }
                    barState = PlayerBarState(
                        isVisible = snapshot.stationId != null,
                        isPlaying = snapshot.isPlaying,
                        isBuffering = snapshot.isBuffering,
                        nowPlayingText = snapshot.nowPlaying,
                        errorMessage = snapshot.error,
                        stationId = snapshot.stationId,
                        station = info
                    )
                }
        }
    }

    private data class StationSnapshot(
        val stationId: String?,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val nowPlaying: String?,
        val error: String?
    )

    fun togglePlayPause() = controller.togglePlayPause()
    fun playRandom() = controller.playRandom()

    class Factory(
        private val repository: PlaybackRepository,
        private val controller: RadioController,
        private val stationMetadataProvider: suspend (String) -> StationBarInfo?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(repository, controller, stationMetadataProvider) as T
    }
}

/**
 * Persistent bottom bar — hazard-orange background per the post-brutalist
 * accent, with a pulsing "on air" LED while playing (see mockup.html's
 * .onair-led). Shows now-playing text (when available) stacked above the
 * countrycode/genre/bitrate metadata line, not replacing it. Now-playing text
 * scroll-marquees when it's too long to fit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersistentPlayerBar(
    state: PlayerViewModel.PlayerBarState,
    onTogglePlayPause: () -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible || state.station == null) return
    val station = state.station
    val onAccent = Color(0xFF17171A)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = onAccent,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationArt(
                name = station.title,
                imageUrl = station.imageUrl,
                size = 44.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (state.isPlaying) {
                    LiveLed()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = station.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    } else {
                        // Always rendered (not just while playing) so the bar's height
                        // stays constant across play/pause instead of growing the
                        // moment playback starts and this line first appears.
                        Text(
                            text = state.nowPlayingText?.takeIf { it.isNotBlank() }
                                ?: "No 'Now Playing' data provided",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = onAccent.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                    val metadataLine = listOfNotNull(
                        station.countryCode,
                        station.genres.joinToString("/").ifEmpty { null },
                        station.bitrate?.let { "${it}kbps" }
                    ).joinToString(" - ")
                    if (metadataLine.isNotEmpty()) {
                        Text(
                            text = metadataLine,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            color = onAccent.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRandom, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Random station", tint = onAccent)
                }

                Spacer(modifier = Modifier.width((-6).dp))

                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(44.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = onAccent)
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = onAccent
                        )
                    }
                }
            }
        }
    }
}
