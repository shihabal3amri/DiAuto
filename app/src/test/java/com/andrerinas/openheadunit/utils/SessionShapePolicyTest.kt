package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.SessionShapePolicy.Shape
import com.andrerinas.openheadunit.utils.SessionShapePolicy.Size
import com.andrerinas.openheadunit.utils.SessionShapePolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shapes are the ones of #61: a BYD Leopard 8 (DiLink 5.1, 2560x1600) with DiAuto full screen
 * and in the half-screen window of BYD's split screen.
 */
class SessionShapePolicyTest {

    private val fullScreen = Shape(resolution = Size(2560, 1440), canvas = Size(2560, 1600))
    private val halfScreen = Shape(resolution = Size(1080, 1920), canvas = Size(1270, 1600))

    /** A wireless session announced full screen whose window has settled as half the screen. */
    private fun decide(
        session: Shape? = fullScreen,
        window: Shape? = halfScreen,
        sessionUp: Boolean = true,
        inFront: Boolean = true,
        pictureInPicture: Boolean = false,
        screenOn: Boolean = true,
        keyguardLocked: Boolean = false,
        reconnectsByItself: Boolean = true,
        redrawnFor: Size? = null,
    ) = SessionShapePolicy.decide(
        session = session,
        window = window,
        sessionUp = sessionUp,
        inFront = inFront,
        pictureInPicture = pictureInPicture,
        screenOn = screenOn,
        keyguardLocked = keyguardLocked,
        reconnectsByItself = reconnectsByItself,
        redrawnFor = redrawnFor,
    )

    // --- a change of shape ---

    @Test
    fun `entering split screen redraws the session`() {
        assertEquals(Verdict.REDRAW, decide())
    }

    @Test
    fun `leaving split screen redraws the session`() {
        assertEquals(Verdict.REDRAW, decide(session = halfScreen, window = fullScreen))
    }

    @Test
    fun `a window that stays landscape but wants a lower resolution class redraws the session`() {
        val twoThirds = Shape(resolution = Size(1920, 1080), canvas = Size(1700, 1000))
        assertEquals(Verdict.REDRAW, decide(window = twoThirds))
    }

    @Test
    fun `a window announced as the session was is left alone`() {
        assertEquals(Verdict.MATCHES, decide(window = fullScreen))
        // The same resolution from a smaller window is told to the phone as margins.
        val narrower = Shape(resolution = Size(2560, 1440), canvas = Size(1700, 1600))
        assertEquals(Verdict.MATCHES, decide(window = narrower))
    }

    // --- a system bar ---

    @Test
    fun `a bar is bar sized and split screen is not`() {
        // BYD's 96 px bar on DiLink 5.0 (#72).
        assertTrue(SessionShapePolicy.isBarSized(Size(1920, 1080), Size(1920, 984)))
        assertTrue(SessionShapePolicy.isBarSized(Size(1920, 984), Size(1920, 1080)))
        // A status bar and a navigation bar on two sides at once.
        assertTrue(SessionShapePolicy.isBarSized(Size(1280, 800), Size(1184, 728)))
        assertTrue(SessionShapePolicy.isBarSized(Size(2560, 1600), Size(2560, 1600)))
        // Half and a third of the screen, and the two thirds left beside the third.
        assertFalse(SessionShapePolicy.isBarSized(Size(2560, 1600), Size(1270, 1600)))
        assertFalse(SessionShapePolicy.isBarSized(Size(2560, 1600), Size(853, 1600)))
        assertFalse(SessionShapePolicy.isBarSized(Size(2560, 1600), Size(1700, 1600)))
        assertFalse(SessionShapePolicy.isBarSized(Size(1270, 1600), Size(2560, 1600)))
        // A screen that turns.
        assertFalse(SessionShapePolicy.isBarSized(Size(1920, 1080), Size(1080, 1920)))
    }

    // A 1280x800 panel is 1080p to the automatic choice and 720p once a bar takes 96 px of its
    // height. That is a change of margins, and must not cost a reconnect each time the bar moves.
    @Test
    fun `a bar that tips the resolution class does not redraw the session`() {
        val bare = Shape(resolution = Size(1920, 1080), canvas = Size(1280, 800))
        val withBar = Shape(resolution = Size(1280, 720), canvas = Size(1280, 704))
        assertEquals(Verdict.BAR_SIZED, decide(session = bare, window = withBar))
        assertEquals(Verdict.BAR_SIZED, decide(session = withBar, window = bare))
    }

    // --- the gates ---

    @Test
    fun `nothing is redrawn without a session`() {
        assertEquals(Verdict.NO_SESSION, decide(sessionUp = false))
        assertEquals(Verdict.NO_SESSION, decide(session = null))
        assertEquals(Verdict.NO_SESSION, decide(window = null))
    }

