package com.andrerinas.openheadunit.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.R
import com.google.android.material.button.MaterialButton

/**
 * Renders the [AppPermissions] registry as a checklist of rows into [container], reused by both
 * the setup wizard permissions step and the Settings > Permissions screen. Each row shows a title,
 * a help line, and either a green check (already granted) or an "Allow" button. The host owns the
 * two activity-result launchers and passes them in so results route back to the host.
 */
class PermissionRowBinder(
    private val activity: Activity,
    private val container: LinearLayout,
    private val normalLauncher: ActivityResultLauncher<Array<String>>,
    private val specialLauncher: ActivityResultLauncher<Intent>,
) {
    // Permissions we have already asked for this session; used to detect a permanent denial
    // (a repeat request would show no dialog), where we deep-link to the app's settings instead.
    private val attempted = mutableSetOf<String>()

    /** Rebuild every row from the registry and its current granted state. */
    fun rebind() {
        val inflater = LayoutInflater.from(container.context)
        container.removeAllViews()
        for (entry in AppPermissions.visible()) {
            val row = inflater.inflate(R.layout.layout_onboarding_permission_item, container, false)
            val title = row.findViewById<TextView>(R.id.perm_title)
            val help = row.findViewById<TextView>(R.id.perm_help)
            val check = row.findViewById<ImageView>(R.id.perm_status_check)
            val button = row.findViewById<MaterialButton>(R.id.perm_status_button)

            val name = container.context.getString(entry.titleRes)
            title.text = if (entry.optional)
                container.context.getString(R.string.perm_optional_format, name) else name
            help.text = container.context.getString(entry.helpRes)

            val granted = entry.isGranted(container.context)
            check.visibility = if (granted) View.VISIBLE else View.GONE
            button.visibility = if (granted) View.GONE else View.VISIBLE
            button.setOnClickListener { onGrantClicked(entry) }

            container.addView(row)
        }
    }

    /** Request every missing normal permission in one system dialog. Special ones stay per-row. */
    fun requestAllMissing() {
        val missing = AppPermissions.missingNormalPermissions(activity)
        if (missing.isNotEmpty()) {
            attempted.addAll(missing)
            normalLauncher.launch(missing.toTypedArray())
        }
    }

    private fun onGrantClicked(entry: AppPermissions.Entry) {
        when (entry.kind) {
            AppPermissions.Kind.NORMAL -> {
                val missing = entry.requestablePermissions().filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) return
                // If we already asked and the system will no longer prompt, send the user to
                // the app's settings page where they can toggle it manually.
                val permanentlyDenied = missing.all {
                    attempted.contains(it) &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                }
                if (permanentlyDenied) {
                    openAppSettings()
                } else {
                    attempted.addAll(missing)
                    normalLauncher.launch(missing.toTypedArray())
                }
            }
            AppPermissions.Kind.OVERLAY -> {
                try {
                    specialLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri()))
                } catch (e: Exception) {
                    try {
                        specialLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    } catch (e2: Exception) {
                        openAppSettings()
                    }
                }
            }
            AppPermissions.Kind.WRITE_SETTINGS -> {
                try {
                    specialLauncher.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri()))
                } catch (e: Exception) {
                    try {
                        specialLauncher.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
                    } catch (e2: Exception) {
                        openAppSettings()
                    }
                }
            }
        }
    }

    private fun openAppSettings() {
        try {
            specialLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
        } catch (e: Exception) {
            try {
                specialLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                ToastUtils.showToast(activity, R.string.cannot_open_settings)
            }
        }
    }

    private fun packageUri(): Uri = Uri.parse("package:${activity.packageName}")
}
