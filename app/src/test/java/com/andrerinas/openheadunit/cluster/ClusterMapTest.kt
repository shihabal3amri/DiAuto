package com.andrerinas.openheadunit.cluster

import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.aap.protocol.proto.Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterMapTest {
    private val fingerprint = ClusterLayout.DILINK51_FINGERPRINT

    @Test fun `DiLink 5_1 map windows are the measured ones`() {
        assertEquals(ClusterLayout.Plan(0, 144, 1920, 480, true), ClusterLayout.plan(1920, 720, ClusterLayout.Theme.MAP, fingerprint))
        assertEquals(ClusterLayout.Plan(1320, 0, 600, 720, false), ClusterLayout.plan(1920, 720, ClusterLayout.Theme.SCENARIO, fingerprint))
    }

    @Test fun `the cluster descriptions copy ones phones accept and carry no margins`() {
        val first = ClusterStreamProfile.LADDER.first()
        assertEquals(VideoCodecResolutionType._1280x720, first.resolution)
        assertEquals(300, first.viewingDistance)
        assertEquals(160, first.realDensity)
        assertTrue("the description the OnePlus 15 accepted goes first", first.withInput)
        val last = ClusterStreamProfile.LADDER.last()
        assertEquals(VideoCodecResolutionType._800x480, last.resolution)
        assertNull(last.viewingDistance)
        assertEquals(3, ClusterStreamProfile.LADDER.size)
        assertFalse(ClusterStreamProfile.LADDER[1].withInput || last.withInput)
    }

    @Test fun `display selection never swaps the side layer for the full one`() {
        val names = listOf("ivi", ClusterLayout.FULL, ClusterLayout.SIDE)
        assertEquals(ClusterLayout.FULL, ClusterLayout.displayName(names, fingerprint, ClusterLayout.Theme.MAP))
        assertEquals(ClusterLayout.SIDE, ClusterLayout.displayName(names, fingerprint, ClusterLayout.Theme.SIMPLE))
        assertNull(ClusterLayout.displayName(listOf(ClusterLayout.FULL), fingerprint, ClusterLayout.Theme.SCENARIO))
        // DiLink 5.0 and unmeasured firmware keep DiPlay's original order, without a viewport.
        assertEquals(ClusterLayout.FULL, ClusterLayout.displayName(names, "other", ClusterLayout.Theme.SCENARIO))
        assertNull(ClusterLayout.plan(1920, 720, ClusterLayout.Theme.MAP, "other"))
        assertNull(ClusterLayout.plan(1280, 480, ClusterLayout.Theme.MAP, fingerprint))
        assertEquals(ClusterLayout.Theme.SCENARIO, ClusterLayout.theme(7))
    }

    private fun varints(unknown: com.google.protobuf.UnknownFieldSet): Map<Int, Long> {
        val sink = Control.Service.MediaSinkService.newBuilder()
            .setAvailableType(Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP)
            .setUnknownFields(unknown)
            .build()
        val fields = mutableMapOf<Int, Long>()
        val input = CodedInputStream.newInstance(sink.toByteArray())
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_VARINT) fields[WireFormat.getTagFieldNumber(tag)] = input.readInt64()
            else input.skipField(tag)
        }
        return fields
    }

    @Test fun `the cluster sink carries display id 1 and the cluster display type`() {
        val fields = varints(ClusterSinkFields.cluster())
        assertEquals(1L, fields[6])
        assertEquals(1L, fields[7])
        assertEquals(Media.MediaCodecType.MEDIA_CODEC_VIDEO_H264_BP_VALUE.toLong(), fields[1])
    }

    @Test fun `viewing distance and real density are written only when the description sets them`() {
        val config = Control.Service.MediaSinkService.VideoConfiguration.newBuilder()
            .setCodecResolution(VideoCodecResolutionType._1280x720)
            .setFrameRate(Control.Service.MediaSinkService.VideoConfiguration.VideoFrameRateType._30)
            .setMarginWidth(0).setMarginHeight(0).setDensity(160)
            .setUnknownFields(ClusterSinkFields.videoExtras(ClusterStreamProfile.LADDER[1]))
            .build()
        val fields = mutableMapOf<Int, Long>()
        val input = CodedInputStream.newInstance(config.toByteArray())
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_VARINT) fields[WireFormat.getTagFieldNumber(tag)] = input.readInt64()
            else input.skipField(tag)
        }
        assertEquals(300L, fields[7])
        assertEquals(160L, fields[9])
        assertTrue(ClusterSinkFields.videoExtras(ClusterStreamProfile.LADDER.last()).asMap().isEmpty())
    }

    @Test fun `parameter sets and keyframes are recognised in an access unit`() {
        val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1f)
        val pps = byteArrayOf(0, 0, 0, 1, 0x68, 0xce.toByte(), 0x3c, 0x80.toByte())
        val idr = byteArrayOf(0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte())
        val slice = byteArrayOf(0, 0, 0, 1, 0x41, 0x9a.toByte())

        val config = AvcNal.scan(sps + pps)
        assertTrue(config.hasSps && config.hasPps && config.onlyParameterSets)
        val key = AvcNal.scan(sps + pps + idr)
        assertTrue(key.hasIdr)
        assertFalse(key.onlyParameterSets)
        val delta = AvcNal.scan(slice)
        assertFalse(delta.hasIdr)
        assertFalse(delta.onlyParameterSets)
        assertEquals((sps + pps).toList(), AvcNal.parameterSets(sps + pps + idr).toList())
    }

    @Test fun `the turn card stays inside the map window it belongs to`() {
        val side = ClusterLayout.Plan(1320, 0, 600, 720, false)
        val sideCard = ClusterTurnCardPlacement.card(1920, 720, side)!!
        assertTrue(sideCard.left >= side.left && sideCard.left + sideCard.width <= side.left + side.width)
        val full = ClusterLayout.Plan(0, 144, 1920, 480, true)
        val fullCard = ClusterTurnCardPlacement.card(1920, 720, full)!!
        assertTrue(fullCard.top >= full.top && fullCard.top + fullCard.height <= full.top + full.height)
        assertTrue("clear of the left readouts", fullCard.left >= 460)
        val panel = ClusterTurnCardPlacement.card(1920, 720, null)!!
        assertTrue(panel.left + panel.width <= 1920 && panel.top >= 0)
        assertNull(ClusterTurnCardPlacement.card(0, 720, null))
    }
}
