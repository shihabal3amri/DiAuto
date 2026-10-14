package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
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
        return buildString {
            appendLine("DiAuto ${context.packageManager.getPackageInfo(context.packageName, 0).versionName} · diagnostic report")
            appendLine("Android=${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}")
            appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}; build=${Build.DISPLAY}")
            appendLine("Wireless mode=${settings.wifiConnectionMode}; native transport=${settings.nativeApTransport}")
            appendLine("Hotspot auto-enable=${settings.autoEnableHotspot}; hotspot state=${SoftApStateReader.read(context)}")
            appendLine("Hotspot name saved=${settings.hotspotSsid.isNotBlank()}; password saved=${settings.hotspotPassword.isNotEmpty()}")
            appendLine("Logging level=${settings.exporterLogLevel}; source=${settings.logSource}")
            runCatching { appendLine(com.andrerinas.openheadunit.cluster.ClusterMapStream.diagnosticLine(context, settings)) }
            appendLine("Saved credentials, network addresses and protocol payloads are omitted.")
            appendLine("--- Recent app logs (up to 6000 lines) ---")
            if (lines.isEmpty()) appendLine("No recent app logs available. Reproduce the issue and save a new report.")
            lines.forEach { appendLine(it) }
        }
    }
}
