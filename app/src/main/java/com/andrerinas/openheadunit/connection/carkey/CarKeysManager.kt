package com.andrerinas.openheadunit.connection.carkey

import android.content.Context
import com.andrerinas.openheadunit.utils.AppLog

class CarKeysManager {

    private val all: Array<CarKeyReceiver> = CarKeyReceiver.newDefaultReceivers()
    private val registered: MutableCollection<CarKeyReceiver> = ArrayList()

    fun registerReceivers(context: Context) {
        if (registered.isNotEmpty())
            return

        try {
            for (r in all) {
                if (!r.isSupported)
                    continue

                r.register(context)
                registered.add(r)
            }

            AppLog.d("AapService: ${registered.size} CarKeyReceivers registered")
        } catch (e: Exception) {
            AppLog.e("AapService: Failed to register CarKeyReceivers", e)
        }
    }

    fun unregisterReceivers() {
        if (registered.isEmpty())
            return

        try {
            registered.forEach { it.unregister() }
        } catch (e: Exception) {
            AppLog.e("AapService: Failed to unregister CarKeyReceiver", e)
        } finally {
            registered.clear()
        }
    }

    fun isSUNeeded(): Boolean {
        return registered.any { it.isSUNeeded }
    }
}
