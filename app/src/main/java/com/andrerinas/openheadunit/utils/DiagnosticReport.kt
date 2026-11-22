package com.andrerinas.openheadunit.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.ArrayDeque

/** Small, shareable report. Never exports saved credentials or raw protocol payloads. */
object DiagnosticReport {
    /**
     * Which screen the "Bluetooth settings" buttons reach. On one DiLink 3.0 unit that screen only
     * answered "IVI system doesn't support this operation" (#69), and the report did not say which
     * app had been asked, or whether the car's own Bluetooth app could be opened instead.
     */
    private fun bluetoothSettingsTarget(context: Context): String = runCatching {
        val packages = context.packageManager
        val standard = packages.resolveActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), 0)
            ?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "nothing"
        val carApp = packages.getLaunchIntentForPackage("com.byd.btsetting")?.component?.flattenToShortString()
        "$standard; com.byd.btsetting can be opened=${carApp ?: "no"}"
    }.getOrElse { "unreadable (${it.javaClass.simpleName})" }

    fun build(context: Context): String {
        val settings = Settings(context)
        val ssid = settings.hotspotSsid
        val password = settings.hotspotPassword
        val lines = ArrayDeque<String>()
        fun accept(line: String) {
            DiagnosticReportRedactor.redact(line, ssid, password)?.let {
                if (lines.size == 6000) lines.removeFirst()
                lines.addLast(it)
            }
        }
        val history = DiagnosticJournal.snapshot()
        val source = if (settings.logSource == Settings.LogSource.APPLOG_FILE)
            AppLog.currentLogFile ?: AppLog.lastLogFile else null
        if (history.isNotEmpty()) {
            history.forEach(::accept)
        } else if (source?.isFile == true) {
            source.useLines { it.forEach(::accept) }
        } else {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "OPENHU:V", "*:S")
                .redirectErrorStream(true).start()
            try {
                process.inputStream.bufferedReader().useLines { it.forEach(::accept) }
                check(process.waitFor() == 0) { "Could not read the app logs" }
            } finally { process.destroy() }
        }
        return buildString {
            appendLine("DiAuto ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · diagnostic report")
            appendLine("Android=${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}")
            appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}; build=${Build.DISPLAY}")
            appendLine("Wireless mode=${settings.wifiConnectionMode}; native transport=${settings.nativeApTransport}")
            appendLine("Hotspot auto-enable=${settings.autoEnableHotspot}; hotspot state=${SoftApStateReader.read(context)}")
            appendLine("Hotspot name saved=${settings.hotspotSsid.isNotBlank()}; password saved=${settings.hotspotPassword.isNotEmpty()}")
            appendLine("Logging level=${settings.exporterLogLevel}; source=${settings.logSource}")
            appendLine("Bluetooth settings open with: ${bluetoothSettingsTarget(context)}")
            appendLine("Saved credentials, network addresses and protocol payloads are omitted.")
            appendLine("--- Hotspot and network interfaces at report time (read-only) ---")
            HotspotNetworkProbe.report(context).forEach { appendLine(it) }
            appendLine("--- Recent app logs (up to 6000 lines) ---")
            if (lines.isEmpty()) appendLine("No recent app logs available. Reproduce the issue and save a new report.")
            lines.forEach { appendLine(it) }
            if (com.andrerinas.openheadunit.hud.BydVehicleProbe.appliesTo()) {
                appendLine("--- BYD vehicle interfaces (read-only inventory) ---")
                com.andrerinas.openheadunit.hud.BydVehicleProbe.report(context).forEach { appendLine(it) }
            }
        }
    }
}
