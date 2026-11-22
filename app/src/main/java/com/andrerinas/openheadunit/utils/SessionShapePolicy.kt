package com.andrerinas.openheadunit.utils

import kotlin.math.abs

/**
 * Decides when a running session has to end and come back because its window changed shape (#61).
 *
 * The phone is told the video size once, at service discovery, and streams that size until the
 * session ends. When the projection window changes shape under it - BYD split screen on DiLink 4
 * and 5.1 - the stream cannot follow, and the only way to a picture drawn for the new window is a
 * new session. Open Headunit fixed the same fault that way (fd24999e, 70c70802, a7e851b3). This is
 * that rule for DiAuto, with a window compared by the resolution it would negotiate rather than by
 * landscape or portrait alone.
 *
 * A reconnect costs the driver several seconds without a picture, so every gate here is a case
 * where that price buys nothing:
 *
 *  - The window only gained or lost a system bar. That is already told to the phone as new margins
 *    (`sendUpdateUiConfigRequest`), and on a panel that sits on a resolution threshold it would
 *    otherwise reconnect each time a bar came or went.
 *  - The reading is not the projection as the driver sees it: behind another app, with the screen
 *    off, behind the keyguard or shrunk into picture-in-picture.
 *  - The session would not come back by itself (see `AapProjectionActivity.redrawIfReshaped`).
 *  - This shape was already redrawn for and the session came back in the old one. BYD's freeform
 *    windows report passing sizes while other panels cover the app; without this a session that
 *    cannot be negotiated in such a size would be ended again every time the window settled.
 *
 * The caller waits [SETTLE_MS] after the last change before asking, for the same reason.
 */
object SessionShapePolicy {

    /** A width and height in pixels. */
    data class Size(val width: Int, val height: Int) {
        override fun toString(): String = "${width}x$height"
    }

    /**
     * A window as the phone is told about it: [resolution] is the video size negotiated for it,
     * which carries both landscape or portrait and the resolution class, and [canvas] the window
     * that size was chosen for.
     *
     * The canvas is the whole window, not what insets leave of it. System bars, a display cutout,
     * the manual margins and the keyboard all take their share as insets, the keyboard a large one,
     * and none of them may count as a change of shape.
     */
    data class Shape(val resolution: Size, val canvas: Size)

    /** How long a window must have stayed as it is before its session is redrawn. */
    const val SETTLE_MS = 1_000L

    /**
     * One part in this many of a side is the most a system bar is taken to change it by.
     *
     * BYD's bar is 96 px of 1080 (9%), and a status and a navigation bar together stay under a fifth
     * on the panels this runs on. Split screen takes a third of the long side at the least, and the
     * half-screen window of #61 took 1270 px of 2560.
     */
    const val BAR_SHARE = 4

    /** Whether [window] differs from [session] by no more than system bars coming or going. */
    fun isBarSized(session: Size, window: Size): Boolean =
        abs(window.width - session.width) * BAR_SHARE <= session.width &&
            abs(window.height - session.height) * BAR_SHARE <= session.height

    enum class Verdict {
        /** No session is up, or what it announced or what the window wants is not known. */
        NO_SESSION,
        /** Not the projection as the driver sees it, so the reading says nothing either way. */
        NOT_ON_SCREEN,
        /** The window would be announced with the resolution the session has. */
        MATCHES,
        /** The window wants another resolution only because a system bar came or went. */
        BAR_SIZED,
        /** Ending this session would not bring it back by itself. */
        WOULD_NOT_RETURN,
        /** This shape was redrawn for already, and the session came back in another one. */
        ALREADY_REDRAWN,
        /** End the session, so that it reconnects announced for the window as it is now. */
        REDRAW,
    }

    /**
     * What to do about a window that has settled.
     *
     * @param session what service discovery announced, or null between sessions
     * @param window what the window as it is now would be announced as
     * @param redrawnFor the resolution of the window shape last redrawn for, see [rememberAfter]
     */
    fun decide(
        session: Shape?,
        window: Shape?,
        sessionUp: Boolean,
        inFront: Boolean,
        pictureInPicture: Boolean,
        screenOn: Boolean,
        keyguardLocked: Boolean,
        reconnectsByItself: Boolean,
        redrawnFor: Size?,
    ): Verdict = when {
        !sessionUp || session == null || window == null -> Verdict.NO_SESSION
        // Before the comparison: a reading that is not the projection must not count as a match
        // either, or it would clear what [rememberAfter] holds.
        !inFront || pictureInPicture || !screenOn || keyguardLocked -> Verdict.NOT_ON_SCREEN
        window.resolution == session.resolution -> Verdict.MATCHES
        isBarSized(session.canvas, window.canvas) -> Verdict.BAR_SIZED
        !reconnectsByItself -> Verdict.WOULD_NOT_RETURN
        window.resolution == redrawnFor -> Verdict.ALREADY_REDRAWN
        else -> Verdict.REDRAW
    }

    /**
     * The window shape to hold as redrawn for after [verdict].
     *
     * A redraw is remembered until a session and its window agree again. While they do not, the
     * redraw did not take - the session came back in the old shape - and doing it again would only
     * repeat that, so one shape is redrawn for once however often the window settles in it.
     */
    fun rememberAfter(verdict: Verdict, window: Shape?, redrawnFor: Size?): Size? = when (verdict) {
        Verdict.REDRAW -> window?.resolution
        Verdict.MATCHES -> null
        else -> redrawnFor
    }
}
