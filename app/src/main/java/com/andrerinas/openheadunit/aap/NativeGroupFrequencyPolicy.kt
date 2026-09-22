package com.andrerinas.openheadunit.aap

/** Prefer the associated station channel only on the initial native group attempt. */
object NativeGroupFrequencyPolicy {
    fun preferredFrequency(stationFrequency: Int, associated: Boolean, force24: Boolean,
                           retryCount: Int, recreateCount: Int): Int {
        if (!associated || force24 || retryCount != 0 || recreateCount != 0) return 0
        // Non-DFS 20 MHz primary channels; let the driver enforce its regulatory domain.
        return if (stationFrequency in setOf(5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805, 5825))
            stationFrequency else 0
    }
}
