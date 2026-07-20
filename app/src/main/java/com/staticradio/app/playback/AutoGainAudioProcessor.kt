package com.staticradio.app.playback

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Real-time automatic gain control. Internet radio stations don't send
 * loudness metadata (no ReplayGain/EBU R128 tags in-band), so there's
 * nothing to read off the stream to normalize against — this measures the
 * signal itself (short-term RMS) and slowly adapts gain toward a target
 * level, smoothed so it doesn't audibly pump on transients. PCM 16-bit only;
 * passes through unchanged for any other encoding.
 */
class AutoGainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private var currentGain = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || remaining % 2 != 0) {
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val basePosition = input.position()
        val sampleCount = remaining / 2

        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val sample = input.getShort(basePosition + i * 2).toDouble()
            sumSquares += sample * sample
        }
        val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount).toFloat() else 0f

        val desiredGain = if (rms > 1f) (TARGET_RMS / rms).coerceIn(MIN_GAIN, MAX_GAIN) else currentGain
        currentGain += (desiredGain - currentGain) * SMOOTHING

        val output = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val boosted = input.getShort(basePosition + i * 2) * currentGain
            val clamped = boosted.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            output.putShort(clamped.toInt().toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private companion object {
        // -20 dBFS target, expressed as a fraction of full-scale (32768).
        const val TARGET_RMS = 0.1f * 32768f
        const val MIN_GAIN = 0.3f   // floor — avoids amplifying noise/silence during dead air
        const val MAX_GAIN = 4f     // ceiling — avoids over-boosting already-quiet stations
        const val SMOOTHING = 0.02f // slow adaptation avoids audible pumping
    }
}
