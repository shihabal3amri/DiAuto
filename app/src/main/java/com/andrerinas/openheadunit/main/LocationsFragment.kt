package com.andrerinas.openheadunit.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.location.GeofenceLocation
import com.andrerinas.openheadunit.main.settings.SettingItem
import com.andrerinas.openheadunit.main.settings.SettingsAdapter
import com.andrerinas.openheadunit.utils.AppThemeManager
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.appbar.MaterialToolbar

/**
 * Manages the saved places used by the "Location (by area)" theme mode. Each place sets
 * a light or dark appearance while the device is inside it. CRUD persists immediately and
 * re-applies the theme engines.
 */
class LocationsFragment : Fragment() {

    private lateinit var settings: Settings
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SettingsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_locations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = App.provide(requireContext()).settings

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        adapter = SettingsAdapter()
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        updateList()
    }

    private fun updateList() {
        val items = mutableListOf<SettingItem>()
        items.add(SettingItem.CategoryHeader("places", R.string.location_manage_places))
        items.add(SettingItem.InfoBanner("placesHint", R.string.location_places_desc))

        for (loc in settings.geofenceLocations) {
            items.add(SettingItem.SettingEntry(
                stableId = "geo_${loc.id}",
                nameResId = R.string.location_manage_places,
                nameOverride = loc.name.ifBlank { getString(R.string.geofence_unnamed) },
                value = summary(loc),
                onClick = { openEditor(loc.id) }
            ))
        }

        items.add(SettingItem.ActionButton(
            stableId = "geofenceAdd",
            textResId = R.string.geofence_add,
            onClick = { openEditor(null) }
        ))

        GeofenceLocation.currentPlace(requireContext(), settings)?.let { g ->
            val state = getString(if (g.forceNight) R.string.geofence_mode_dark else R.string.geofence_mode_light)
            val place = g.name.ifBlank { getString(R.string.geofence_unnamed) }
            // Live status shown as informational text, not a tappable option.
            items.add(SettingItem.InfoBanner(
                stableId = "liveStatus",
                textResId = R.string.geofence_current_status_label,
                text = getString(R.string.geofence_current_status_label) + ": " +
                    getString(R.string.geofence_current_status, state, place)
            ))
        }

        adapter.submitList(items)
    }

    private fun summary(loc: GeofenceLocation): String {
        val radius = getString(R.string.geofence_radius_summary, loc.radiusMeters.toInt())
        val mode = getString(if (loc.forceNight) R.string.geofence_mode_dark else R.string.geofence_mode_light)
        return "$radius · $mode"
    }

    private fun openEditor(geofenceId: String?) {
        findNavController().navigate(
            R.id.action_locationsFragment_to_mapPickerFragment,
            bundleOf(
                MapPickerFragment.ARG_MODE to MapPickerFragment.MODE_GEOFENCE,
                MapPickerFragment.ARG_GEOFENCE_ID to geofenceId
            )
        )
    }
}
