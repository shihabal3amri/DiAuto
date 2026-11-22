package com.andrerinas.openheadunit.aap

/**
 * Whether a wake stood down by [BluetoothWakePolicy.WakeReason.TARGET_CONNECTED] has waited long
 * enough to go out anyway.
 *
 * Android Auto starts wireless setup from a Bluetooth *event*, not from a connected state: its
 * receiver listens for `ACL_CONNECTED` and the hands-free and media `CONNECTION_STATE_CHANGED`.
 * A phone that was on the car's Bluetooth before this app's Android Auto listener existed, or that
 * stays on it past the end of a session, raises no further event, so it never dials and the
 * stand-down is permanent. Three reports show exactly that and nothing else: the listener up, the
 * poke withheld, and no connection ever accepted (#69, #59, #50).
 *
 * The poke is what produces the event, at the measured cost of the phone's hands-free slot, so it
 * is budgeted rather than freed: by itself only for a phone on the Auto Start list, otherwise only
 * for the phone Connect was pressed for, only [MAX_ESCALATED_WAKES] times per arming, never during
 * a call or a session, and only while [NativeAaWakeDamagePolicy] has not measured this unit losing
 * the link for good.
 *
 * Ported from Open Headunit (`HandsFreeWakeEscalationPolicy`). Three things differ here: a call
 * gate; the Auto Start list as the record of which phones an automatic wake may reach; and Connect
 * as a request in its own right, with a shorter wait, for any phone the user picks, including one
 * that has never run Android Auto on this unit. Upstream never wakes that phone. Here it is the
 * reported case, the button names the phone, and the worst outcome is the Bluetooth off-and-on
 * the user would otherwise be asked for.
 */
object HandsFreeWakeEscalationPolicy {

    /** Escalated wakes allowed between one start of the handshake manager and the next. */
    const val MAX_ESCALATED_WAKES = 2

    /** Minimum gap between them, so a phone that is simply slow is not woken twice over. */
    const val ESCALATION_COOLDOWN_MS = 60_000L

    /**
     * How long the stand-down runs before the first escalation. Longer than the ~45 s a phone takes
     * to start by itself where anything is going to, so an ordinary reconnect is never disturbed.
     */
    const val ESCALATE_AFTER_MS = 90_000L

    /**
     * The wait when the user pressed Connect for this phone. Pressing the button is asking for the
     * wake, so a minute and a half of nothing would read as a dead button; the phone still gets
     * long enough to dial by itself if its Bluetooth had only just connected (measured at about 2 s).
     */
    const val ESCALATE_AFTER_CONNECT_MS = 15_000L

    /** Unanswered pokes allowed after an escalated wake before the loop leaves the phone alone. */
    const val POKES_AFTER_ESCALATION = 4

    /** `AudioManager.MODE_NORMAL`; any other mode is a call ringing, up or being set up. */
    private const val AUDIO_MODE_NORMAL = 0

    /**
     * Whether to wake the phone despite the hands-free link.
     *
     * @param unitAllowsWake what [NativeAaWakeDamagePolicy] made of this unit's own link last time.
     * @param reason why [BluetoothWakePolicy.wakeDecision] refused. Only TARGET_CONNECTED escalates:
     *   TARGET_UNREADABLE could not tell whose link it is, and a guess is not a reason to drop
     *   somebody's call; TARGET_ABSENT has no link of the target's to raise an event on.
     * @param onAutoStartList the phone is on the Auto Start list. A phone joins it by connecting
     *   Android Auto here or by the user choosing it, and the user takes it off there, so nothing
     *   automatic reaches for a phone paired only for calls or one the user took off.
     * @param callActive a call rides on the link this would take.
     * @param sessionInProgress a session up, handshaking or settling; nothing touches the radio then.
     * @param standDownSinceMs when this phone's stand-down began, or zero if it has not.
     * @param lastEscalationMs when the last escalated wake went out, or zero if none has.
     * @param userAsked the user pressed Connect for this phone. That stands in for the list, and
     *   shortens the wait; every other gate holds.
     */
    fun shouldEscalate(
        unitAllowsWake: Boolean,
        reason: BluetoothWakePolicy.WakeReason,
        onAutoStartList: Boolean,
        callActive: Boolean,
        sessionInProgress: Boolean,
        standDownSinceMs: Long,
        lastEscalationMs: Long,
        escalationsUsed: Int,
        now: Long,
        userAsked: Boolean = false,
    ): Boolean {
        if (!unitAllowsWake) return false
        if (reason != BluetoothWakePolicy.WakeReason.TARGET_CONNECTED) return false
        if (!onAutoStartList && !userAsked) return false
        if (callActive) return false
        if (sessionInProgress) return false
        if (escalationsUsed >= MAX_ESCALATED_WAKES) return false
        val waitMs = if (userAsked) ESCALATE_AFTER_CONNECT_MS else ESCALATE_AFTER_MS
        if (standDownSinceMs <= 0L || now - standDownSinceMs < waitMs) return false
        if (lastEscalationMs > 0L && now - lastEscalationMs < ESCALATION_COOLDOWN_MS) return false
        return true
    }

    /** Whether [targetMac] is one of [autoStartMacs]. Addresses compare without case or padding. */
    fun isListed(targetMac: String?, autoStartMacs: Collection<String>): Boolean {
        val target = targetMac?.trim().orEmpty()
        if (target.isEmpty()) return false
        return autoStartMacs.any { it.trim().equals(target, ignoreCase = true) }
    }

    /**
     * Whether a call may be on the link. An unreadable mode counts as a call: not knowing is not a
     * licence to drop one.
     */
    fun callMayBeActive(audioMode: Int?): Boolean = audioMode == null || audioMode != AUDIO_MODE_NORMAL

    /**
     * Whether an escalated wake has cost the hands-free link for nothing. Taking the slot stops the
     * guard refusing, so the ordinary loop would re-take it every pass and the link would never come
     * back: measured upstream at over five minutes down on a phone that never opened the channel.
     */
    fun escalationSpent(escalationsUsed: Int, pokesSinceLastAccept: Int): Boolean =
        escalationsUsed > 0 && pokesSinceLastAccept >= POKES_AFTER_ESCALATION
}
