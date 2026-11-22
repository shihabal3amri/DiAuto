package com.andrerinas.openheadunit.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.decoder.VideoDecoder
import kotlin.math.roundToInt

object HeadUnitScreenConfig {

    private var screenWidthPx: Int = 0
    private var screenHeightPx: Int = 0
    private var density: Float = 1.0f
    private var densityDpi: Int = 240
    private var scaleFactor: Float = 1.0f
    private var isSmallScreen: Boolean = true
    private var isPortraitScaled: Boolean = false
    private var isInitialized: Boolean = false
    private var lastSettingsHash: Int = 0
    
    // Flag to determine if the projection should stretch and ignore aspect ratio
    private var stretchToFill: Boolean = false 
    
    // Forced scale for older devices (Legacy fix)
    var forcedScale: Boolean = false
        private set

    var negotiatedResolutionType: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
    var isResolutionLocked: Boolean = false
        private set

    // What service discovery told the phone, held from then until the session ends. A session
    // cannot change its resolution, so while this is set nothing below chooses another one: every
    // re-choice mid-session left the scaling and the touch mapping computed for a size the phone
    // was not sending (#61). Null between sessions, where the lock alone decides as it always did.
    @Volatile private var announced: SessionShapePolicy.Shape? = null

    private lateinit var currentSettings: Settings // Store settings instance

    // System Insets (Bars/Cutouts)
    var systemInsetLeft: Int = 0
        private set
    var systemInsetTop: Int = 0
        private set
    var systemInsetRight: Int = 0
        private set
    var systemInsetBottom: Int = 0
        private set

    // Raw Screen Dimensions (Full Display)
    private var realScreenWidthPx: Int = 0
    private var realScreenHeightPx: Int = 0


    @Synchronized
    fun init(context: Context, displayMetrics: DisplayMetrics, settings: Settings) {
        stretchToFill = settings.stretchToFill
        forcedScale = settings.forcedScale && settings.viewMode == Settings.ViewMode.SURFACE

        val realW: Int
        val realH: Int
        val usableW: Int
        val usableH: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            val windowManager = context.getSystemService(android.view.WindowManager::class.java)
            val bounds = windowManager.currentWindowMetrics.bounds
            // On API 30+, bounds on an Activity context often return the usable area.
            // We use the displayMetrics as a fallback for the physical area.
            //
            // DiLink 5.1 caveat: BYD's "byd-freeform" windowing mode clamps the activity's
            // appBounds (and therefore displayMetrics) to 1920x984 while the window frame
            // (currentWindowMetrics.bounds) really is the full 1920x1080 with both system
            // bars hidden. Taking displayMetrics at face value makes the "real" screen
            // 96px shorter than the view BydFullscreen actually draws into, which shows up
            // as a vertically stretched picture plus a growing touch offset down the screen.
            // The real area can never be smaller than the window we are given, so clamp it up.
            realW = Math.max(displayMetrics.widthPixels, bounds.width())
            realH = Math.max(displayMetrics.heightPixels, bounds.height())
            usableW = bounds.width()
            usableH = bounds.height()
        } else { // Older APIs
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            realW = size.x
            realH = size.y
            
            @Suppress("DEPRECATION")
            display.getSize(size)
            usableW = size.x
            usableH = size.y
        }

        val finalRealW: Int
        val finalRealH: Int
        val finalUsableW: Int
        val finalUsableH: Int

