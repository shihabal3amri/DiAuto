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
        phoneHasRunAaHere: Boolean = true,
        callActive: Boolean = false,
        sessionInProgress: Boolean = false,
        standDownSinceMs: Long = 1_000L,
        lastEscalationMs: Long = 0L,
        escalationsUsed: Int = 0,
        now: Long = 1_000L + wait,
        userAsked: Boolean = false,
    ) = HandsFreeWakeEscalationPolicy.shouldEscalate(
        unitAllowsWake, reason, phoneHasRunAaHere, callActive, sessionInProgress,
        standDownSinceMs, lastEscalationMs, escalationsUsed, now, userAsked,
    )

    @Test
    fun `a phone that ran Android Auto here is woken once its stand-down has run its time`() {
        assertTrue(escalate())
    }

    @Test
    fun `an ordinary reconnect is not disturbed before that`() {
        assertFalse(escalate(now = 1_000L + wait - 1))
    }

    /** #69, #59, #50 are first connections. Nothing automatic is spent on them. */
    @Test
    fun `a phone paired only for calls is never woken over its link`() {
        assertFalse(escalate(phoneHasRunAaHere = false))
        assertFalse(escalate(phoneHasRunAaHere = false, userAsked = true, now = 1_000L + wait))
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

    // --- which phones count ---

    @Test
    fun `a phone on any of the lists has run Android Auto here`() {
        val known = listOf("AA:BB:CC:DD:EE:01", "aa:bb:cc:dd:ee:02")
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere("AA:BB:CC:DD:EE:01", known))
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere("AA:BB:CC:DD:EE:02", known))
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(" aa:bb:cc:dd:ee:01 ", known))
    }

    @Test
    fun `another phone having run it says nothing about this one`() {
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere("AA:BB:CC:DD:EE:03", listOf("AA:BB:CC:DD:EE:01")))
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere("AA:BB:CC:DD:EE:03", emptyList()))
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(null, listOf("AA:BB:CC:DD:EE:01")))
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere("", listOf("")))
    }

    private val onList = "AA:BB:CC:DD:EE:01"
    private val takenOffList = "AA:BB:CC:DD:EE:02"
    private val seenThisProcess = "AA:BB:CC:DD:EE:03"

    private fun candidates(userAsked: Boolean) = HandsFreeWakeEscalationPolicy.wakeCandidates(
        userAsked = userAsked,
        autoStartMacs = setOf(onList),
        ranAaStored = setOf(onList, takenOffList),
        acceptedThisProcess = listOf(seenThisProcess),
    )

    /** Taking a phone off the Auto Start list is how the user says "do not start this one for me". */
    @Test
    fun `nothing automatic reaches for a phone the user took off the Auto Start list`() {
        val automatic = candidates(userAsked = false)
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(onList, automatic))
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(takenOffList, automatic))
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(seenThisProcess, automatic))
    }

    @Test
    fun `pressing Connect counts any record of the phone having run Android Auto here`() {
        val asked = candidates(userAsked = true)
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(onList, asked))
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(takenOffList, asked))
        assertTrue(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere(seenThisProcess, asked))
        assertFalse(HandsFreeWakeEscalationPolicy.phoneHasRunAaHere("AA:BB:CC:DD:EE:04", asked))
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
