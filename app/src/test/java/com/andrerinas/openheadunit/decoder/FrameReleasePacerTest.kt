package com.andrerinas.openheadunit.decoder

import org.junit.Assert.*
import org.junit.Test

class FrameReleasePacerTest {
    @Test fun shortBurstGetsDistinctSlots() {
        val p = FrameReleasePacer(58f)
        assertEquals(1_000_000_000L, p.schedule(1_000_000_000L))
        assertEquals(1_017_241_379L, p.schedule(1_000_000_000L))
    }
    @Test fun backlogIsBoundedAndRecoversAfterIdle() {
        val p = FrameReleasePacer(60f)
        repeat(3) { assertNotNull(p.schedule(1_000_000_000L)) }
        assertNull(p.schedule(1_000_000_000L))
        assertEquals(2_000_000_000L, p.schedule(2_000_000_000L))
    }
    @Test fun slowerSourceIsNotDelayed() {
        val p = FrameReleasePacer(58f)
        repeat(300) { val now = 1_000_000_000L + it * 33_333_333L; assertEquals(now, p.schedule(now)) }
    }
    @Test fun invalidRefreshRateUsesSafeFallback() {
        val p = FrameReleasePacer(Float.NaN)
        p.schedule(0)
        assertEquals(16_666_666L, p.schedule(0))
    }
}
