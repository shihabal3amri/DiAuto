package com.andrerinas.openheadunit.aap

import android.media.AudioAttributes
import com.andrerinas.openheadunit.aap.CarVolumeGroups.AUTOMATIC
import com.andrerinas.openheadunit.aap.CarVolumeGroups.USAGE_ONLY
import com.andrerinas.openheadunit.aap.protocol.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarVolumeGroupsTest {

    private fun route(channel: Int, assistant: Boolean = false, sdk: Int = 33,
                      navi: Int = AUTOMATIC, voice: Int = AUTOMATIC, dilink: Int = 5) =
        CarVolumeGroups.routeFor(channel, assistant, sdk, navi, voice, dilink)

    @Test
    fun `media stays on the media usage`() {
        val r = route(Channel.ID_AUD)
        assertEquals(AudioAttributes.USAGE_MEDIA, r.usage)
        assertNull(r.legacyStream)
    }

    @Test
    fun `navigation uses the DiLink stream automatically`() {
        assertEquals(15, route(Channel.ID_AU1, dilink = 5).legacyStream)
        assertEquals(14, route(Channel.ID_AU1, dilink = 4).legacyStream)
        assertEquals(14, route(Channel.ID_AU1, dilink = 3).legacyStream)
        assertNull(route(Channel.ID_AU1, dilink = 0).legacyStream)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, route(Channel.ID_AU1).usage)
    }

    @Test
    fun `a chosen navigation stream wins and zero means usage only`() {
        assertEquals(9, route(Channel.ID_AU1, navi = 9).legacyStream)
        assertNull(route(Channel.ID_AU1, navi = USAGE_ONLY).legacyStream)
    }

    @Test
    fun `assistant follows navigation unless the Voice slider stream is chosen`() {
        assertEquals(15, route(Channel.ID_AU1, assistant = true).legacyStream)
        val voice = route(Channel.ID_AU1, assistant = true, voice = 17)
        assertEquals(17, voice.legacyStream)
        assertEquals(AudioAttributes.USAGE_ASSISTANT, voice.usage)
        assertEquals(14, route(Channel.ID_AU1, assistant = true, dilink = 4).legacyStream)
        assertEquals(15, route(Channel.ID_AU1, assistant = true, navi = 15, dilink = 0).legacyStream)
        val own = route(Channel.ID_AU1, assistant = true, voice = 12)
        assertEquals(12, own.legacyStream)
        assertEquals(AudioAttributes.USAGE_ASSISTANT, own.usage)
        assertEquals(AudioAttributes.USAGE_ASSISTANT, route(Channel.ID_AU1, assistant = true, voice = USAGE_ONLY).usage)
    }

    @Test
    fun `the speech track is rebuilt only when the assistant route differs`() {
        assertFalse(CarVolumeGroups.speechRouteChanges(33, AUTOMATIC, AUTOMATIC, 5))
        assertTrue(CarVolumeGroups.speechRouteChanges(33, AUTOMATIC, 17, 5))
        assertFalse(CarVolumeGroups.speechRouteChanges(33, 17, 17, 5))
        assertTrue(CarVolumeGroups.speechRouteChanges(33, AUTOMATIC, 12, 5))
        assertFalse(CarVolumeGroups.speechRouteChanges(25, AUTOMATIC, 12, 5))
    }

    @Test
    fun `DiLink generation is read from the build`() {
        assertEquals(3, CarVolumeGroups.detectDiLink("DiLink3.0", null, null, 29, null))
        assertEquals(5, CarVolumeGroups.detectDiLink("IVI", "BYD-AUTO/IVI/IVI:13/x", null, 33, null))
        assertEquals(4, CarVolumeGroups.detectDiLink(null, null, null, 30, "21.1.2"))
        assertEquals(0, CarVolumeGroups.detectDiLink("sdk_phone", "google/sdk", null, 34, null))
    }

    private val voice17 = CarVolumeGroups.Route(AudioAttributes.USAGE_ASSISTANT, AudioAttributes.CONTENT_TYPE_SPEECH, 17)

    @Test
    fun `a stream number Android maps to its slider is kept`() {
        val r = CarVolumeGroups.reach(voice17, legacyStreamOf = { it }, usageStreamOf = { 3 })
        assertEquals(voice17, r)
    }

    @Test
    fun `an unmapped stream number is replaced by the usage that reaches that slider`() {
        val usages = mapOf(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY to 17)
        val r = CarVolumeGroups.reach(voice17, legacyStreamOf = { 3 }, usageStreamOf = { usages[it] ?: 3 })
        assertNull(r.legacyStream)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, r.usage)
    }

    @Test
    fun `with no usage reaching the slider the stream number is kept`() {
        val r = CarVolumeGroups.reach(voice17, legacyStreamOf = { 3 }, usageStreamOf = { 3 })
        assertEquals(voice17, r)
        val media = CarVolumeGroups.Route(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC, null)
        assertEquals(media, CarVolumeGroups.reach(media, { error("not asked") }, { error("not asked") }))
    }
}
