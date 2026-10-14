package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.cluster.ClusterMapStream
import com.andrerinas.openheadunit.utils.AppLog
import java.io.ByteArrayOutputStream

/**
 * Reassembles the instrument-cluster video channel and hands whole access units to
 * [ClusterMapStream]. Same framing as the main channel, see [AapVideo]; a broken frame on this
 * channel only drops cluster frames until the next keyframe, never the main picture.
 */
internal class AapClusterVideo {
    private val assembler = VideoFragmentAssembler()
    private val frame = ByteArrayOutputStream(64 * 1024)

    fun process(message: AapMessage): Boolean {
        val buf = message.data
        val len = message.size
        val decision = assembler.onMessage(
            flags = message.flags.toInt(),
            payloadStartsAt10 = startsAt(buf, VideoFragmentAssembler.OFFSET_TIMESTAMP_INDICATION, len),
            payloadStartsAt2 = startsAt(buf, VideoFragmentAssembler.OFFSET_MEDIA_INDICATION, len),
        )
        decision.anomaly?.let {
            AppLog.w("ClusterMap: cluster frame dropped ($it)")
            frame.reset()
        }
        return when (val action = decision.action) {
            is VideoFragmentAssembler.Action.DecodeWhole -> {
                frame.reset()
                ClusterMapStream.onFrame(buf, action.payloadOffset, len - action.payloadOffset)
                true
            }
            is VideoFragmentAssembler.Action.BeginAssembly -> {
                frame.reset()
                frame.write(buf, action.payloadOffset, len - action.payloadOffset)
                true
            }
            VideoFragmentAssembler.Action.Append -> {
                if (frame.size() + len > MAX_FRAME_BYTES) overflow() else frame.write(buf, 0, len)
                true
            }
            VideoFragmentAssembler.Action.AppendAndDecode -> {
                if (frame.size() + len > MAX_FRAME_BYTES) {
                    overflow()
                } else {
                    frame.write(buf, 0, len)
                    val whole = frame.toByteArray()
                    ClusterMapStream.onFrame(whole, 0, whole.size)
                }
                frame.reset()
                true
            }
            is VideoFragmentAssembler.Action.Discard -> action.consumed
        }
    }

    fun release() {
        assembler.reset()
        frame.reset()
    }

    private fun overflow() {
        AppLog.w("ClusterMap: cluster frame exceeds ${MAX_FRAME_BYTES / 1024}KB, dropped")
        assembler.onOverflow()
        frame.reset()
    }

    private fun startsAt(buf: ByteArray, offset: Int, len: Int): Boolean {
        if (offset + 4 > len) return false
        if (buf[offset].toInt() != 0 || buf[offset + 1].toInt() != 0) return false
        return buf[offset + 2].toInt() == 1 || (buf[offset + 2].toInt() == 0 && buf[offset + 3].toInt() == 1)
    }

    private companion object {
        /** A 1080p H.264 keyframe of a map is far below this. */
        const val MAX_FRAME_BYTES = 4 * 1024 * 1024
    }
}
