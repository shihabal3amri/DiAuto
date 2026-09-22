package com.andrerinas.openheadunit.connection

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2pInterfaceBssidTest {
    private fun bytes(ip: String) = InetAddress.getByName(ip).address

    @Test fun decodesLocalAndUniversalMacs() {
        assertEquals("DA:12:34:56:78:9A", P2pInterfaceBssid.decode(bytes("fe80::d812:34ff:fe56:789a")))
        assertEquals("00:12:34:56:78:9A", P2pInterfaceBssid.decode(bytes("fe80::212:34ff:fe56:789a")))
    }

    @Test fun rejectsOpaqueAndNonLinkLocalAddresses() {
        for (ip in listOf("fe80::1234:5678:abcd:ef01", "2001:db8::d812:34ff:fe56:789a",
            "fe80:1::d812:34ff:fe56:789a", "::1", "192.168.49.1")) {
            assertNull(ip, P2pInterfaceBssid.decode(bytes(ip)))
        }
        assertNull(P2pInterfaceBssid.decode(ByteArray(0)))
    }

    @Test fun rejectsMaskedZeroAndMulticastMacs() {
        for (ip in listOf("fe80::ff:fe00:0", "fe80::200:ff:fe00:0", "fe80::d912:34ff:fe56:789a",
            "fe80::fdff:ffff:feff:ffff")) {
            assertNull(ip, P2pInterfaceBssid.decode(bytes(ip)))
        }
    }

    @Test fun refusesConflictingCandidatesAndIgnoresUnrelatedAddresses() {
        val first = bytes("fe80::d812:34ff:fe56:789a")
        val second = bytes("fe80::d812:34ff:fe56:789b")
        assertNull(P2pInterfaceBssid.fromAddresses(listOf(first, second)))
        assertNull(P2pInterfaceBssid.fromAddresses(emptyList()))
        assertEquals("DA:12:34:56:78:9A", P2pInterfaceBssid.fromAddresses(
            listOf(first, first, bytes("fe80::1234:5678:abcd:ef01"))))
    }

    @Test fun requiresAnExplicitInterface() {
        assertNull(P2pInterfaceBssid.read(null))
        assertNull(P2pInterfaceBssid.read(""))
        assertNull(P2pInterfaceBssid.read("does-not-exist"))
    }
}
