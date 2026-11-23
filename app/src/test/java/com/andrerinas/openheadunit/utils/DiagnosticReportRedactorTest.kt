package com.andrerinas.openheadunit.utils

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportRedactorTest {
    private fun redact(line: String, ssid: String = "", password: String = "") =
        DiagnosticReportRedactor.redact(line, ssid, password)

    @Test fun removesCredentialsAndRetainsUsefulFailureDetails() {
        assertNull(redact("SSID=car, password=secret", "car", "secret"))
        assertNull(redact("payload=010203"))
        assertEquals("NativeAA: No usable BSSID; join failure status=-3", redact(
            "NativeAA: No usable BSSID; join failure status=-3"))
        assertEquals("Network [saved value] at [address] [ip]", redact(
            "Network My Car at 4e:b1:c7:94:48:3f 192.168.46.1", "My Car", "secret"))
    }

    @Test fun aLineThatNamesTheNetworkIsKeptWithoutTheName() {
        // The lines #50, #59 and #69 needed and did not have.
        assertEquals("NativeAA: Credentials updated. SSID=[name], IP=[ip], BSSID=[address]", redact(
            "NativeAA: Credentials updated. SSID=DIRECT-xy-My Car, IP=192.168.49.1, BSSID=4e:b1:c7:94:48:3f"))
        assertEquals("SoftApCredentials: SUCCESS - Providing credentials from wlan0: SSID=[name], IP=[ip], BSSID=<none>", redact(
            "SoftApCredentials: SUCCESS - Providing credentials from wlan0: SSID=BYD-1234, IP=192.168.43.1, BSSID=<none>"))
        assertEquals("AapService: Received WiFi credentials from manager (SSID=[name], IP=[ip]). Updating and Triggering Poke.", redact(
            "AapService: Received WiFi credentials from manager (SSID=BYD-1234, IP=192.168.43.1). Updating and Triggering Poke."))
        assertEquals("WifiDirectManager: onGroupInfoAvailable: SSID: [name], BSSID: [address], GO: true, IFACE: p2p0, Freq: 5180 MHz (5 GHz)", redact(
            "WifiDirectManager: onGroupInfoAvailable: SSID: DIRECT-ab-Car, BSSID: 4e:b1:c7:94:48:3f, GO: true, IFACE: p2p0, Freq: 5180 MHz (5 GHz)"))
        assertEquals("  > Target SSID: [name]", redact("  > Target SSID: My Car"))
        assertEquals("WifiDirectManager: Group ready, SSID=[name]", redact("WifiDirectManager: Group ready, SSID=DIRECT-ab-Car"))
    }

    @Test fun whetherThereWasANameAtAllStaysReadable() {
        // "null" here is the finding: the poke went out with no WiFi details to hand over.
        val waiting = "NativeAA: Pre-poke credential wait completed. SSID=null, IP=null (waited 4000ms)"
        assertEquals(waiting, redact(waiting))
        val none = "NativeAA: Phone connected. Current credentials state: SSID=<null>, IP=<null>"
        assertEquals(none, redact(none))
        val unchanged = "SoftApConfiguration applied (SSID=unchanged). Success=true"
        assertEquals(unchanged, redact(unchanged))
        assertEquals("SoftApConfiguration applied (SSID=[name]). Success=true", redact(
            "SoftApConfiguration applied (SSID=BYD-1234). Success=true"))
    }

    @Test fun aNameWithACommaOrABracketInItIsStillHiddenWhole() {
        assertEquals("Credentials updated. SSID=[name], IP=[ip]", redact("Credentials updated. SSID=Sam, Home (5G), IP=192.168.43.1"))
        assertEquals("applied (SSID=[name]). Success=true", redact("applied (SSID=Car (5G) spot). Success=true"))
        assertEquals("Group ready, SSID=[name]", redact("Group ready, SSID=Car (5G), spot"))
    }

    @Test fun aSavedNameIsStillMarkedAsTheSavedOne() {
        // Which lets a report show that the saved hotspot name is the one in use.
        assertEquals("Credentials updated. SSID=[saved value], IP=[ip]", redact(
            "Credentials updated. SSID=My Car, IP=192.168.43.1", "My Car", "secret"))
    }

    @Test fun anAddressNameIsNotMistakenForANetworkName() {
        assertEquals("Fallback 1 - Using lastKnownBssid: [address]", redact("Fallback 1 - Using lastKnownBssid: 4e:b1:c7:94:48:3f"))
        assertEquals("BSSID=[address], IP=[ip]", redact("BSSID=4e:b1:c7:94:48:3f, IP=192.168.43.1"))
    }

    @Test fun twoNamesOnOneLineAreBothHidden() {
        assertEquals("was SSID=[name], Now=1, then SSID: [name]", redact("was SSID=Old Car, Now=1, then SSID: New Car"))
    }
}
