package com.andrerinas.openheadunit.connection

/**
 * What to do when a Bluetooth accept loop of the Native AA handshake stops serving.
 *
 * The loops used to end on a socket error having recorded nothing: the manager still counted as
 * running, so `start()` returned at once, `isActive()` stayed true and neither the Connect button
 * nor the Bluetooth auto-start re-armed anything. Reported on a Leopard 8 (#77): unpairing the
 * phone made the car's Bluetooth stack drop both listeners, and nothing reopened them until the
 * app was force-stopped.
 *
 * The rules follow Open Headunit's fix for the same loop (6885df91), where the trigger was the
 * head unit's own radio bouncing.
 */
object AaListenerRecoveryPolicy {

    /** How many times lost listeners are reopened before waiting for the radio to say it is back. */
    const val MAX_REOPEN_ATTEMPTS = 4

    /** How long to wait before each attempt; the radio takes a moment to come back after a bounce. */
    const val REOPEN_DELAY_MS = 2_000L

    /** Why an accept loop stopped serving. */
    enum class Exit {
        /** The handoff closed it on purpose; the session owns what happens next. */
        HANDOFF,

        /** The manager was stopped; nothing is meant to be listening. */
        STOPPED,

        /** It died under us, and only this leaves the unit unable to answer the phone. */
        FAILED,
    }

    /**
     * [closedForSession] only ever applies to the Android Auto listeners; the hands-free ones stay
     * open through a session, so their loops pass false.
     */
    fun exitKind(closedForSession: Boolean, running: Boolean): Exit = when {
        closedForSession -> Exit.HANDOFF
        !running -> Exit.STOPPED
        else -> Exit.FAILED
    }

    /**
     * Whether lost listeners may be reopened now.
     *
     * A radio that is off cannot host one, and retrying into it only spends the budget a real
     * transient needs; the radio coming back is a separate event and resets the count.
     */
    fun mayReopen(adapterEnabled: Boolean, attemptsSoFar: Int): Boolean =
        adapterEnabled && attemptsSoFar < MAX_REOPEN_ATTEMPTS

    /**
     * Whether the unit can take an Android Auto connection right now. A manager that is running
     * with its listener gone must not report itself as able to: the re-arm paths in AapService
     * only act when this is false.
     */
    fun canAccept(running: Boolean, closedForSession: Boolean, listenerLost: Boolean): Boolean =
        running && !closedForSession && !listenerLost
}
