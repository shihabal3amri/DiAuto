package com.andrerinas.openheadunit.hud

import org.junit.Assert.*
import org.junit.Test

class LatestNavigationOutputTest {
    private fun frame(distance: Int, now: Long) = BydGuidance(2, 1, 1, 0, distance, "Road", updatedNs = now)
    @Test fun `only newest snapshot is retained during a stalled output`() {
        val output = LatestNavigationOutput { 0 }
        repeat(10000) { output.update(frame(it, 0)) }
        assertEquals(9999, output.current()!!.distanceMeters)
    }
    @Test fun `silent snapshot expires and fresh update restores`() {
        var now = 0L
        val output = LatestNavigationOutput { now }
        output.update(frame(50, now))
        now = LatestNavigationOutput.STALE_AFTER_NS
        assertNull(output.current())
        output.update(frame(20, now))
        assertEquals(20, output.current()!!.distanceMeters)
    }
    @Test fun `guidance survives the minute-long gaps of legacy next-turn updates`() {
        var now = 0L
        val output = LatestNavigationOutput { now }
        output.update(frame(4000, now))
        now = 62_000_000_000L
        assertEquals(4000, output.current()!!.distanceMeters)
    }
    @Test fun `clear cannot replay old guidance`() {
        val output = LatestNavigationOutput { 0 }
        output.update(frame(50, 0))
        output.update(null)
        assertNull(output.current())
    }
}
