package com.staticradio.app.playback

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem

/**
 * Radio stations are continuous, durationless live streams, but Media3's
 * DefaultMediaItemConverter hardcodes MediaInfo.STREAM_TYPE_BUFFERED (an
 * on-demand file with a known duration) for every item, with no way to
 * override it via MediaItem. A receiver treating a live ICY stream as VOD
 * misjudges its own buffering — confirmed as the cause of a repeatable
 * pattern where playback starts fine, then ~400ms later drops into several
 * seconds of unprompted rebuffering, identically on two different Cast
 * devices (so it's about what we declare, not a receiver-specific quirk).
 *
 * Delegates everything else (metadata, mimeType, the custom-data JSON
 * payload CastPlayer relies on to deserialize items back via toMediaItem)
 * to DefaultMediaItemConverter, only rebuilding the MediaInfo with
 * STREAM_TYPE_LIVE instead.
 */
class LiveStreamMediaItemConverter : MediaItemConverter {

    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val original = delegate.toMediaQueueItem(mediaItem)
        val originalInfo = original.media ?: return original
        val liveInfo = MediaInfo.Builder(originalInfo.contentId)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(originalInfo.contentType)
            .setContentUrl(originalInfo.contentUrl!!) // always set — DefaultMediaItemConverter always populates it from the MediaItem's uri
            .setMetadata(originalInfo.metadata)
            .setCustomData(originalInfo.customData)
            .build()
        return MediaQueueItem.Builder(liveInfo).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)
}
