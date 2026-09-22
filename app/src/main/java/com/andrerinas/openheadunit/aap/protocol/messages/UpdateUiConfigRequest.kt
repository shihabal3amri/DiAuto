package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.Message

/**
 * Message sent on the Video channel (Channel 3) to update the headunit's UI config
 * (margins, insets, theme) without a full session reconnect.
 */
class UpdateUiConfigRequest(
    left: Int, top: Int, right: Int, bottom: Int
) : AapMessage(
    Channel.ID_VID,
    Media.MsgType.MEDIA_MESSAGE_UPDATE_UI_CONFIG_REQUEST_VALUE,
    makeProto(left, top, right, bottom)
) {

    companion object {
        private fun makeProto(left: Int, top: Int, right: Int, bottom: Int): Message {
            // Protocol-correct: UiConfig with ONLY margins set.
            // No content_insets, no stable_content_insets, no ui_theme.
            val insets = Media.Insets.newBuilder()
                .setLeft(left)
                .setTop(top)
                .setRight(right)
                .setBottom(bottom)
                .build()

            val uiConfig = Media.UiConfig.newBuilder()
                .setMargins(insets)
                .build()

            return Media.UpdateUiConfigRequest.newBuilder()
                .setUiConfig(uiConfig)
                .build()
        }
    }
}

