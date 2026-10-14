package com.andrerinas.openheadunit.cluster

import android.app.Activity
import android.os.Handler
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Owned by the projection activity: shows the cluster presentation while the session carries a
 * cluster stream and keeps the turn card current. A presentation needs an activity window, so the
 * dashboard map follows the projection screen, as in DiPlay.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class ClusterMapHost(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private var presentation: ClusterMapPresentation? = null
    private var shownFor: ClusterMapStream.Session? = null
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            update()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        if (running || !ClusterMapStream.available()) return
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        dismiss()
    }

    private fun update() {
        val session = ClusterMapStream.session
        if (session == null || activity.isFinishing || activity.isDestroyed) {
            dismiss()
            return
        }
        if (presentation == null || shownFor != session) {
            dismiss()
            show(session)
        }
        presentation?.setStreamActive(ClusterMapStream.streamActive)
        presentation?.setGuidance(ClusterMapStream.guidance?.takeIf { System.nanoTime() - it.updatedNs < GUIDANCE_TTL_NS })
    }

    private fun show(session: ClusterMapStream.Session) {
        val display = ClusterLayout.findDisplay(activity, session.theme)?.takeIf { it.name == session.displayName } ?: return
        val created = ClusterMapPresentation(activity, display, session.profile, session.theme) { surface ->
            activity.runOnUiThread { if (presentation != null) ClusterMapStream.setSurface(surface) }
        }
        // The system dismisses a presentation when its display goes away; the next tick shows a new one.
        created.setOnDismissListener {
            if (presentation === created) {
                presentation = null
                shownFor = null
                ClusterMapStream.setSurface(null)
            }
        }
        try {
            created.show()
            presentation = created
            shownFor = session
        } catch (error: RuntimeException) {
            AppLog.w("ClusterMap: cannot show the dashboard map on ${display.name}: ${error.message}")
        }
    }

    private fun dismiss() {
        val current = presentation ?: return
        presentation = null
        shownFor = null
        ClusterMapStream.setSurface(null)
        runCatching { current.dismiss() }
    }

    private companion object {
        const val TICK_MS = 500L
        /** Matches the BYD outputs: a route the phone stopped describing is not shown for long. */
        const val GUIDANCE_TTL_NS = 30_000_000_000L
    }
}
