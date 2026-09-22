package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.messages.Messages
import com.andrerinas.openheadunit.aap.protocol.proto.MediaPlayback
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.protoUint32ToLong
import java.nio.ByteBuffer

class AapMediaPlayback(
    private val onAaMediaMetadata: ((MediaPlayback.MediaMetaData) -> Unit)?,
    private val onAaPlaybackStatus: ((MediaPlayback.MediaPlaybackStatus) -> Unit)?
) {
    private val messageBuffer = ByteBuffer.allocate(1024 * 1024) // 1MB to handle large album art
    private var started = false

    fun process(message: AapMessage) {

        val flags = message.flags.toInt()

        when (message.type) {
            MSG_MEDIA_PLAYBACK_METADATA -> processMetadataPacket(message, flags)
            MSG_MEDIA_PLAYBACK_STATUS -> processStatusPacket(message)
            MSG_MEDIA_PLAYBACK_INPUT -> Unit
            else -> {
                if (started && (flags == FLAG_MIDDLE_FRAGMENT || flags == FLAG_LAST_FRAGMENT)) {
                    processMetadataPacket(message, flags)
                    return
                }
                AppLog.e("Unsupported %s", message.toString())
            }
        }
    }

    private fun processStatusPacket(message: AapMessage) {
        try {
            val status = message.parse(MediaPlayback.MediaPlaybackStatus.newBuilder()).build()
            onAaPlaybackStatus?.invoke(status)
            AppLog.d(
                "AapMediaPlayback: status mediaSource='${status.mediaSource}', " +
                    "playbackSeconds(u32)=${status.playbackSeconds.protoUint32ToLong()}, state=${status.state}"
            )
        } catch (e: Exception) {
            AppLog.w("AapMediaPlayback: Failed to parse playback status: ${e.message}")
        }
    }

    private fun processMetadataPacket(message: AapMessage, flags: Int) {
        when (flags) {
            FLAG_FIRST_FRAGMENT -> {
                messageBuffer.clear()
                messageBuffer.put(message.data, message.dataOffset, message.size - message.dataOffset)
                started = true
            }

            FLAG_MIDDLE_FRAGMENT -> {
                if (!started) return
                messageBuffer.put(message.data, 0, message.size)
            }

            FLAG_LAST_FRAGMENT -> {
                if (!started) return
                messageBuffer.put(message.data, 0, message.size)
                messageBuffer.flip()
                try {
                    val request = MediaPlayback.MediaMetaData.newBuilder()
                        .mergeFrom(messageBuffer.array(), 0, messageBuffer.limit())
                        .build()
                    notifyRequest(request)
                } catch (e: Exception) {
                    AppLog.w("AapMediaPlayback: Failed to parse metadata (fragmented): ${e.message}")
                } finally {
                    started = false
                    messageBuffer.clear()
                }
            }

            else -> {
                try {
                    val request = message.parse(MediaPlayback.MediaMetaData.newBuilder()).build()
                    notifyRequest(request)
                } catch (e: Exception) {
                    AppLog.w("AapMediaPlayback: Failed to parse metadata (single packet): ${e.message}")
                }
            }
        }
    }

    private fun notifyRequest(request: MediaPlayback.MediaMetaData) {
        onAaMediaMetadata?.invoke(request)
    }

    private companion object {
        // Based on AA protocol enum MediaPlaybackStatusMessageId from protos.proto.
        const val MSG_MEDIA_PLAYBACK_STATUS = 32769
        const val MSG_MEDIA_PLAYBACK_INPUT = 32770
        const val MSG_MEDIA_PLAYBACK_METADATA = 32771

        // AAP fragmentation flags used by incoming payload packets.
        const val FLAG_FIRST_FRAGMENT = 0x09
        const val FLAG_MIDDLE_FRAGMENT = 0x08
        const val FLAG_LAST_FRAGMENT = 0x0A
    }
}
