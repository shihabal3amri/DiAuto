package com.andrerinas.openheadunit.cluster

import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.RequiresApi
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Decodes the phone's cluster stream onto the cluster presentation's surface.
 *
 * Deliberately separate from the main [com.andrerinas.openheadunit.decoder.VideoDecoder]: two
 * decoders must never share a surface or a codec, and a failure here must not touch the main
 * picture. Everything that touches the codec runs on one thread. After any (re)start the decoder
 * waits for an IDR frame and asks the phone for one, so it never shows a smeared picture.
 *
 * The phone starts the cluster stream about a tenth of a second after setup, before the cluster
 * window has a surface, and a still map may not send another keyframe for a long time. So until
 * a window is there the decoder draws into a hidden placeholder, and windows are handed over with
 * setOutputSurface, keeping the reference frames: the map appears as soon as the window does.
 */
@RequiresApi(23)
internal class ClusterVideoDecoder(
    private val width: Int,
    private val height: Int,
    private val onPicture: () -> Unit,
    private val requestKeyframe: () -> Unit,
) {
    private val thread = HandlerThread("diauto-cluster-video").apply { start() }
    private val handler = Handler(thread.looper)

    // Decoder thread only.
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var placeholder: ImageReader? = null
    private var parameterSets: ByteArray? = null
    private var waitingForKeyframe = true
    private val pending = ArrayDeque<Pair<ByteArray, Int>>()
    private val freeInputs = ArrayDeque<Int>()
    private var presentationUs = 0L
    private var pictureShown = false
    private var lastKeyframeRequestMs = 0L
    private var released = false

    fun setSurface(next: Surface?) {
        handler.post {
            if (released || next === surface) return@post
            surface = next
            val running = codec ?: return@post
            // A direct handoff keeps the reference frames, so the map continues without an IDR.
            try {
                running.setOutputSurface(outputSurface())
                if (next != null) {
                    // A still map may not send another frame for a while; ask for a full one now,
                    // and report the first picture that reaches this window.
                    pictureShown = false
                    askForKeyframe()
                }
            } catch (error: RuntimeException) {
                AppLog.w("ClusterMap: surface handoff failed, restarting the decoder: ${error.message}")
                stopCodec()
                startCodec()
                askForKeyframe()
            }
        }
    }

    /** The cluster window's surface, or the hidden placeholder while there is none. */
    private fun outputSurface(): Surface {
        surface?.let { return it }
        val reader = placeholder ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 3, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        else ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 3)).also { created ->
            // Nothing shows these frames; returning each buffer at once keeps the decoder running.
            created.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
            placeholder = created
        }
        return reader.surface
    }

    fun decode(buf: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val unit = buf.copyOfRange(offset, offset + length)
        handler.post { if (!released) onAccessUnit(unit) }
    }

    fun release() {
        handler.post {
            released = true
            stopCodec()
            surface = null
            placeholder?.close()
            placeholder = null
            thread.quitSafely()
        }
    }

    private fun onAccessUnit(unit: ByteArray) {
        val nal = AvcNal.scan(unit)
        if (nal.hasSps && nal.hasPps) parameterSets = AvcNal.parameterSets(unit)
        if (codec == null && !startCodec()) return
        if (nal.onlyParameterSets) {
            enqueue(unit, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
            return
        }
        if (waitingForKeyframe) {
            if (!nal.hasIdr) {
                askForKeyframe()
                return
            }
            waitingForKeyframe = false
        }
        enqueue(unit, if (nal.hasIdr) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
    }

    private fun enqueue(unit: ByteArray, flags: Int) {
        if (pending.size >= MAX_PENDING) {
            // Behind by more than a quarter second: drop the backlog and restart from a keyframe.
            AppLog.w("ClusterMap: decoder fell behind by ${pending.size} frames; waiting for a keyframe")
            pending.clear()
            waitingForKeyframe = true
            askForKeyframe()
            return
        }
        pending.addLast(unit to flags)
        feed()
    }

    private fun startCodec(): Boolean {
        val config = parameterSets ?: run {
            askForKeyframe()
            return false
        }
        return try {
            val created = MediaCodec.createDecoderByType(MIME)
            created.setCallback(callback, handler)
            created.configure(MediaFormat.createVideoFormat(MIME, width, height), outputSurface(), null, 0)
            created.start()
            codec = created
            pending.clear()
            freeInputs.clear()
            pending.addLast(config to MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
            waitingForKeyframe = true
            AppLog.i("ClusterMap: decoder ${created.name} started for ${width}x$height")
            true
        } catch (error: Exception) {
            AppLog.e("ClusterMap: cannot start the cluster decoder", error)
            stopCodec()
            false
        }
    }

    private fun stopCodec() {
        codec?.let { running ->
            runCatching { running.stop() }
            runCatching { running.release() }
        }
        codec = null
        pending.clear()
        freeInputs.clear()
        pictureShown = false
        waitingForKeyframe = true
    }

    private fun feed() {
        val running = codec ?: return
        try {
            while (freeInputs.isNotEmpty() && pending.isNotEmpty()) {
                val index = freeInputs.removeFirst()
                val (unit, flags) = pending.removeFirst()
                val buffer = running.getInputBuffer(index) ?: continue
                if (buffer.capacity() < unit.size) {
                    AppLog.w("ClusterMap: frame of ${unit.size} bytes exceeds the codec buffer")
                    running.queueInputBuffer(index, 0, 0, presentationUs, 0)
                    waitingForKeyframe = true
                    askForKeyframe()
                    continue
                }
                buffer.clear()
                buffer.put(unit)
                running.queueInputBuffer(index, 0, unit.size, presentationUs, flags)
                if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) presentationUs += FRAME_US
            }
        } catch (error: IllegalStateException) {
            AppLog.w("ClusterMap: decoder rejected input, restarting: ${error.message}")
            stopCodec()
            askForKeyframe()
        }
    }

    private fun askForKeyframe() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastKeyframeRequestMs < KEYFRAME_REQUEST_INTERVAL_MS) return
        lastKeyframeRequestMs = now
        requestKeyframe()
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(c: MediaCodec, index: Int) {
            if (c !== codec) return
            freeInputs.addLast(index)
            feed()
        }

        override fun onOutputBufferAvailable(c: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (c !== codec) return
            val render = info.size > 0
            runCatching { c.releaseOutputBuffer(index, render) }
            if (render && !pictureShown) {
                pictureShown = true
                AppLog.i("ClusterMap: first cluster picture rendered")
                onPicture()
            }
        }

        override fun onError(c: MediaCodec, error: MediaCodec.CodecException) {
            if (c !== codec) return
            AppLog.e("ClusterMap: cluster decoder error, restarting", error)
            stopCodec()
            askForKeyframe()
        }

        override fun onOutputFormatChanged(c: MediaCodec, format: MediaFormat) {
            if (c === codec) AppLog.i("ClusterMap: cluster output format $format")
        }
    }

    private companion object {
        const val MIME = "video/avc"
        const val MAX_PENDING = 8
        const val FRAME_US = 33_333L
        const val KEYFRAME_REQUEST_INTERVAL_MS = 2_000L
    }
}

