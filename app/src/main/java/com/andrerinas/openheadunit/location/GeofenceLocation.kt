package com.andrerinas.openheadunit.location

import android.content.Context
import android.location.Location
import com.andrerinas.openheadunit.utils.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A user-defined place (e.g. "Home", "Garage"): a circular area that sets a light or
 * dark appearance while the device is inside it. Only used by the "Location (by area)"
 * theme mode; whether it affects the app UI, Android Auto, or both depends on which
 * selector is set to that mode.
 */
data class GeofenceLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    /** true -> dark inside this area, false -> light inside this area. */
    val forceNight: Boolean = true
) {
    fun distanceTo(location: Location): Float {
        val center = Location("").apply {
            latitude = this@GeofenceLocation.latitude
            longitude = this@GeofenceLocation.longitude
        }
        return center.distanceTo(location)
    }

    fun contains(location: Location): Boolean = distanceTo(location) <= radiusMeters

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("lat", latitude)
        put("lon", longitude)
        put("radius", radiusMeters.toDouble())
        put("forceNight", forceNight)
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 150f

        fun fromJson(o: JSONObject): GeofenceLocation = GeofenceLocation(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            latitude = o.optDouble("lat", 0.0),
            longitude = o.optDouble("lon", 0.0),
            radiusMeters = o.optDouble("radius", DEFAULT_RADIUS_METERS.toDouble()).toFloat(),
            forceNight = o.optBoolean("forceNight", true)
        )

        fun listFromJson(json: String?): List<GeofenceLocation> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromJson(it) } }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun listToJson(list: List<GeofenceLocation>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        /**
         * The place the device is currently inside, or null if outside all places /
         * no usable location. Uses the last known fix regardless of age (a parked head
         * unit rarely gets a fresh fix, but its last position is still where it is).
         */
        fun currentPlace(context: Context, settings: Settings): GeofenceLocation? {
            val places = try { settings.geofenceLocations } catch (e: Exception) { return null }
            if (places.isEmpty()) return null
            val loc = LocationHolder.geofenceFix(context) ?: return null
            return places.firstOrNull { it.contains(loc) }
        }

        /**
         * Resolves the "Location (by area)" appearance: the current place's day/night if
         * inside one, otherwise the user's configured outside-places default.
         */
        fun resolveNight(context: Context, settings: Settings): Boolean =
            currentPlace(context, settings)?.forceNight ?: settings.locationOutsideNight
    }
}
