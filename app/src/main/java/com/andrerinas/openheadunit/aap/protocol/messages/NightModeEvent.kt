package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.google.protobuf.Message



class NightModeEvent(enabled: Boolean)
    : SensorEvent(Sensors.SensorType.NIGHT_VALUE, makeProto(enabled)) {

    companion object {
        private fun makeProto(enabled: Boolean): Message {
            return Sensors.SensorBatch.newBuilder().also {
                it.addNightMode(
                        Sensors.SensorBatch.NightData.newBuilder().apply {
                            isNightMode = enabled
                        }
                )
            }.build()
        }
    }
}
