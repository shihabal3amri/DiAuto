package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import com.andrerinas.openheadunit.hud.BydNavigationDiagnostics
import java.util.ArrayDeque

/** Small, shareable report. Never exports saved credentials or raw protocol payloads. */
object DiagnosticReport {
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
        // The BYD navigation outputs log through android.util.Log under their own tags, which neither
        // the journal nor the OPENHU filter above ever sees.
        val navigationLines = ArrayDeque<String>()
        runCatching {
            val filter = BydNavigationDiagnostics.LOG_TAGS.map { "$it:V" } + "*:S"
            val process = ProcessBuilder(listOf("logcat", "-d", "-v", "threadtime") + filter)
                .redirectErrorStream(true).start()
            try {
                process.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        DiagnosticReportRedactor.redact(line, ssid, password)?.let {
                            if (navigationLines.size == 300) navigationLines.removeFirst()
                            navigationLines.addLast(it)
                        }
                    }
                }
                process.waitFor()
            } finally { process.destroy() }
        }
        val navigation = runCatching { BydNavigationDiagnostics.describe(context) }
            .getOrElse { listOf("BYD navigation probe failed: ${it.javaClass.simpleName}: ${it.message}") }
        return buildString {
            appendLine("DiAuto ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · diagnostic report")
            appendLine("Android=${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}")
            appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}; build=${Build.DISPLAY}")
            appendLine("Wireless mode=${settings.wifiConnectionMode}; native transport=${settings.nativeApTransport}")
            appendLine("Hotspot auto-enable=${settings.autoEnableHotspot}; hotspot state=${SoftApStateReader.read(context)}")
            appendLine("Hotspot name saved=${settings.hotspotSsid.isNotBlank()}; password saved=${settings.hotspotPassword.isNotEmpty()}")
            appendLine("Logging level=${settings.exporterLogLevel}; source=${settings.logSource}")
            appendLine("Saved credentials, network addresses and protocol payloads are omitted.")
            appendLine("--- Hotspot and network interfaces at report time (read-only) ---")
            HotspotNetworkProbe.report(context).forEach { appendLine(it) }
            appendLine("--- BYD navigation (HUD / instrument cluster) ---")
            navigation.forEach { appendLine(it) }
            if (navigationLines.isEmpty()) appendLine("No BYD navigation output log lines.")
            else navigationLines.forEach { appendLine(it) }
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
