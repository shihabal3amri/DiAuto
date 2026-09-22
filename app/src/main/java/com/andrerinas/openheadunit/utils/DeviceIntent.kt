package com.andrerinas.openheadunit.utils

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class DeviceIntent(private val intent: Intent?) {
    val device: UsbDevice?
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}