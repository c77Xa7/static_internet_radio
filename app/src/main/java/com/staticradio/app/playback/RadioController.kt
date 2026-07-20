package com.staticradio.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper around MediaController connection lifecycle.
 * Hold ONE instance of this at Application scope (or via Hilt @Singleton) —
 * reconnecting per-screen is wasteful and causes UI flicker on nav changes.
 */
class RadioController(private val context: Context) {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    suspend fun connect(): MediaController = suspendCancellableCoroutine { cont ->
        val sessionToken = SessionToken(
            context,
            ComponentName(context, RadioPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            val mediaController = future.get()
            controller = mediaController
            if (cont.isActive) cont.resume(mediaController)
        }, MoreExecutors.directExecutor())

        cont.invokeOnCancellation { release() }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    // ---- Actions the UI calls. These map to custom session commands or ----
    // ---- plain Player transport controls depending on what's needed.    ----

    fun play() = controller?.play()
    fun pause() = controller?.pause()
    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun playStation(stationId: String, streamUrl: String, title: String, imageUrl: String? = null) {
        val args = Bundle().apply {
            putString(RadioPlaybackService.ARG_STATION_ID, stationId)
            putString(RadioPlaybackService.ARG_STREAM_URL, streamUrl)
            putString(RadioPlaybackService.ARG_TITLE, title)
            imageUrl?.let { putString(RadioPlaybackService.ARG_IMAGE_URL, it) }
        }
        controller?.sendCustomCommand(
            SessionCommand(RadioPlaybackService.CMD_PLAY_STATION, Bundle.EMPTY),
            args
        )
    }

    fun playRandom() {
        controller?.sendCustomCommand(
            SessionCommand(RadioPlaybackService.CMD_PLAY_RANDOM, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    fun startSleepTimer(durationMillis: Long) {
        val args = Bundle().apply {
            putLong(RadioPlaybackService.ARG_DURATION_MILLIS, durationMillis)
        }
        controller?.sendCustomCommand(
            SessionCommand(RadioPlaybackService.CMD_SET_SLEEP_TIMER, Bundle.EMPTY),
            args
        )
    }

    fun cancelSleepTimer() {
        controller?.sendCustomCommand(
            SessionCommand(RadioPlaybackService.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }
}
