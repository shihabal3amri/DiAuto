package com.andrerinas.openheadunit.hud

import com.andrerinas.openheadunit.aap.AapNavigationHelper.NavigationSnapshot
import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus

internal object BydNavigationMapper {
    /**
     * @param leftHandTraffic the "traffic keeps left" setting. Legacy turn events carry no
     * circulation direction for roundabouts, so this setting supplies it: clockwise where traffic
     * keeps left, counter-clockwise where it keeps right (the default).
     */
    fun from(snapshot: NavigationSnapshot, leftHandTraffic: Boolean = false): BydGuidance? {
        val status = snapshot.clusterStatus?.payload?.status
        if (status != null && status != NavigationStatus.NavigationClusterStatus.NavigationStatusEnum.ACTIVE) return null
        val step = snapshot.navigationState?.payload?.stepsList?.firstOrNull()
        if (snapshot.navigationState != null && step == null) return null
        val legacy = snapshot.nextTurnDetail?.payload
        val modern = step?.takeIf { it.hasManeuver() && it.maneuver.hasType() }?.maneuver
        val type = modern?.type?.number ?: legacy?.takeIf { it.hasNextTurn() }?.let {
            legacyType(it.nextTurn.number, if (it.hasSide()) it.side.number else 3, it.turnNumber, leftHandTraffic)
        }
        // A maneuver the phone sent but we cannot draw (Waze's UNKNOWN, a turn with no side...) still
        // shows its distance and street, with no arrow, rather than blanking the HUD - which read as
        // a lost connection. Only a snapshot with no maneuver at all clears it.
        if (type == null && modern == null && legacy?.hasNextTurn() != true) return null
        val codes = type?.let { codes(it, modern?.roundaboutExitNumber ?: legacy?.turnNumber ?: 0) } ?: NO_ARROW_CODES
        val position = snapshot.currentPosition?.payload
        val distance = position?.takeIf { it.hasStepDistance() && it.stepDistance.hasDistance() && it.stepDistance.distance.hasMeters() }
            ?.stepDistance?.distance?.meters
            ?: snapshot.nextTurnDistance?.payload?.takeIf { it.hasDistanceMeters() }?.distanceMeters
            ?: return null // Do not present unknown distance as zero/"Now".
        if (distance < 0) return null
        val road = step?.takeIf { it.hasRoad() && it.road.hasName() }?.road?.name?.takeIf { it.isNotBlank() }
            ?: legacy?.road?.takeIf { it.isNotBlank() } ?: snapshot.currentStreet?.payload.orEmpty()
        val destination = position?.destinationDistancesList?.firstOrNull()
        val remaining = destination?.takeIf { it.hasDistance() && it.distance.hasMeters() }?.distance?.meters ?: -1
        val seconds = destination?.takeIf { it.hasTimeToArrivalSeconds() }?.timeToArrivalSeconds
            ?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: -1
        return BydGuidance(codes.cluster, codes.gaode, codes.arrow, codes.exit, distance, road, remaining, seconds)
    }

    data class Codes(val cluster: Int, val gaode: Int, val arrow: Int, val exit: Int = 0)

    /** Cluster icon meaning "no arrow": the factory table maps it to turn code 0. */
    const val NO_ARROW = 0
    private val NO_ARROW_CODES = Codes(NO_ARROW, NO_ARROW, NO_ARROW)

    fun codes(type: Int, exit: Int = 0): Codes? = when (type) {
        7, 15, 23 -> Codes(2, 1, 1)
        8, 16, 24 -> Codes(3, 2, 2)
        3, 5, 13, 21, 25, 27 -> Codes(4, 3, 3)
        4, 6, 14, 22, 26, 28 -> Codes(5, 4, 5)
        9, 17 -> Codes(6, 7, 1)
        10, 18 -> Codes(7, 8, 2)
        11, 19 -> Codes(8, 9, 7)
        12, 20 -> Codes(19, 10, 8)
        30 -> Codes(11, 13, 99)
        31 -> Codes(12, 24, 99)
        32, 33, 34, 35 -> {
            val clockwise = type == 32 || type == 33
            val validExit = exit.takeIf { it in 1..10 } ?: 0
            Codes(if (clockwise) 17 else 11,
                if (validExit == 0) 13 else (if (clockwise) 34 else 24) + validExit,
                99, validExit)
        }
        39, 40, 41, 42 -> Codes(15, 48, 99)
        1, 2, 29, 36, 37, 38 -> Codes(9, 11, 11)
        else -> null // Unknown instructions must not become a misleading straight arrow.
    }

    fun legacyType(event: Int, side: Int, roundaboutExit: Int = 0, leftHandTraffic: Boolean = false): Int? = when (event) {
        1 -> 1
        2 -> 2
        3, 7, 8, 9, 10 -> when(side) { 1 -> 5; 2 -> 6; else -> null }
        4 -> when(side) { 1 -> 7; 2 -> 8; else -> null }
        5 -> when(side) { 1 -> 9; 2 -> 10; else -> null }
        6 -> when(side) { 1 -> 11; 2 -> 12; else -> null }
        12 -> 31
        // Legacy turn side does not establish roundabout circulation direction. With an exit
        // number, take the direction from the traffic-side setting so the exit reaches the cluster
        // (enter-and-exit: 32 clockwise, 34 counter-clockwise), as DiPlay sends CarPlay's numbered
        // roundabouts; without one keep the generic icon. An enter event can carry the number too.
        11, 13 -> if (roundaboutExit in 1..10) (if (leftHandTraffic) 32 else 34) else 30
        14 -> 36
        16 -> 37
        17 -> 38
        18 -> 39
        else -> null
    }
}
