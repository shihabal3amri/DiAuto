package com.andrerinas.openheadunit.hud

import java.util.concurrent.atomic.AtomicReference

/** A one-slot mailbox: slow outputs skip obsolete complete snapshots without blocking the input. */
internal class LatestNavigationOutput(private val nanoTime: () -> Long = System::nanoTime) {
    private val latest = AtomicReference<BydGuidance?>()
    fun update(value: BydGuidance?) { latest.set(value) }
    fun current(): BydGuidance? = latest.get()?.takeIf { nanoTime() - it.updatedNs < STALE_AFTER_NS }

    companion object {
        /**
         * How long guidance survives without any update from the phone.
         *
         * Only a backstop: route end, disconnect and disabling the output all clear explicitly. It
         * used to be 30 s, but phones on the legacy next-turn messages resend an unchanged instruction
         * only about once a minute - measured 50-62 s on a long straight - so the arrow kept vanishing
         * mid-route exactly when nothing was changing.
         */
        const val STALE_AFTER_NS = 180_000_000_000L
    }
}
