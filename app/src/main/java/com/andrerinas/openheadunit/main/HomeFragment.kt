package com.andrerinas.openheadunit.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapProjectionActivity
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.utils.BluetoothHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** The car's standby screen. Wireless pairing is the primary path; USB remains a fallback. */
class HomeFragment : Fragment() {
    private val app get() = App.provide(requireContext())
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null
    private val bluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) selectPhone()
        else showBluetoothHelp(R.string.bt_permission_denied)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        view.findViewById<View>(R.id.wifi_button).setOnClickListener {
            if (app.commManager.isConnected) openProjection()
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else selectPhone()
        }
        view.findViewById<View>(R.id.usb_button).setOnClickListener {
            if (app.commManager.isConnected) openProjection()
            else findNavController().navigate(R.id.action_homeFragment_to_usbListFragment)
        }
        view.findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        // Going back to the car does not tear down a connection or disable reconnect.
        view.findViewById<View>(R.id.exit_button).setOnClickListener { requireActivity().moveTaskToBack(true) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.commManager.connectionState.collect { state ->
                    val status = when (state) {
                        is CommManager.ConnectionState.Disconnected -> R.string.da_ready
                        is CommManager.ConnectionState.TransportStarted -> R.string.da_connected
                        else -> R.string.da_connecting
                    }
                    view.findViewById<TextView>(R.id.connection_status).setText(status)
                    view.findViewById<MaterialButton>(R.id.wifi_button).setText(
                        if (app.commManager.isConnected) R.string.da_open else R.string.da_connect)
                }
            }
        }
        if (app.settings.autoStartOnBoot || app.settings.autoStartOnScreenOn) {
            ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), AapService::class.java))
        }
    }

    private fun openProjection() {
        startActivity(AapProjectionActivity.intent(requireContext()).apply {
            putExtra(AapProjectionActivity.EXTRA_FOCUS, true)
        })
    }

    private fun selectPhone() {
        val adapter = BluetoothHelper.getBluetoothAdapter(requireContext())
        try {
            if (adapter == null || !adapter.isEnabled) {
                showBluetoothHelp(R.string.da_bluetooth_off)
                return
            }
            val devices = adapter.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
            if (devices.isEmpty()) {
                showBluetoothHelp(R.string.da_no_paired)
                return
            }
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
                .setTitle(R.string.da_connect)
                .setItems(devices.map { it.name ?: getString(R.string.da_device) }.toTypedArray()) { _, index ->
                    val device = devices[index]
                    // The button explicitly selects native wireless AA, including on upgrades
                    // from upstream installations that used the phone helper.
                    app.settings.wifiConnectionMode = 3
                    (activity as? MainActivity)?.beginAutoConnect(
                        "manual native wireless", MainActivity.ConnectionUiMode.PILL)
                    ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), AapService::class.java).apply {
                        action = AapService.ACTION_NATIVE_AA_POKE
                        putExtra(AapService.EXTRA_MAC, device.address)
                    })
                }
                .setNeutralButton(R.string.da_pair) { _, _ -> openBluetoothSettings() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } catch (_: SecurityException) {
            showBluetoothHelp(R.string.bt_permission_denied)
        }
    }

    private fun showBluetoothHelp(message: Int) {
        activeDialog?.dismiss()
        activeDialog = MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.da_pair).setMessage(message)
            .setPositiveButton(R.string.da_system_settings) { _, _ -> openBluetoothSettings() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun openBluetoothSettings() {
        try { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    override fun onPause() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onPause()
    }

    companion object {
        // Retained for existing automation entry points; self projection has no standby control.
        var forceSelfModeLaunch = false
        fun resetAutoStart() { forceSelfModeLaunch = false }
    }
}
