package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothWakePolicyTest {

    // --- the targets themselves, pinned ---

    @Test
    fun `the poke targets are the assigned audio gateway service classes`() {
        assertEquals("0000111f-0000-1000-8000-00805f9b34fb", BluetoothWakePolicy.HFP_AG_UUID.toString())
        assertEquals("00001112-0000-1000-8000-00805f9b34fb", BluetoothWakePolicy.HSP_AG_UUID.toString())
    }

    /**
     * The poke is load-bearing on some head units — they never connect without it — so the list may
     * never be emptied to disable it. Standing a poke down is [BluetoothWakePolicy.shouldPoke]'s
     * decision, taken per attempt, and not this list's.
     */
    @Test
    fun `hands-free is tried first, headset second, and neither is ever dropped`() {
        assertEquals(
            listOf(BluetoothWakePolicy.HFP_AG_UUID, BluetoothWakePolicy.HSP_AG_UUID),
            BluetoothWakePolicy.POKE_TARGETS
        )
    }

    @Test
    fun `targets are named for a log reader`() {
        assertEquals("HFP-AG", BluetoothWakePolicy.profileName(BluetoothWakePolicy.HFP_AG_UUID))
        assertEquals("HSP-AG", BluetoothWakePolicy.profileName(BluetoothWakePolicy.HSP_AG_UUID))
    }

    @Test
    fun `an unknown uuid still prints as itself`() {
        val other = java.util.UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66")
        assertEquals(other.toString(), BluetoothWakePolicy.profileName(other))
    }

    // --- the guard: never poke a link we would destroy ---

    /**
     * The measured failure: a poke that connects takes the phone's one hands-free slot, this unit's
     * own client is dropped 4 ms later, and it does not come back without a Bluetooth adapter cycle.
     */
    @Test
    fun `a live hands-free link is never poked`() {
        assertFalse(BluetoothWakePolicy.shouldPoke(BluetoothWakePolicy.HandsFreeLink.CONNECTED))
    }

    @Test
    fun `no hands-free link means there is nothing to destroy, so poke`() {
        assertTrue(BluetoothWakePolicy.shouldPoke(BluetoothWakePolicy.HandsFreeLink.ABSENT))
    }

    /**
     * An adapter that will not report its profiles must not silently disable a mechanism some head
     * units cannot connect without. Those units keep today's behaviour.
     */
    @Test
    fun `an unreadable adapter still pokes`() {
        assertTrue(BluetoothWakePolicy.shouldPoke(BluetoothWakePolicy.HandsFreeLink.UNREADABLE))
    }

    @Test
    fun `the three-valued profile read maps onto the three states`() {
        assertEquals(BluetoothWakePolicy.HandsFreeLink.CONNECTED, BluetoothWakePolicy.HandsFreeLink.of(true))
        assertEquals(BluetoothWakePolicy.HandsFreeLink.ABSENT, BluetoothWakePolicy.HandsFreeLink.of(false))
        assertEquals(BluetoothWakePolicy.HandsFreeLink.UNREADABLE, BluetoothWakePolicy.HandsFreeLink.of(null))
    }

    /** Exactly one state suppresses the poke; if a fourth is ever added it has to choose out loud. */
    @Test
    fun `only a connected link suppresses the poke`() {
        val suppressed = BluetoothWakePolicy.HandsFreeLink.values().filterNot { BluetoothWakePolicy.shouldPoke(it) }
        assertEquals(listOf(BluetoothWakePolicy.HandsFreeLink.CONNECTED), suppressed)
    }

    // --- pairing: strict about poking, lenient about forgetting ---

    private val BONDED = BluetoothWakePolicy.BondReading.BONDED
    private val NOT_BONDED = BluetoothWakePolicy.BondReading.NOT_BONDED
    private val MALFORMED = BluetoothWakePolicy.BondReading.MALFORMED
    private val UNREADABLE = BluetoothWakePolicy.BondReading.UNREADABLE

    @Test
    fun `only a confirmed pairing may be poked`() {
        assertTrue(BluetoothWakePolicy.mayPoke(BONDED))
        for (reading in BluetoothWakePolicy.BondReading.values().filterNot { it == BONDED }) {
            assertFalse("$reading should not be poked", BluetoothWakePolicy.mayPoke(reading))
        }
    }

    /**
     * The one that matters: `getBondState()` answers BOND_NONE when the Bluetooth service is
     * unavailable, so an adapter that is off looks exactly like a phone the user unpaired. Forgetting
     * is permanent and written through to device-protected storage, so it needs a real answer.
     */
    @Test
    fun `an unreadable state is never forgotten`() {
        assertFalse(BluetoothWakePolicy.shouldForget(UNREADABLE))
    }

    @Test
    fun `a paired device is never forgotten`() {
        assertFalse(BluetoothWakePolicy.shouldForget(BONDED))
    }

    @Test
    fun `a positive not-paired answer is forgotten`() {
        assertTrue(BluetoothWakePolicy.shouldForget(NOT_BONDED))
    }

    /** An address that is not a Bluetooth address can never become one. */
    @Test
    fun `a malformed address is forgotten`() {
        assertTrue(BluetoothWakePolicy.shouldForget(MALFORMED))
    }

    /**
     * The asymmetry is the design: refusing to poke costs a retry seconds later, forgetting costs
     * the user their configured device with nothing to restore it. So nothing may ever be forgotten
     * that was also considered pokeable, and the two rules must never both be lenient.
     */
    @Test
    fun `nothing pokeable is ever forgotten`() {
        for (reading in BluetoothWakePolicy.BondReading.values()) {
            assertFalse(
                "$reading was both poked and forgotten",
                BluetoothWakePolicy.mayPoke(reading) && BluetoothWakePolicy.shouldForget(reading)
            )
        }
    }

    // --- whose link it is: the per-device decision ---

    private fun decide(
        link: BluetoothWakePolicy.HandsFreeLink,
        target: BluetoothWakePolicy.TargetLink,
        userAsked: Boolean = false,
    ) = BluetoothWakePolicy.wakeDecision(link, target, userAsked)

    @Test
    fun `with no hands-free link the target does not matter`() {
        for (target in BluetoothWakePolicy.TargetLink.values()) {
            val decision = decide(BluetoothWakePolicy.HandsFreeLink.ABSENT, target)
            assertTrue(decision.poke)
            assertEquals(BluetoothWakePolicy.WakeReason.NO_LINK, decision.reason)
        }
    }

    /** Unchanged from [BluetoothWakePolicy.shouldPoke]: an adapter that will not say is poked. */
    @Test
    fun `an unreadable adapter is still poked whatever the target reads`() {
        for (target in BluetoothWakePolicy.TargetLink.values()) {
            assertTrue(decide(BluetoothWakePolicy.HandsFreeLink.UNREADABLE, target).poke)
        }
    }

    /** The guard as it was: the phone on the link is the phone a poke would take the slot from. */
    @Test
    fun `a connected target is stood down, also when the user pressed Connect`() {
        for (userAsked in listOf(false, true)) {
            val decision = decide(
                BluetoothWakePolicy.HandsFreeLink.CONNECTED, BluetoothWakePolicy.TargetLink.CONNECTED, userAsked
            )
            assertFalse(decision.poke)
            assertEquals(BluetoothWakePolicy.WakeReason.TARGET_CONNECTED, decision.reason)
        }
    }

    /**
     * #59: four paired devices, another one on the hands-free link, and Connect pressed for a phone
     * that was not connected at all. The adapter-wide read refused it.
     */
    @Test
    fun `a phone that is not connected is woken over another device's link when Connect asks for it`() {
        val decision = decide(
            BluetoothWakePolicy.HandsFreeLink.CONNECTED, BluetoothWakePolicy.TargetLink.ABSENT, userAsked = true
        )
        assertTrue(decision.poke)
        assertEquals(BluetoothWakePolicy.WakeReason.TARGET_ABSENT, decision.reason)
    }

    /** The loop runs every 15 s; paging an absent phone that often under somebody's call is not free. */
    @Test
    fun `the automatic loop does not page an absent phone while another device holds the link`() {
        val decision = decide(BluetoothWakePolicy.HandsFreeLink.CONNECTED, BluetoothWakePolicy.TargetLink.ABSENT)
        assertFalse(decision.poke)
        assertEquals(BluetoothWakePolicy.WakeReason.TARGET_ABSENT, decision.reason)
    }

    @Test
    fun `a target that cannot be read is never taken as absent`() {
        for (userAsked in listOf(false, true)) {
            val decision = decide(
                BluetoothWakePolicy.HandsFreeLink.CONNECTED, BluetoothWakePolicy.TargetLink.UNREADABLE, userAsked
            )
            assertFalse(decision.poke)
            assertEquals(BluetoothWakePolicy.WakeReason.TARGET_UNREADABLE, decision.reason)
        }
    }

    @Test
    fun `the target's own reading keeps its unknown case`() {
        assertEquals(BluetoothWakePolicy.TargetLink.CONNECTED, BluetoothWakePolicy.TargetLink.of(true))
        assertEquals(BluetoothWakePolicy.TargetLink.ABSENT, BluetoothWakePolicy.TargetLink.of(false))
        assertEquals(BluetoothWakePolicy.TargetLink.UNREADABLE, BluetoothWakePolicy.TargetLink.of(null))
    }
}
