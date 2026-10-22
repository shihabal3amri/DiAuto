package com.andrerinas.openheadunit.hud

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.security.MessageDigest

/** Ordinary-app IPC to the real stock receiver. No shell, local socket or permission grant. */
internal class BydStandaloneHudOutput private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("byd_standalone_hud", Context.MODE_PRIVATE)
    private val session = BydStandaloneSession(
        send = { packet ->
            app.sendBroadcast(Intent("byd.hud.NAVIGATION").setComponent(TARGET)
                .putExtra("normal", packet).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
            HudLog.d(TAG, "dispatch uid=${Process.myUid()} bytes=${packet.split(',').size}")
        },
        rememberPendingClear = { pending ->
            check(prefs.edit().putBoolean("pending_clear", pending).commit()) { "Cannot persist HUD cleanup" }
        },
        needsRecovery = prefs.getBoolean("pending_clear", false),
    )

    init {
        HudLog.i(TAG, "Standalone navigation ready uid=${Process.myUid()} helper=none")
        // Retain the journal if dispatch fails; the next scheduled tick retries.
        runCatching { session.clear() }.onFailure { HudLog.w(TAG, "Startup clear will retry", it) }
    }

    private var loggedGuidance = false
    private var updates = 0
    private var lastSummaryMs = 0L
    // The scheduler calls clear() every second while there is no guidance; log only real clears.
    private var guidanceShown = false

    fun update(icon: Int, exit: Int, distanceMeters: Int, road: String) {
        session.update(icon, exit, HudText.distance(distanceMeters), HudText.street(road))
        guidanceShown = true
        updates++
        val now = android.os.SystemClock.elapsedRealtime()
        if (!loggedGuidance) {
            // Once per process: the per-second dispatch line is debug-only and logcat-only.
            loggedGuidance = true
            lastSummaryMs = now
            HudLog.i(TAG, "first guidance sent icon=$icon exit=$exit distance=${distanceMeters}m " +
                    "fingerprint=${Build.FINGERPRINT}")
        } else if (now - lastSummaryMs >= SUMMARY_INTERVAL_MS) {
            // Proves from a report whether the HUD kept receiving guidance, including while the
            // projection was in the background.
            HudLog.i(TAG, "guidance alive: $updates updates in ${(now - lastSummaryMs) / 1000}s, " +
                    "now icon=$icon distance=${distanceMeters}m " +
                    "projectionForeground=${com.andrerinas.openheadunit.aap.AapProjectionActivity.isForeground}")
            updates = 0
            lastSummaryMs = now
        }
    }
    fun clear() {
        if (guidanceShown) HudLog.i(TAG, "guidance cleared")
        guidanceShown = false
        session.clear()
    }

    companion object {
        private const val TAG = "BYD-Standalone-Live"
        private const val SUMMARY_INTERVAL_MS = 60_000L
        private const val VERIFIED_FINGERPRINT =
            "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys"
        private val TARGET = ComponentName("com.byd.clusterdebug", "com.byd.clusterdebug.BroadcastReceiverCAN")
        @Volatile var syntheticHold = false

        fun create(context: Context): BydStandaloneHudOutput? =
            if (available(context)) BydStandaloneHudOutput(context) else null

        /** Enable production and diagnostic packages on BYD firmware carrying the verified receiver. */
        fun available(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 28 || context.packageName !in setOf(
                    "com.andrerinas.headunitrevived", "com.shihab.diplay",
                    "com.andrerinas.headunitrevived.bydhudtest", "com.shihab.diplay.hudtest")) return false
            // The packet format belongs to the ClusterDebug receiver checked below (exact version and
            // signing certificate), not to the system build. Any BYD firmware carrying that same
            // receiver is therefore accepted. Confirmed on the road on a DiLink5.0 / Android 12
            // Sealion 06 (fingerprint BYD-AUTO/DiLink5.0/DiLink5.0:12/SKQ1.230128.001/...), in addition
            // to the originally validated DiLink5.1 / Android 13 build.
            if (Build.FINGERPRINT != VERIFIED_FINGERPRINT && !Build.FINGERPRINT.startsWith("BYD-AUTO/")) return false
            return runCatching {
                val manager = context.packageManager
                val info = manager.getPackageInfo(TARGET.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val receiver = manager.getReceiverInfo(TARGET, 0)
                val signers = info.signingInfo?.apkContentsSigners ?: return false
                info.longVersionCode == 10601004L &&
                    info.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    receiver.enabled && receiver.exported && receiver.permission.isNullOrEmpty() &&
                    signers.size == 1 && MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())
                        .joinToString("") { "%02x".format(it.toInt() and 255) } ==
                        "efe3ca8ada0d10c655c3df9910ad2ebc121a47d9a6358434eb24074309933efc"
            }.getOrDefault(false)
        }
    }
}
