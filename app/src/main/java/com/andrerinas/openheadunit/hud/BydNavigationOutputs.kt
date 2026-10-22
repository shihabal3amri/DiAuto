package com.andrerinas.openheadunit.hud

import android.content.Context
import android.os.Build
import com.andrerinas.openheadunit.utils.Settings
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object BydNavigationOutputs {
    /** Recover a journaled interrupted output when the app opens, even before a phone reconnects. */
    fun onAppOpened(context: Context) { if (BydStandaloneHudOutput.available(context)) start(context) }
    fun setDiagnosticHold(hold: Boolean) { BydStandaloneHudOutput.syntheticHold = hold }
    private val latest = LatestNavigationOutput()
    @Volatile private var active = false
    // While a debug synthetic sequence runs, the scheduled outputs must not overwrite it.
    @Volatile internal var syntheticHold = false
    private var initialized = false

    fun available(context: Context): Boolean = Build.VERSION.SDK_INT >= 28 &&
        (BydStandaloneHudOutput.available(context) || clusterPackage(context) != null || installed(context, "com.ts.car.someip.service"))

    private fun clusterPackage(context: Context): String? = BydClusterOutput.PACKAGES.firstOrNull { installed(context, it) }

    private fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    @Synchronized fun start(context: Context) {
        HudLog.i("DiAuto-BYD", "start enabled=${Settings(context).bydNavigationEnabled} cluster=${clusterPackage(context)} hud=${installed(context, "com.ts.car.someip.service")}")
        active = true
        if (initialized || !available(context)) return
        initialized = true
        val app = context.applicationContext
        if (BydStandaloneHudOutput.available(app)) {
            var standalone: BydStandaloneHudOutput? = null
            schedule("diauto-standalone-navi", app, repeatNs = 1_000_000_000L) { value ->
                val output = standalone ?: BydStandaloneHudOutput.create(app)?.also { standalone = it }
                if (value == null) output?.clear()
                else output?.update(value.clusterIcon, value.roundaboutExit, value.distanceMeters, value.road)
            }
            return // This firmware has one validated windshield transport.
        }
        clusterPackage(app)?.let { target ->
            val cluster = BydClusterOutput(app, target)
            schedule("diauto-cluster", app, cluster::update)
        }
        if (installed(app, "com.ts.car.someip.service")) {
            var hudInitialized = false
            schedule("diauto-hud", app) { value ->
                if (value != null && !hudInitialized) {
                    BydHudBridge.initialize(app)
                    hudInitialized = true
                }
                if (hudInitialized) BydHudBridge.update(value)
            }
        }
    }

    @Synchronized internal fun currentForDiagnostic(context: Context): BydGuidance? =
        if (context.packageName.endsWith(".bydhudtest") && active && Settings(context).bydNavigationEnabled) latest.current() else null

    @Synchronized internal fun update(value: BydGuidance?) { if (active) latest.update(value) }
    @Synchronized fun stop() { active = false; latest.update(null) }

    private fun schedule(name: String, context: Context, send: (BydGuidance?) -> Unit) =
        schedule(name, context, 5_000_000_000L, send)

    private fun schedule(name: String, context: Context, repeatNs: Long, send: (BydGuidance?) -> Unit) {
        var previous: BydGuidance? = null
        var lastSendNs = 0L
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, name).apply { isDaemon = true } }
            .scheduleWithFixedDelay({
                try {
                    if (syntheticHold || BydStandaloneHudOutput.syntheticHold) return@scheduleWithFixedDelay
                    val value = if (active && Settings(context).bydNavigationEnabled) latest.current() else null
                    val now = System.nanoTime()
                    if (value != previous || now - lastSendNs >= repeatNs) {
                        send(value)
                        previous = value
                        lastSendNs = now
                    }
                } catch (error: Exception) { HudLog.w("DiAuto-BYD", "$name output failed", error) }
            }, 0, 500, TimeUnit.MILLISECONDS)
    }
}
