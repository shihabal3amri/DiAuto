package com.andrerinas.openheadunit.hud

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.protocol.messages.ClusterDisplayServices
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.AppPermissions

/**
 * Shows the Android Auto cluster stream on BYD's instrument-cluster projection display.
 *
 * BYD exposes that area as a public presentation display (owned by com.byd.containerservice on
 * DiLink 5, com.xdja.containerservice on DiLink 3/4); the dashboard shows it while its own navi
 * mode is Small or Full screen, which only the driver can switch. Display names and sizes are the
 * ones DiPlay measured on those firmwares; anything else is left alone rather than guessed.
 *
 * The decoded frame is the whole codec frame; [ClusterMapConfig.cropMatrix] shows only the part
 * the phone drew, so the 8:3 picture is not squashed by the frame's 16:9 shape.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class ClusterMapScreen(
    context: Context,
    display: Display,
    private val profile: ClusterMapConfig.Profile,
) : Presentation(context, display) {

    private var turnCard: ClusterTurnCardView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val guidanceTick = object : Runnable {
        override fun run() {
            turnCard?.setGuidance(DashboardGuidance.current())
            handler.postDelayed(this, GUIDANCE_TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = App.provide(context).settings
        val mapWindow = ClusterDisplayServices.window(context, settings)
        this.mapWindow = mapWindow
        val root = FrameLayout(context)
        // The Map theme shows its whole layer, so the map fills it; Scenario and Simple show a side
        // window, and Scenario shows the rest of this layer too, so only that window is drawn.
        val sideWindow = mapWindow?.takeIf { !it.fullMap }
        if (sideWindow != null) {
            // The rest of the layer stays clear, so the car's own dashboard shows around it.
            this.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            this.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            root.setBackgroundColor(Color.TRANSPARENT)
        } else {
            root.setBackgroundColor(Color.BLACK)
        }
        addMap(root, sideWindow)
        if (settings.clusterTurnCardOverlay) {
            turnCard = ClusterTurnCardView(context).apply {
                visibility = android.view.View.GONE
                setPlan(mapWindow)
                setNightMode(mapWindow != null && ClusterMapConfig.dark(ClusterDisplayServices.theme(context, settings)))
            }
            root.addView(turnCard, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        contentRoot = root
        AppLog.i("ClusterMap: map on ${display.name} window=$mapWindow card=${turnCard != null}")
    }

    override fun onStart() {
        super.onStart()
        if (turnCard != null) handler.post(guidanceTick)
    }

    override fun onStop() {
        handler.removeCallbacks(guidanceTick)
        super.onStop()
    }

    private var contentRoot: FrameLayout? = null
    private var mapWindow: ClusterMapConfig.Window? = null

    /** True on the Map theme's layer, false on the side layer, null where nothing is measured. */
    val fullMapLayer: Boolean? get() = mapWindow?.fullMap

    /**
     * Hides the content, not the window: the overlay owner gives this window a type its window
     * context does not have, and Android refuses any window attribute update on it ("Window type
     * mismatch"). A transparent view still draws, so the TextureView keeps taking frames.
     */
    fun setMapVisible(visible: Boolean) {
        val root = contentRoot ?: return
        val alpha = if (visible) 1f else 0f
        if (root.alpha != alpha) root.alpha = alpha
    }

    /** Adds the map: across the whole layer, or only inside [window], where it is cut out of the panel. */
    private fun addMap(root: FrameLayout, window: ClusterMapConfig.Window?) {
        val texture = TextureView(context)
        fun applyCrop(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            val m = ClusterMapConfig.cropMatrix(profile, width, height)
            texture.setTransform(Matrix().apply { setScale(m[0], m[1]); postTranslate(m[2], m[3]) })
        }
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                st.setDefaultBufferSize(profile.codecWidth, profile.codecHeight)
                applyCrop(width, height)
                val s = Surface(st)
                sharedTexture = st
                sharedSurface = s
                AppLog.i("ClusterMap: surface ready ${width}x$height on display ${display.displayId}:${display.name}")
                decoder().setSurface(s)
                // A codec on a new surface needs a keyframe before it can draw anything.
                requestKeyframe(context)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = applyCrop(width, height)
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                if (keepSurface && st === sharedTexture) {
                    // Moving to another layer: the next window takes this surface over, so the
                    // decoder keeps running and needs no keyframe from the phone.
                    AppLog.i("ClusterMap: keeping the map's surface for the next window")
                    return false
                }
                AppLog.i("ClusterMap: surface destroyed")
                releaseSharedSurface("dashboard surface destroyed", releaseTexture = false)
                return true
            }
        }
        sharedTexture?.let { kept ->
            // A theme change: carry on with the running decoder's surface, as DiPlay does.
            texture.setSurfaceTexture(kept)
            texture.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> applyCrop(r - l, b - t) }
            AppLog.i("ClusterMap: map moved to ${display.name} with the running decoder")
        }
        if (window == null) {
            root.addView(texture, FrameLayout.LayoutParams(-1, -1))
            return
        }
        // The phone draws the whole panel with the car in the middle of one safe area for every
        // theme. Lay the map out at panel size, slid so that point is the window's centre, and let
        // the window clip it: a theme change only moves the picture.
        val (dx, dy) = ClusterMapConfig.mapOffset(window)
        val clip = FrameLayout(context).apply { clipChildren = true; setBackgroundColor(Color.BLACK) }
        root.addView(clip, FrameLayout.LayoutParams(window.width, window.height, Gravity.TOP or Gravity.START).apply {
            leftMargin = window.left
            topMargin = window.top
        })
        clip.addView(texture, FrameLayout.LayoutParams(
            ClusterMapConfig.PANEL_WIDTH, ClusterMapConfig.PANEL_HEIGHT, Gravity.TOP or Gravity.START,
        ).apply {
            leftMargin = dx - window.left
            topMargin = dy - window.top
        })
    }

    private fun decoder() = App.provide(context).clusterVideoDecoder

    companion object {
        private const val DILINK5_BASE = "fission_bg_XDJAScreenProjection"
        private const val DILINK4_NAME = "fission_bg_xdjaVirtualSurface"
        private const val GUIDANCE_TICK_MS = 500L

        private var current: ClusterMapScreen? = null
        private var currentOwner: Owner? = null

        /**
         * Who the dashboard window belongs to, tried in this order. A window owned by DiAuto's
         * screen is stopped with it, so the map would vanish whenever the camera or another app
         * comes to the front; the first two belong to the app and stay up until the session ends.
         */
        private enum class Owner { OVERLAY, APP, SCREEN }

        /** What [show] was last called with, so the map can move layers when the cluster theme changes. */
        private var showContext: java.lang.ref.WeakReference<Context>? = null

        // One watcher of the stock cluster screens for the whole session, as in DiPlay. It lives
        // here, not in a window: a window that failed to open must not leave one running.
        private var monitor: Any? = null
        private var clusterState: ClusterActivityState.Snapshot? = null
        private var lastMoveTo: Boolean? = null
        private var lastMoveAt = 0L
        private const val MOVE_RETRY_MS = 10_000L

        // The map's surface for the whole session: a theme change hands it to the next window
        // instead of stopping the decoder, which then waited up to a minute for a keyframe.
        private var sharedTexture: SurfaceTexture? = null
        private var sharedSurface: Surface? = null
        private var keepSurface = false
        private var decoderContext: java.lang.ref.WeakReference<Context>? = null

        /** [releaseTexture] false when the TextureView releases it itself (its destroy callback returned true). */
        private fun releaseSharedSurface(reason: String, releaseTexture: Boolean = true) {
            val context = decoderContext?.get()
            sharedSurface?.let { surface -> context?.let { App.provide(it).clusterVideoDecoder.stopIfCurrentSurface(surface, reason) } }
            sharedSurface?.release()
            sharedSurface = null
            if (releaseTexture) sharedTexture?.release()
            sharedTexture = null
        }

        private fun startMonitor(context: Context) {
            if (monitor != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            if (!ClusterMapConfig.measured(Build.FINGERPRINT)) return
            if (!DiLink51ClusterMonitor.hasAccess(context)) {
                AppLog.i("ClusterMap: no Usage access, so the map shows on every cluster card")
                return
            }
            monitor = DiLink51ClusterMonitor(context) { onClusterState(it) }.also { it.start() }
        }

        private fun stopMonitor() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (monitor as? DiLink51ClusterMonitor)?.stop()
            monitor = null
            clusterState = null
            ClusterMapConfig.reportedTheme = null
            lastMoveTo = null
        }

        /**
         * Follows the cluster: the map goes on the layer of the theme the cluster shows, and on the
         * side layer only shows while the map card is open. Main thread.
         */
        private fun onClusterState(state: ClusterActivityState.Snapshot) {
            if (state != clusterState) AppLog.i("ClusterMap: cluster theme=${state.theme} map card visible=${state.mapVisible}")
            clusterState = state
            ClusterMapConfig.reportedTheme = state.theme
            val screen = current ?: return
            val onFullMap = screen.fullMapLayer
            val wantsFullMap = state.theme?.let { it == ClusterMapConfig.Theme.MAP }
            if (onFullMap != null && wantsFullMap != null && onFullMap != wantsFullMap) {
                screen.setMapVisible(false)
                // One move per theme change, and never a loop if the other layer cannot be shown.
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastMoveTo == wantsFullMap && now - lastMoveAt < MOVE_RETRY_MS) return
                lastMoveTo = wantsFullMap
                lastMoveAt = now
                AppLog.i("ClusterMap: the cluster switched to ${state.theme}; moving the map to its layer")
                moveLayer()
                return
            }
            screen.setMapVisible(ClusterMapConfig.shownOnLayer(state.theme, state.mapVisible, onFullMap))
        }

        /**
         * Reopens the map on the layer of the cluster's current theme. Nothing goes to the phone:
         * the safe area is the same for every theme, so only the picture moves.
         */
        private fun moveLayer() {
            val context = showContext?.get() ?: return
            closeWindow(keepSurface = true)
            show(context)
        }

        /** Shows the dashboard map while a session with it enabled is up. Safe to call repeatedly. */
        fun show(context: Context) {
            showContext = java.lang.ref.WeakReference(context)
            decoderContext = java.lang.ref.WeakReference(context.applicationContext)
            val settings = App.provide(context).settings
            if (!settings.clusterMapEnabled || current?.isShowing == true) return
            val display = findDisplay(context)
            if (display == null) {
                AppLog.w("ClusterMap: no BYD dashboard display among ${describeDisplays(context)}")
                return
            }
            if (!ClusterMapConfig.measured(Build.FINGERPRINT)) {
                // The layer is a guess here, and a map on a layer the cluster never shows looks
                // exactly like a working one in the log (#64). Say which layers there were.
                AppLog.i("ClusterMap: no measured layout for this firmware; using ${display.name} of ${describeDisplays(context)}")
            }
            val profile = ClusterDisplayServices.profile(context, settings)
            val app = context.applicationContext
            val owners = buildList {
                // "Display over other apps" lets the window outlive DiAuto's screen for certain.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppPermissions.isOverlayGranted(app)) add(Owner.OVERLAY)
                // Without it, a presentation of the app's own is accepted on many firmwares.
                add(Owner.APP)
                add(Owner.SCREEN)
            }
            for (owner in owners) {
                val screen = ClusterMapScreen(if (owner == Owner.SCREEN) context else app, display, profile)
                if (owner == Owner.OVERLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    screen.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                }
                val shown = runCatching { screen.show() }
                    .onFailure { AppLog.w("ClusterMap: $owner window refused on ${display.name}: ${it.message}") }
                    .isSuccess
                if (shown) {
                    current = screen
                    currentOwner = owner
                    AppLog.i("ClusterMap: shown as $owner window" +
                        if (owner == Owner.SCREEN) " (closes when DiAuto's screen is hidden)" else "")
                    startMonitor(app)
                    clusterState?.let(::onClusterState)
                    return
                }
                // A refused window may have been half created; close it so nothing of it runs on.
                runCatching { screen.dismiss() }
            }
        }

        /** DiAuto's screen is going away: closes the map only if that screen owns its window. */
        fun onScreenHidden() {
            if (currentOwner == Owner.SCREEN) {
                closeWindow(keepSurface = false)
                releaseSharedSurface("DiAuto's screen hidden")
            }
        }

        /** The session ended. */
        fun dismiss() {
            closeWindow(keepSurface = false)
            // A surface kept for a move that never found its window is released here.
            releaseSharedSurface("session ended")
            stopMonitor()
        }

        private fun closeWindow(keepSurface: Boolean) {
            this.keepSurface = keepSurface
            current?.let { runCatching { it.dismiss() } }
            this.keepSurface = false
            current = null
            currentOwner = null
        }

        fun requestKeyframe(context: Context) {
            App.provide(context).commManager.requestClusterKeyframe()
        }

        fun findDisplay(context: Context): Display? {
            val displays = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION) ?: return null
            // Measured DiLink 5.1: the layer the cluster shows for the car's theme, and no other.
            if (ClusterMapConfig.measured(Build.FINGERPRINT)) {
                val theme = ClusterDisplayServices.theme(context, App.provide(context).settings)
                val layer = if (theme == ClusterMapConfig.Theme.MAP) "shared_${DILINK5_BASE}_0" else "shared_${DILINK5_BASE}_1"
                return displays.firstOrNull { it.name == layer }
            }
            // DiLink 5: the base projection display, else its full-map layer (_0).
            displays.firstOrNull { it.name == DILINK5_BASE }?.let { return it }
            displays.firstOrNull { it.name.contains(DILINK5_BASE) && it.name.endsWith("_0") }?.let { return it }
            displays.firstOrNull { it.name.contains(DILINK5_BASE) }?.let { return it }
            // DiLink 3 / 4: an exact name and one of the measured sizes, nothing looser.
            return displays.firstOrNull { d ->
                val size = sizeOf(d)
                d.name == DILINK4_NAME && ((size.x == 1920 && size.y == 720) || (size.x == 1280 && size.y == 480))
            }
        }

        fun describeDisplays(context: Context): String =
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.displays
                ?.joinToString { d -> val s = sizeOf(d); "${d.displayId}:${d.name} ${s.x}x${s.y}" }.orEmpty()

        private fun sizeOf(display: Display): Point = Point().also {
            @Suppress("DEPRECATION")
            display.getRealSize(it)
        }
    }
}
