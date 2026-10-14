package com.andrerinas.openheadunit.cluster

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * Which BYD cluster projection display shows the Android Auto map, and where on it.
 *
 * BYD exposes the cluster's projection area as public presentation displays owned by
 * com.byd.containerservice. The geometry below was measured by DiPlay against the stock full-map
 * and right-hand side-card layers; it is shared here unchanged.
 */
internal object ClusterLayout {
    /** DiLink 5.1, Android 13: the measured three-layer 1920x720 cluster (Han L). */
    const val DILINK51_FINGERPRINT = "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys"
    // Leopard 5 Ultra 2025, system 34.1.23.2604176.1: the same three 1920x720 fission layers (DiPlay #301).
    const val LEOPARD5_FINGERPRINT = "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260705.021352:user/release-keys"
    private val FINGERPRINTS = setOf(DILINK51_FINGERPRINT, LEOPARD5_FINGERPRINT)
    const val BASE = "fission_bg_XDJAScreenProjection"
    const val FULL = "shared_${BASE}_0"
    const val SIDE = "shared_${BASE}_1"

    /** The cluster theme the driver picked on the car: where its map window is. */
    enum class Theme { SCENARIO, MAP, SIMPLE }

    /** A viewport on the 1920x720 panel. Outside it the stock instruments stay visible. */
    data class Plan(val left: Int, val top: Int, val width: Int, val height: Int, val fullMap: Boolean)

    fun supported(fingerprint: String = Build.FINGERPRINT): Boolean = fingerprint in FINGERPRINTS

    fun theme(value: Int): Theme = Theme.values().getOrElse(value) { Theme.SCENARIO }

    /** Simple keeps a dark background on this firmware; Scenario and Map are light. */
    fun dark(theme: Theme): Boolean = theme == Theme.SIMPLE

    fun displayName(names: List<String>, fingerprint: String, theme: Theme): String? {
        if (supported(fingerprint)) {
            // Never substitute the full layer for a missing side layer: that hides Scenario/Simple.
            return (if (theme == Theme.MAP) FULL else SIDE).takeIf { it in names }
        }
        // DiLink 5.0 and unmeasured firmware: DiPlay's original selection order.
        return names.firstOrNull { it == BASE }
            ?: names.firstOrNull { it.contains(BASE) && it.endsWith("_0") }
            ?: names.firstOrNull { it.contains(BASE) }
    }

    fun plan(width: Int, height: Int, theme: Theme, fingerprint: String = Build.FINGERPRINT): Plan? {
        if (!supported(fingerprint)) return null
        // The measured side-map bounds are x=1320..1920 on the 1920x720 cluster.
        if (width != 1920 || height != 720) return null
        // Map protects speed/status at the top and gear/range at the bottom.
        return if (theme == Theme.MAP) Plan(0, 144, width, 480, true) else Plan(1320, 0, 600, height, false)
    }

    fun findDisplay(context: Context, theme: Theme): Display? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val displays = context.getSystemService(DisplayManager::class.java)
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION) ?: return null
        val name = displayName(displays.map { it.name }, Build.FINGERPRINT, theme) ?: return null
        val display = displays.firstOrNull { it.name == name } ?: return null
        if (supported()) {
            val size = sizeOf(display)
            if (plan(size.x, size.y, theme) == null) return null
        }
        return display
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun sizeOf(display: Display): Point = Point().also {
        @Suppress("DEPRECATION")
        display.getRealSize(it)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun describeDisplays(context: Context): String =
        context.getSystemService(DisplayManager::class.java)?.displays
            ?.joinToString {
                val size = sizeOf(it)
                "${it.displayId}:${it.name} ${size.x}x${size.y} flags=${it.flags} valid=${it.isValid}"
            }.orEmpty()
}
