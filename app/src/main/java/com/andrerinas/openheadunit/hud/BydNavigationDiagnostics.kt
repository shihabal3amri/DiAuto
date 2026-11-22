package com.andrerinas.openheadunit.hud

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.andrerinas.openheadunit.utils.Settings
import java.security.MessageDigest

/**
 * Explains, in the diagnostic report, which BYD navigation output this head unit would use and
 * why the windshield path is or is not enabled. The windshield output is gated on one exact
 * firmware fingerprint and one exact stock receiver; on any other car the only way to tell why
 * nothing appears is to compare these values with the verified ones.
 */
object BydNavigationDiagnostics {

    private const val VERIFIED_FINGERPRINT =
        "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys"
    private const val VERIFIED_RECEIVER_VERSION = 10601004L
    private const val VERIFIED_RECEIVER_CERT =
        "efe3ca8ada0d10c655c3df9910ad2ebc121a47d9a6358434eb24074309933efc"
    private val RECEIVER = ComponentName("com.byd.clusterdebug", "com.byd.clusterdebug.BroadcastReceiverCAN")

    /** Log tags the navigation outputs write with android.util.Log, outside the app's own log. */
    val LOG_TAGS = listOf(
        "DiAuto-BYD", "DiAuto-BYD-HUD", "BYD-Standalone-Live", "BYD-Factory-Navi", "BYD-CarServer-Navi"
    )

    fun describe(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return listOf("BYD navigation outputs need Android 9 or newer (API=${Build.VERSION.SDK_INT})")
        }
        val pm = context.packageManager
        val lines = mutableListOf<String>()
        val settings = Settings(context)
        lines += "Navigation output enabled in settings=${settings.bydNavigationEnabled}; " +
            "any output available=${BydNavigationOutputs.available(context)}"
        lines += "Firmware fingerprint=${Build.FINGERPRINT}"
        lines += "Fingerprint matches verified windshield firmware=${Build.FINGERPRINT == VERIFIED_FINGERPRINT}"

        val receiver = runCatching {
            val info = pm.getPackageInfo(RECEIVER.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val component = runCatching { pm.getReceiverInfo(RECEIVER, 0) }.getOrNull()
            val signers = info.signingInfo?.apkContentsSigners
            val cert = signers?.singleOrNull()?.let { signer ->
                MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            }
            val system = info.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: false
            "installed version=${info.longVersionCode} (verified $VERIFIED_RECEIVER_VERSION) system=$system " +
                "certMatches=${cert == VERIFIED_RECEIVER_CERT} signers=${signers?.size ?: 0} " +
                "receiver=" + (component?.let {
                    "enabled=${it.enabled} exported=${it.exported} permission=${it.permission ?: "none"}"
                } ?: "missing")
        }.getOrElse { "not installed or not visible" }
        lines += "Windshield receiver ${RECEIVER.packageName}: $receiver"
        lines += "Windshield output (standalone) available=${BydStandaloneHudOutput.available(context)}"
        lines += "Instrument-cluster service com.byd.amapservice: ${packageState(pm, "com.byd.amapservice")}"
        lines += "SOME-IP HUD service com.ts.car.someip.service: ${packageState(pm, "com.ts.car.someip.service")}"
        lines += "Output that will be used: " + when {
            BydStandaloneHudOutput.available(context) -> "windshield (standalone ClusterDebug broadcast)"
            !BydNavigationOutputs.available(context) -> "none - this head unit exposes no known navigation service"
            else -> listOfNotNull(
                "instrument cluster (amapservice)".takeIf { installed(pm, "com.byd.amapservice") },
                "HUD via SOME-IP (unverified)".takeIf { installed(pm, "com.ts.car.someip.service") },
            ).joinToString(" + ")
        }
        return lines
    }

    private fun installed(pm: PackageManager, pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    private fun packageState(pm: PackageManager, pkg: String): String = runCatching {
        val info = pm.getPackageInfo(pkg, 0)
        "installed version=${info.versionName} (${info.longVersionCode})"
    }.getOrElse { "not installed or not visible" }
}