    @Test
    fun `a projection behind another app is not redrawn`() {
        assertEquals(Verdict.NOT_ON_SCREEN, decide(inFront = false))
    }

    @Test
    fun `picture in picture is not redrawn`() {
        assertEquals(Verdict.NOT_ON_SCREEN, decide(pictureInPicture = true))
    }

    @Test
    fun `a dark screen is not redrawn`() {
        assertEquals(Verdict.NOT_ON_SCREEN, decide(screenOn = false))
    }

    @Test
    fun `a locked screen is not redrawn`() {
        assertEquals(Verdict.NOT_ON_SCREEN, decide(keyguardLocked = true))
    }

    @Test
    fun `a session that would not come back by itself is not redrawn`() {
        assertEquals(Verdict.WOULD_NOT_RETURN, decide(reconnectsByItself = false))
    }

    // --- once per shape ---

    @Test
    fun `a shape already redrawn for is not redrawn again`() {
        assertEquals(Verdict.ALREADY_REDRAWN, decide(redrawnFor = halfScreen.resolution))
    }

    @Test
    fun `another shape is still redrawn for`() {
        val third = Shape(resolution = Size(720, 1280), canvas = Size(700, 1600))
        assertEquals(Verdict.REDRAW, decide(window = third, redrawnFor = halfScreen.resolution))
    }

    /** One settled window: the verdict, and what is remembered after it. */
    private fun settle(session: Shape?, window: Shape?, redrawnFor: Size?, onScreen: Boolean = true): Pair<Verdict, Size?> {
        val verdict = decide(session = session, window = window, sessionUp = session != null, inFront = onScreen, redrawnFor = redrawnFor)
        return verdict to SessionShapePolicy.rememberAfter(verdict, window, redrawnFor)
    }

    // The loop this must never become: the session cannot be negotiated for the new window, comes
    // back as it was, and is ended again each time the window settles.
    @Test
    fun `a session that comes back in the old shape is redrawn once however often the window settles`() {
        val (first, afterRedraw) = settle(session = fullScreen, window = halfScreen, redrawnFor = null)
        assertEquals(Verdict.REDRAW, first)
        assertEquals(halfScreen.resolution, afterRedraw)

        // The gap before the reconnect must not forget it.
        val (inTheGap, afterGap) = settle(session = null, window = halfScreen, redrawnFor = afterRedraw)
        assertEquals(Verdict.NO_SESSION, inTheGap)
        assertEquals(halfScreen.resolution, afterGap)

        var remembered = afterGap
        repeat(5) {
            val (again, still) = settle(session = fullScreen, window = halfScreen, redrawnFor = remembered)
            assertEquals(Verdict.ALREADY_REDRAWN, again)
            assertEquals(halfScreen.resolution, still)
            remembered = still
        }
    }

    @Test
    fun `a session that came back in the new shape can follow the window back`() {
        val (_, afterRedraw) = settle(session = fullScreen, window = halfScreen, redrawnFor = null)

        val (landed, afterLanding) = settle(session = halfScreen, window = halfScreen, redrawnFor = afterRedraw)
        assertEquals(Verdict.MATCHES, landed)
        assertNull(afterLanding)

        val (back, afterBack) = settle(session = halfScreen, window = fullScreen, redrawnFor = afterLanding)
        assertEquals(Verdict.REDRAW, back)
        assertEquals(fullScreen.resolution, afterBack)

        // And into split screen once more, after the session landed full screen.
        val (_, afterSecondLanding) = settle(session = fullScreen, window = fullScreen, redrawnFor = afterBack)
        assertEquals(Verdict.REDRAW, settle(session = fullScreen, window = halfScreen, redrawnFor = afterSecondLanding).first)
    }

    // A window read while the projection is hidden can look like anything, also like the session.
    // It must not clear what is remembered, or the next return would redraw the same shape again.
    @Test
    fun `a reading taken off screen neither redraws nor forgets`() {
        val (verdict, remembered) = settle(
            session = fullScreen, window = fullScreen, redrawnFor = halfScreen.resolution, onScreen = false
        )
        assertEquals(Verdict.NOT_ON_SCREEN, verdict)
        assertEquals(halfScreen.resolution, remembered)
    }

    @Test
    fun `only a match or a redraw changes what is remembered`() {
        val held = Size(1080, 1920)
        for (verdict in Verdict.values()) {
            val after = SessionShapePolicy.rememberAfter(verdict, fullScreen, held)
            when (verdict) {
                Verdict.REDRAW -> assertEquals(fullScreen.resolution, after)
                Verdict.MATCHES -> assertNull(after)
                else -> assertEquals("verdict=$verdict", held, after)
            }
        }
    }
}
