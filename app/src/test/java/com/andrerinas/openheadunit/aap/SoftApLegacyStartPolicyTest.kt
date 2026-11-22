package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftApLegacyStartPolicyTest {

    @Test fun anAccessPointStartedByTheOldCallOnAndroid7MustShowAnAddress() {
        // #50, API 25: startTethering() refused, setWifiApEnabled() accepted, "enabled" with no address.
        assertTrue(SoftApLegacyStartPolicy.needsAddressProof(sdkInt = 25, startedByLegacyCallOnly = true))
        assertTrue(SoftApLegacyStartPolicy.needsAddressProof(sdkInt = 24, startedByLegacyCallOnly = true))
    }

    @Test fun aTetheringRequestThatWasAcceptedIsTrustedAsBefore() {
        assertFalse(SoftApLegacyStartPolicy.needsAddressProof(sdkInt = 25, startedByLegacyCallOnly = false))
        assertFalse(SoftApLegacyStartPolicy.needsAddressProof(sdkInt = 24, startedByLegacyCallOnly = false))
    }

    @Test fun otherAndroidVersionsKeepTheFrameworkStateAsTheAnswer() {
        // Android 6 and older tether the access point themselves; from Android 8 the old call is gone.
        for (sdk in listOf(16, 19, 21, 23, 26, 28, 29, 33, 36)) {
            assertFalse("API $sdk", SoftApLegacyStartPolicy.needsAddressProof(sdk, startedByLegacyCallOnly = true))
        }
    }

    @Test fun wifiCanOnlyBeSwitchedBackOnWhereItCouldBeSwitchedOff() {
        assertTrue(SoftApLegacyStartPolicy.canRestoreWifi(25))
        assertTrue(SoftApLegacyStartPolicy.canRestoreWifi(28))
        assertFalse(SoftApLegacyStartPolicy.canRestoreWifi(29))
        assertFalse(SoftApLegacyStartPolicy.canRestoreWifi(33))
    }
}
