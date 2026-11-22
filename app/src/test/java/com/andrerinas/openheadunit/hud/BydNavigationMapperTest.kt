package com.andrerinas.openheadunit.hud

import org.junit.Assert.*
import org.junit.Test
import com.andrerinas.openheadunit.aap.AapNavigationHelper
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus as N

class BydNavigationMapperTest {
    @Test fun `left and right maneuvers retain direction`() {
        assertEquals(2, BydNavigationMapper.codes(7)!!.cluster)
        assertEquals(3, BydNavigationMapper.codes(8)!!.cluster)
        assertEquals(7, BydNavigationMapper.codes(11)!!.arrow)
        assertEquals(8, BydNavigationMapper.codes(12)!!.arrow)
        assertEquals(6, BydNavigationMapper.codes(9)!!.cluster)
        assertEquals(7, BydNavigationMapper.codes(10)!!.cluster)
    }
    @Test fun `roundabouts preserve direction and reject out of range exits`() {
        assertEquals(BydNavigationMapper.Codes(17, 37, 99, 3), BydNavigationMapper.codes(32, 3))
        assertEquals(BydNavigationMapper.Codes(11, 27, 99, 3), BydNavigationMapper.codes(34, 3))
        assertEquals(0, BydNavigationMapper.codes(32, 19)!!.exit)
        assertEquals(13, BydNavigationMapper.codes(32, 19)!!.gaode)
    }
    @Test fun `unknown does not become straight`() {
        assertNull(BydNavigationMapper.codes(0))
        assertNull(BydNavigationMapper.codes(500))
        assertNull(BydNavigationMapper.legacyType(4, 3))
    }
    @Test fun `legacy left right and uturn map correctly`() {
        assertEquals(7, BydNavigationMapper.legacyType(4, 1))
        assertEquals(8, BydNavigationMapper.legacyType(4, 2))
        assertEquals(12, BydNavigationMapper.legacyType(6, 2))
        assertEquals(30, BydNavigationMapper.legacyType(13, 2))
    }
    @Test fun `legacy roundabout keeps its exit using the traffic side setting`() {
        // Right-hand traffic (default): counter-clockwise, exit reaches the cluster.
        assertEquals(34, BydNavigationMapper.legacyType(13, 3, roundaboutExit = 2))
        assertEquals(BydNavigationMapper.Codes(11, 26, 99, 2), BydNavigationMapper.codes(34, 2))
        // Left-hand traffic setting: clockwise.
        assertEquals(32, BydNavigationMapper.legacyType(13, 3, roundaboutExit = 2, leftHandTraffic = true))
        // No usable exit number: the generic roundabout icon, unchanged.
        assertEquals(30, BydNavigationMapper.legacyType(13, 3, roundaboutExit = 0))
        assertEquals(30, BydNavigationMapper.legacyType(13, 3, roundaboutExit = 12))
    }
    @Test fun `a numbered roundabout reaches the HUD as the numbered icon DiPlay sends`() {
        for (event in listOf(11, 13)) {
            val codes = BydNavigationMapper.codes(BydNavigationMapper.legacyType(event, 3, roundaboutExit = 2)!!, 2)!!
            // DiPlay: CarPlay "roundabout, exit 2" -> NEW_ICON 11 with exit 2 -> instrument code 26.
            assertEquals(26, BydFactoryTurnCode.map(codes.cluster, codes.exit))
        }
        val clockwise = BydNavigationMapper.codes(BydNavigationMapper.legacyType(13, 3, 3, leftHandTraffic = true)!!, 3)!!
        assertEquals(37, BydFactoryTurnCode.map(clockwise.cluster, clockwise.exit))
    }
    private fun snapshot(distance: Int? = 75): AapNavigationHelper.NavigationSnapshot {
        val step = N.NavigationStep.newBuilder()
            .setManeuver(N.NavigationManeuver.newBuilder().setType(N.NavigationManeuver.NavigationType.TURN_NORMAL_LEFT))
            .setRoad(N.NavigationRoad.newBuilder().setName("Next road")).build()
        val state = N.NavigationState.newBuilder().addSteps(step).build()
        return AapNavigationHelper.NavigationSnapshot().apply {
            navigationState = AapNavigationHelper.TimedMessage(state, 0)
            currentStreet = AapNavigationHelper.TimedMessage("Current road", 0)
            if (distance != null) currentPosition = AapNavigationHelper.TimedMessage(
                N.NavigationCurrentPosition.newBuilder().setStepDistance(N.NavigationStepDistance.newBuilder()
                    .setDistance(N.NavigationDistance.newBuilder().setMeters(distance))).build(), 0)
        }
    }
    @Test fun `uses next road and metre distance`() {
        val frame = BydNavigationMapper.from(snapshot())!!
        assertEquals("Next road", frame.road)
        assertEquals(75, frame.distanceMeters)
        assertEquals(2, frame.clusterIcon)
    }
    @Test fun `missing or invalid distance hides guidance but zero remains valid`() {
        assertNull(BydNavigationMapper.from(snapshot(null)))
        assertNull(BydNavigationMapper.from(snapshot(-1)))
        assertEquals(0, BydNavigationMapper.from(snapshot(0))!!.distanceMeters)
    }
    @Test fun `empty modern route cannot revive legacy maneuver`() {
        val value = snapshot()
        value.navigationState = AapNavigationHelper.TimedMessage(N.NavigationState.newBuilder().build(), 0)
        value.nextTurnDetail = AapNavigationHelper.TimedMessage(N.NextTurnDetail.newBuilder()
            .setRoad("Old road").setNextTurn(N.NextTurnDetail.NextEvent.TURN).setSide(N.NextTurnDetail.Side.LEFT).build(), 0)
        assertNull(BydNavigationMapper.from(value))
    }
    @Test fun `unknown maneuver keeps distance and street without an arrow`() {
        val value = AapNavigationHelper.NavigationSnapshot().apply {
            nextTurnDetail = AapNavigationHelper.TimedMessage(N.NextTurnDetail.newBuilder()
                .setRoad("Str. Kiev").setNextTurn(N.NextTurnDetail.NextEvent.UNKNOWN)
                .setSide(N.NextTurnDetail.Side.UNSPECIFIED).build(), 0)
            nextTurnDistance = AapNavigationHelper.TimedMessage(
                N.NextTurnDistanceEvent.newBuilder().setDistanceMeters(300).build(), 0)
        }
        val frame = BydNavigationMapper.from(value)!!
        assertEquals(BydNavigationMapper.NO_ARROW, frame.clusterIcon)
        assertEquals(300, frame.distanceMeters)
        assertEquals("Str. Kiev", frame.road)
        assertNotNull(BydStandalonePackets.guidance(frame.clusterIcon, frame.roundaboutExit, 300, frame.road))
    }
    @Test fun `no maneuver at all still clears`() {
        assertNull(BydNavigationMapper.from(AapNavigationHelper.NavigationSnapshot()))
    }
    @Test fun `inactive unavailable and rerouting clear guidance`() {
        for (status in listOf(N.NavigationClusterStatus.NavigationStatusEnum.INACTIVE,
            N.NavigationClusterStatus.NavigationStatusEnum.UNAVAILABLE, N.NavigationClusterStatus.NavigationStatusEnum.REROUTING)) {
            val value = snapshot()
            value.clusterStatus = AapNavigationHelper.TimedMessage(N.NavigationClusterStatus.newBuilder().setStatus(status).build(), 0)
            assertNull(BydNavigationMapper.from(value))
        }
    }
}
