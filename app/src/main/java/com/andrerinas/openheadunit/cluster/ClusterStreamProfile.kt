package com.andrerinas.openheadunit.cluster

import com.andrerinas.openheadunit.aap.protocol.proto.Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType

/**
 * One way of describing the instrument-cluster video display to the phone.
 *
 * A phone closes the session on a description it does not accept. A 1280x720 stream with a
 * 680 px width margin (a map shaped like the 600x720 side window) was refused at protocol 1.2 and
 * 1.7 alike, so the descriptions below copy ones that phones are known to accept, without margins;
 * the presentation crops the picture to the cluster window instead.
 */
internal data class ClusterStreamProfile(
    val name: String,
    val resolution: VideoCodecResolutionType,
    val codecWidth: Int,
    val codecHeight: Int,
    val density: Int = DENSITY_DPI,
    /** VideoConfiguration field 7, absent from this app's generated classes. */
    val viewingDistance: Int? = null,
    /** VideoConfiguration field 9, absent from this app's generated classes. */
    val realDensity: Int? = null,
    /** Also declare an input source for the cluster: the D-pad keycodes aa-proxy-rs gives it. */
    val withInput: Boolean = false,
) {
    companion object {
        const val DENSITY_DPI = 160
        const val FRAME_RATE = 30

        /** Tried in order, one per connection, until the phone accepts one. */
        val LADDER = listOf(
            // aa-proxy-rs's cluster defaults with its D-pad input source: accepted by a OnePlus 15,
            // where the same without the input source was refused.
            ClusterStreamProfile("1280x720+input", VideoCodecResolutionType._1280x720, 1280, 720,
                viewingDistance = 300, realDensity = DENSITY_DPI, withInput = true),
            // aa-proxy-rs's cluster defaults without an input source.
            ClusterStreamProfile("1280x720", VideoCodecResolutionType._1280x720, 1280, 720,
                viewingDistance = 300, realDensity = DENSITY_DPI),
            // The cluster example of Android's desktop head unit.
            ClusterStreamProfile("800x480", VideoCodecResolutionType._800x480, 800, 480),
        )
    }
}
