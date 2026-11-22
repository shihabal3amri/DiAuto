package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.BluetoothWakePolicy.WakeReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeWakeEscalationPolicyTest {

    private val wait = HandsFreeWakeEscalationPolicy.ESCALATE_AFTER_MS
    private val connectWait = HandsFreeWakeEscalationPolicy.ESCALATE_AFTER_CONNECT_MS
    private val cooldown = HandsFreeWakeEscalationPolicy.ESCALATION_COOLDOWN_MS

    /** A stand-down that began at t=1000 and has run exactly long enough, everything else allowing it. */
    private fun escalate(
        unitAllowsWake: Boolean = true,
        reason: WakeReason = WakeReason.TARGET_CONNECTED,
        onAutoStartList: Boolean = true,
        callActive: Boolean = false,
        sessionInProgress: Boolean = false,
        standDownSinceMs: Long = 1_000L,
        lastEscalationMs: Long = 0L,
        escalationsUsed: Int = 0,
        now: Long = 1_000L + wait,
        userAsked: Boolean = false,
    ) = HandsFreeWakeEscalationPolicy.shouldEscalate(
        unitAllowsWake, reason, onAutoStartList, callActive, sessionInProgress,
        standDownSinceMs, lastEscalationMs, escalationsUsed, now, userAsked,
    )

    @Test
    fun `a phone on the Auto Start list is woken once its stand-down has run its time`() {
        assertTrue(escalate())
    }

    @Test
    fun `an ordinary reconnect is not disturbed before that`() {
        assertFalse(escalate(now = 1_000L + wait - 1))
    }

    /** A phone joins the list by connecting Android Auto here, and the user takes it off there. */
    @Test
    fun `nothing automatic reaches for a phone that is not on the Auto Start list`() {
        assertFalse(escalate(onAutoStartList = false))
        assertFalse(escalate(onAutoStartList = false, now = 1_000L + 100 * wait))
    }

    /**
     * #69, #59, #50: first connections, the phone already on the car's Bluetooth, Connect pressed
     * and nothing happening. The button names the phone, so it is woken without being on any list.
     */
    @Test
    fun `Connect wakes the phone it was pressed for even if it has never run Android Auto here`() {
        assertFalse(escalate(onAutoStartList = false, userAsked = true, now = 1_000L + connectWait - 1))
        assertTrue(escalate(onAutoStartList = false, userAsked = true, now = 1_000L + connectWait))
    }

    @Test
    fun `every other gate holds for a first-time phone picked with Connect`() {
        fun firstTime(
            unitAllowsWake: Boolean = true,
            reason: WakeReason = WakeReason.TARGET_CONNECTED,
            callActive: Boolean = false,
            sessionInProgress: Boolean = false,
            escalationsUsed: Int = 0,
        ) = escalate(
            unitAllowsWake = unitAllowsWake, reason = reason, onAutoStartList = false,
            callActive = callActive, sessionInProgress = sessionInProgress,
            escalationsUsed = escalationsUsed, userAsked = true,
        )
        assertTrue(firstTime())
        assertFalse(firstTime(callActive = true))
        assertFalse(firstTime(sessionInProgress = true))
        assertFalse(firstTime(unitAllowsWake = false))
        assertFalse(firstTime(reason = WakeReason.TARGET_UNREADABLE))
        assertFalse(firstTime(escalationsUsed = HandsFreeWakeEscalationPolicy.MAX_ESCALATED_WAKES))
    }

    @Test
    fun `a call on the link is never dropped for a wake`() {
        assertFalse(escalate(callActive = true))
        assertFalse(escalate(callActive = true, userAsked = true))
    }

    @Test
    fun `nothing touches the radio during a session, a handshake or a settling handoff`() {
        assertFalse(escalate(sessionInProgress = true))
    }

    @Test
    fun `only the target's own link is grounds for a wake`() {
        // Unreadable: a guess is not a reason to drop somebody's call. Absent: no link of its to
        // raise an event on. No link: nothing stood down in the first place.
        assertFalse(escalate(reason = WakeReason.TARGET_UNREADABLE))
        assertFalse(escalate(reason = WakeReason.TARGET_ABSENT))
        assertFalse(escalate(reason = WakeReason.NO_LINK))
    }

    @Test
    fun `a unit measured to lose its link for good is not woken`() {
        assertFalse(escalate(unitAllowsWake = false))
    }

    @Test
    fun `a stand-down that never began cannot have run long enough`() {
        assertFalse(escalate(standDownSinceMs = 0L, now = 10 * wait))
    }

    @Test
    fun `the budget is two wakes per arming`() {
        assertTrue(escalate(escalationsUsed = 1, lastEscalationMs = 1_000L, now = 1_000L + wait + cooldown))
        assertFalse(
            escalate(
                escalationsUsed = HandsFreeWakeEscalationPolicy.MAX_ESCALATED_WAKES,
                lastEscalationMs = 1_000L, now = 1_000L + wait + 10 * cooldown,
            )
        )
    }

    @Test
    fun `the second wake waits out the cooldown, not a fresh stand-down`() {
        val first = 1_000L + wait
        assertFalse(escalate(escalationsUsed = 1, lastEscalationMs = first, now = first + cooldown - 1))
        assertTrue(escalate(escalationsUsed = 1, lastEscalationMs = first, now = first + cooldown))
    }

    @Test
    fun `pressing Connect shortens the wait and nothing else`() {
        assertFalse(escalate(userAsked = true, now = 1_000L + connectWait - 1))
        assertTrue(escalate(userAsked = true, now = 1_000L + connectWait))
        // The same moment without the button is still an ordinary reconnect in progress.
        assertFalse(escalate(userAsked = false, now = 1_000L + connectWait))
        // Budget and cooldown hold for the button too.
        assertFalse(
            escalate(
                userAsked = true, escalationsUsed = HandsFreeWakeEscalationPolicy.MAX_ESCALATED_WAKES,
                now = 1_000L + wait,
            )
        )
        assertFalse(
            escalate(userAsked = true, escalationsUsed = 1, lastEscalationMs = 50_000L, now = 50_000L + cooldown - 1)
        )
        assertTrue(connectWait < wait)
    }

    // --- which phones are on the list ---

    @Test
    fun `addresses on the list match without case or padding`() {
        val list = listOf("AA:BB:CC:DD:EE:01", "aa:bb:cc:dd:ee:02")
        assertTrue(HandsFreeWakeEscalationPolicy.isListed("AA:BB:CC:DD:EE:01", list))
        assertTrue(HandsFreeWakeEscalationPolicy.isListed("AA:BB:CC:DD:EE:02", list))
        assertTrue(HandsFreeWakeEscalationPolicy.isListed(" aa:bb:cc:dd:ee:01 ", list))
    }

    @Test
    fun `another phone being listed says nothing about this one`() {
        assertFalse(HandsFreeWakeEscalationPolicy.isListed("AA:BB:CC:DD:EE:03", listOf("AA:BB:CC:DD:EE:01")))
        assertFalse(HandsFreeWakeEscalationPolicy.isListed("AA:BB:CC:DD:EE:03", emptyList()))
        assertFalse(HandsFreeWakeEscalationPolicy.isListed(null, listOf("AA:BB:CC:DD:EE:01")))
        assertFalse(HandsFreeWakeEscalationPolicy.isListed("", listOf("")))
    }

    // --- calls ---

    @Test
    fun `only the normal audio mode is no call`() {
        assertFalse(HandsFreeWakeEscalationPolicy.callMayBeActive(android.media.AudioManager.MODE_NORMAL))
        assertTrue(HandsFreeWakeEscalationPolicy.callMayBeActive(android.media.AudioManager.MODE_RINGTONE))
        assertTrue(HandsFreeWakeEscalationPolicy.callMayBeActive(android.media.AudioManager.MODE_IN_CALL))
        assertTrue(HandsFreeWakeEscalationPolicy.callMayBeActive(android.media.AudioManager.MODE_IN_COMMUNICATION))
    }

    @Test
    fun `a mode that cannot be read counts as a call`() {
        assertTrue(HandsFreeWakeEscalationPolicy.callMayBeActive(null))
    }

    // --- giving the link back ---

    @Test
    fun `after a wake that bought nothing the phone is left alone`() {
        val limit = HandsFreeWakeEscalationPolicy.POKES_AFTER_ESCALATION
        assertFalse(HandsFreeWakeEscalationPolicy.escalationSpent(escalationsUsed = 1, pokesSinceLastAccept = limit - 1))
        assertTrue(HandsFreeWakeEscalationPolicy.escalationSpent(escalationsUsed = 1, pokesSinceLastAccept = limit))
    }

    @Test
    fun `without an escalated wake the ordinary loop is not limited by this`() {
        assertFalse(HandsFreeWakeEscalationPolicy.escalationSpent(escalationsUsed = 0, pokesSinceLastAccept = 100))
    }
}
