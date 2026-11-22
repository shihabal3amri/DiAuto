package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeJoinWatchdogPolicyTest {

    @Test fun aGroupNoPhoneWasToldAboutHasNotFailedAnyone() {
        // #59: the group came up, no Bluetooth handshake ever ran, and it was recreated three times.
        assertFalse(NativeJoinWatchdogPolicy.groupWasOfferedToAPhone(lastCredentialsDeliveredAtMs = 0L, watchStartedAtMs = 5_000L))
    }

    @Test fun aPhoneGivenTheCredentialsThatNeverJoinedIsWhatRecoveryIsFor() {
        assertTrue(NativeJoinWatchdogPolicy.groupWasOfferedToAPhone(lastCredentialsDeliveredAtMs = 20_000L, watchStartedAtMs = 5_000L))
        assertTrue(NativeJoinWatchdogPolicy.groupWasOfferedToAPhone(lastCredentialsDeliveredAtMs = 5_000L, watchStartedAtMs = 5_000L))
    }

    @Test fun credentialsFromBeforeThisWaitBelongToAnEarlierGroupOrSession() {
        // A phone joined on them and left, or the group was recreated since.
        assertFalse(NativeJoinWatchdogPolicy.groupWasOfferedToAPhone(lastCredentialsDeliveredAtMs = 4_000L, watchStartedAtMs = 5_000L))
    }

    @Test fun aClockThatHasNotStartedAWaitStillNeedsADelivery() {
        assertFalse(NativeJoinWatchdogPolicy.groupWasOfferedToAPhone(lastCredentialsDeliveredAtMs = 0L, watchStartedAtMs = 0L))
    }
}
