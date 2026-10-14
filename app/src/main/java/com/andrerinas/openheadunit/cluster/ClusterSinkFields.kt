package com.andrerinas.openheadunit.cluster

import com.google.protobuf.UnknownFieldSet

/**
 * Fields this app's generated protocol classes do not have, from the Android Auto wire schema
 * aa-proxy-rs uses to add cluster displays: MediaSinkService `display_id` (6) and `display_type`
 * (7, MAIN 0 / CLUSTER 1 / AUXILIARY 2), and VideoConfiguration `viewing_distance` (7) and
 * `real_density` (9). A sink without a display id and type is the main display.
 */
internal object ClusterSinkFields {
    const val DISPLAY_ID_FIELD = 6
    const val DISPLAY_TYPE_FIELD = 7
    const val DISPLAY_TYPE_CLUSTER = 1L
    const val CLUSTER_DISPLAY_ID = 1L

    /** InputSourceService `display_id`. */
    const val INPUT_DISPLAY_ID_FIELD = 5
    /** D-pad up, down, left, right and centre: aa-proxy-rs's cluster keycodes. */
    val CLUSTER_KEYCODES = listOf(19, 20, 21, 22, 23)
    const val VIEWING_DISTANCE_FIELD = 7
    const val REAL_DENSITY_FIELD = 9

    fun cluster(): UnknownFieldSet = UnknownFieldSet.newBuilder()
        .addField(DISPLAY_ID_FIELD, UnknownFieldSet.Field.newBuilder().addVarint(CLUSTER_DISPLAY_ID).build())
        .addField(DISPLAY_TYPE_FIELD, UnknownFieldSet.Field.newBuilder().addVarint(DISPLAY_TYPE_CLUSTER).build())
        .build()

    fun clusterInput(): UnknownFieldSet = UnknownFieldSet.newBuilder()
        .addField(INPUT_DISPLAY_ID_FIELD, UnknownFieldSet.Field.newBuilder().addVarint(CLUSTER_DISPLAY_ID).build())
        .build()

    /** VideoConfiguration's viewing_distance (7) and real_density (9), when the profile sets them. */
    fun videoExtras(profile: ClusterStreamProfile): UnknownFieldSet = UnknownFieldSet.newBuilder().apply {
        profile.viewingDistance?.let { addField(VIEWING_DISTANCE_FIELD, UnknownFieldSet.Field.newBuilder().addVarint(it.toLong()).build()) }
        profile.realDensity?.let { addField(REAL_DENSITY_FIELD, UnknownFieldSet.Field.newBuilder().addVarint(it.toLong()).build()) }
    }.build()
}
