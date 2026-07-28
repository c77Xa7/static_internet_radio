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
import androidx.media3.common.MimeTypes
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
import androidx.media3.cast.CastPlayer
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
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
import kotlinx.coroutines.withContext
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

    // Cast is entirely opt-in (see SettingsRepository.castEnabled) — none of
    // this touches Google Play Services until the user turns it on. See
    // enableCastSupport/disableCastSupport and the switchToCast/switchToLocal
    // pair that actually swaps the session's active Player.
    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null

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

        serviceScope.launch {
            app.settingsRepository.castEnabled.collect { enabled ->
                if (enabled) enableCastSupport() else disableCastSupport()
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
        disableCastSupport()
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    // ---- Cast: opt-in swap between the local ExoPlayer and a CastPlayer ----

    private fun enableCastSupport() {
        if (castContext != null) return
        // The deprecated synchronous CastContext.getSharedInstance(Context) can
        // return before Play Services has actually finished setting it up —
        // touching .sessionManager immediately after threw silently inside a
        // coroutine with no CoroutineExceptionHandler, so cast support never
        // activated despite no crash and no visible error. The Task-based
        // overload only completes once CastContext is genuinely ready.
        CastContext.getSharedInstance(this, MoreExecutors.directExecutor())
            .addOnSuccessListener { ctx ->
                if (castContext != null) return@addOnSuccessListener
                try {
                    castContext = ctx
                    ctx.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                    if (ctx.sessionManager.currentCastSession?.isConnected == true) switchToCast()
                } catch (e: Exception) {
                    Log.w("RadioPlaybackService", "Failed to register cast session listener", e)
                }
            }
            .addOnFailureListener { e -> Log.w("RadioPlaybackService", "Cast unavailable", e) }
    }

    private fun disableCastSupport() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        switchToLocal()
        castContext = null
    }

    // Block bodies deliberately, not `= expr` / `= Unit` — the single-expression
    // form on these particular overrides (implementing a generic Java interface
    // with void methods) tripped a KSP compiler bug ("unexpected jvm signature V").
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) { switchToCast() }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { switchToCast() }
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (error != 0) Log.w("RadioPlaybackService", "Cast session ended with error $error")
            switchToLocal()
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) { switchToLocal() }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w("RadioPlaybackService", "Cast session failed to start, error $error")
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.w("RadioPlaybackService", "Cast session failed to resume, error $error")
        }
    }

    private fun switchToCast() {
        val ctx = castContext ?: return
        if (mediaSession.player is CastPlayer) return
        player.pause() // keep the local ExoPlayer instance alive (paused) so switching back doesn't need to rebuild it
        val cp = castPlayer ?: CastPlayer(ctx, LiveStreamMediaItemConverter()).apply { addListener(playerListener) }.also { castPlayer = it }
        player.currentMediaItem?.let {
            cp.setMediaItem(it.withCastMimeType())
            cp.prepare()
            cp.playWhenReady = true
        }
        mediaSession.player = cp
    }

    private fun switchToLocal() {
        if (mediaSession.player === player) return
        castPlayer?.let { cp ->
            cp.currentMediaItem?.let { item ->
                player.setMediaItem(item)
                player.prepare()
                player.playWhenReady = cp.playWhenReady
            }
            cp.removeListener(playerListener)
            cp.release()
        }
        castPlayer = null
        mediaSession.player = player
    }

    // ---- Playback control API, called from your UI/ViewModel via a controller ----

    fun playStation(stationId: String, streamUrl: String, title: String, imageUrl: String? = null) {
        repository.updateCurrentStation(stationId)
        repository.updateError(null)

        // Targets whichever player is currently attached to the session (local
        // ExoPlayer, or the CastPlayer if a Chromecast session is active) so
        // picking a new station from the app also redirects an active cast.
        val target = mediaSession.player
        val item = buildPlayableMediaItem(stationId, streamUrl, title, imageUrl)
        target.setMediaItem(if (target is CastPlayer) item.withCastMimeType() else item)
        target.prepare()
        target.playWhenReady = true
    }

    // CastPlayer's DefaultMediaItemConverter requires an explicit mimeType to
    // build its Cast queue item (throws otherwise) — local ExoPlayer doesn't
    // need one, it auto-detects via extractors, so buildPlayableMediaItem()
    // itself deliberately doesn't set one. Applied wherever an item might be
    // handed to a CastPlayer: here and in switchToCast(). Stations don't
    // carry known codec info, so this is a best-effort default rather than
    // something determined per-station; audio/mpeg covers the large majority
    // of icecast/shoutcast streams.
    private fun MediaItem.withCastMimeType(): MediaItem =
        buildUpon().setMimeType(MimeTypes.AUDIO_MPEG).build()

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
        val target = mediaSession.player
        if (target.isPlaying) target.pause() else target.play()
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
