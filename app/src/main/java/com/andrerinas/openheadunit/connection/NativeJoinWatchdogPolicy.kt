package com.andrerinas.openheadunit.connection

/**
 * Whether a Native AA WiFi Direct group that no phone has joined is a group that failed a phone.
 *
 * The join watchdog recreates the group when nobody joins within a minute, and after two rounds
 * creates it on 2.4 GHz instead. That is the right repair when a phone was handed the group's
 * credentials over Bluetooth and then could not join. It is the wrong one when no phone was handed
 * anything: the group is fine, the phone has simply not arrived or has not opened the Bluetooth
 * channel yet. A Qin L report (#59) shows three recreations, the second forcing 2.4 GHz, with no
 * Bluetooth handshake at all; a phone connecting after that gets the slower band for nothing.
 */
object NativeJoinWatchdogPolicy {

    /**
     * @param lastCredentialsDeliveredAtMs when a phone was last handed credentials, 0 if never.
     * @param watchStartedAtMs when the current wait for a join began (the group came up, or its
     *   last client left). Credentials handed out before that belong to an earlier wait.
     * Both on the same monotonic clock.
     */
    fun groupWasOfferedToAPhone(lastCredentialsDeliveredAtMs: Long, watchStartedAtMs: Long): Boolean =
        lastCredentialsDeliveredAtMs > 0L && lastCredentialsDeliveredAtMs >= watchStartedAtMs
}
