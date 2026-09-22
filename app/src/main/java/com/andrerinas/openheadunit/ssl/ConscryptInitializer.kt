package com.andrerinas.openheadunit.ssl

import android.os.Build
import com.andrerinas.openheadunit.utils.AppLog
import java.security.Security

object ConscryptInitializer {
    @Volatile private var initialized = false
    @Volatile private var conscryptAvailable = false

    @Synchronized
    fun initialize(): Boolean {
        if (initialized) return conscryptAvailable
        initialized = true

        try {
            val conscrypt = Class.forName("org.conscrypt.Conscrypt")
            val newProviderMethod = conscrypt.getMethod("newProvider")
            val provider = newProviderMethod.invoke(null) as java.security.Provider

            // Insert at position 1 (highest priority)
            val result = Security.insertProviderAt(provider, 1)

            // Check if installation succeeded or if already installed
            conscryptAvailable = result != -1 || Security.getProvider("Conscrypt") != null

            if (conscryptAvailable) {
                AppLog.i("Conscrypt installed as security provider (position: %d)", result)
            }
        } catch (e: ClassNotFoundException) {
            AppLog.e("Conscrypt library not found - TLS 1.2 may not work on Android < 21", e)
            conscryptAvailable = false
        } catch (e: Exception) {
            AppLog.e("Failed to initialize Conscrypt", e)
            conscryptAvailable = false
        }

        return conscryptAvailable
    }

    fun isAvailable(): Boolean = conscryptAvailable

    fun isNeededForTls12(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP

    fun getProviderName(): String? = if (conscryptAvailable) "Conscrypt" else null
}
