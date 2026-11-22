package com.andrerinas.openheadunit.utils

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

/**
 * The order of a first connection after the app starts (#72): the projection screen initialises
 * the screen configuration and reports the system bar it keeps, and only then is the service
 * discovery response built, which initialises the configuration again from the service.
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [32], manifest = Config.NONE, qualifiers = "w1920dp-h1080dp-land-mdpi")
class HeadUnitScreenConfigStartupTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val metrics get() = context.resources.displayMetrics

    private fun settings() = Settings(context).apply {
        resolutionId = 0 // Automatic
        fullscreenMode = Settings.FullscreenMode.STATUS_ONLY
    }

    @Before fun newlyStartedApp() {
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        HeadUnitScreenConfig.resetForTest()
    }

    @Test fun theBarReportedBeforeServiceDiscoveryIsStillThereWhenTheResponseIsBuilt() {
        val settings = settings()
        HeadUnitScreenConfig.init(context, metrics, settings)
        assertEquals(1920, HeadUnitScreenConfig.getUsableWidth())
        assertEquals(1080, HeadUnitScreenConfig.getUsableHeight())

        // The projection window keeps BYD's 96 px bar at the bottom.
        HeadUnitScreenConfig.updateInsets(0, 0, 0, 96)
        assertEquals(96, HeadUnitScreenConfig.getHeightMargin())

        // ServiceDiscoveryResponse.makeProto() initialises again, with nothing changed.
        HeadUnitScreenConfig.init(context, metrics, settings)

        assertEquals(96, HeadUnitScreenConfig.systemInsetBottom)
        assertEquals(984, HeadUnitScreenConfig.getUsableHeight())
        assertEquals(96, HeadUnitScreenConfig.getHeightMargin())
        assertEquals(0, HeadUnitScreenConfig.getWidthMargin())
    }

    @Test fun aLockTakenOnTheFirstConnectionSurvivesTheResponseBeingBuilt() {
        val settings = settings()
        HeadUnitScreenConfig.init(context, metrics, settings)
        HeadUnitScreenConfig.lockResolution()

        HeadUnitScreenConfig.init(context, metrics, settings)

        assertEquals(true, HeadUnitScreenConfig.isResolutionLocked)
    }

    @Test fun aRealSettingsChangeStillStartsOver() {
        val settings = settings()
        HeadUnitScreenConfig.init(context, metrics, settings)
        HeadUnitScreenConfig.updateInsets(0, 0, 0, 96)
        HeadUnitScreenConfig.lockResolution()

        settings.fullscreenMode = Settings.FullscreenMode.IMMERSIVE
        HeadUnitScreenConfig.init(context, metrics, settings)

        // The new mode has no bar until its own window reports one, and may renegotiate.
        assertEquals(0, HeadUnitScreenConfig.systemInsetBottom)
        assertEquals(false, HeadUnitScreenConfig.isResolutionLocked)
    }
}
