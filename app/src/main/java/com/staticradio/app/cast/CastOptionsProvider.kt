package com.staticradio.app.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Required entry point the Cast SDK looks up via the manifest meta-data
 * (com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME).
 * Targets the generic default media receiver — no custom receiver app is
 * registered, since stations are plain HTTP(S) audio streams the default
 * receiver already knows how to play.
 *
 * setMediaSessionEnabled(false) is required — by default the Cast SDK spins
 * up its own separate MediaSession + notification the moment a session
 * starts, on top of RadioPlaybackService's own MediaLibrarySession/
 * notification, which is what's really driving Cast playback here
 * (mediaSession.player swaps to a CastPlayer — see switchToCast). Left at
 * the default, that showed up as three near-identical "now playing" cards
 * in the notification shade.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setMediaSessionEnabled(false)
                    .build()
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
