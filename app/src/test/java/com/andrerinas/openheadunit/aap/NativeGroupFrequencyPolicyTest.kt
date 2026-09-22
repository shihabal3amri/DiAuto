package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeGroupFrequencyPolicyTest {
    @Test fun prefersAssociatedNonDfsChannel() {
        for (frequency in listOf(5180, 5200, 5240, 5745, 5825))
            assertEquals(frequency, NativeGroupFrequencyPolicy.preferredFrequency(frequency, true, false, 0, 0))
    }
    @Test fun leavesUnsupportedAndDfsChannelsToDriver() {
        for (frequency in listOf(0, 2412, 5260, 5500, 5181, 5955))
            assertEquals(0, NativeGroupFrequencyPolicy.preferredFrequency(frequency, true, false, 0, 0))
    }
    @Test fun preservesForcedBandAndFallsBackOnRecovery() {
        assertEquals(0, NativeGroupFrequencyPolicy.preferredFrequency(5200, false, false, 0, 0))
        assertEquals(0, NativeGroupFrequencyPolicy.preferredFrequency(5200, true, true, 0, 0))
        assertEquals(0, NativeGroupFrequencyPolicy.preferredFrequency(5200, true, false, 1, 0))
        assertEquals(0, NativeGroupFrequencyPolicy.preferredFrequency(5200, true, false, 0, 1))
    }
}
