package com.andrerinas.openheadunit.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * DiLink 5.1 ("byd-freeform") wraps every activity in a DecorCaptionView that clamps the
 * app's visible area to 1920x984 while the window frame is the full 1920x1080 and all
 * system bars are hidden during projection. The bottom ~96px show a dead black strip and
 * touches there never reach the app (parent hit-testing rejects them).
 *
 * The clamp lands AFTER the first layout pass (the caption system kicks in late), so this
 * cannot be fixed by a one-shot measurement. Instead we install a persistent enforcer:
 *  1. the projection container is forced to the real window height whenever anything
 *     re-shrinks it, with ancestor clipping disabled so it can actually draw there;
 *  2. a transparent sub-window panel covers the strip the decor can no longer hit-test
 *     and forwards touches into the projection touch pipe with corrected Y.
 *
 * Pure app-side; no root, no system changes.
 */
class BydFullscreen {

    private var stripView: View? = null
    private var ownerContainer: FrameLayout? = null
    private var layoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    fun expand(activity: Activity, container: FrameLayout, touchOverlay: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val wm = activity.windowManager
        val decor = activity.window.decorView
        release()
        ownerContainer = container

        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            try {
                val real = wm.currentWindowMetrics.bounds
                val realH = real.height()
                val parentV = container.parent as? View ?: return@OnGlobalLayoutListener
                val visibleH = parentV.height          // caption-clamped height (1080 early, 984 late)

                // 1) keep the container at the true window height, ancestors unclipped
                if (container.height != realH && container.layoutParams?.height != realH) {
                    container.layoutParams?.let { lp ->
                        lp.height = realH
                        container.layoutParams = lp
                    }
                }
                var p: android.view.ViewParent? = container.parent
                while (p is ViewGroup) {
                    if (p.clipChildren || p.clipToPadding) {
                        p.clipChildren = false
                        p.clipToPadding = false
                    }
                    p = p.parent
                }

                // 2) strip panel over the dead zone (appears only when a zone exists)
                val hidden = realH - visibleH
                if (hidden >= 8) {
                    ensureStrip(activity, wm, decor, touchOverlay, real.width(), visibleH, hidden)
                } else {
                    removeStrip()
                }
            } catch (e: Exception) {
                AppLog.w("BydFullscreen: enforce failed: ${e.message}")
            }
        }
        layoutListener = listener
        container.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun ensureStrip(
        activity: Activity,
        wm: WindowManager,
        decor: View,
        touchOverlay: View,
        width: Int,
        top: Int,
        height: Int
    ) {
        val existing = stripView
        if (existing != null) {
            val cur = existing.layoutParams as? WindowManager.LayoutParams
            if (cur != null && (cur.y != top || cur.height != height || cur.width != width)) {
                cur.y = top
                cur.height = height
                cur.width = width
                try {
                    wm.updateViewLayout(existing, cur)
                } catch (_: Exception) {
                }
            }
            return
        }
        if (decor.windowToken == null) return
        val strip = View(activity)
        strip.setBackgroundColor(Color.TRANSPARENT)
        val lp = WindowManager.LayoutParams(
            width, height,
            0, top,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.token = decor.windowToken
        strip.setOnTouchListener { _, ev ->
            // Use current panel geometry after resize; never mutate the framework event.
            val copy = android.view.MotionEvent.obtain(ev)
            try {
                val stripLocation = IntArray(2)
                val overlayLocation = IntArray(2)
                strip.getLocationOnScreen(stripLocation)
                touchOverlay.getLocationOnScreen(overlayLocation)
                copy.offsetLocation((stripLocation[0] - overlayLocation[0]).toFloat(),
                    (stripLocation[1] - overlayLocation[1]).toFloat())
                touchOverlay.dispatchTouchEvent(copy)
            } finally {
                copy.recycle()
            }
            true
        }
        try {
            wm.addView(strip, lp)
            stripView = strip
            AppLog.i("BydFullscreen: strip panel added ${width}x$height at y=$top")
        } catch (e: Exception) {
            AppLog.w("BydFullscreen: addView failed: ${e.message}")
        }
    }

    fun release() {
        val observer = ownerContainer?.viewTreeObserver
        layoutListener?.let { if (observer?.isAlive == true) observer.removeOnGlobalLayoutListener(it) }
        ownerContainer = null
        layoutListener = null
        removeStrip()
    }

    private fun removeStrip() {
        stripView?.let { v ->
            try {
                (v.context.getSystemService(Activity.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (_: Exception) {
            }
        }
        stripView = null
    }
}
