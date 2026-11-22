package com.andrerinas.openheadunit.hud

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import java.lang.reflect.Modifier

/**
 * Read-only inventory of the BYD vehicle interfaces present on this head unit, for the diagnostic
 * report. It looks things up and lists them; it never calls a setter, sends a broadcast, binds a
 * service or writes any vehicle value. Every step is isolated so one missing piece cannot hide
 * the rest.
 *
 * Its purpose is to tell, from one report, which navigation/cluster/HUD paths a given firmware
 * could support - in particular on units (such as DiLink 3.0) where none of the validated outputs
 * is available and the setting is therefore hidden.
 */
internal object BydVehicleProbe {
    private const val SDK_PREFIX = "android.hardware.bydauto"
    private const val INSTRUMENT_DEVICE = "$SDK_PREFIX.instrument.BYDAutoInstrumentDevice"
    private const val FEATURE_IDS = "$SDK_PREFIX.BYDAutoFeatureIds"
    private val packageHints = listOf("byd", "someip", "amap", "instrument", "cluster", "hud", "navi")
    private val featureHints = Regex("NAVI|GUIDE|HUD|MUSIC|MEDIA|SONG|SINGER|ARTIST|ALBUM|LYRIC|RADIO|PLAY|TEXT|STRING|THEME", RegexOption.IGNORE_CASE)

    fun appliesTo(): Boolean =
        Build.MANUFACTURER.contains("BYD", ignoreCase = true) || Build.BRAND.contains("BYD", ignoreCase = true)

    fun report(context: Context): List<String> {
        val out = mutableListOf<String>()
        section(out, "Fingerprint") { listOf(Build.FINGERPRINT) }
        section(out, "Validated outputs") {
            listOf(
                "standalone windshield HUD available=${BydStandaloneHudOutput.available(context)}",
                "navigation setting shown=${BydNavigationOutputs.available(context)}"
            )
        }
        section(out, "Navigation packages (looked up by name)") { knownPackages(context) }
        section(out, "Related packages") { relatedPackages(context) }
        section(out, "Displays") { displays(context) }
        section(out, "Dashboard map") { dashboardMap(context) }
        section(out, "BYD permissions declared on this unit") { bydPermissions(context) }
        section(out, "SDK classes in the boot class path") { sdkClasses() }
        section(out, "Instrument device") { instrumentDevice(context) }
        section(out, "Instrument feature IDs") { instrumentFeatureIds() }
        section(out, "Other feature IDs matching navigation/media hints") { otherFeatureIds() }
        return out
    }

    private fun section(out: MutableList<String>, title: String, block: () -> List<String>) {
        out += "[$title]"
        val lines = try {
            block().ifEmpty { listOf("(none)") }
        } catch (t: Throwable) {
            listOf("(unavailable: ${t.javaClass.simpleName}: ${t.message})")
        }
        out += lines.map { "  $it" }
    }

