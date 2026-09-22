package com.andrerinas.openheadunit.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.R

/**
 * Central, declarative registry of the runtime and special permissions the app surfaces to the
 * user (the setup wizard permissions step and the Settings > Permissions screen read from here).
 *
 * To support a future permission, add a single [Entry] to [ALL] plus its two strings (title +
 * help). The UI renders it automatically, with the correct SDK gating and grant flow. No wizard
 * or layout code needs to change.
 */
object AppPermissions {

    enum class Kind {
        /** Standard dangerous permission, granted via a system dialog. */
        NORMAL,
        /** "Display over other apps" special access, granted via a settings screen. */
        OVERLAY,
        /** "Modify system settings" special access, granted via a settings screen. */
        WRITE_SETTINGS,
    }

    data class Entry(
        val id: String,
        val titleRes: Int,
        val helpRes: Int,
        val kind: Kind,
        /** Manifest permissions covered by this row (NORMAL only). */
        val permissions: List<String> = emptyList(),
        /** Lowest SDK where this row is relevant. */
        val minSdk: Int = 1,
        /** Highest SDK where this row is relevant (e.g. legacy storage tops out at API 29). */
        val maxSdk: Int = Int.MAX_VALUE,
        /** Recommended rather than required; shown with an "(optional)" hint. */
        val optional: Boolean = false,
    ) {
        /** True when this row is relevant on the current device. */
        fun applies(): Boolean = Build.VERSION.SDK_INT in minSdk..maxSdk

        /** Manifest permissions that actually exist to request on this API level. */
        fun requestablePermissions(): List<String> =
            permissions.filter { Build.VERSION.SDK_INT >= permissionMinSdk(it) }

        fun isGranted(context: Context): Boolean = when (kind) {
            Kind.NORMAL -> requestablePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            Kind.OVERLAY -> isOverlayGranted(context)
            Kind.WRITE_SETTINGS -> isWriteSettingsGranted(context)
        }
    }

    val ALL: List<Entry> = listOf(
        Entry(
            id = "mic",
            titleRes = R.string.perm_mic_title,
            helpRes = R.string.perm_mic_help,
            kind = Kind.NORMAL,
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
        ),
        Entry(
            id = "location",
            titleRes = R.string.perm_location_title,
            helpRes = R.string.perm_location_help,
            kind = Kind.NORMAL,
            permissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        ),
        Entry(
            id = "nearby",
            titleRes = R.string.perm_nearby_title,
            helpRes = R.string.perm_nearby_help,
            kind = Kind.NORMAL,
            // BLUETOOTH_* are API 31+, NEARBY_WIFI_DEVICES is API 33+; requestablePermissions()
            // filters each one out on lower levels.
            permissions = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ),
            minSdk = Build.VERSION_CODES.S,
        ),
        Entry(
            id = "notifications",
            titleRes = R.string.perm_notifications_title,
            helpRes = R.string.perm_notifications_help,
            kind = Kind.NORMAL,
            permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        Entry(
            id = "overlay",
            titleRes = R.string.perm_overlay_title,
            helpRes = R.string.perm_overlay_help,
            kind = Kind.OVERLAY,
        ),
        Entry(
            id = "write_settings",
            titleRes = R.string.perm_write_settings_title,
            helpRes = R.string.perm_write_settings_help,
            kind = Kind.WRITE_SETTINGS,
            optional = true,
        ),
        Entry(
            id = "storage",
            titleRes = R.string.perm_storage_title,
            helpRes = R.string.perm_storage_help,
            kind = Kind.NORMAL,
            permissions = listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            // Legacy storage is a no-op under scoped storage (API 30+); only show it where it matters.
            maxSdk = Build.VERSION_CODES.Q,
            optional = true,
        ),
    )

    /** Registry entries relevant on the current device. */
    fun visible(): List<Entry> = ALL.filter { it.applies() }

    /**
     * Every not-yet-granted normal permission across the visible entries, de-duplicated, so they
     * can be requested together in a single system dialog ("Enable all").
     */
    fun missingNormalPermissions(context: Context): List<String> =
        visible().filter { it.kind == Kind.NORMAL }
            .flatMap { it.requestablePermissions() }
            .distinct()
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    /** Count of visible entries already granted (for the wizard summary). */
    fun grantedCount(context: Context): Int = visible().count { it.isGranted(context) }

    /** Robust check for "Display over other apps" permission. */
    fun isOverlayGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.canDrawOverlays(context)) return true

        return isActuallyGranted(context, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)
    }

    /** Robust check for "Modify system settings" permission. */
    fun isWriteSettingsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Settings.System.canWrite(context)) return true

        return isActuallyGranted(context, AppOpsManager.OPSTR_WRITE_SETTINGS)
    }

    /** Fallback for some devices (especially headunits) where Settings#xx is unreliable */
    private fun isActuallyGranted(context: Context, opStr: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return true

        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                opStr,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** The API level at which a given manifest permission became requestable. */
    private fun permissionMinSdk(permission: String): Int = when (permission) {
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE -> Build.VERSION_CODES.S
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.POST_NOTIFICATIONS -> Build.VERSION_CODES.TIRAMISU
        else -> 1
    }
}
