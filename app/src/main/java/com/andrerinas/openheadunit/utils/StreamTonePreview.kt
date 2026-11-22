package com.andrerinas.openheadunit.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a short tone on one audio stream type, so the driver can move the car's sliders and hear
 * which one controls it. Stream 0 plays by Android audio usage instead.
 */
class StreamTonePreview {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "stream-tone-preview").apply { isDaemon = true } }
    @Volatile private var current: AudioTrack? = null

    fun play(stream: Int, usage: Int) {
        // Tested the way the audio will play: a stream number Android does not map to its slider
        // is replaced by the usage that does reach it.
        val reached = com.andrerinas.openheadunit.aap.CarVolumeGroups.reachOnThisUnit(
            com.andrerinas.openheadunit.aap.CarVolumeGroups.Route(usage, AudioAttributes.CONTENT_TYPE_SPEECH, stream.takeIf { it > 0 }))
        play(reached.legacyStream ?: 0, reached.usage, stream)
    }

    private fun play(stream: Int, usage: Int, asked: Int) {
        if (stream != asked) AppLog.i("Tone preview: stream $asked plays by usage $usage, which reaches its slider")
        worker.execute {
            current?.let { runCatching { it.stop(); it.release() } }
            current = null
            val pcm = tone()
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val size = maxOf(min, pcm.size * 2)
            val track = runCatching {
                if (stream > 0) {
                    @Suppress("DEPRECATION")
                    AudioTrack(stream, RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(size)
                        .build()
                } else null
            }.getOrNull()
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                AppLog.w("Tone preview: stream $stream is not available on this head unit")
                track?.release()
                return@execute
            }
            current = track
            runCatching {
                track.play()
                track.write(pcm, 0, pcm.size)
                Thread.sleep(TONE_MS + 150L)
                track.stop()
            }
            track.release()
            if (current === track) current = null
        }
    }

    fun close() {
        worker.execute { current?.let { runCatching { it.stop(); it.release() } }; current = null }
        worker.shutdown()
    }

    private fun tone(): ShortArray {
        val samples = RATE * TONE_MS / 1000
        return ShortArray(samples) { i ->
            val fade = minOf(1.0, minOf(i, samples - i) / (RATE * 0.02))
            (sin(2 * PI * 880.0 * i / RATE) * 0.5 * fade * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private companion object {
        const val RATE = 16000
        const val TONE_MS = 700
    }
}
