package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.Message



class VideoFocusEvent(mode: Media.VideoFocusMode, unsolicited: Boolean, channel: Int = Channel.ID_VID)
    : AapMessage(channel, Media.MsgType.MEDIA_MESSAGE_VIDEO_FOCUS_NOTIFICATION_VALUE, makeProto(mode, unsolicited)) {

    constructor(gain: Boolean, unsolicited: Boolean, channel: Int = Channel.ID_VID) : this(
        if (gain) Media.VideoFocusMode.VIDEO_FOCUS_PROJECTED else Media.VideoFocusMode.VIDEO_FOCUS_NATIVE,
        unsolicited, channel
    )

    companion object {
        private fun makeProto(mode: Media.VideoFocusMode, unsolicited: Boolean): Message {
            return Media.VideoFocusNotification.newBuilder().apply {
                this.mode = mode
                this.unsolicited = unsolicited
            }.build()
        }
    }
}
