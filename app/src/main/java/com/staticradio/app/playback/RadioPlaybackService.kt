package com.staticradio.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import com.staticradio.app.MainActivity
import com.staticradio.app.R
import com.staticradio.app.StaticRadioApp
import com.staticradio.app.data.StationLookupImpl
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import androidx.media3.cast.CastPlayer
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.CommandButton
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
import okhttp3.Request
import java.util.concurrent.TimeUnit
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

    // Updated whenever we explicitly ask the player to start (playStation,
    // switchToCast, togglePlayPause-to-play) — bounds the suppression
    // watchdog (see onPlaybackSuppressionReasonChanged) to shortly after a
    // real play request, so it can never fire long after, e.g. once you've
    // since turned the car off and disconnected. suppressionRecoveryAttempted
    // makes the watchdog's retry one-shot per play request — without it,
    // the retry's own play() call re-triggers the same suppression
    // callback, re-arming another retry, looping every ~1-2s (confirmed via
    // dumpsys audio: rapid AudioTrack create/pause/release cycling) for the
    // whole recovery window when the suppression is actually legitimate
    // (e.g. no audio route at all after disconnecting).
    private var lastPlayRequestedAtMillis = 0L
    private var suppressionRecoveryAttempted = false

    // Direct OS-level signal for "an external audio output disappeared" —
    // more fundamental than either MediaSession controller disconnect
    // (Android Auto's own connect/disconnect protocol quirks) or
    // setHandleAudioBecomingNoisy (apparently doesn't fire for this device's
    // wired Android Auto disconnect); neither of those two reliably stopped
    // playback on USB-C disconnect. This watches actual audio hardware
    // routing state instead, which nothing else in the pipeline depends on.
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val removedSinkIds = removedDevices.filter { it.isExternalSink() }.map { it.id }
            if (removedSinkIds.isEmpty()) return
            Log.d("RadioPlaybackServiceAudio", "External sinks removed: " +
                removedDevices.filter { it.isExternalSink() }.joinToString { "id=${it.id} type=${it.type}" })
            // The car's audio device gets reported removed-then-re-added
            // during the connect-time routing churn documented in
            // onPlaybackSuppressionReasonChanged below, not just at a genuine
            // disconnect, so this waits before acting. The recheck asks
            // specifically whether *these* devices came back (by id) — an
            // earlier version asked whether ANY external sink was present,
            // which is always true on a phone (telephony, paired Bluetooth
            // and friends all sit in the output-device list permanently), so
            // it never paused at all and playback kept running on the phone
            // after unplugging.
            serviceScope.launch {
                delay(DEVICE_REMOVAL_DEBOUNCE_MS)
                val currentIds = (getSystemService(AUDIO_SERVICE) as AudioManager)
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .map { it.id }
                    .toSet()
                if (removedSinkIds.none { it in currentIds }) {
                    Log.d("RadioPlaybackServiceAudio", "External audio output stayed gone, pausing")
                    mediaSession.player.pause()
                } else {
                    Log.d("RadioPlaybackServiceAudio", "External audio output came back, not pausing")
                }
            }
        }
    }

    private fun AudioDeviceInfo.isExternalSink(): Boolean =
        isSink && type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE

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
            // Requests real audio focus. Media3 does NOT do this by default —
            // handleAudioFocus defaults to false, and this call was missing
            // entirely, so the app streamed audio without ever holding focus.
            // On a phone that mostly still makes noise, which is why it went
            // unnoticed; in a car it doesn't. Android Auto's head unit only
            // opens its media channel for a stream that has claimed media
            // focus, so playback ran "successfully" into a channel the car
            // kept muted — matching the symptom exactly (shows as playing,
            // silent until the car's own mute/unmute forces the amp to
            // re-open the channel). Declaring USAGE_MEDIA/CONTENT_TYPE_MUSIC
            // also tells the car this is music rather than an unclassified
            // stream, which is what its audio policy routes and ducks on.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Pauses automatically when the current audio output disappears
            // (ACTION_AUDIO_BECOMING_NOISY) — covers headphone unplug and,
            // relevantly, disconnecting from Android Auto (wired or
            // Bluetooth), which otherwise leaves audio playing through the
            // phone speaker. Standard Android/Media3 mechanism, tied to
            // actual audio-routing state rather than guessing at Android
            // Auto's own session-connect/disconnect protocol.
            .setHandleAudioBecomingNoisy(true)
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

        mediaSession = MediaLibrarySession.Builder(
            this,
            StationSkippingPlayer(player, ::playPreviousStation, ::playNextStation),
            librarySessionCallback
        )
            .setSessionActivity(sessionActivityIntent)
            .setBitmapLoader(bitmapLoader)
            .build()

        // Set session-wide (not per-controller via ConnectionResult) — a
        // per-controller custom layout scoped to just Android Auto's package
        // didn't reliably reach its actual now-playing UI, likely because
        // Auto connects through the legacy MediaBrowserServiceCompat bridge
        // (needed for car-launcher discovery — see the manifest's
        // android.media.browse.MediaBrowserService action) rather than a
        // native Media3 controller, and per-connection custom layouts don't
        // thread through that legacy conversion path the same way. Setting
        // it on the session itself is what legacy PlaybackStateCompat custom
        // actions are actually built from. Also shows up in the phone's own
        // notification/lock screen as a side effect — not just an Android
        // Auto feature.
        mediaSession.setCustomLayout(transportCustomLayout)

        // Registered last, after mediaSession exists — audioDeviceCallback
        // touches mediaSession.player, and device-change events could
        // otherwise theoretically arrive before it's initialized.
        (getSystemService(AUDIO_SERVICE) as AudioManager)
            .registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    // Only shuffle — previous/next are native seekToPrevious/seekToNext
    // commands now (see StationSkippingPlayer), not custom buttons, since
    // reusing custom actions for something that visually claims a native
    // transport slot was what made Android Auto's button positions unstable.
    private val transportCustomLayout: List<CommandButton> by lazy {
        listOf(
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setSessionCommand(SessionCommand(CMD_PLAY_RANDOM, Bundle.EMPTY))
                .setDisplayName("Random station")
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onDestroy() {
        (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(audioDeviceCallback)
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
        if (castPlayer != null) return
        player.pause() // keep the local ExoPlayer instance alive (paused) so switching back doesn't need to rebuild it
        val cp = CastPlayer(ctx, LiveStreamMediaItemConverter()).apply { addListener(playerListener) }.also { castPlayer = it }
        player.currentMediaItem?.let { item ->
            // Launched rather than inline: the content-type sniff does network
            // I/O. Assigning mediaSession.player below stays synchronous so
            // the session never briefly points at the old local player.
            serviceScope.launch {
                val contentType = sniffCastContentType(item.localConfiguration?.uri?.toString())
                cp.setMediaItem(item.withCastMimeType(contentType))
                cp.prepare()
                cp.playWhenReady = true
                markPlayRequested()
            }
        }
        mediaSession.player = StationSkippingPlayer(cp, ::playPreviousStation, ::playNextStation)
    }

    private fun switchToLocal() {
        if (castPlayer == null) return
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
        mediaSession.player = StationSkippingPlayer(player, ::playPreviousStation, ::playNextStation)
    }

    // ---- Playback control API, called from your UI/ViewModel via a controller ----

    fun playStation(stationId: String, streamUrl: String, title: String, imageUrl: String? = null) {
        repository.updateCurrentStation(stationId)
        repository.updateError(null)

        // Artwork bytes are resolved *before* the item is ever handed to the
        // player, not patched in afterwards — an earlier attempt embedded
        // artwork asynchronously post-play (via replaceMediaItem once
        // resolved), but Android Auto's compact "now playing" card (shown
        // alongside another app like Maps) apparently reads metadata once at
        // play-start and doesn't pick up a later update, so a late patch was
        // invisible to it. Resolving first is the only way that surface
        // reliably sees an image, and Coil's cache keeps this fast in
        // practice (the station's image was almost always already loaded
        // once, e.g. in a list/grid card, before it's played).
        serviceScope.launch {
            val artworkBytes = loadArtworkBytes(imageUrl)
            // Targets whichever player is currently attached to the session
            // (local ExoPlayer, or the CastPlayer if a Chromecast session is
            // active) so picking a new station from the app also redirects
            // an active cast.
            val target = mediaSession.player
            val item = buildPlayableMediaItem(stationId, streamUrl, title, imageUrl, artworkBytes)
            // mediaSession.player is always wrapped in StationSkippingPlayer now,
            // so it's never literally `is CastPlayer` — castPlayer's nullness is
            // the actual signal for "currently routing to cast".
            target.setMediaItem(
                if (castPlayer != null) item.withCastMimeType(sniffCastContentType(streamUrl)) else item
            )
            target.prepare()
            target.playWhenReady = true
            markPlayRequested()
        }
    }

    // CastPlayer's DefaultMediaItemConverter requires an explicit mimeType to
    // build its Cast queue item (throws otherwise) — local ExoPlayer doesn't
    // need one, it auto-detects via extractors, so buildPlayableMediaItem()
    // itself deliberately doesn't set one.
    //
    // This used to hardcode audio/mpeg for every station, which is simply
    // wrong for the AAC/HE-AAC stations that are common now — Operator Radio
    // serves audio/aacp, for one, despite advertising icy-br 320 and having
    // no codec hint in its URL. Receivers have been sniffing past the wrong
    // value rather than failing outright, so it went unnoticed, but declaring
    // the real type is what the Cast API asks for. Read from the stream's own
    // Content-Type and cached per URL, so repeat casts of a station don't
    // re-request.
    private val sniffedContentTypes = mutableMapOf<String, String>()

    private val sniffClient by lazy {
        OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    }

    private suspend fun sniffCastContentType(streamUrl: String?): String {
        if (streamUrl.isNullOrBlank()) return CAST_DEFAULT_CONTENT_TYPE
        sniffedContentTypes[streamUrl]?.let { return it }
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                // GET, not HEAD — plenty of Icecast/Shoutcast servers answer
                // HEAD with an error or something useless. The body is closed
                // without being read, so this costs one set of response
                // headers rather than any audio.
                val request = Request.Builder().url(streamUrl)
                    .header("Icy-MetaData", "0")
                    .build()
                sniffClient.newCall(request).execute().use { it.header("Content-Type") }
            }.getOrNull().let(::castContentTypeFor)
        }
        sniffedContentTypes[streamUrl] = resolved
        return resolved
    }

    private fun castContentTypeFor(contentType: String?): String {
        val value = contentType?.substringBefore(';')?.trim()?.lowercase()
            ?: return CAST_DEFAULT_CONTENT_TYPE
        return when {
            // audio/aacp (HE-AAC) and audio/aac are both raw ADTS to a receiver
            value.contains("aac") -> "audio/aac"
            value.contains("mp4") -> "audio/mp4"
            value.contains("flac") -> "audio/flac"
            value.contains("opus") || value.contains("ogg") -> "audio/ogg"
            value.contains("wav") -> "audio/wav"
            value.contains("mpeg") || value.contains("mp3") -> "audio/mpeg"
            // Unrecognised: keep the old default rather than declaring
            // something specific and wrong — receivers cope better with a
            // generic type they can sniff past than a confident mismatch.
            else -> CAST_DEFAULT_CONTENT_TYPE
        }
    }

    private fun MediaItem.withCastMimeType(contentType: String): MediaItem =
        buildUpon().setMimeType(contentType).build()

    /**
     * Shared by direct playback, the Android Auto browse tree, and
     * onAddMediaItems (which fills in a real URI for a browse-tree item
     * that only arrived with a mediaId).
     */
    private fun buildPlayableMediaItem(
        stationId: String,
        streamUrl: String,
        title: String,
        imageUrl: String?,
        artworkBytes: ByteArray? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setStation(title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply {
                when {
                    artworkBytes != null -> setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    imageUrl != null -> setArtworkUri(Uri.parse(imageUrl)) // fallback if resolving bytes failed
                }
            }
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

    /**
     * A synthetic browse-tree entry (not a real station) resolved specially
     * in onAddMediaItems below — picks a fresh random station at the moment
     * it's tapped, matching the app's own "Random station" shuffle button.
     */
    private fun buildRandomStationStub(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle("Random Station")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply { randomStationArtwork?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(RANDOM_STATION_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    // The app's own launcher glyph, reused as the Random Station icon —
    // decoded once and cached rather than re-reading the resource per browse
    // request.
    private val randomStationArtwork: ByteArray? by lazy {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
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

    fun playPreviousStation() = playAdjacentStation(offset = -1)
    fun playNextStation() = playAdjacentStation(offset = 1)

    // Treats the same favourites-first-then-alphabetical order used by the
    // Android Auto browse tree (StationLookup.getAllStations()) as a virtual
    // playlist for previous/next, since stations aren't a real ExoPlayer
    // multi-item playlist. Wraps around at either end.
    private fun playAdjacentStation(offset: Int) {
        val lookup = stationLookup ?: return
        serviceScope.launch {
            val stations = lookup.getAllStations()
            if (stations.isEmpty()) return@launch
            val currentIndex = stations.indexOfFirst { it.id == repository.currentStationId.value }
            val nextIndex = if (currentIndex == -1) 0 else (currentIndex + offset).mod(stations.size)
            val next = stations[nextIndex]
            playStation(next.id, next.streamUrl, next.title, next.imageUrl)
        }
    }

    fun togglePlayPause() {
        val target = mediaSession.player
        if (target.isPlaying) {
            target.pause()
        } else {
            target.play()
            markPlayRequested()
        }
    }

    private fun markPlayRequested() {
        lastPlayRequestedAtMillis = System.currentTimeMillis()
        suppressionRecoveryAttempted = false
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

        // Diagnostic only. Every unexplained "it paused itself" report so far
        // has been guesswork about which layer paused; this reports the
        // reason the platform itself attributes to the change, which
        // distinguishes an audio-focus loss from a becoming-noisy route
        // change from a remote controller (Android Auto, notification,
        // headset button) from a genuine in-app tap.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val reasonName = when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
                Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
                else -> "UNKNOWN($reason)"
            }
            Log.d("RadioPlaybackServiceAudio", "onPlayWhenReadyChanged: playWhenReady=$playWhenReady reason=$reasonName")
        }

        // The app never intentionally sets Player.volume below 1.0 anywhere
        // (no in-app volume control) — the only known way it drops is
        // ExoPlayer's own automatic audio-focus ducking, which is supposed
        // to restore it back to 1.0 on regaining focus. Matches an
        // intermittent, hard-to-reproduce report of audio going silent
        // (often right when Android Auto connects) until manually muted and
        // unmuted. Since a sub-1.0 volume is never legitimate here, self-heal
        // regardless of root cause rather than chase the exact focus-timing
        // race — cheap and safe.
        override fun onVolumeChanged(volume: Float) {
            Log.d("RadioPlaybackServiceAudio", "onVolumeChanged: $volume")
            if (volume < 1f) {
                mediaSession.player.volume = 1f
            }
        }

        // Root cause confirmed via `dumpsys audio`: right as a new audio
        // output device attaches (e.g. the car, connecting via Android
        // Auto), this app's AudioTrack got created, muted, and had its
        // routed device reassigned multiple times within about 12 seconds,
        // ending in a pause that never auto-resumed — ExoPlayer's own
        // audio-focus-regain handling getting stuck mid-race during that
        // routing churn, not app-level state. playWhenReady stays true
        // throughout (this is a *suppression*, not a real pause), so a
        // manual mute/unmute — which forces the OS audio pipeline to
        // re-evaluate the route — was the only thing that unstuck it.
        //
        // Reproducing that recovery automatically, BUT:
        // 1. Bounded to shortly after we actually asked to play something
        //    (lastPlayRequestedAtMillis) — an earlier version fired
        //    unconditionally and force-resumed playback after the car was
        //    turned off and disconnected, the opposite of what's wanted.
        //    Suppression still present long after any play request is a
        //    legitimate "nothing to play to" state, not the connect-time
        //    race, and must be left alone.
        // 2. One-shot per play request (suppressionRecoveryAttempted) — the
        //    retry's own play() call re-triggers this same callback if the
        //    suppression is genuinely persistent (e.g. no route at all),
        //    which without this guard re-armed another retry every time,
        //    looping every ~1-2s for the whole recovery window — confirmed
        //    via dumpsys audio as rapid AudioTrack create/pause/release
        //    cycling, not the intended single recovery attempt.
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            Log.d("RadioPlaybackServiceAudio", "onPlaybackSuppressionReasonChanged: $playbackSuppressionReason")
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) return
            // Now that the player actually manages audio focus (see
            // setAudioAttributes in onCreate), a transient-focus-loss
            // suppression is legitimate and self-resolving — it's how the car
            // ducks music under a navigation prompt or Assistant reply.
            // Forcing play() through it would talk over the very thing that
            // asked for silence, so leave that reason alone; the watchdog is
            // only for a suppression that never resolves on its own.
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) return
            if (suppressionRecoveryAttempted) return
            val target = mediaSession.player
            val requestedAt = lastPlayRequestedAtMillis
            serviceScope.launch {
                delay(SUPPRESSION_RECOVERY_DELAY_MS)
                val withinRecoveryWindow = System.currentTimeMillis() - requestedAt < SUPPRESSION_RECOVERY_WINDOW_MS
                if (withinRecoveryWindow &&
                    target.playWhenReady &&
                    target.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
                ) {
                    // target.play() alone is a no-op here: playWhenReady is
                    // already true, so ExoPlayer sees no state change and
                    // never re-requests audio focus or recreates the
                    // AudioTrack — which is exactly why this watchdog wasn't
                    // actually recovering anything before. A manual
                    // mute/unmute works because it's a real transition that
                    // forces the OS to re-evaluate the route; pause() then
                    // play() reproduces that from code (false->true is a
                    // genuine change, unlike true->true).
                    Log.w("RadioPlaybackServiceAudio", "Still suppressed after ${SUPPRESSION_RECOVERY_DELAY_MS}ms, forcing pause/play retry (one-shot)")
                    suppressionRecoveryAttempted = true
                    target.pause()
                    target.play()
                }
            }
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
            Log.d("RadioPlaybackServiceAA", "onConnect from packageName=${controller.packageName}")
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_PLAY_STATION, Bundle.EMPTY))
                .add(SessionCommand(CMD_PLAY_RANDOM, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .build()

            // Custom layout (shuffle button) is set session-wide in onCreate,
            // not per-connection here — see transportCustomLayout. Previous/next
            // are native seekToPrevious/seekToNext commands (StationSkippingPlayer),
            // not custom session commands.
            return MediaSession.ConnectionResult.accept(
                availableCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        // Deliberately does NOT pause on an Android Auto controller
        // disconnect any more, only logs. It used to, as a "harmless extra
        // layer" for stopping playback when you leave the car — it was not
        // harmless. Android Auto connects more than one controller (a
        // browse controller separate from the playback one) and drops the
        // browse controller during normal use, e.g. whenever you navigate
        // away from STATIC to another screen like Maps. Every one of those
        // routine disconnects hard-paused playback, needing a manual tap on
        // play to resume — the reported "randomly pauses itself". It also
        // never achieved what it was added for: on a real unplug the whole
        // session tears down without delivering this callback, which is why
        // playback kept running on the phone regardless. Stopping on a genuine
        // disconnect is handled by audioDeviceCallback, on the actual audio
        // routing signal.
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            Log.d("RadioPlaybackServiceAA", "onDisconnected from packageName=${controller.packageName}")
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
                val stationItems = coroutineScope {
                    stations.map { async { buildBrowsableStub(it) } }.awaitAll()
                }
                val children = listOf(buildRandomStationStub()) + stationItems
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
            if (mediaId == RANDOM_STATION_ID) {
                future.set(LibraryResult.ofItem(buildRandomStationStub(), null))
                return future
            }
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
                    } else if (item.mediaId == RANDOM_STATION_ID) {
                        // Resolved at tap time, not baked into the browse list, so it's
                        // actually random each time rather than a fixed station.
                        stationLookup?.getRandomStation(excludeId = repository.currentStationId.value)?.let {
                            repository.updateCurrentStation(it.id)
                            val built = buildPlayableMediaItem(it.id, it.streamUrl, it.title, it.imageUrl, loadArtworkBytes(it.imageUrl))
                            if (castPlayer != null) built.withCastMimeType(sniffCastContentType(it.streamUrl)) else built
                        }
                    } else {
                        stationLookup?.getStation(item.mediaId)?.let {
                            repository.updateCurrentStation(it.id)
                            val built = buildPlayableMediaItem(it.id, it.streamUrl, it.title, it.imageUrl, loadArtworkBytes(it.imageUrl))
                            if (castPlayer != null) built.withCastMimeType(sniffCastContentType(it.streamUrl)) else built
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
        const val RANDOM_STATION_ID = "static_radio_random"
        const val SUPPRESSION_RECOVERY_DELAY_MS = 3000L
        // The connect-time routing race observed via dumpsys settled within
        // ~12s; this is a generous multiple of that so the watchdog only
        // ever recovers a stuck *connect-time* suppression, never a
        // legitimate later one (e.g. car turned off and disconnected).
        const val SUPPRESSION_RECOVERY_WINDOW_MS = 20_000L

        // How long to wait after an external audio sink is reported removed
        // before actually pausing — long enough to ride out the connect-time
        // device reassignment churn (see onPlaybackSuppressionReasonChanged)
        // without meaningfully delaying a real disconnect's stop-on-exit.
        const val DEVICE_REMOVAL_DEBOUNCE_MS = 1500L

        // Fallback when a stream's Content-Type is missing or unrecognised —
        // the old unconditional hardcoded value, now only a last resort.
        const val CAST_DEFAULT_CONTENT_TYPE = "audio/mpeg"

        // The Android Auto phone-projection app's package — used to stop
        // playback when a wired/wireless Auto session disconnects (see
        // onDisconnected below) rather than continuing to play through the
        // phone speaker once you've left the car.
        const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

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
