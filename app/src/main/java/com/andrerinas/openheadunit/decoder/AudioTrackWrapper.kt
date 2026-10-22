package com.andrerinas.openheadunit.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import com.andrerinas.openheadunit.utils.AppLog
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AudioTrackWrapper(
    stream: Int,
    sampleRateInHz: Int,
    bitDepth: Int,
    channelCount: Int,
    private val isAac: Boolean = false,
    gain: Float,
    private val audioLatencyMultiplier: Int = 8,
    private val audioQueueCapacity: Int = 0,
    private val mixer: AudioMixer? = null,
    private val channelId: Int = -1,
    private val attachHwDspEqualizer: Boolean = false
) : Thread() {

    private data class AudioChunk(
        val data: ByteArray,
        val size: Int
    )

    companion object {
        private const val AUDIO_BUFFER_POOL_LIMIT = 16
        private const val MIN_POOLED_AUDIO_BUFFER_SIZE = 4096

        // Audio held back before playback starts, so the track has a cushion to ride out the
        // wireless link's jitter instead of playing every chunk the moment it lands. Music can
        // afford more than guidance and speech, which must stay close to the picture and the user.
        // Measured on a Sealion 06 drive: the phone's media stream paused for 0.6-0.9 s at a time,
        // and a 200 ms cushion ran dry on every one of those.
        private const val MEDIA_PREBUFFER_MS = 400
        private const val SPEECH_PREBUFFER_MS = 80

        // A drained track after a gap this long is the phone having paused, not a glitch.
        private const val PAUSE_GAP_MS = 1000L
    }

    private val audioTrack: AudioTrack?
    private var decoder: MediaCodec? = null
    private var codecHandlerThread: HandlerThread? = null
    private val freeInputBuffers = LinkedBlockingQueue<Int>()
    private val writeExecutor = Executors.newSingleThreadExecutor()
    private val writeSemaphore = java.util.concurrent.Semaphore(3)
    private var equalizer: Equalizer? = null

    // Limit queue capacity to provide backpressure to the network thread if audio playback is slow
    private val dataQueue = if (audioQueueCapacity > 0)
        LinkedBlockingQueue<AudioChunk>(audioQueueCapacity)
    else
        LinkedBlockingQueue<AudioChunk>()
    private val audioBufferPool = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var isRunning = true

    @Volatile
    private var currentGain: Float = gain

    fun setVolume(gain: Float) {
        currentGain = gain
        val track = audioTrack
        if (track != null) {
            try {
                val hwGain = gain.coerceAtMost(1.0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    track.setVolume(hwGain)
                } else {
                    @Suppress("DEPRECATION")
                    track.setStereoVolume(hwGain, hwGain)
                }
            } catch (e: Exception) {
                AppLog.e("Failed to set volume on AudioTrack", e)
            }
        } else {
            mixer?.setChannelGain(channelId, gain)
        }
    }

    private fun applyGain(buffer: ByteArray, size: Int) {
        if (currentGain <= 1.0f) return
        for (i in 0 until size - 1 step 2) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt() // High byte handles sign
            val sample = (high shl 8) or low
            val modifiedSample = (sample * currentGain).toInt().coerceIn(-32768, 32767)
            buffer[i] = (modifiedSample and 0xFF).toByte()
            buffer[i + 1] = (modifiedSample shr 8).toByte()
        }
    }

    // Track frames written for better draining
    private var framesWritten: Long = 0
    private val bytesPerFrame: Int = channelCount * (if (bitDepth == 16) 2 else 1)

    // Jitter cushion for the direct AudioTrack path. The track is created with a buffer several
    // times its minimum, but a buffer only helps when it holds something: started empty, it plays
    // each chunk as it arrives and the first late one is an audible gap. So playback waits until
    // [prebufferFrames] are queued, and goes back to waiting whenever the track runs dry, rather
    // than resuming with no cushion and stuttering on every following hiccup.
    // Guarded by [trackLock]: PCM is written from this thread, decoded AAC from writeExecutor.
    private val trackLock = Any()
    private var trackBufferFrames: Int = 0
    private var prebufferFrames: Int = 0
    private var prebuffering = true
    private var lastTrackWriteMs = 0L
    private var underruns = 0
    private val sampleRate = sampleRateInHz

    init {
        this.name = "AudioPlaybackThread"
        audioTrack = if (mixer == null) {
            createAudioTrack(stream, sampleRateInHz, bitDepth, channelCount, audioLatencyMultiplier)
        } else {
            null
        }

        if (mixer != null) {
            mixer.registerChannel(channelId, sampleRateInHz, channelCount)
            mixer.setChannelGain(channelId, gain)
        } else {
            setVolume(gain)
            audioTrack?.let { track ->
                attachHwDspEqualizerQuietly(track.audioSessionId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && track.bufferSizeInFrames > 0) {
                    // The framework may size the track differently from what was asked for.
                    trackBufferFrames = if (trackBufferFrames > 0) {
                        minOf(trackBufferFrames, track.bufferSizeInFrames)
                    } else {
                        track.bufferSizeInFrames
                    }
                }
                val targetMs = if (stream == AudioManager.STREAM_MUSIC && channelCount == 2) {
                    MEDIA_PREBUFFER_MS
                } else {
                    SPEECH_PREBUFFER_MS
                }
                // Never more than half the track buffer: a blocking write into a full track that
                // has not been started would wait forever.
                prebufferFrames = minOf(sampleRateInHz * targetMs / 1000, trackBufferFrames / 2)
                    .coerceAtLeast(0)
                AppLog.i("AudioTrackWrapper: channel=$channelId prebuffer=${prebufferFrames} frames " +
                        "(${prebufferFrames * 1000L / sampleRateInHz}ms of ${trackBufferFrames} buffered)")
                if (prebufferFrames == 0) {
                    prebuffering = false
                    track.play()
                }
            }
        }

        if (isAac) {
            initDecoder(sampleRateInHz, channelCount)
        }

        this.start()
    }

    private fun initDecoder(sampleRate: Int, channels: Int) {
        try {
            val mime = "audio/mp4a-latm"
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels)
            format.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            // CSD for RAW AAC-LC (AudioSpecificConfig)
            val csd = makeAacCsd(sampleRate, channels)
            format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(csd))

            decoder = MediaCodec.createDecoderByType(mime)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Use a HandlerThread for the codec callback but set its priority to AUDIO
                // to prevent it from being starved by the video decoder.
                codecHandlerThread = object : HandlerThread("AacCodecThread") {
                    override fun onLooperPrepared() {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                    }
                }
                codecHandlerThread!!.start()

                val callback = object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        freeInputBuffers.offer(index)
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        try {
                            if (!isRunning) return
                            val outputBuffer = codec.getOutputBuffer(index)
                            if (outputBuffer != null && info.size > 0) {
                                val chunk = ByteArray(info.size)
                                outputBuffer.position(info.offset)
                                outputBuffer.get(chunk)
                                outputBuffer.clear()

                                writeSemaphore.acquire()
                                // Write to AudioTrack or Mixer using executor
                                writeExecutor.submit {
                                    try {
                                        writeToTrack(chunk)
                                    } catch (e: Exception) {
                                        AppLog.e("Error writing decoded AAC to AudioTrack", e)
                                    } finally {
                                        writeSemaphore.release()
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(index, false)
                        } catch (e: Exception) {
                            AppLog.e("Error processing AAC output", e)
                            if (e is InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                        }
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        AppLog.e("AAC Codec Error", e)
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        AppLog.i("AAC Output Format Changed: $format")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val handler = Handler(codecHandlerThread!!.looper)
                    decoder!!.setCallback(callback, handler)
                } else {
                    decoder!!.setCallback(callback)
                }
            }

            decoder?.configure(format, null, null, 0)
            decoder?.start()
            AppLog.i("AAC Decoder started for $sampleRate Hz, $channels channels (Async)")
        } catch (e: Exception) {
            AppLog.e("Failed to init AAC decoder", e)
        }
    }

    private fun writeToTrack(buffer: ByteArray) {
        writeToTrack(buffer, buffer.size)
    }

    private fun writeToTrack(buffer: ByteArray, size: Int) {
        if (mixer != null) {
            mixer.feed(channelId, buffer, 0, size)
            framesWritten += size / bytesPerFrame
        } else {
            val track = audioTrack ?: return
            applyGain(buffer, size)
            synchronized(trackLock) {
                val now = android.os.SystemClock.elapsedRealtime()
                rearmIfDrained(track, now)
                val result = track.write(buffer, 0, size)
                if (result > 0) {
                    framesWritten += result / bytesPerFrame
                }
                lastTrackWriteMs = now
                if (prebuffering && pendingFrames(track) >= prebufferFrames) {
                    startTrack(track, "prebuffer filled")
                }
            }
        }
    }

    /** Frames handed to the track that it has not played yet. */
    private fun pendingFrames(track: AudioTrack): Long {
        // The head position is an unsigned 32-bit frame counter.
        val played = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        return (framesWritten - played).coerceAtLeast(0L)
    }

    /**
     * A playing track that has nothing left to play has underrun. Pause it and collect a fresh
     * cushion before resuming; resuming straight away would play the next chunk with nothing behind
     * it, and the link that just stalled tends to stall again.
     */
    private fun rearmIfDrained(track: AudioTrack, now: Long) {
        if (prebuffering || prebufferFrames == 0) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
        if (pendingFrames(track) > 0) return

        val gapMs = if (lastTrackWriteMs == 0L) 0L else now - lastTrackWriteMs
        if (gapMs < PAUSE_GAP_MS) {
            underruns++
            AppLog.w("AudioTrackWrapper: channel=$channelId ran dry after a ${gapMs}ms gap in incoming audio " +
                    "(underrun #$underruns) - rebuffering ${prebufferFrames * 1000L / sampleRate}ms")
        }
        try {
            track.pause()
        } catch (e: IllegalStateException) {
            AppLog.e("AudioTrackWrapper: pause failed", e)
            return
        }
        prebuffering = true
    }

    private fun startTrack(track: AudioTrack, reason: String) {
        try {
            track.play()
            prebuffering = false
            AppLog.d("AudioTrackWrapper: channel=$channelId playback started ($reason, " +
                    "${pendingFrames(track) * 1000L / sampleRate}ms queued)")
        } catch (e: IllegalStateException) {
            AppLog.e("AudioTrackWrapper: play failed", e)
        }
    }

    /**
     * Incoming audio has stopped while the cushion is still filling: a short prompt or the tail of
     * a stream that will never reach [prebufferFrames]. Play what is there instead of holding it.
     */
    private fun flushPrebuffer(reason: String) {
        val track = audioTrack ?: return
        synchronized(trackLock) {
            if (prebuffering && pendingFrames(track) > 0) {
                startTrack(track, reason)
            }
        }
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        // Drain the queue even after isRunning is set to false
        while (isRunning || dataQueue.isNotEmpty()) {
            try {
                // Use poll to avoid blocking indefinitely if isRunning becomes false
                val chunk = dataQueue.poll(200, TimeUnit.MILLISECONDS)
                if (chunk == null) {
                    flushPrebuffer("incoming audio paused")
                } else {
                    try {
                        if (isAac && decoder != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                queueInput(chunk.data, chunk.size)
                            } else {
                                decodeSync(chunk.data, chunk.size)
                            }
                        } else {
                            // PCM path - direct write in this high-priority thread
                            writeToTrack(chunk.data, chunk.size)
                        }
                    } finally {
                        recycleAudioBuffer(chunk.data)
                    }
                }
            } catch (e: InterruptedException) {
                drainQueuedAudio()
                break
            } catch (e: Exception) {
                AppLog.e("Error in AudioTrackWrapper run loop", e)
                isRunning = false
            }
        }
        cleanup()
        AppLog.i("AudioTrackWrapper thread finished.")
    }

    @Suppress("DEPRECATION")
    private fun decodeSync(inputData: ByteArray, size: Int) {
        try {
            val dec = this.decoder ?: return
            val inputIndex = dec.dequeueInputBuffer(200000)
            if (inputIndex >= 0) {
                val inputBuffer = dec.inputBuffers[inputIndex]
                inputBuffer.clear()
                inputBuffer.put(inputData, 0, size)
                dec.queueInputBuffer(inputIndex, 0, size, 0, 0)
            }

            val info = MediaCodec.BufferInfo()
            var outputIndex = dec.dequeueOutputBuffer(info, 0)
            while (outputIndex >= 0) {
                val outputBuffer = dec.outputBuffers[outputIndex]
                val chunk = ByteArray(info.size)
                outputBuffer.position(info.offset)
                outputBuffer.get(chunk)
                writeToTrack(chunk)
                dec.releaseOutputBuffer(outputIndex, false)
                outputIndex = dec.dequeueOutputBuffer(info, 0)
            }
        } catch (e: Exception) {
            AppLog.e("Error in decodeSync", e)
        }
    }

    @Throws(InterruptedException::class)
    private fun queueInput(inputData: ByteArray, size: Int) {
        try {
            // Wait for input buffer (with timeout to avoid deadlock if codec dies)
            // Restore to 200ms to prevent dropping frames under load
            val inputIndex = freeInputBuffers.poll(200, TimeUnit.MILLISECONDS)

            if (inputIndex != null && inputIndex >= 0) {
                val inputBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    decoder?.getInputBuffer(inputIndex)
                } else {
                    @Suppress("DEPRECATION")
                    decoder?.inputBuffers?.get(inputIndex)
                }

                inputBuffer?.clear()
                inputBuffer?.put(inputData, 0, size)
                decoder?.queueInputBuffer(inputIndex, 0, size, 0, 0)
            } else {
                AppLog.w("AAC Input Buffer timeout (200ms) - dropping frame")
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("Error queuing AAC input", e)
        }
    }

    private fun makeAacCsd(sampleRate: Int, channelCount: Int): ByteArray {
        val sampleRateIndex = getFrequencyIndex(sampleRate)
        val audioObjectType = 2 // AAC-LC

        // Correct packing: [AOT:5][FreqIdx:4][ChanCfg:4][...padding:3]
        val config = ((audioObjectType and 0x1F) shl 11) or
                     ((sampleRateIndex and 0x0F) shl 7) or
                     ((channelCount and 0x0F) shl 3)

        return byteArrayOf(
            ((config shr 8) and 0xFF).toByte(),
            (config and 0xFF).toByte()
        )
    }

    private fun getFrequencyIndex(sampleRate: Int): Int {
        return when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000  -> 11
            7350  -> 12
            else  -> 4 // Default 44100
        }
    }

    private fun createAudioTrack(
        stream: Int,
        sampleRateInHz: Int,
        bitDepth: Int,
        channelCount: Int,
        multiplier: Int
    ): AudioTrack {
        val channelConfig =
            if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val dataFormat =
            if (bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, dataFormat)
        // Room for twice the media cushion: the cushion is capped at half the track, so a buffer
        // sized from the minimum alone would silently cut it short.
        val cushionBytes = if (channelCount == 2 && stream == AudioManager.STREAM_MUSIC) {
            sampleRateInHz * bytesPerFrame * MEDIA_PREBUFFER_MS * 2 / 1000
        } else 0
        val bufferSize = if (minBufferSize > 0) maxOf(minBufferSize * multiplier, cushionBytes) else minBufferSize
        trackBufferFrames = if (bufferSize > 0) bufferSize / bytesPerFrame else 0

        AppLog.i("Audio stream: $stream buffer size: $bufferSize (min: $minBufferSize) sampleRateInHz: $sampleRateInHz channelCount: $channelCount")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val (usage, contentType) = when (stream) {
                AudioManager.STREAM_NOTIFICATION -> Pair(
                    AudioAttributes.USAGE_NOTIFICATION,
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                )
                AudioManager.STREAM_VOICE_CALL -> Pair(
                    AudioAttributes.USAGE_VOICE_COMMUNICATION,
                    AudioAttributes.CONTENT_TYPE_SPEECH
                )
                else -> Pair(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.CONTENT_TYPE_MUSIC
                )
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .setLegacyStreamType(stream)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRateInHz)
                .setChannelMask(channelConfig)
                .setEncoding(dataFormat)
                .build()

            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                stream,
                sampleRateInHz,
                channelConfig,
                dataFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
    }

    private fun attachHwDspEqualizerQuietly(sessionId: Int) {
        if (!attachHwDspEqualizer) return
        if (sessionId != AudioManager.AUDIO_SESSION_ID_GENERATE && sessionId > 0) {
            try {
                equalizer?.release()
                val eq = Equalizer(0, sessionId)
                eq.enabled = true
                equalizer = eq
                AppLog.i("Attached dummy Equalizer to audioSessionId $sessionId to trigger HW DSP")
            } catch (t: Throwable) {
                // Ignore if Equalizer or AudioEffect is unsupported on device
            }
        }
    }

    fun write(buffer: ByteArray, offset: Int, size: Int) {
        if (!isRunning) return

        var data: ByteArray? = null
        try {
            data = obtainAudioBuffer(size)
            System.arraycopy(buffer, offset, data, 0, size)
            val success = dataQueue.offer(AudioChunk(data, size), 5, TimeUnit.MILLISECONDS)
            if (!success) {
                recycleAudioBuffer(data)
                AppLog.w("Audio queue is full, dropping audio frame to prevent stalling")
            }
        } catch (e: InterruptedException) {
            data?.let { recycleAudioBuffer(it) }
            Thread.currentThread().interrupt()
            AppLog.w("Interrupted while putting audio data to queue")
        }
    }

    fun setGain(gain: Float) {
        AppLog.d("AudioTrackWrapper: updating gain to $gain")
        setVolume(gain)
    }

    fun stopPlayback() {
        isRunning = false
        this.interrupt()
    }

    private fun cleanup() {
        // stopPlayback() interrupts this thread. When the interrupt lands during a blocking track
        // write rather than in poll(), its flag survives to here and the drain wait below threw on
        // its first sleep - so a navigation prompt's last few hundred ms were released unplayed
        // (seen ~10 times a day on a Sealion 06, always right after "Audio Stop"). The thread is
        // ending anyway; clear the flag so the bounded drain can run.
        Thread.interrupted()
        drainQueuedAudio()
        audioBufferPool.clear()

        // 1. Stop the decoder to stop producing new output buffers
        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            AppLog.e("Error releasing audio decoder", e)
        }

        // 2. Wait for AAC writes that were already submitted to the executor
        writeExecutor.shutdown()
        try {
            if (!writeExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                AppLog.w("Audio write executor did not terminate in time")
                writeExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            AppLog.w("Audio write executor interrupted during shutdown")
            writeExecutor.shutdownNow()
        }

        if (mixer != null) {
            mixer.unregisterChannel(channelId)
        }

        // A cushion still filling when the stream ended has never been heard; play it out like
        // any other buffered tail.
        flushPrebuffer("stream ended")
        if (underruns > 0) {
            AppLog.i("AudioTrackWrapper: channel=$channelId finished with $underruns underrun(s)")
        }

        // 3. Gracefully stop the AudioTrack – stop() plays remaining buffer data
        val track = audioTrack
        if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.stop()

                // Wait for the AudioTrack hardware buffer to drain.
                // Especially important on older devices (KitKat etc.).
                var lastPos = -1
                var stagnantCount = 0
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 2500) {
                    val pos = track.playbackHeadPosition
                    if (framesWritten > 0 && pos >= framesWritten) break
                    if (pos == lastPos && pos > 0) {
                        stagnantCount++
                        if (stagnantCount >= 3) break
                    } else {
                        lastPos = pos
                        stagnantCount = 0
                    }
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                AppLog.e("Error during audio track cleanup", e)
            }
        }

        // 4. Release the AudioTrack
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            AppLog.e("Error releasing audio track", e)
        }

        // 5. Clean up the codec handler thread
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                codecHandlerThread?.quitSafely()
            } else {
                codecHandlerThread?.quit()
            }
            codecHandlerThread = null
        } catch (e: Exception) {
            AppLog.e("Error quitting codec thread", e)
        }
    }

    private fun obtainAudioBuffer(size: Int): ByteArray {
        while (true) {
            val pooled = audioBufferPool.poll() ?: return ByteArray(maxOf(size, MIN_POOLED_AUDIO_BUFFER_SIZE))
            if (pooled.size >= size) return pooled
        }
    }

    private fun recycleAudioBuffer(buffer: ByteArray) {
        if (audioBufferPool.size < AUDIO_BUFFER_POOL_LIMIT) {
            audioBufferPool.offer(buffer)
        }
    }

    private fun drainQueuedAudio() {
        while (true) {
            val chunk = dataQueue.poll() ?: break
            recycleAudioBuffer(chunk.data)
        }
    }
}
