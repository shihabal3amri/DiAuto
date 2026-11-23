package com.andrerinas.openheadunit.aap

/**
 * When an access point this app switched on has to prove it is usable before it is reported as up.
 *
 * On Android 7.0 and 7.1 the framework only gives an access point its address and DHCP server when
 * it was asked for through `ConnectivityManager.startTethering()`. That call is refused to an
 * ordinary app there ("You either need MANAGE_USERS or CREATE_USERS permission"), and the fallback,
 * `WifiManager.setWifiApEnabled()`, still brings the radio up: the framework then reports the access
 * point as enabled while its interface has no address, so no phone can join it.
 *
 * Reported on a DiLink 4 head unit (API 25, #50): the car's own hotspot had worked with an address
 * of its own, then this app's auto-enable switched WiFi off, started an access point that way, logged
 * "Hotspot is up" and waited 30 s for an interface that never got an address.
 */
object SoftApLegacyStartPolicy {

    /** How long a healthy access point may take to get its address after the framework says it is up. */
    const val ADDRESS_TIMEOUT_MS = 5_000L

    /**
     * Whether "the framework says enabled" is not enough, and an addressed interface has to be seen.
     *
     * Only where the failure is known: an access point that was started by the legacy call alone on
     * Android 7.x. Below that the WiFi state machine tethers the access point itself; from Android 8
     * the legacy call no longer exists. Elsewhere the framework state stays the answer, because
     * recognising the interface is a heuristic and a miss would tear down a working access point.
     */
    fun needsAddressProof(sdkInt: Int, startedByLegacyCallOnly: Boolean): Boolean =
        startedByLegacyCallOnly && sdkInt in 24..25

    /**
     * Whether this app can switch WiFi back on after switching it off to free the radio.
     * `setWifiEnabled()` is ignored from Android 10 for an app targeting it, in both directions, so
     * there the station was never taken down by this app in the first place.
     */
    fun canRestoreWifi(sdkInt: Int): Boolean = sdkInt < 29
}
