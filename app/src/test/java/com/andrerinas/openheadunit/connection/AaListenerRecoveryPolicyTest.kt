package com.andrerinas.openheadunit.connection

import com.andrerinas.openheadunit.connection.AaListenerRecoveryPolicy.Exit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaListenerRecoveryPolicyTest {

    @Test fun aSocketErrorOnARunningManagerIsALoss() {
        // #77: "AA Server socket error: read failed" with the manager still running.
        assertEquals(Exit.FAILED, AaListenerRecoveryPolicy.exitKind(closedForSession = false, running = true))
    }

    @Test fun theHandoffClosingTheListenerIsNotALoss() {
        assertEquals(Exit.HANDOFF, AaListenerRecoveryPolicy.exitKind(closedForSession = true, running = true))
        // A session that ended while its listener was closed is still the handoff's doing.
        assertEquals(Exit.HANDOFF, AaListenerRecoveryPolicy.exitKind(closedForSession = true, running = false))
    }

    @Test fun aStoppedManagerClosingItsSocketsIsNotALoss() {
        assertEquals(Exit.STOPPED, AaListenerRecoveryPolicy.exitKind(closedForSession = false, running = false))
    }

    @Test fun aLostListenerCannotAcceptEvenThoughTheManagerRuns() {
        // This is what lets Connect and the Bluetooth auto-start re-arm it.
        assertFalse(AaListenerRecoveryPolicy.canAccept(running = true, closedForSession = false, listenerLost = true))
        assertTrue(AaListenerRecoveryPolicy.canAccept(running = true, closedForSession = false, listenerLost = false))
    }

    @Test fun aStoppedOrHandedOffManagerCannotAcceptEither() {
        assertFalse(AaListenerRecoveryPolicy.canAccept(running = false, closedForSession = false, listenerLost = false))
        assertFalse(AaListenerRecoveryPolicy.canAccept(running = true, closedForSession = true, listenerLost = false))
    }

    @Test fun reopeningIsBounded() {
        for (attempt in 0 until AaListenerRecoveryPolicy.MAX_REOPEN_ATTEMPTS) {
            assertTrue(AaListenerRecoveryPolicy.mayReopen(adapterEnabled = true, attemptsSoFar = attempt))
        }
        assertFalse(
            AaListenerRecoveryPolicy.mayReopen(adapterEnabled = true, attemptsSoFar = AaListenerRecoveryPolicy.MAX_REOPEN_ATTEMPTS)
        )
    }

    @Test fun aRadioThatIsOffIsNotRetriedInto() {
        assertFalse(AaListenerRecoveryPolicy.mayReopen(adapterEnabled = false, attemptsSoFar = 0))
    }
}
