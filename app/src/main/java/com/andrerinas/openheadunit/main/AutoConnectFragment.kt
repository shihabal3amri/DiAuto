package com.andrerinas.openheadunit.main

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.main.settings.AutoConnectAdapter
import com.andrerinas.openheadunit.main.settings.AutoConnectMethod
import com.andrerinas.openheadunit.main.settings.AutoConnectTouchCallback
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class AutoConnectFragment : Fragment() {

    private lateinit var settings: Settings
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AutoConnectAdapter
    private var saveButton: MaterialButton? = null

    // Snapshot of initial state for change detection
    private lateinit var initialOrder: List<String>
    private lateinit var initialEnabled: Map<String, Boolean>

    // Methods hidden because they do not apply to the chosen connection (kept in the saved
    // order so they are not lost when switching connection type later).
    private var hiddenMethodIds: List<String> = emptyList()

    private var hasChanges = false
    private val SAVE_ITEM_ID = 1001

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_auto_connect, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settings = App.provide(requireContext()).settings

        // Snapshot initial state
        initialOrder = settings.autoConnectPriorityOrder.toList()
        initialEnabled = mapOf(
            Settings.AUTO_CONNECT_LAST_SESSION to settings.autoConnectLastSession,
            Settings.AUTO_CONNECT_SELF_MODE to settings.autoStartSelfMode,
            Settings.AUTO_CONNECT_SINGLE_USB to settings.autoConnectSingleUsbDevice
        )

        // Build method list in priority order
        val methods = initialOrder.mapNotNull { id ->
            methodDefinition(id)?.let { (nameRes, descRes) ->
                AutoConnectMethod(id, nameRes, descRes, initialEnabled[id] ?: false)
            }
        }.toMutableList()

        // Single-USB auto-connect only applies to USB connections; hide it otherwise.
        hiddenMethodIds = if (!settings.showsUsb())
            listOf(Settings.AUTO_CONNECT_SINGLE_USB) else emptyList()
        val visibleMethods = methods.filterNot { it.id in hiddenMethodIds }.toMutableList()
        adapter = AutoConnectAdapter(visibleMethods) { checkChanges() }

        val touchCallback = AutoConnectTouchCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(touchCallback)
        adapter.itemTouchHelper = itemTouchHelper

        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(recyclerView)

        setupToolbar()

        // Intercept system back button
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun methodDefinition(id: String): Pair<Int, Int>? {
        return when (id) {
            Settings.AUTO_CONNECT_LAST_SESSION -> R.string.auto_connect_last_session to R.string.auto_connect_last_session_description
            Settings.AUTO_CONNECT_SELF_MODE -> R.string.auto_start_self_mode to R.string.auto_start_self_mode_description
            Settings.AUTO_CONNECT_SINGLE_USB -> R.string.auto_connect_single_usb to R.string.auto_connect_single_usb_description
            else -> null
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            handleBackPress()
        }

        val saveItem = toolbar.menu.add(0, SAVE_ITEM_ID, 0, getString(R.string.save))
        saveItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        saveItem.setActionView(R.layout.layout_save_button)

        saveButton = saveItem.actionView?.findViewById(R.id.save_button_widget)
        saveButton?.setOnClickListener {
            saveSettings()
        }

        updateSaveButtonState()
    }

    private fun handleBackPress() {
        if (hasChanges) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.unsaved_changes)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.discard) { _, _ ->
                    navigateBack()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            navigateBack()
        }
    }

    private fun navigateBack() {
        try {
            val navController = findNavController()
            if (!navController.navigateUp()) {
                requireActivity().finish()
            }
        } catch (e: Exception) {
            requireActivity().finish()
        }
    }

    private fun checkChanges() {
        val currentOrder = adapter.getOrderedIds()
        val currentEnabled = adapter.getEnabledStates()

        // Compare against the initial state minus the hidden (non-applicable) methods.
        hasChanges = currentOrder != initialOrder.filterNot { it in hiddenMethodIds } ||
            currentEnabled != initialEnabled.filterKeys { it !in hiddenMethodIds }
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        saveButton?.isEnabled = hasChanges
        saveButton?.text = getString(R.string.save)
    }

    private fun saveSettings() {
        val orderedIds = adapter.getOrderedIds()
        val enabledStates = adapter.getEnabledStates()

        // Persist order (append hidden methods so they are not lost from the priority list)
        settings.autoConnectPriorityOrder = orderedIds + hiddenMethodIds

        // Persist individual toggles
        enabledStates[Settings.AUTO_CONNECT_LAST_SESSION]?.let { settings.autoConnectLastSession = it }
        enabledStates[Settings.AUTO_CONNECT_SELF_MODE]?.let { settings.autoStartSelfMode = it }
        enabledStates[Settings.AUTO_CONNECT_SINGLE_USB]?.let { settings.autoConnectSingleUsbDevice = it }

        // Update snapshot
        initialOrder = orderedIds.toList()
        initialEnabled = enabledStates.toMap()

        hasChanges = false
        updateSaveButtonState()

        Toast.makeText(context, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
    }
}