/** Just enough of H.264 Annex B to find parameter sets and IDR frames in an access unit. */
internal object AvcNal {
    data class Info(val hasSps: Boolean, val hasPps: Boolean, val hasIdr: Boolean, val hasPicture: Boolean) {
        val onlyParameterSets: Boolean get() = (hasSps || hasPps) && !hasPicture
    }

    private const val IDR = 5
    private const val SPS = 7
    private const val PPS = 8

    fun scan(unit: ByteArray): Info {
        var sps = false; var pps = false; var idr = false; var picture = false
        forEachNal(unit) { type, _, _ ->
            when (type) {
                SPS -> sps = true
                PPS -> pps = true
                IDR -> { idr = true; picture = true }
                in 1..4 -> picture = true
            }
        }
        return Info(sps, pps, idr, picture)
    }

    /** The SPS and PPS units of [unit], each with a four-byte start code, for codec configuration. */
    fun parameterSets(unit: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        forEachNal(unit) { type, start, end ->
            if (type == SPS || type == PPS) {
                out.write(byteArrayOf(0, 0, 0, 1))
                out.write(unit, start, end - start)
            }
        }
        return out.toByteArray()
    }

    /** Calls [block] with each NAL unit's type and its payload range (after the start code). */
    private inline fun forEachNal(unit: ByteArray, block: (type: Int, start: Int, end: Int) -> Unit) {
        var start = nextStart(unit, 0)
        while (start >= 0) {
            val next = nextStart(unit, start)
            val end = if (next < 0) unit.size else next - startCodeLength(unit, next)
            if (start < unit.size) block(unit[start].toInt() and 0x1F, start, end)
            start = next
        }
    }

    /** Index just after the next start code at or after [from], or -1. */
    private fun nextStart(unit: ByteArray, from: Int): Int {
        var i = from
        while (i + 2 < unit.size) {
            if (unit[i].toInt() == 0 && unit[i + 1].toInt() == 0 && unit[i + 2].toInt() == 1) return i + 3
            i++
        }
        return -1
    }

    private fun startCodeLength(unit: ByteArray, payloadStart: Int): Int =
        if (payloadStart >= 4 && unit[payloadStart - 4].toInt() == 0) 4 else 3
}
