package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.R
import com.google.android.material.appbar.MaterialToolbar

class DriveSettingsFragment : Fragment(R.layout.fragment_drive_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().finish()
        }
        mapOf(
            R.id.da_automation to R.id.autoStartFragment,
            R.id.da_display to R.id.settingsFragment,
            R.id.da_permissions to R.id.permissionsFragment,
            R.id.da_about to R.id.aboutFragment
        ).forEach { (button, destination) ->
            view.findViewById<View>(button).setOnClickListener { findNavController().navigate(destination) }
        }
    }
}