        val screenOrientation = settings.screenOrientation
        if (screenOrientation == Settings.ScreenOrientation.LANDSCAPE || 
            screenOrientation == Settings.ScreenOrientation.LANDSCAPE_REVERSE) {
            finalRealW = Math.max(realW, realH)
            finalRealH = Math.min(realW, realH)
            finalUsableW = Math.max(usableW, usableH)
            finalUsableH = Math.min(usableW, usableH)
        } else if (screenOrientation == Settings.ScreenOrientation.PORTRAIT || 
                   screenOrientation == Settings.ScreenOrientation.PORTRAIT_REVERSE) {
            finalRealW = Math.min(realW, realH)
            finalRealH = Math.max(realW, realH)
            finalUsableW = Math.min(usableW, usableH)
            finalUsableH = Math.max(usableW, usableH)
        } else {
            finalRealW = realW
            finalRealH = realH
            finalUsableW = usableW
            finalUsableH = usableH
        }

        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Raw size: ${realW}x${realH}, usable: ${usableW}x${usableH}, orientation setting: $screenOrientation")
        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Final normalized size: ${finalRealW}x${finalRealH}, usable: ${finalUsableW}x${finalUsableH}")

        // Only update if dimensions or settings changed
        val currentHash = computeSettingsHash(settings)
        if (isInitialized && realScreenWidthPx == finalRealW && realScreenHeightPx == finalRealH && lastSettingsHash == currentHash) {
            return
        }

        // If settings changed (e.g. orientation swap), unlock resolution before recalculating
        if (isInitialized && lastSettingsHash != 0 && lastSettingsHash != currentHash) {
            if (announced == null) {
                AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Settings changed ($lastSettingsHash -> $currentHash). Unlocking resolution.")
                dropLock()
            } else {
                // The hash covers the anchor, so a window that changed size lands here as well.
                AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Settings changed ($lastSettingsHash -> $currentHash) under a running session. Keeping $negotiatedResolutionType until it ends.")
            }
        }

        isInitialized = true
        lastSettingsHash = currentHash
        currentSettings = settings

        // Determine if we are planning to hide the bars (Immersive)
        val immersive = settings.fullscreenMode == Settings.FullscreenMode.IMMERSIVE || 
                        settings.fullscreenMode == Settings.FullscreenMode.IMMERSIVE_WITH_NOTCH

        // THE ANCHOR: 
        // If we are immersive, our "World" is the physical screen. 
        // If we are NOT, our "World" is limited to the usable window area (no lying to AA).
        val defaultAnchorW = if (immersive) finalRealW else finalUsableW
        val defaultAnchorH = if (immersive) finalRealH else finalUsableH
        
        density = displayMetrics.density
        densityDpi = displayMetrics.densityDpi

        // Initial Insets: For non-immersive, the bars are already baked into the anchor (realSize = 736),
        // so we start with 0 system insets and just add manual settings.
        systemInsetLeft = settings.insetLeft
        systemInsetTop = settings.insetTop
        systemInsetRight = settings.insetRight
        systemInsetBottom = settings.insetBottom
        
        // Check if we have cached surface dimensions from a previous session.
        // If the settings haven't changed (same hash), use the cached values
        // to avoid a mid-session UpdateUiConfigRequest and potential flicker.
        val cachedW = settings.cachedSurfaceWidth
        val cachedH = settings.cachedSurfaceHeight
        val cachedHash = settings.cachedSurfaceSettingsHash

        if (cachedW > 0 && cachedH > 0 && cachedHash == currentHash) {
            // Cached surface dimensions are the usable area. The anchor includes insets.
            realScreenWidthPx = cachedW + systemInsetLeft + systemInsetRight
            realScreenHeightPx = cachedH + systemInsetTop + systemInsetBottom
            AppLog.i("[UI_DEBUG_FIX] HeadUnitScreenConfig: Using cached surface dimensions: ${cachedW}x${cachedH} (anchor: ${realScreenWidthPx}x${realScreenHeightPx})")
            // A cached surface recorded while the window was clamped (e.g. a DiLink session
            // before the fullscreen enforcer had run) must never shrink the immersive anchor
            // below the window we are actually drawing into - that reintroduces the stretch
            // and the touch offset. Self-heals a stale cache instead of requiring a wipe.
            if (immersive && (realScreenWidthPx < defaultAnchorW || realScreenHeightPx < defaultAnchorH)) {
                AppLog.i("[UI_DEBUG_FIX] HeadUnitScreenConfig: Stale cached anchor ${realScreenWidthPx}x${realScreenHeightPx} < window ${defaultAnchorW}x${defaultAnchorH}; using the window.")
                realScreenWidthPx = Math.max(realScreenWidthPx, defaultAnchorW)
                realScreenHeightPx = Math.max(realScreenHeightPx, defaultAnchorH)
            }
        } else {
            realScreenWidthPx = defaultAnchorW
            realScreenHeightPx = defaultAnchorH
            if (cachedW > 0) {
                AppLog.i("[UI_DEBUG_FIX] HeadUnitScreenConfig: Cache invalidated (hash mismatch: stored=$cachedHash, current=$currentHash)")
            }
        }

