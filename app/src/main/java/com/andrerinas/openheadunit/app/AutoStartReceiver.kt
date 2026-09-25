package com.andrerinas.openheadunit.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.main.MainActivity
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import android.os.UserManager
import android.os.Build

class AutoStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // Use device-protected storage so the BT MACs are readable during locked boot
        val targetMacs = Settings.getAutoStartBtMacs(context)

        if (targetMacs.isEmpty()) return
        
        val isLocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && 
                      !(context.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked
        
        // [FIX] Don't trigger auto-start if we are already connected!
        // This prevents activity restarts if BT reconnects during a session.
        if (!isLocked && com.andrerinas.openheadunit.App.provide(context).commManager.isConnected) {
            AppLog.d("AutoStartReceiver: Already connected to Android Auto. Ignoring BT event.")
            return
        }

        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            AppLog.i("BT Device connected: ${device?.name} (${device?.address})")

            if (device != null && targetMacs.contains(device.address)) {
                AppLog.i("MATCH! Starting AapService via Bluetooth Auto-start...")

                // Start the service to make the app alive. Explicit action so onStartCommand
                // re-arms wireless mode even if the service process was already running from
                // an earlier session (onCreate's init only runs once) — see ACTION_BT_AUTO_START.
                val serviceIntent = Intent(context, AapService::class.java).setAction(AapService.ACTION_BT_AUTO_START)
                var serviceStarted = true
                try {
                    androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    serviceStarted = false
                    AppLog.e("Failed to start AapService from background: ${e.message}")
                }

                // Also attempt to start the UI (might be blocked on Android 10+ without special permission)
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_LAUNCH_SOURCE, "Bluetooth auto-start")
                }
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    AppLog.w("Could not start UI from background (expected on Android 10+): ${e.message}")
                }

                // Android 12+ refuses a background foreground-service start unless the app is
                // exempt, and a blocked startActivity() does not throw, so nothing above would
                // bring the app up. A full-screen notification is still allowed to open the
                // activity, which starts the service itself — as WifiAutoStartReceiver does.
                if (!serviceStarted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showLaunchNotification(context, launchIntent, device.name ?: device.address)
                }
            }
        }
    }

    private fun showLaunchNotification(context: Context, launchIntent: Intent, deviceLabel: String) {
        try {
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, App.bootStartChannel)
                .setSmallIcon(R.drawable.ic_stat_aa)
                .setContentTitle(context.getString(R.string.auto_start_bt_label))
                .setContentText(context.getString(R.string.wifi_autostart_content, deviceLabel))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .build()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(BT_AUTO_START_NOTIFICATION_ID, notification)
            AppLog.i("AutoStartReceiver: Triggered FullScreenIntent notification.")
        } catch (e: Exception) {
            AppLog.e("AutoStartReceiver: Could not post launch notification: ${e.message}")
        }
    }

    private companion object {
        // WifiAutoStartReceiver uses 99; kept apart so one does not replace the other.
        const val BT_AUTO_START_NOTIFICATION_ID = 98
    }
}