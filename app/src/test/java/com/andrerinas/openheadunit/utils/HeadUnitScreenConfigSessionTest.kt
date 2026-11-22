package com.andrerinas.openheadunit.utils

import android.app.Application
import android.content.Context
import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.utils.SessionShapePolicy.Shape
import com.andrerinas.openheadunit.utils.SessionShapePolicy.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

private typealias Resolution = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType

/**
 * A session keeps the resolution service discovery announced, whatever its window does (#61): a
 * 1920x1080 panel with the projection full screen, and BYD's split screen leaving it 950x1080.
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [32], manifest = Config.NONE, qualifiers = "w1920dp-h1080dp-land-mdpi")
class HeadUnitScreenConfigSessionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val metrics get() = context.resources.displayMetrics

    private fun settings() = Settings(context).apply {
        resolutionId = 0 // Automatic
        fullscreenMode = Settings.FullscreenMode.IMMERSIVE
    }

    @Before fun newlyStartedApp() {
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        HeadUnitScreenConfig.resetForTest()
    }

    /** What ServiceDiscoveryResponse.makeProto() does before it reads the resolution. */
    private fun announceFullScreen(settings: Settings = settings()): Settings {
        HeadUnitScreenConfig.init(context, metrics, settings)
        HeadUnitScreenConfig.beginSession()
        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
        return settings
    }

    @Test fun theSessionIsRecordedAsItWasAnnounced() {
        announceFullScreen()

        assertEquals(Shape(Size(1920, 1080), Size(1920, 1080)), HeadUnitScreenConfig.sessionShape())
        assertTrue(HeadUnitScreenConfig.isResolutionLocked)
    }

    // The fault of #61: this dropped the lock and re-chose _1080x1920 under a 1920x1080 stream.
    @Test fun aWindowThatTurnsUnderASessionKeepsItsResolution() {
        announceFullScreen()

        assertTrue(HeadUnitScreenConfig.updateSurfaceDimensions(950, 1080))

        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
        assertTrue(HeadUnitScreenConfig.isResolutionLocked)
        assertEquals(950, HeadUnitScreenConfig.getUsableWidth())
        assertEquals(Size(1920, 1080), HeadUnitScreenConfig.sessionShape()?.resolution)
    }

    // The panel cap ran on a locked resolution too, and lowered it to what a smaller window warrants.
    @Test fun aWindowThatShrinksUnderASessionKeepsItsResolution() {
        announceFullScreen()

        assertTrue(HeadUnitScreenConfig.updateSurfaceDimensions(1200, 700))

        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
    }

    // The settings hash covers the anchor, so the init() every scale update makes saw a "settings
    // change" after any window change and unlocked.
    @Test fun anInitAfterTheWindowChangedKeepsItsResolution() {
        val settings = announceFullScreen()
        HeadUnitScreenConfig.updateSurfaceDimensions(950, 1080)

        HeadUnitScreenConfig.init(context, metrics, settings)

        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
        assertTrue(HeadUnitScreenConfig.isResolutionLocked)
    }

    @Test fun aResolutionSetDuringASessionWaitsForTheNextOne() {
        val settings = announceFullScreen()

        settings.resolutionId = Settings.Resolution._1280x720.id
        HeadUnitScreenConfig.init(context, metrics, settings)
        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)

        HeadUnitScreenConfig.unlockResolution()
        assertEquals(Resolution._1280x720, HeadUnitScreenConfig.negotiatedResolutionType)
    }

    // Nothing else is certain to run between a disconnect and the next service discovery, so the
    // end of the session has to choose for the window as it is by then.
    @Test fun theEndOfTheSessionChoosesForTheWindowAsItIsNow() {
        announceFullScreen()
        HeadUnitScreenConfig.updateSurfaceDimensions(950, 1080)

        HeadUnitScreenConfig.unlockResolution()

        assertNull(HeadUnitScreenConfig.sessionShape())
        assertFalse(HeadUnitScreenConfig.isResolutionLocked)
        assertEquals(Resolution._1080x1920, HeadUnitScreenConfig.negotiatedResolutionType)

        // The reconnect: the projection locks at the handshake, service discovery announces.
        HeadUnitScreenConfig.lockResolution()
        HeadUnitScreenConfig.beginSession()
        assertEquals(Shape(Size(1080, 1920), Size(950, 1080)), HeadUnitScreenConfig.sessionShape())
    }

    // CommManager.transportedQuited leaves the lock set. A window that turned meanwhile still has
    // to be chosen for, which the lock's own orientation check does once the session lets go.
    @Test fun aSessionTheTransportEndedIsChosenAgainUnderItsLock() {
        announceFullScreen()
        HeadUnitScreenConfig.updateSurfaceDimensions(950, 1080)

        HeadUnitScreenConfig.endSession()

        assertNull(HeadUnitScreenConfig.sessionShape())
        assertEquals(Resolution._1080x1920, HeadUnitScreenConfig.negotiatedResolutionType)
    }

    @Test fun aSessionTheTransportEndedInTheSameWindowKeepsItsLock() {
        announceFullScreen()

        HeadUnitScreenConfig.endSession()

        assertTrue(HeadUnitScreenConfig.isResolutionLocked)
        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
    }

    // Between sessions the lock behaves as it did: a window in the other orientation drops it.
    @Test fun withoutASessionAStaleLockIsStillDropped() {
        HeadUnitScreenConfig.init(context, metrics, settings())
        HeadUnitScreenConfig.lockResolution()

        HeadUnitScreenConfig.updateSurfaceDimensions(950, 1080)

        assertFalse(HeadUnitScreenConfig.isResolutionLocked)
        assertEquals(Resolution._1080x1920, HeadUnitScreenConfig.negotiatedResolutionType)
    }

    // What the redraw check compares the session with must be what the next session would get,
    // with a system bar taking its share of the window (BYD's 96 px) and without one.
    @Test fun aWindowIsAskedAboutAsItWouldBeAnnounced() {
        for (bar in listOf(0, 96)) {
            for ((width, height) in listOf(1920 to 1080, 950 to 1080, 1200 to 700, 640 to 1080, 1280 to 720, 1280 to 800, 800 to 480)) {
                HeadUnitScreenConfig.resetForTest()
                HeadUnitScreenConfig.init(context, metrics, settings())
                HeadUnitScreenConfig.updateInsets(0, 0, 0, bar)
                val asked = HeadUnitScreenConfig.shapeForWindow(width, height)

                HeadUnitScreenConfig.adoptWindow(width, height)
                HeadUnitScreenConfig.beginSession()

                assertEquals("${width}x$height with a $bar px bar", HeadUnitScreenConfig.sessionShape(), asked)
                assertEquals(Size(width, height), asked?.canvas)
            }
        }
    }

    // The canvas is the window, so that a bar coming or going is bar sized whatever it does to
    // the resolution: on a 1280x800 panel it tips the automatic choice from 1080p to 720p.
    @Test fun aBarChangesWhatAWindowWantsButNotItsCanvas() {
        HeadUnitScreenConfig.init(context, metrics, settings())
        val bare = HeadUnitScreenConfig.shapeForWindow(1280, 800)
        HeadUnitScreenConfig.updateInsets(0, 0, 0, 96)
        val withBar = HeadUnitScreenConfig.shapeForWindow(1280, 800)

        assertEquals(Shape(Size(1920, 1080), Size(1280, 800)), bare)
        assertEquals(Shape(Size(1280, 720), Size(1280, 800)), withBar)
    }

    @Test fun askingAboutAWindowChangesNothing() {
        announceFullScreen()

        val asked = HeadUnitScreenConfig.shapeForWindow(950, 1080)

        assertEquals(Shape(Size(1080, 1920), Size(950, 1080)), asked)
        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
        assertEquals(1920, HeadUnitScreenConfig.getUsableWidth())
        assertEquals(Size(1920, 1080), HeadUnitScreenConfig.sessionShape()?.canvas)
    }

    // The TextureView backend reports no resize as a surface change, so the window a session is
    // redrawn for is handed over before it ends, and the reconnect is announced for it.
    @Test fun aSessionRedrawnForAWindowIsAnnouncedForIt() {
        announceFullScreen()
        HeadUnitScreenConfig.updateInsets(0, 0, 0, 96)

        assertTrue(HeadUnitScreenConfig.adoptWindow(950, 1080))
        assertFalse(HeadUnitScreenConfig.adoptWindow(950, 1080))
        assertEquals(Resolution._1920x1080, HeadUnitScreenConfig.negotiatedResolutionType)
        HeadUnitScreenConfig.unlockResolution()

        assertEquals(Resolution._1080x1920, HeadUnitScreenConfig.negotiatedResolutionType)
        HeadUnitScreenConfig.beginSession()
        assertEquals(Shape(Size(1080, 1920), Size(950, 1080)), HeadUnitScreenConfig.sessionShape())
        assertEquals(984, HeadUnitScreenConfig.getUsableHeight())
    }

    // The settings hash covers the anchor. Handing the window over without storing it would make
    // the init() of the reconnect's service discovery see a settings change, start over and drop
    // the bar the window had reported: the margins fault of #72, on every redraw.
    @Test fun theServiceDiscoveryAfterARedrawKeepsTheBar() {
        val settings = announceFullScreen()
        HeadUnitScreenConfig.updateInsets(0, 0, 0, 96)
        HeadUnitScreenConfig.adoptWindow(950, 1080)
        HeadUnitScreenConfig.unlockResolution()

        RuntimeEnvironment.setQualifiers("w950dp-h1080dp-port-mdpi")
        HeadUnitScreenConfig.init(context, context.resources.displayMetrics, settings)
        HeadUnitScreenConfig.beginSession()

        assertEquals(96, HeadUnitScreenConfig.systemInsetBottom)
        assertEquals(Shape(Size(1080, 1920), Size(950, 1080)), HeadUnitScreenConfig.sessionShape())
    }

    @Test fun aWindowCannotBeAskedAboutBeforeTheScreenIsKnown() {
        assertNull(HeadUnitScreenConfig.shapeForWindow(950, 1080))
        HeadUnitScreenConfig.init(context, metrics, settings())
        assertNull(HeadUnitScreenConfig.shapeForWindow(0, 0))
    }
}
