package com.andrerinas.openheadunit.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper to request the runtime location permission required for configuring a Wi‑Fi hotspot
 * on Android 10 (API 29) and newer.
 */
object PermissionHelper {
    private const val REQUEST_LOCATION = 1001

    /**
     * Returns true if the app already has ACCESS_FINE_LOCATION permission.
     * If not, it launches the Android permission dialog (must be called from an Activity).
     */
    fun ensureLocationPermission(activity: Activity): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            return true
        }
        // Request the permission – the caller should handle the result in onRequestPermissionsResult
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION
        )
        return false
    }
}
