package com.andrerinas.openheadunit.connection

import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Some head units mask hardwareAddress but assign their P2P interface a MAC-derived
 * IPv6 link-local address. Reverse the modified EUI-64 encoding (RFC 4291 Appendix A).
 * Opaque/privacy IPv6 identifiers cannot be decoded and must not become a BSSID.
 */
internal object P2pInterfaceBssid {
    fun read(interfaceName: String?): String? {
        // Never substitute the station, hotspot, or a peer's interface for the active GO.
        if (interfaceName.isNullOrBlank()) return null
        return try {
            val network = NetworkInterface.getByName(interfaceName) ?: return null
            if (!network.isUp || network.isLoopback) return null
            val addresses = network.inetAddresses.toList()
                .filterIsInstance<Inet6Address>()
                .map { it.address }
            fromAddresses(addresses)
        } catch (_: Exception) {
            null
        }
    }

    fun fromAddresses(addresses: List<ByteArray>): String? =
        addresses.mapNotNull(::decode).distinct().singleOrNull()

    fun decode(address: ByteArray): String? {
        if (address.size != 16) return null
        val bytes = address.map { it.toInt() and 0xff }
        // Accept only fe80::/64, not a global address or arbitrary link-local subnet bits.
        if (bytes[0] != 0xfe || bytes[1] != 0x80 || (2..7).any { bytes[it] != 0 }) return null
        if (bytes[11] != 0xff || bytes[12] != 0xfe) return null
        val mac = listOf(bytes[8] xor 0x02, bytes[9], bytes[10], bytes[13], bytes[14], bytes[15])
        if ((mac[0] and 1) != 0 || mac.all { it == 0 }) return null
        if (mac == listOf(2, 0, 0, 0, 0, 0)) return null
        return mac.joinToString(":") { String.format(Locale.ROOT, "%02X", it) }
    }
}
