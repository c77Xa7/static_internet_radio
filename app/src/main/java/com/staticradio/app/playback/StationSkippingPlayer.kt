package com.staticradio.app.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Wraps whichever Player is attached to the session (local ExoPlayer or
 * CastPlayer) so consumers like Android Auto see genuine, stable native
 * seekToPrevious/seekToNext transport commands — mapped here to
 * previous/next *station* — instead of custom SessionCommand buttons using
 * the same skip icons.
 *
 * Reusing custom actions for something that visually claims a native
 * transport slot (previous/next) is what caused Android Auto's now-playing
 * UI to render the buttons in unstable, swapping positions — it was trying
 * to reconcile our custom buttons with the native skip slots it expects to
 * exist. Native seek commands get fixed, predictable slots the platform
 * already knows how to lay out; only shuffle remains a genuine custom
 * action, since there's no equivalent native "play something else" command.
 */
class StationSkippingPlayer(
    player: Player,
    private val onSeekToPrevious: () -> Unit,
    private val onSeekToNext: () -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_NEXT ||
            super.isCommandAvailable(command)

    override fun seekToPrevious() = onSeekToPrevious()

    override fun seekToNext() = onSeekToNext()
}
