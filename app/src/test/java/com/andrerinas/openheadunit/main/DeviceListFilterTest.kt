package com.andrerinas.openheadunit.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListFilterTest {

    @Test
    fun `hides accessory device while normal device also present during AOA transition`() {
        assertFalse(MainViewModel.shouldIncludeDevice(
            isInAccessoryMode = true,
            anyNonAccessoryPresent = true))
    }

    @Test
    fun `shows accessory device when it is the only connected device`() {
        assertTrue(MainViewModel.shouldIncludeDevice(
            isInAccessoryMode = true,
            anyNonAccessoryPresent = false))
    }

    @Test
    fun `always shows normal mode devices`() {
        assertTrue(MainViewModel.shouldIncludeDevice(
            isInAccessoryMode = false,
            anyNonAccessoryPresent = false))
        assertTrue(MainViewModel.shouldIncludeDevice(
            isInAccessoryMode = false,
            anyNonAccessoryPresent = true))
    }

    @Test
    fun `same device with multiple USB paths shows as single entry`() {
        // Samsung phone gets /dev/bus/usb/002/040, /002/084, /002/091 on each reconnection.
        // All share uniqueName "SAMSUNG SAMSUNG_Android" — only one should appear.
        val repeatedName = "SAMSUNG SAMSUNG_Android"
        val names = listOf(repeatedName, repeatedName, repeatedName, repeatedName)

        assertEquals(1, MainViewModel.deduplicateNames(names).size)
    }

    @Test
    fun `different devices are not removed by deduplication`() {
        val names = listOf("SAMSUNG SAMSUNG_Android", "Google Pixel 8", "Motorola Moto G")

        assertEquals(3, MainViewModel.deduplicateNames(names).size)
    }
}