        // The hash covers the anchor, and the anchor has just been set. Remembering the hash from
        // before that made the next call see a settings change that never happened: on the first
        // connection after the app starts the anchor goes from 0x0 to the screen, so the call that
        // builds the service discovery response re-seeded the insets below and dropped the system
        // bar the projection window had already reported. The phone was then told there were no
        // margins and drew under the bar until the next connection (#72).
        lastSettingsHash = computeSettingsHash(settings)

        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Honest Init | Mode: ${settings.fullscreenMode} | Anchor: ${realScreenWidthPx}x${realScreenHeightPx} | Seeded Insets: L$systemInsetLeft T$systemInsetTop R$systemInsetRight B$systemInsetBottom")
        
        recalculate()
    }

    /** Back to the state of a newly started app. For unit tests of the start-up order only. */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTest() {
        isInitialized = false
        lastSettingsHash = 0
        realScreenWidthPx = 0
        realScreenHeightPx = 0
        screenWidthPx = 0
        screenHeightPx = 0
        systemInsetLeft = 0
        systemInsetTop = 0
        systemInsetRight = 0
        systemInsetBottom = 0
        isResolutionLocked = false
        announced = null
    }

    @Synchronized
    fun updateInsets(left: Int, top: Int, right: Int, bottom: Int) {
        if (systemInsetLeft == left && systemInsetTop == top && systemInsetRight == right && systemInsetBottom == bottom) {
            return
        }
        
        systemInsetLeft = left
        systemInsetTop = top
        systemInsetRight = right
        systemInsetBottom = bottom
        
        if (isInitialized) {
            recalculate()
        }
    }

    // Native standard resolution for a given panel size, mirroring the AUTO selection so the
    // resolution cap never advertises more than the panel warrants (issue #650).
    /** Map a resolution class to the proto type for the current orientation (shared by the manual
     * selection path and the panel cap, so both agree). */
    private fun protoForResolution(
        res: Settings.Resolution,
        portrait: Boolean
    ): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
        val landscape = res.codec
            ?: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
        if (!portrait) return landscape
        return when (res) {
            Settings.Resolution._800x480 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._720x1280
            Settings.Resolution._1280x720 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._720x1280
            Settings.Resolution._1920x1080 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1080x1920
            Settings.Resolution._2560x1440 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1440x2560
            Settings.Resolution._3840x2160 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._2160x3840
            else -> landscape
        }
    }

    /**
     * The proto resolution the physical panel warrants, in the display's orientation. Delegates to
     * SystemOptimizer.panelCeiling (the shared source of truth) so the runtime cap agrees with the
     * settings "too high" warning and the DPI calculation (issue #767).
     */
    private fun autoResolutionForPanel(
        w: Int,
        h: Int,
        portrait: Boolean,
        canHevc: Boolean
    ): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
        return protoForResolution(SystemOptimizer.panelCeiling(w, h, canHevc), portrait)
    }

    private fun pixelsOf(type: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType): Long {
        val s = type.toString().replace("_", "")
        return try {
            val parts = s.split("x")
            parts[0].toLong() * parts[1].toLong()
        } catch (e: Exception) {
            0L
        }
    }

    /** The resolution the setting asks for on a [usableW] x [usableH] window, before the panel cap. */
    private fun requestedFor(usableW: Int, usableH: Int): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
        val selectedResolution = Settings.Resolution.fromId(currentSettings.resolutionId)
        val portrait = usableH > usableW
        if (selectedResolution != Settings.Resolution.AUTO) {
            // Manual selection: map to the correct orientation via the shared helper.
            return protoForResolution(selectedResolution ?: Settings.Resolution._800x480, portrait)
        }
        if (portrait) {
            return if (usableW > 720 || usableH > 1280) {
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1080x1920
            } else {
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._720x1280
            }
        }
        // AUTO picks 1440p/4K only on a decoder from the HEVC reliability list (or software HEVC
        // the user chose), the rule the Auto codec setting already used. Those sizes need H.265, and
        // a hardware HEVC decoder outside the list got a 1440p H.264 profile and no picture (#35,
        // MediaTek mt8676). A manual 1440p choice still gets H.265 on any hardware HEVC decoder.
        val autoHevcHighResolution = canAutoNegotiateHevcHighResolution()
        return when {
            usableW <= 800 && usableH <= 480 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
            (usableW >= 3840 || usableH >= 2160) && VideoDecoder.isHevcReliable() && Build.VERSION.SDK_INT >= 24 ->
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._3840x2160
            (usableW >= 2560 || usableH >= 1440) && autoHevcHighResolution && Build.VERSION.SDK_INT >= 24 ->
                Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._2560x1440
            usableW > 1280 || usableH > 720 -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080
            else -> Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720
        }
    }

    /**
     * [requested] capped to what a [realW] x [realH] panel warrants, so we never ask the phone for
     * more pixels than the screen can show. Downscaling e.g. 1080p to a 600p panel every frame
     * overloads the display scaler (MediaTek MDP) and stalls video (issue #650).
     * min(requested, panelCeiling): only ever lowers, so explicit lower choices and HEVC-gated
     * 1440p/4K are never raised.
     */
    private fun cappedFor(
        requested: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType,
        realW: Int,
        realH: Int,
        portrait: Boolean
    ): Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType {
        val panelCeiling = autoResolutionForPanel(realW, realH, portrait, canNegotiateHevcHighResolution())
        return if (pixelsOf(requested) > pixelsOf(panelCeiling)) panelCeiling else requested
    }

    private fun sizeOf(type: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType): SessionShapePolicy.Size {
        val parts = type.toString().replace("_", "").split("x")
        return try {
            SessionShapePolicy.Size(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            SessionShapePolicy.Size(800, 480)
        }
    }

    /** A reading turned the way a fixed orientation setting says; left as it came for the others. */
    private fun orientedAsSet(w: Int, h: Int): Pair<Int, Int> {
        val screenOrientation = if (this::currentSettings.isInitialized) currentSettings.screenOrientation else Settings.ScreenOrientation.SYSTEM
        return when (screenOrientation) {
            Settings.ScreenOrientation.LANDSCAPE,
            Settings.ScreenOrientation.LANDSCAPE_REVERSE -> Pair(Math.max(w, h), Math.min(w, h))
            Settings.ScreenOrientation.PORTRAIT,
            Settings.ScreenOrientation.PORTRAIT_REVERSE -> Pair(Math.min(w, h), Math.max(w, h))
            else -> Pair(w, h)
        }
    }

    /**
     * What a [width] x [height] window would be announced as, were a session to start in it now:
     * what [recalculate] chooses with that window as the anchor and the insets as they are.
     * Changes nothing here.
     */
    @Synchronized
    fun shapeForWindow(width: Int, height: Int): SessionShapePolicy.Shape? {
        if (!isInitialized || width <= 0 || height <= 0) return null
        val (realW, realH) = orientedAsSet(width, height)
        var usableW = realW - systemInsetLeft - systemInsetRight
        var usableH = realH - systemInsetTop - systemInsetBottom
        if (usableW <= 0 || usableH <= 0) {
            usableW = realW
            usableH = realH
        }
        val resolution = cappedFor(requestedFor(usableW, usableH), realW, realH, portrait = usableH > usableW)
        return SessionShapePolicy.Shape(sizeOf(resolution), SessionShapePolicy.Size(realW, realH))
    }

    /**
     * Takes a [width] x [height] window as the anchor, for a session about to be ended so that it
     * is announced again for that window. True when the anchor changed.
     *
     * Not every resize reaches [updateSurfaceDimensions] (the TextureView backend reports none as
     * a surface change), and without this the next announcement would be made for the old window.
     * The hash is stored for the reason it is in [init]: it covers the anchor, and a stale one
     * makes the init() of the next service discovery start over and drop the insets.
     */
    @Synchronized
    fun adoptWindow(width: Int, height: Int): Boolean {
        if (!isInitialized || width <= 0 || height <= 0) return false
        val (realW, realH) = orientedAsSet(width, height)
        if (realW == realScreenWidthPx && realH == realScreenHeightPx) return false
        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Anchor ${realScreenWidthPx}x${realScreenHeightPx} -> ${realW}x${realH}, the window the session is redrawn for")
        realScreenWidthPx = realW
        realScreenHeightPx = realH
        lastSettingsHash = computeSettingsHash(currentSettings)
        recalculate()
        return true
    }

    private fun recalculate() {
        // Calculate USABLE area
        screenWidthPx = realScreenWidthPx - systemInsetLeft - systemInsetRight
        screenHeightPx = realScreenHeightPx - systemInsetTop - systemInsetBottom

        if (screenWidthPx <= 0 || screenHeightPx <= 0) {
            screenWidthPx = realScreenWidthPx
            screenHeightPx = realScreenHeightPx
        }

        val isPortraitDisplay = screenHeightPx > screenWidthPx

        // 1. Determine base negotiated resolution
        val session = announced
        if (session != null) {
            // The phone streams what service discovery announced until the session ends, whatever
            // the window does. This used to drop the lock on an orientation mismatch and to cap a
            // locked resolution to the new window; both re-chose a size the phone was not sending,
            // and the picture was drawn stretched until a reconnect (#61).
            AppLog.i("[UI_DEBUG] CarScreen: SESSION HOLDS $negotiatedResolutionType. Usable area is ${screenWidthPx}x${screenHeightPx}.")
        } else if (isResolutionLocked) {
            // Safety Check: If the locked resolution's orientation (Landscape/Portrait) 
            // no longer matches the display orientation, the lock is stale and must be dropped.
            val isPortraitRes = getNegotiatedHeight() > getNegotiatedWidth()
            if (isPortraitRes != isPortraitDisplay) {
                AppLog.i("[UI_DEBUG] CarScreen: Orientation mismatch detected (Res: ${if(isPortraitRes) "P" else "L"}, Display: ${if(isPortraitDisplay) "P" else "L"}). DROPPING LOCK.")
                dropLock()
            } else {
                AppLog.i("[UI_DEBUG] CarScreen: RESOLUTION LOCKED to $negotiatedResolutionType. Usable area is ${screenWidthPx}x${screenHeightPx}. Skipping re-negotiation.")
            }
        }

        // A locked session keeps what it already negotiated. This used to fall through to the
        // manual branch, where AUTO carries no codec and the fallback landed on 480p: the
        // margins re-announced for a surface change were then computed for an 800x480 stream
        // while the phone still sent 1080p, and the next connection asked for 800x480 (#19).
        // Upstream Open Headunit fixed the same fall-through in 9e1ce321.
        val preCapResolution =
            if (session != null || isResolutionLocked) negotiatedResolutionType
            else requestedFor(screenWidthPx, screenHeightPx)
        if (session == null) {
            negotiatedResolutionType = cappedFor(preCapResolution, realScreenWidthPx, realScreenHeightPx, isPortraitDisplay)
        }
        AppLog.i(
            "[RES_CAP] resolutionId=${currentSettings.resolutionId} " +
                "realScreen=${realScreenWidthPx}x${realScreenHeightPx} usable=${screenWidthPx}x${screenHeightPx} " +
                "portrait=$isPortraitDisplay locked=$isResolutionLocked chosen=$preCapResolution " +
                "capped=$negotiatedResolutionType changed=${preCapResolution != negotiatedResolutionType} " +
                "session=${session?.resolution ?: "none"}"
        )

        // 2. Perform scaling calculations (now safe because negotiatedResolutionType is set)
        AppLog.i("[UI_DEBUG] CarScreen: usable area ${screenWidthPx}x${screenHeightPx}, using $negotiatedResolutionType")

        if (screenHeightPx > screenWidthPx) {
            isSmallScreen = screenWidthPx <= 1080 && screenHeightPx <= 1920
        } else {
            isSmallScreen = screenWidthPx <= 1920 && screenHeightPx <= 1080
        }

        scaleFactor = 1.0f
        if (!isSmallScreen) {
            val sWidth = screenWidthPx.toFloat()
            val sHeight = screenHeightPx.toFloat()
            if (getNegotiatedWidth() > 0 && getNegotiatedHeight() > 0) {
                 if (sWidth / sHeight < getAspectRatio()) {
                    isPortraitScaled = true
                    scaleFactor = sHeight / getNegotiatedHeight().toFloat()
                } else {
                    isPortraitScaled = false
                    scaleFactor = sWidth / getNegotiatedWidth().toFloat()
                }
            }
        }
        
        AppLog.i("[UI_DEBUG] CarScreen isSmallScreen: $isSmallScreen, scaleFactor: $scaleFactor, margins: w=${getWidthMargin()}, h=${getHeightMargin()}")
    }

    fun getAdjustedHeight(): Int {
        return (getNegotiatedHeight() * scaleFactor).roundToInt()
    }

    fun getAdjustedWidth(): Int {
        return (getNegotiatedWidth() * scaleFactor).roundToInt()
    }

    private fun getAspectRatio(): Float {
        return getNegotiatedWidth().toFloat() / getNegotiatedHeight().toFloat()
    }

    fun getNegotiatedHeight(): Int {
        val resString = negotiatedResolutionType.toString().replace("_", "")
        return try {
            resString.split("x")[1].toInt()
        } catch (e: Exception) {
            480
        }
    }

    private fun canNegotiateHevcHighResolution(): Boolean =
        VideoDecoder.isHevcSupported() || explicitSoftwareHevc()

    /** HEVC that AUTO may use for 1440p: a decoder from the reliability list, or chosen software HEVC. */
    private fun canAutoNegotiateHevcHighResolution(): Boolean =
        VideoDecoder.isHevcReliable() || explicitSoftwareHevc()

    private fun explicitSoftwareHevc(): Boolean {
        if (currentSettings.videoCodec != VideoDecoder.CodecType.H265.settingsValue || !currentSettings.forceSoftwareDecoding) return false
        return when (currentSettings.softwareVideoDecoder) {
            Settings.SoftwareVideoDecoder.BUNDLED_FFMPEG -> VideoDecoder.isBundledHevcDecoderAvailable()
            Settings.SoftwareVideoDecoder.DEVICE_MEDIACODEC -> VideoDecoder.isHevcDecoderAvailable(includeSoftware = true)
        }
    }

    fun getNegotiatedWidth(): Int {
        val resString = negotiatedResolutionType.toString().replace("_", "")
        return try {
            resString.split("x")[0].toInt()
        } catch (e: Exception) {
            800
        }
    }

    fun getHeightMargin(): Int {
        val margin = ((getAdjustedHeight() - screenHeightPx) / scaleFactor).roundToInt()
        return margin.coerceAtLeast(0)
    }

    fun getWidthMargin(): Int {
        val margin = ((getAdjustedWidth() - screenWidthPx) / scaleFactor).roundToInt()
        return margin.coerceAtLeast(0)
    }

    private fun divideOrOne(numerator: Float, denominator: Float): Float {
        return if (denominator == 0.0f) 1.0f else numerator / denominator
    }

    fun getScaleX(): Float {
        if (forcedScale) {
            return 1.0f
        }

        if (getNegotiatedWidth() > screenWidthPx) {
            return divideOrOne(getNegotiatedWidth().toFloat(), screenWidthPx.toFloat())
        }
        if (isPortraitScaled) {
            return divideOrOne(getAspectRatio(), (screenWidthPx.toFloat() / screenHeightPx.toFloat()))
        }
        return 1.0f
    }
        // Stretch option PR #259
    fun getScaleY(): Float {
        if (forcedScale) {
            return 1.0f
        }

        if (getNegotiatedHeight() > screenHeightPx) {
            return if (stretchToFill) {
                // Before PR #233 Fix scaler Y
                divideOrOne(getNegotiatedHeight().toFloat(), screenHeightPx.toFloat())
            } else {
                // After PR #233 Fix scaler Y
                divideOrOne((screenWidthPx.toFloat() / screenHeightPx.toFloat()), getAspectRatio())
            }
        }

        if (isPortraitScaled) {
            return 1.0f
        }

        return divideOrOne((screenWidthPx.toFloat() / screenHeightPx.toFloat()), getAspectRatio())
    }

    fun getDensityDpi(): Int {
        return if (this::currentSettings.isInitialized && currentSettings.dpiPixelDensity != 0) {
            currentSettings.dpiPixelDensity
        } else {
            densityDpi
        }
    }

    fun getPixelAspectRatioE4(): Int {
        return if (this::currentSettings.isInitialized && currentSettings.pixelAspectRatioE4 > 0) {
            currentSettings.pixelAspectRatioE4
        } else {
            10000 // 1.0 = square pixels
        }
    }

    fun getUsableWidth(): Int = screenWidthPx
    fun getUsableHeight(): Int = screenHeightPx

    // These are half the total margin, distributed symmetrically.
    fun getLeftMargin(): Int = getWidthMargin() / 2
    fun getRightMargin(): Int = getWidthMargin() - getLeftMargin()
    fun getTopMargin(): Int = getHeightMargin() / 2
    fun getBottomMargin(): Int = getHeightMargin() - getTopMargin()

    /**
     * Called when the actual rendering surface dimensions become known (from onSurfaceChanged).
     * Compares with the current usable area and updates the anchor if they differ.
     * @return true if the dimensions changed and margins need to be re-sent to AA.
     */
    @Synchronized
    fun updateSurfaceDimensions(surfaceW: Int, surfaceH: Int): Boolean {
        val (finalSurfaceW, finalSurfaceH) = orientedAsSet(surfaceW, surfaceH)

        val diffW = kotlin.math.abs(finalSurfaceW - screenWidthPx)
        val diffH = kotlin.math.abs(finalSurfaceH - screenHeightPx)

        if (diffW <= SURFACE_MISMATCH_TOLERANCE && diffH <= SURFACE_MISMATCH_TOLERANCE) {
            return false
        }

        if( (diffW > 0 && getNegotiatedWidth() == finalSurfaceW) || (diffH > 0 && getNegotiatedHeight() == finalSurfaceH)) {
            AppLog.i("[UI_DEBUG_FIX] Surface mismatch detected but matches negotiated resolution. Usable: ${screenWidthPx}x${screenHeightPx}, Actual surface: ${finalSurfaceW}x${finalSurfaceH}. Ignoring.")
            return false
        }

        AppLog.i("[UI_DEBUG_FIX] Surface mismatch detected! Usable: ${screenWidthPx}x${screenHeightPx}, Actual surface: ${finalSurfaceW}x${finalSurfaceH} (diff: ${diffW}x${diffH})")

        // Update anchor: the surface dimensions ARE the real usable area,
        // so the anchor is the usable area plus insets.
        realScreenWidthPx = finalSurfaceW + systemInsetLeft + systemInsetRight
        realScreenHeightPx = finalSurfaceH + systemInsetTop + systemInsetBottom

        recalculate()

        AppLog.i("[UI_DEBUG_FIX] Recalculated: usable=${screenWidthPx}x${screenHeightPx}, margins: w=${getWidthMargin()}, h=${getHeightMargin()}, per-side: L=${getLeftMargin()} T=${getTopMargin()} R=${getRightMargin()} B=${getBottomMargin()}")
        return true
    }

    /**
     * Computes a hash of all settings that affect screen dimensions.
     * Used to invalidate the cached surface dimensions when settings change.
     */
    fun computeSettingsHash(settings: Settings): Int {
        var hash = 17
        hash = 31 * hash + settings.resolutionId
        hash = 31 * hash + settings.dpiPixelDensity
        hash = 31 * hash + settings.pixelAspectRatioE4
        hash = 31 * hash + settings.insetLeft
        hash = 31 * hash + settings.insetTop
        hash = 31 * hash + settings.insetRight
        hash = 31 * hash + settings.insetBottom
        hash = 31 * hash + settings.viewMode.ordinal
        hash = 31 * hash + settings.screenOrientation.ordinal
        hash = 31 * hash + settings.fullscreenMode.value
        hash = 31 * hash + (if (settings.stretchToFill) 1 else 0)
        hash = 31 * hash + (if (settings.forcedScale) 1 else 0)
        // Include physical dimensions in the hash. If the screen rotates or a foldable is unfolded,
        // the hash will change, triggering a clean unlock and recalculation.
        hash = 31 * hash + realScreenWidthPx
        hash = 31 * hash + realScreenHeightPx
        return hash
    }

    @Synchronized
    fun lockResolution() {
        if (!isResolutionLocked) {
            AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Locking resolution at $negotiatedResolutionType")
            isResolutionLocked = true
        }
    }

    /** Ends the session and releases the lock, so the next one is negotiated afresh. */
    @Synchronized
    fun unlockResolution() {
        dropLock()
        endSession()
    }

    private fun dropLock() {
        if (isResolutionLocked) {
            AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Unlocking resolution (was $negotiatedResolutionType)")
            isResolutionLocked = false
        }
    }

    /**
     * Service discovery is telling the phone [negotiatedResolutionType], chosen for the window as
     * it is now. The phone streams that until the session ends, so from here it is held whatever
     * the window does, and remembered as the shape a changed window is compared with
     * ([SessionShapePolicy]).
     */
    @Synchronized
    fun beginSession() {
        lockResolution()
        val shape = SessionShapePolicy.Shape(
            sizeOf(negotiatedResolutionType),
            SessionShapePolicy.Size(realScreenWidthPx, realScreenHeightPx)
        )
        announced = shape
        AppLog.i("[UI_DEBUG] HeadUnitScreenConfig: Session announced as ${shape.resolution} for a ${shape.canvas} window")
    }

    /** What service discovery announced for the running session, or null between sessions. */
    fun sessionShape(): SessionShapePolicy.Shape? = announced

    /**
     * The session is over: nothing holds its resolution any more.
     *
     * The window may have changed while it was held, and nothing else is certain to run before the
     * next service discovery, which would then announce the ended session's shape for a window
     * that no longer has it. So the choice is made again here, under the lock if that is still set
     * (a session the transport ended keeps it, as it always did).
     */
    @Synchronized
    fun endSession() {
        if (announced == null) return
        announced = null
        if (isInitialized) recalculate()
    }

    private const val SURFACE_MISMATCH_TOLERANCE = 4
}
