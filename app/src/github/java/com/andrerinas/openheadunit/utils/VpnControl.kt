package com.andrerinas.openheadunit.utils

import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.aap.DummyVpnService

object VpnControl {
    fun startVpn(context: Context) {
        AppLog.i("VpnControl: Starting DummyVpnService (GitHub Build)")
        try {
            context.startService(Intent(context, DummyVpnService::class.java))
        } catch (e: Exception) {
            AppLog.e("VpnControl: Failed to start VPN", e)
        }
    }

    fun stopVpn(context: Context) {
        AppLog.i("VpnControl: Stopping DummyVpnService (GitHub Build)")
        try {
            context.startService(Intent(context, DummyVpnService::class.java).apply { 
                action = DummyVpnService.ACTION_STOP_VPN 
            })
        } catch (e: Exception) {
            AppLog.e("VpnControl: Failed to stop VPN", e)
        }
    }
    
    fun isVpnAvailable(): Boolean = true
}
