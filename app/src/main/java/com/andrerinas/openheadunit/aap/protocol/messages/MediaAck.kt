package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.Message

class MediaAck(channel: Int, sessionId: Int)
    : AapMessage(channel, Media.MsgType.MEDIA_MESSAGE_ACK_VALUE, makeProto(sessionId)) {

    companion object {
        private fun makeProto(sessionId: Int): Message {
            return Media.Ack.newBuilder().apply {
                this.sessionId = sessionId
                this.ack = 1
            }.build()
        }
    }
}
