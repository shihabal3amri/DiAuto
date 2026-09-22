package com.andrerinas.openheadunit.aap

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.andrerinas.openheadunit.utils.AppLog

class DummyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_VPN) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route all traffic to VPN to become the "active network"
                .setSession("Open Headunit Offline Mode")
            
            vpnInterface = builder.establish()
            AppLog.i("Dummy VPN started for offline Self Mode")
        } catch (e: Exception) {
            AppLog.e("Failed to start Dummy VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopSelf()
            AppLog.i("Dummy VPN stopped")
        } catch (e: Exception) {
            AppLog.e("Error closing Dummy VPN", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    companion object {
        const val ACTION_STOP_VPN = "com.andrerinas.openheadunit.ACTION_STOP_VPN"
    }
}
