package com.andrerinas.openheadunit.utils

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Convert a IPv4 address from an integer to an InetAddress.
 * @param hostAddress an int corresponding to the IPv4 address in network byte order
 */
fun Int.toInetAddress(): InetAddress {
    val hostAddress = this
    val addressBytes = byteArrayOf((0xff and hostAddress).toByte(),
            (0xff and (hostAddress shr 8)).toByte(),
            (0xff and (hostAddress shr 16)).toByte(),
            (0xff and (hostAddress shr 24)).toByte())
    return try {
        InetAddress.getByAddress(addressBytes)
    } catch (e: UnknownHostException) {
        AppLog.e(e)
        throw e
    }
}

fun InetAddress.changeLastBit(byte: Byte): InetAddress {
    return InetAddress.getByAddress(byteArrayOf(address[0], address[1], address[2], byte))
}