    // Firmware only: reports are posted publicly, and a user's own apps (a "navi" app, say) are
    // neither BYD interfaces nor anyone else's business.
    private fun systemPackages(context: Context): List<PackageInfo> =
        context.packageManager.getInstalledPackages(0)
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0 }

    /**
     * The packages the navigation outputs talk to, asked for one by one. The listing below can
     * come back empty on firmware that hides the installed-app list from ordinary apps (two
     * DiLink 5.1 reports said "(none)" while one of these was answering), and an empty list then
     * reads as "not installed".
     */
    private fun knownPackages(context: Context): List<String> =
        (listOf("com.byd.clusterdebug") + BydClusterOutput.PACKAGES + "com.ts.car.someip.service").map { name ->
            val info = runCatching { context.packageManager.getPackageInfo(name, 0) }.getOrNull()
            if (info == null) "$name: not found"
            else {
                val system = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                @Suppress("DEPRECATION")
                val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                "$name: ${info.versionName ?: "?"} ($code) system=$system"
            }
        }

    /**
     * Every display with its size, so a report shows which dashboard layers this firmware has.
     * Names are printed for the built-in screen and BYD's projection layers only: another display
     * can carry the name a user gave a device.
     */
    private fun displays(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return listOf("(not read below Android 5)")
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
            ?: return listOf("(no display service)")
        val presentation = manager.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .map { it.displayId }.toSet()
        return manager.displays.map { display ->
            val size = android.graphics.Point().also {
                @Suppress("DEPRECATION")
                display.getRealSize(it)
            }
            displayLine(display.displayId, display.name, size.x, size.y, display.displayId in presentation, display.state)
        }
    }

    internal fun displayLine(id: Int, name: String?, width: Int, height: Int, presentation: Boolean, state: Int): String {
        val shown = name?.takeIf { id == android.view.Display.DEFAULT_DISPLAY || displayNameHints.containsMatchIn(it) }
            ?: "(other)"
        val stateName = when (state) {
            android.view.Display.STATE_ON -> "on"
            android.view.Display.STATE_OFF -> "off"
            else -> "state=$state"
        }
        return "$id: $shown ${width}x$height ${if (presentation) "presentation" else "not presentation"} $stateName"
    }

    /** What the dashboard map would do on this firmware right now; it opens nothing. */
    private fun dashboardMap(context: Context): List<String> {
        val settings = com.andrerinas.openheadunit.App.provide(context).settings
        val measured = ClusterMapConfig.measured(Build.FINGERPRINT)
        val out = mutableListOf(
            "enabled=${settings.clusterMapEnabled}",
            "layout measured for this firmware=$measured",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            out += "display it would use=${ClusterMapScreen.findDisplay(context)?.name ?: "none"}"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            out += "usage access (follows the cluster theme)=${DiLink51ClusterMonitor.hasAccess(context)}"
        }
        return out
    }

    private val displayNameHints = Regex("fission|xdja|projection|cluster|hud|built-?in", RegexOption.IGNORE_CASE)

    private fun relatedPackages(context: Context): List<String> =
        systemPackages(context)
            .map { it.packageName to (it.versionName ?: "?") }
            .filter { (name, _) -> packageHints.any { name.contains(it, ignoreCase = true) } }
            .sortedBy { it.first }
            .take(150)
            .map { (name, version) -> "$name $version" }

    @Suppress("DEPRECATION")
    private fun bydPermissions(context: Context): List<String> {
        val pm = context.packageManager
        val result = sortedMapOf<String, String>()
        // Per package rather than one GET_PERMISSIONS listing: on a unit with hundreds of system
        // packages the single call can exceed the binder transaction limit.
        for (name in systemPackages(context).map { it.packageName }) {
            val pkg = runCatching { pm.getPackageInfo(name, PackageManager.GET_PERMISSIONS) }.getOrNull() ?: continue
            for (perm in pkg.permissions.orEmpty()) {
                if (!perm.name.contains("BYD", ignoreCase = true)) continue
                val base = perm.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                val level = when (base) {
                    PermissionInfo.PROTECTION_NORMAL -> "normal"
                    PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
                    PermissionInfo.PROTECTION_SIGNATURE -> "signature"
                    else -> "level=$base"
                }
                val held = context.checkPermission(perm.name, android.os.Process.myPid(), android.os.Process.myUid()) == PackageManager.PERMISSION_GRANTED
                result[perm.name] = "$level, declared by ${pkg.packageName}, held=$held"
            }
        }
        return result.entries.take(200).map { "${it.key}: ${it.value}" }
    }

    @Suppress("DEPRECATION")
    private fun sdkClasses(): List<String> {
        val jars = (System.getenv("BOOTCLASSPATH") ?: "").split(':').filter { it.isNotBlank() }
        val found = sortedSetOf<String>()
        val notes = mutableListOf<String>()
        for (jar in jars) {
            try {
                val entries = dalvik.system.DexFile(jar).entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith(SDK_PREFIX) && !name.contains('$')) found += name
                }
            } catch (t: Throwable) {
                if (jar.contains("byd", ignoreCase = true)) notes += "$jar unreadable: ${t.javaClass.simpleName}"
            }
        }
        return notes + found.take(300).toList() + listOf("(${found.size} classes)")
    }

    private fun instrumentDevice(context: Context): List<String> {
        val cls = Class.forName(INSTRUMENT_DEVICE)
        val lines = mutableListOf<String>()
        // getInstance only returns the SDK's handle object; nothing is sent to the vehicle.
        lines += try {
            val instance = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            "getInstance: ok (${instance?.javaClass?.name})"
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            "getInstance: failed ${cause.javaClass.simpleName}: ${cause.message}"
        }
        lines += cls.methods
            .filter { it.declaringClass == cls && Modifier.isPublic(it.modifiers) }
            .map { m -> "${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}" }
            .sorted()
            .take(250)
        return lines
    }

    private fun instrumentFeatureIds(): List<String> =
        constantsOf(Class.forName("$FEATURE_IDS\$Instrument")).take(500)

    private fun otherFeatureIds(): List<String> {
        val root = Class.forName(FEATURE_IDS)
        val lines = mutableListOf<String>()
        lines += "groups: " + root.declaredClasses.map { it.simpleName }.sorted().joinToString()
        for (group in root.declaredClasses.sortedBy { it.simpleName }) {
            if (group.simpleName == "Instrument") continue
            constantsOf(group).filter { featureHints.containsMatchIn(it) }
                .forEach { lines += "${group.simpleName}.$it" }
        }
        return lines.take(400)
    }

    private fun constantsOf(cls: Class<*>): List<String> =
        cls.fields
            .filter { Modifier.isStatic(it.modifiers) && (it.type == Int::class.javaPrimitiveType || it.type == String::class.java) }
            .map { field -> "${field.name}=${runCatching { field.get(null) }.getOrNull()}" }
            .sorted()
}
