package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.NativeAaWakeDamagePolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAaWakeDamagePolicyTest {

    private val reprobe = NativeAaWakeDamagePolicy.REPROBE_AFTER_ARMINGS

    @Test
    fun `a wake that brought the phone in paid for itself whatever the link reads`() {
        assertEquals(Verdict.SAFE, NativeAaWakeDamagePolicy.verdictFrom(linkReturned = false, wakeStartedAaSession = true))
        assertEquals(Verdict.SAFE, NativeAaWakeDamagePolicy.verdictFrom(linkReturned = null, wakeStartedAaSession = true))
    }

    @Test
    fun `a link that came back makes the wake a blip`() {
        assertEquals(Verdict.SAFE, NativeAaWakeDamagePolicy.verdictFrom(linkReturned = true, wakeStartedAaSession = false))
    }

    @Test
    fun `a link still down with nothing gained is the cost this unit pays`() {
        assertEquals(Verdict.DESTRUCTIVE, NativeAaWakeDamagePolicy.verdictFrom(linkReturned = false, wakeStartedAaSession = false))
    }

    @Test
    fun `an adapter that will not answer condemns nothing`() {
        val measured = NativeAaWakeDamagePolicy.verdictFrom(linkReturned = null, wakeStartedAaSession = false)
        assertEquals(Verdict.UNKNOWN, measured)
        assertFalse(NativeAaWakeDamagePolicy.recordable(measured))
        assertTrue(NativeAaWakeDamagePolicy.recordable(Verdict.SAFE))
        assertTrue(NativeAaWakeDamagePolicy.recordable(Verdict.DESTRUCTIVE))
    }

    @Test
    fun `an unmeasured or safe unit may be woken`() {
        assertTrue(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.UNKNOWN, 0))
        assertTrue(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.SAFE, 0))
        assertTrue(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.SAFE, 100))
    }

    @Test
    fun `a destructive verdict withholds the wake until enough armings have gone by`() {
        assertFalse(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.DESTRUCTIVE, 0))
        assertFalse(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.DESTRUCTIVE, reprobe - 1))
        assertTrue(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.DESTRUCTIVE, reprobe))
    }

    @Test
    fun `the first wake and a re-measurement are probes, a safe unit's wakes are not`() {
        assertTrue(NativeAaWakeDamagePolicy.isProbe(Verdict.UNKNOWN, 0))
        assertFalse(NativeAaWakeDamagePolicy.isProbe(Verdict.SAFE, 0))
        assertFalse(NativeAaWakeDamagePolicy.isProbe(Verdict.DESTRUCTIVE, reprobe - 1))
        assertTrue(NativeAaWakeDamagePolicy.isProbe(Verdict.DESTRUCTIVE, reprobe))
    }

    @Test
    fun `a stored value from a newer build reads as unmeasured`() {
        assertEquals(Verdict.UNKNOWN, Verdict.of(0))
        assertEquals(Verdict.SAFE, Verdict.of(1))
        assertEquals(Verdict.DESTRUCTIVE, Verdict.of(2))
        assertEquals(Verdict.UNKNOWN, Verdict.of(7))
        assertEquals(Verdict.UNKNOWN, Verdict.of(-1))
    }
}
