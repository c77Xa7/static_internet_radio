package com.staticradio.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import com.staticradio.app.MainActivity
import com.staticradio.app.StaticRadioApp
import com.staticradio.app.data.StationLookupImpl
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Foreground service hosting the ExoPlayer instance + MediaLibrarySession.
 * MediaLibraryService (a MediaSessionService subtype) handles the
 * notification and lock-screen controls automatically, and additionally
 * exposes a browsable tree (root -> flat station list) so Android Auto can
 * let the user pick a station from the car screen instead of only
 * play/pausing whatever's already loaded.
 */
class RadioPlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private val repository = PlaybackRepository.getInstance()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Fill this from your Repository/DAO layer — see note below the code block
    var stationLookup: StationLookup? = null

    private var sleepTimerJob: Job? = null
    private val autoGainProcessor = AutoGainAudioProcessor()

    // Initialized in onCreate (needs an attached Context) — also reused to
    // pre-resolve browse-tree artwork into embedded bytes, since Android
    // Auto's process can't read this app's private file:// image storage
    // directly (see buildBrowsableStub below).
    private lateinit var bitmapLoader: CoilBitmapLoader

    override fun onCreate() {
        super.onCreate()

        val app = application as StaticRadioApp
        stationLookup = StationLookupImpl(app.database.stationDao())

        // Default Media3 DataSource sends a generic user agent and doesn't
        // follow cross-protocol redirects — some stream hosts (e.g. Radiojar's
        // token-gated edge redirects) reject or break on that. Route through
        // OkHttp with a browser-like UA instead.
        val httpDataSourceFactory = OkHttpDataSource.Factory(OkHttpClient())
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) StaticRadio/1.0")
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this, httpDataSourceFactory)
        )

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(autoGainProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }

        // Bigger min/max buffer = more resilience against network blips at the
        // cost of memory, same trade-off Transistor's own buffer setting makes.
        // Playback/rebuffer-start thresholds stay low (ExoPlayer's own live-
        // stream defaults) so a bigger buffer doesn't also mean a slower start.
        val bufferMillis = (runBlocking { app.settingsRepository.bufferSeconds.first() } * 1000)
            .coerceAtLeast(5000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bufferMillis, bufferMillis, 1500, 2500)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply { addListener(playerListener) }

        serviceScope.launch {
            app.settingsRepository.normalizeVolume.collect { enabled ->
                autoGainProcessor.enabled = enabled
            }
        }

        // Custom launch intent so tapping the notification opens your app,
        // not a default system screen.
        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        bitmapLoader = CoilBitmapLoader(this, serviceScope)

        mediaSession = MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(sessionActivityIntent)
            .setBitmapLoader(bitmapLoader)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    // ---- Playback control API, called from your UI/ViewModel via a controller ----

    fun playStation(stationId: String, streamUrl: String, title: String, imageUrl: String? = null) {
        repository.updateCurrentStation(stationId)
        repository.updateError(null)

        player.setMediaItem(buildPlayableMediaItem(stationId, streamUrl, title, imageUrl))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Shared by direct playback, the Android Auto browse tree, and
     * onAddMediaItems (which fills in a real URI for a browse-tree item
     * that only arrived with a mediaId).
     */
    private fun buildPlayableMediaItem(
        stationId: String,
        streamUrl: String,
        title: String,
        imageUrl: String?
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setStation(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply { imageUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(stationId)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Browse-tree entries only need a mediaId + display metadata — no
     * stream URI. Android Auto resolves the real URI by round-tripping the
     * selected item through onAddMediaItems below when the user taps it.
     *
     * Artwork is embedded as raw bytes (setArtworkData), not passed as a
     * URI — Android Auto's browse list is read by a different process
     * (the car host app), which can't read this app's private
     * file://.../files/... storage for user-uploaded images. Only
     * Radio-Browser-sourced http(s) images happened to still render, since
     * any process can fetch those directly. Resolving the bitmap ourselves
     * (same in-process Coil loader used for notification artwork, which can
     * read both file:// and http(s)) and embedding the bytes sidesteps that
     * entirely, regardless of image source.
     */
    private suspend fun buildBrowsableStub(station: StationRef): MediaItem {
        val title = if (station.isFavorite) "★ ${station.title}" else station.title
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply { loadArtworkBytes(station.imageUrl)?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun loadArtworkBytes(imageUrl: String?): ByteArray? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val bitmap = suspendCancellableCoroutine<Bitmap> { cont ->
                val future = bitmapLoader.loadBitmap(Uri.parse(imageUrl))
                future.addListener({
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, MoreExecutors.directExecutor())
            }
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.w("RadioPlaybackService", "Couldn't load browse-tree artwork for $imageUrl", e)
            null
        }
    }

    fun playRandomStation() {
        val lookup = stationLookup ?: return
        serviceScope.launch {
            val random = lookup.getRandomStation(excludeId = repository.currentStationId.value)
            random?.let { playStation(it.id, it.streamUrl, it.title, it.imageUrl) }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun startSleepTimer(durationMillis: Long) {
        sleepTimerJob?.cancel()
        val endAtMillis = System.currentTimeMillis() + durationMillis
        repository.updateSleepTimerEndAt(endAtMillis)
        sleepTimerJob = serviceScope.launch {
            delay(durationMillis)
            player.pause()
            repository.updateSleepTimerEndAt(null)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        repository.updateSleepTimerEndAt(null)
    }

    // ---- Listener: this is where ICY metadata and playback state flow into the repository ----

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            repository.updatePlayingState(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            repository.updateBufferingState(playbackState == Player.STATE_BUFFERING)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Common causes here: dead stream URL, unsupported codec, network drop.
            // Log the real cause since it's otherwise swallowed by the generic
            // user-facing message below.
            Log.w("RadioPlaybackService", "Playback error for station ${repository.currentStationId.value}", error)
            repository.updateError("Couldn't play this station. It may be offline.")
        }

        override fun onMetadata(metadata: Metadata) {
            // ICY metadata arrives here as one or more entries per update.
            // IcyInfo = dynamic "now playing" StreamTitle. IcyHeaders = static
            // station headers (icy-br) sent once at connect — bitrate is
            // display-only and sourced from the stream itself. Genre is no
            // longer read from ICY (icy-genre) — it's user-defined only now.
            val stationId = repository.currentStationId.value
            for (i in 0 until metadata.length()) {
                when (val entry = metadata.get(i)) {
                    is IcyInfo -> {
                        val title = entry.title // typically "Artist - Track", format varies by station
                        if (!title.isNullOrBlank()) {
                            repository.updateNowPlaying(title)
                            stationId?.let { id ->
                                serviceScope.launch { stationLookup?.updateNowPlayingCache(id, title) }
                            }
                            // Reflects the live ICY text as the notification's subtitle line
                            // without interrupting playback (same uri, metadata-only change).
                            player.currentMediaItem?.let { current ->
                                val updatedMetadata = current.mediaMetadata.buildUpon().setArtist(title).build()
                                val updatedItem = current.buildUpon().setMediaMetadata(updatedMetadata).build()
                                player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
                            }
                        }
                    }
                    is IcyHeaders -> {
                        if (stationId != null && entry.bitrate > 0) {
                            serviceScope.launch { stationLookup?.updateBitrateFromStream(stationId, entry.bitrate) }
                        }
                    }
                }
            }
        }
    }

    private val librarySessionCallback = object : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_PLAY_STATION, Bundle.EMPTY))
                .add(SessionCommand(CMD_PLAY_RANDOM, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                availableCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_PLAY_STATION -> {
                    val id = args.getString(ARG_STATION_ID) ?: return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                    )
                    val url = args.getString(ARG_STREAM_URL) ?: return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                    )
                    val title = args.getString(ARG_TITLE).orEmpty()
                    val imageUrl = args.getString(ARG_IMAGE_URL)
                    playStation(id, url, title, imageUrl)
                }
                CMD_PLAY_RANDOM -> playRandomStation()
                CMD_SET_SLEEP_TIMER -> {
                    val durationMillis = args.getLong(ARG_DURATION_MILLIS, -1L)
                    if (durationMillis <= 0) return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE)
                    )
                    startSleepTimer(durationMillis)
                }
                CMD_CANCEL_SLEEP_TIMER -> cancelSleepTimer()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder()
                .setTitle("STATIC")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
            val rootItem = MediaItem.Builder()
                .setMediaId(BROWSE_ROOT_ID)
                .setMediaMetadata(rootMetadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != BROWSE_ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val stations = stationLookup?.getAllStations().orEmpty()
                val children = coroutineScope {
                    stations.map { async { buildBrowsableStub(it) } }.awaitAll()
                }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                val station = stationLookup?.getStation(mediaId)
                if (station == null) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                } else {
                    future.set(LibraryResult.ofItem(buildBrowsableStub(station), null))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                val resolved = mediaItems.mapNotNull { item ->
                    // Items that already carry a URI (e.g. resumed from the app's own
                    // controller) pass through unchanged; browse-tree taps only have a
                    // mediaId and need the real stream URL looked up.
                    if (item.localConfiguration != null) {
                        item
                    } else {
                        stationLookup?.getStation(item.mediaId)?.let {
                            buildPlayableMediaItem(it.id, it.streamUrl, it.title, it.imageUrl)
                        }
                    }
                }
                future.set(resolved)
            }
            return future
        }
    }

    /**
     * Minimal interface so this Service doesn't depend directly on your Room DAO.
     * Implement this in your data layer and inject/set it at service startup
     * (e.g. from Application.onCreate() or via Hilt).
     */
    interface StationLookup {
        suspend fun getRandomStation(excludeId: String?): StationRef?
        suspend fun getAllStations(): List<StationRef>
        suspend fun getStation(stationId: String): StationRef?
        suspend fun updateNowPlayingCache(stationId: String, text: String)
        suspend fun updateBitrateFromStream(stationId: String, bitrate: Int)
    }

    data class StationRef(
        val id: String,
        val streamUrl: String,
        val title: String,
        val imageUrl: String?,
        val isFavorite: Boolean = false
    )

    companion object {
        const val BROWSE_ROOT_ID = "static_radio_root"
        const val CMD_PLAY_STATION = "com.staticradio.app.PLAY_STATION"
        const val CMD_PLAY_RANDOM = "com.staticradio.app.PLAY_RANDOM"
        const val CMD_SET_SLEEP_TIMER = "com.staticradio.app.SET_SLEEP_TIMER"
        const val CMD_CANCEL_SLEEP_TIMER = "com.staticradio.app.CANCEL_SLEEP_TIMER"
        const val ARG_STATION_ID = "station_id"
        const val ARG_STREAM_URL = "stream_url"
        const val ARG_TITLE = "title"
        const val ARG_IMAGE_URL = "image_url"
        const val ARG_DURATION_MILLIS = "duration_millis"
    }
}
