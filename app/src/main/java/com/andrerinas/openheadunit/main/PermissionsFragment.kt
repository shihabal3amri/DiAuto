package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.PermissionRowBinder
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * Settings entry point mirroring the wizard's permissions step, so users who already finished
 * onboarding can review and grant permissions later. Both surfaces share [PermissionRowBinder]
 * and the [com.andrerinas.openheadunit.utils.AppPermissions] registry.
 */
class PermissionsFragment : Fragment() {

    private var binder: PermissionRowBinder? = null

    private val normalLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { binder?.rebind() }
    private val specialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { binder?.rebind() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_permissions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val list = view.findViewById<LinearLayout>(R.id.permissions_container)
        binder = PermissionRowBinder(requireActivity(), list, normalLauncher, specialLauncher)
        view.findViewById<MaterialButton>(R.id.enable_all_button).setOnClickListener {
            binder?.requestAllMissing()
        }
        binder?.rebind()
    }

    override fun onResume() {
        super.onResume()
        // Refresh after returning from a special-permission settings screen.
        binder?.rebind()
    }
}
