package com.andrerinas.openheadunit.hud

import org.junit.Assert.assertEquals
import org.junit.Test

class BydVehicleProbeDisplayTest {
    private val on = android.view.Display.STATE_ON

    @Test fun bydDashboardLayersAreNamed() {
        assertEquals(
            "3: shared_fission_bg_XDJAScreenProjection_1 1920x720 presentation on",
            BydVehicleProbe.displayLine(3, "shared_fission_bg_XDJAScreenProjection_1", 1920, 720, true, on),
        )
        assertEquals(
            "2: fission_bg_xdjaVirtualSurface 1280x480 presentation on",
            BydVehicleProbe.displayLine(2, "fission_bg_xdjaVirtualSurface", 1280, 480, true, on),
        )
    }

    @Test fun theMainScreenIsNamedWhateverItIsCalled() {
        assertEquals(
            "0: 内置屏幕 2560x1600 not presentation on",
            BydVehicleProbe.displayLine(0, "内置屏幕", 2560, 1600, false, on),
        )
    }

    @Test fun aDisplayThatMayCarryAUsersOwnNameIsNotNamed() {
        // Reports are posted publicly; a cast target is called what its owner called it.
        assertEquals(
            "7: (other) 1920x1080 presentation off",
            BydVehicleProbe.displayLine(7, "Living room TV", 1920, 1080, true, android.view.Display.STATE_OFF),
        )
        assertEquals("8: (other) 800x480 not presentation state=0", BydVehicleProbe.displayLine(8, null, 800, 480, false, 0))
    }
}
