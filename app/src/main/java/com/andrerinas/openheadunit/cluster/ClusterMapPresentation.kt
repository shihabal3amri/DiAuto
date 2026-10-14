package com.andrerinas.openheadunit.cluster

import android.app.Presentation
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.hud.BydGuidance
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Shows the phone's cluster stream on a BYD cluster projection display, ported from DiPlay.
 *
 * The cluster shows this display only while its own projection mode is on (Small or Full screen
 * navi on the wheel menu), which DiAuto cannot switch. On the measured DiLink 5.1 firmware the map
 * fills only the stock map window ([ClusterLayout.Plan]); everything outside stays transparent.
 */
@RequiresApi(android.os.Build.VERSION_CODES.P)
internal class ClusterMapPresentation(
    context: Context,
    display: Display,
    private val profile: ClusterStreamProfile,
    private val theme: ClusterLayout.Theme,
    private val onSurface: (Surface?) -> Unit,
) : Presentation(context, display) {
    private var waiting: View? = null
    private var video: TextureView? = null
    private var turnCard: ClusterTurnCardView? = null
    private var surface: Surface? = null
    private var streamActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val size = ClusterLayout.sizeOf(display)
        val plan = ClusterLayout.plan(size.x, size.y, theme)
        val dark = plan != null && ClusterLayout.dark(theme)
        val backdrop = if (dark) Color.rgb(15, 22, 30) else Color.rgb(207, 218, 229)
        val placeholder = if (dark) Color.BLACK else Color.rgb(233, 238, 246)
        val root = FrameLayout(context).apply {
            setBackgroundColor(if (plan == null) placeholder else if (plan.fullMap) backdrop else Color.TRANSPARENT)
        }
        if (plan != null) {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val viewport = if (plan == null) FrameLayout.LayoutParams(-1, -1) else
            FrameLayout.LayoutParams(plan.width, plan.height, Gravity.TOP or Gravity.START).apply {
                leftMargin = plan.left; topMargin = plan.top
            }
        val textureView = TextureView(context)
        fun cropToContent() {
            // Fill the window at the stream's own aspect: centred horizontally and aligned to the
            // bottom, where a navigating map keeps the car marker (DiPlay crops its CarPlay map the
            // same way; a centred crop of the 1920x480 Map window cut the marker off).
            val w = textureView.width.toFloat().takeIf { it > 0 } ?: return
            val h = textureView.height.toFloat().takeIf { it > 0 } ?: return
            val scale = maxOf(w / profile.codecWidth, h / profile.codecHeight)
            textureView.setTransform(Matrix().apply {
                setScale(profile.codecWidth * scale / w, profile.codecHeight * scale / h, w / 2f, h)
            })
        }
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                texture.setDefaultBufferSize(profile.codecWidth, profile.codecHeight)
                cropToContent()
                surface = Surface(texture).also(onSurface)
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = cropToContent()
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                onSurface(null)
                surface?.release()
                surface = null
                return true
            }
        }
        textureView.alpha = 0f
        root.addView(textureView, viewport)
        video = textureView
        if (plan?.fullMap == true) root.addView(InstrumentContrastView(context, plan, backdrop), FrameLayout.LayoutParams(-1, -1))

        waiting = FrameLayout(context).apply {
            if (plan == null) setBackgroundColor(placeholder)
            val density = context.resources.displayMetrics.density
            addView(ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(if (dark || plan == null) Color.WHITE else Color.DKGRAY)
                contentDescription = context.getString(R.string.cluster_waiting_for_map)
            }, FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt(), Gravity.CENTER))
        }
        root.addView(waiting, FrameLayout.LayoutParams(viewport))
        turnCard = ClusterTurnCardView(context).apply {
            visibility = View.GONE
            setPlan(plan)
            setNightMode(dark)
        }
        root.addView(turnCard, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        AppLog.i("ClusterMap: presentation on ${display.name} ${size.x}x${size.y} theme=$theme plan=$plan dark=$dark")
    }

    /** Cross-fades the waiting spinner and the live map. */
    fun setStreamActive(active: Boolean) {
        if (streamActive == active) return
        streamActive = active
        if (active && com.andrerinas.openheadunit.BuildConfig.DEBUG) scheduleSnapshot()
        val map = video ?: return
        val label = waiting ?: return
        map.animate().cancel()
        label.animate().cancel()
        label.visibility = View.VISIBLE
        map.animate().alpha(if (active) 1f else 0f).setDuration(300).start()
        label.animate().alpha(if (active) 0f else 1f).setDuration(300).withEndAction {
            if (streamActive == active) label.visibility = if (active) View.GONE else View.VISIBLE
        }.start()
    }

    fun setGuidance(guidance: BydGuidance?) {
        turnCard?.setGuidance(guidance)
    }

    /**
     * Debug builds only: every 15 s while the map shows, the whole decoded stream frame goes to
     * the app's files folder (cluster-stream.png), so where the phone draws the car can be
     * measured. TextureView.getBitmap reads the buffer itself, not the cropped view.
     */
    private fun scheduleSnapshot() {
        val view = video ?: return
        view.postDelayed(object : Runnable {
            override fun run() {
                if (!streamActive || !isShowing) return
                runCatching {
                    val frame = view.getBitmap(profile.codecWidth, profile.codecHeight) ?: return@runCatching
                    val dir = context.getExternalFilesDir(null) ?: return@runCatching
                    java.io.File(dir, "cluster-stream.png").outputStream().use {
                        frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    frame.recycle()
                    AppLog.i("ClusterMap: debug snapshot saved to ${dir.path}/cluster-stream.png")
                }
                view.postDelayed(this, 15_000L)
            }
        }, 3_000L)
    }
}

/** Shading behind the instrument readouts that sit over the DiLink 5.1 full map. */
@RequiresApi(android.os.Build.VERSION_CODES.P)
private class InstrumentContrastView(
    context: Context,
    private val plan: ClusterLayout.Plan,
    private val background: Int,
) : View(context) {
    private val paint = Paint()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = plan.left.toFloat(); val top = plan.top.toFloat()
        val right = left + plan.width; val bottom = top + plan.height
        fun fade(x0: Float, y0: Float, x1: Float, y1: Float, start: Int, end: Int, l: Float, t: Float, r: Float, b: Float) {
            paint.shader = LinearGradient(x0, y0, x1, y1, start, end, Shader.TileMode.CLAMP)
            canvas.drawRect(l, t, r, b, paint)
        }
        val clear = background and 0x00ffffff
        val soft = (background and 0x00ffffff) or (210 shl 24)
        fade(left, top, left, top + 64, background, clear, left, top, right, top + 64)
        fade(left, bottom - 48, left, bottom, clear, background, left, bottom - 48, right, bottom)
        // The trip/menu readouts occupy the outer panels; keep the route and marker in the clear centre.
        fade(left, top, left + 460, top, soft, clear, left, top, left + 460, bottom)
        fade(right - 460, top, right, top, clear, soft, right - 460, top, right, bottom)
        paint.shader = null
    }
}
