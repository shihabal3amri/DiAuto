package com.andrerinas.openheadunit.app

import com.andrerinas.openheadunit.app.UsbAttachedActivity.DeviceSource
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbDeviceResolverTest {

    @Test
    fun `intent with device takes priority over fallback list`() {
        assertEquals(DeviceSource.FROM_INTENT,
            UsbAttachedActivity.pickDeviceSource(intentHasDevice = true, fallbackCount = 2))
    }

    @Test
    fun `sole fallback device used when intent has none`() {
        assertEquals(DeviceSource.FROM_FALLBACK,
            UsbAttachedActivity.pickDeviceSource(intentHasDevice = false, fallbackCount = 1))
    }

    @Test
    fun `ambiguous when intent empty and multiple fallback devices`() {
        assertEquals(DeviceSource.AMBIGUOUS,
            UsbAttachedActivity.pickDeviceSource(intentHasDevice = false, fallbackCount = 3))
    }

    @Test
    fun `ambiguous when both intent and list are empty`() {
        assertEquals(DeviceSource.AMBIGUOUS,
            UsbAttachedActivity.pickDeviceSource(intentHasDevice = false, fallbackCount = 0))
    }
}
