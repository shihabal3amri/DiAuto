package com.andrerinas.openheadunit.hud

/** Single-worker lifecycle; all vehicle writes and persistence stay off the phone thread. */
internal class BydStandaloneSession(
    private val send: (String) -> Unit,
    private val rememberPendingClear: (Boolean) -> Unit,
    needsRecovery: Boolean = false,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var showing = needsRecovery
    private var recovering = needsRecovery
    private var lastPacket: String? = null
    private var lastSendNs = 0L
    private var lastStartNs = 0L

    fun update(icon: Int, exit: Int, distanceMeters: Int, road: String? = null) {
        if (recovering) clear()
        val packet = BydStandalonePackets.guidance(icon, exit, distanceMeters, road)
        if (packet == null) { clear(); return }
        try {
            val now = nanoTime()
            if (!showing) {
                // Commit before publishing a start, so the next app launch can recover a crash.
                rememberPendingClear(true)
                showing = true
                sendStart(now)
            } else if (now - lastStartNs >= START_REFRESH_NS) {
                // The navigation state is a latch other writers share. The stock map sets it to
                // "not navigating" when the cluster projection display appears after start-up, when
                // the driver changes the cluster's navi mode, or when the map app starts, and the
                // HUD then hides our arrows until the state is 2 again. The stock map re-sends 2
                // with its guidance at most every 3.5 s, so do the same.
                sendStart(now)
            }
            if (lastPacket != packet || now - lastSendNs >= 1_000_000_000L) {
                send(packet)
                lastPacket = packet
                lastSendNs = now
            }
        } catch (error: Exception) {
            // A failed update may follow a successful start; clear before trying again.
            recovering = showing
            runCatching { clear() }
            throw error
        }
    }

    private fun sendStart(now: Long) {
        send(BydStandalonePackets.start())
        lastStartNs = now
    }

    fun clear() {
        if (!showing) return
        recovering = true
        send(BydStandalonePackets.clear())
        // sendBroadcast returning is dispatch success, not hardware acknowledgement.
        rememberPendingClear(false)
        showing = false
        recovering = false
        lastPacket = null
        lastSendNs = 0L
        lastStartNs = 0L
    }

    companion object {
        /** The stock map's SendCacheTool default (3500 ms) for its navigation-state writes. */
        const val START_REFRESH_NS = 3_500_000_000L
    }
}
