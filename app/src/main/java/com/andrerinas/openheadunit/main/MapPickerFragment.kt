package com.andrerinas.openheadunit.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.location.GeofenceLocation
import com.andrerinas.openheadunit.location.LocationHolder
import com.andrerinas.openheadunit.utils.AppThemeManager
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Full-screen OpenStreetMap picker (Leaflet in a WebView, tiles fetched over the
 * network). Two modes:
 *  - MODE_GEOFENCE: pick center + radius, name it, and configure two independent
 *    capabilities (force a theme with a scope, and/or gate automation), then save/delete.
 *  - MODE_POINT: pick a single fixed coordinate used as the shared sunrise/sunset
 *    reference (issue #647). Name / radius / capability toggles are hidden.
 */
class MapPickerFragment : Fragment() {

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_GEOFENCE_ID = "geofenceId"
        const val MODE_GEOFENCE = "geofence"
        const val MODE_POINT = "point"
    }

    private lateinit var settings: Settings
    private lateinit var webView: WebView
    private var saveButton: Button? = null

    private var mode: String = MODE_GEOFENCE
    private var editingId: String? = null

    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private var currentRadius: Float = GeofenceLocation.DEFAULT_RADIUS_METERS
    private var forceNight: Boolean = true

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) useCurrentLocation() else
                Toast.makeText(requireContext(), R.string.geofence_no_location, Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_map_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = App.provide(requireContext()).settings

        mode = arguments?.getString(ARG_MODE) ?: MODE_GEOFENCE
        editingId = arguments?.getString(ARG_GEOFENCE_ID)

        resolveInitialState()
        bindViews(view)
        maybeShowInternetNotice()
    }

    private fun resolveInitialState() {
        if (mode == MODE_POINT) {
            // Center on the device's current location so the user sees where they are; fall
            // back to the stored fixed point if there is no fix. The fixed sunrise/sunset
            // point is just a coordinate for the calculation, so there is no radius here.
            val fix = LocationHolder.geofenceFix(requireContext())
            if (fix != null) {
                currentLat = fix.latitude
                currentLon = fix.longitude
            } else {
                currentLat = settings.fixedSunriseLatitude
                currentLon = settings.fixedSunriseLongitude
            }
            return
        }
        val existing = editingId?.let { id -> settings.geofenceLocations.firstOrNull { it.id == id } }
        if (existing != null) {
            currentLat = existing.latitude
            currentLon = existing.longitude
            currentRadius = existing.radiusMeters
            forceNight = existing.forceNight
        } else {
            val fix = LocationHolder.bestEffortDeviceFix(requireContext())
            val fallback = settings.lastKnownLocation
            currentLat = fix?.latitude ?: fallback.latitude
            currentLon = fix?.longitude ?: fallback.longitude
        }
    }

    private fun bindViews(view: View) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        webView = view.findViewById(R.id.map_web_view)

        val nameLayout = view.findViewById<TextInputLayout>(R.id.name_layout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.name_input)
        val radiusRow = view.findViewById<View>(R.id.radius_row)
        val radiusLabel = view.findViewById<TextView>(R.id.radius_label)
        val radiusSlider = view.findViewById<Slider>(R.id.radius_slider)
        val darkSwitch = view.findViewById<Switch>(R.id.dark_switch)
        val useGpsButton = view.findViewById<Button>(R.id.use_gps_button)
        val deleteButton = view.findViewById<Button>(R.id.delete_button)
        saveButton = view.findViewById(R.id.save_button)

        val isGeofence = mode == MODE_GEOFENCE
        val geofenceOnly = if (isGeofence) View.VISIBLE else View.GONE
        // Explain what the point picker is for (the sunrise/sunset reference).
        view.findViewById<TextView>(R.id.point_help).visibility =
            if (isGeofence) View.GONE else View.VISIBLE
        nameLayout.visibility = geofenceOnly
        // The radius is a real geofence, only for saved places. The fixed sunrise point
        // is just a coordinate, so no radius is shown there.
        radiusRow.visibility = geofenceOnly
        darkSwitch.visibility = geofenceOnly

        if (isGeofence) {
            editingId?.let { id ->
                settings.geofenceLocations.firstOrNull { it.id == id }?.let { nameInput.setText(it.name) }
            }
            radiusSlider.value = currentRadius.coerceIn(radiusSlider.valueFrom, radiusSlider.valueTo)
            radiusLabel.text = getString(R.string.geofence_radius_summary, currentRadius.toInt())
            radiusSlider.addOnChangeListener { _, value, _ ->
                currentRadius = value
                radiusLabel.text = getString(R.string.geofence_radius_summary, value.toInt())
                callJs("setRadius($value)")
            }
            darkSwitch.isChecked = forceNight
            darkSwitch.setOnCheckedChangeListener { _, checked -> forceNight = checked }

            deleteButton.visibility = if (editingId != null) View.VISIBLE else View.GONE
            deleteButton.setOnClickListener { deleteGeofence() }
        }

        useGpsButton.setOnClickListener { requestOrUseLocation() }
        saveButton?.setOnClickListener { save(nameInput.text?.toString()?.trim().orEmpty()) }
    }

    /** Shows the "internet required" notice once (with a don't-show-again option). */
    private fun maybeShowInternetNotice() {
        if (settings.hideMapInternetNotice) {
            loadMap()
            return
        }
        val ctx = requireContext()
        // Build the dialog content with the dialog's own DayNight theme so the text stays
        // readable in both light and dark mode. It was hardcoded white, which was invisible
        // on the white background in light mode.
        val dialogCtx = android.view.ContextThemeWrapper(ctx, R.style.DarkAlertDialog)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val container = LinearLayout(dialogCtx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val message = TextView(dialogCtx).apply {
            text = getString(R.string.geofence_internet_message)
        }
        val checkBox = CheckBox(dialogCtx).apply {
            text = getString(R.string.dont_show_again)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * density).toInt()
            layoutParams = lp
        }
        container.addView(message)
        container.addView(checkBox)

        MaterialAlertDialogBuilder(ctx, R.style.DarkAlertDialog)
            .setTitle(R.string.geofence_internet_title)
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (checkBox.isChecked) settings.hideMapInternetNotice = true
                loadMap()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> findNavController().navigateUp() }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadMap() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Radius circle only for saved places, not for the single sunrise point.
                val showRadius = if (mode == MODE_GEOFENCE) 1 else 0
                callJs("initMap($currentLat, $currentLon, $currentRadius, $showRadius, 0)")
            }
        }
        webView.addJavascriptInterface(JsBridge(), "Android")
        webView.loadUrl("file:///android_asset/map_picker.html")
    }

    private fun callJs(script: String) {
        if (!::webView.isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null)
        } else {
            @Suppress("DEPRECATION")
            webView.loadUrl("javascript:$script")
        }
    }

    private fun requestOrUseLocation() {
        val granted = PermissionChecker.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PermissionChecker.PERMISSION_GRANTED
        if (granted) useCurrentLocation()
        else requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun useCurrentLocation() {
        val fix = LocationHolder.currentLocation(requireContext(), maxAgeMs = 0)
            ?: LocationHolder.bestEffortDeviceFix(requireContext())
        if (fix == null) {
            Toast.makeText(requireContext(), R.string.geofence_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        currentLat = fix.latitude
        currentLon = fix.longitude
        callJs("moveTo($currentLat, $currentLon)")
    }

    /**
     * Applies a place/coordinate change right away: re-evaluates the running app theme
     * manager (recreates only if the appearance actually changed) and asks the service to
     * re-send the Android Auto night mode. Does NOT rebuild the manager, to avoid a
     * recreate loop.
     */
    private fun applyThemeConfigChange() {
        App.appThemeManager?.forceRefresh()
        requireContext().sendBroadcast(
            Intent(AapService.ACTION_REQUEST_NIGHT_MODE_UPDATE).setPackage(requireContext().packageName)
        )
    }

    private fun save(name: String) {
        if (mode == MODE_POINT) {
            settings.fixedSunriseLatitude = currentLat
            settings.fixedSunriseLongitude = currentLon
            // Saving on the map now really turns on the fixed point, so picking coordinates
            // here no longer reverts to GPS when the settings screen was not saved first.
            settings.useFixedSunriseLocation = true
            applyThemeConfigChange()
            Toast.makeText(requireContext(), R.string.geofence_saved, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val finalName = name.ifBlank { getString(R.string.geofence_unnamed) }
        val list = settings.geofenceLocations.toMutableList()
        val id = editingId
        val updated = GeofenceLocation(
            id = id ?: java.util.UUID.randomUUID().toString(),
            name = finalName,
            latitude = currentLat,
            longitude = currentLon,
            radiusMeters = currentRadius,
            forceNight = forceNight
        )
        val idx = if (id != null) list.indexOfFirst { it.id == id } else -1
        if (idx >= 0) list[idx] = updated else list.add(updated)
        settings.geofenceLocations = list
        applyThemeConfigChange()
        Toast.makeText(requireContext(), R.string.geofence_saved, Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun deleteGeofence() {
        val id = editingId ?: return
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.geofence_delete_title)
            .setMessage(R.string.geofence_delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                settings.geofenceLocations = settings.geofenceLocations.filterNot { it.id == id }
                applyThemeConfigChange()
                findNavController().navigateUp()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("Android")
            webView.destroy()
        }
        super.onDestroyView()
    }

    /** Bridge invoked from map_picker.html when the user taps/moves the pin. */
    private inner class JsBridge {
        @JavascriptInterface
        fun onPointChanged(lat: Double, lon: Double) {
            currentLat = lat
            currentLon = lon
        }
    }
}
