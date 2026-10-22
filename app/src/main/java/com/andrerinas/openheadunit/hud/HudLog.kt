package com.andrerinas.openheadunit.hud

import android.util.Log
import com.andrerinas.openheadunit.utils.DiagnosticJournal

/**
 * android.util.Log plus the app's diagnostic journal.
 *
 * BYD firmware drops third-party app tags from logcat, so navigation output lines written with
 * plain Log never reached an exported log or a diagnostic report, and a car where the HUD stayed
 * dark left nothing to read. Info and above are journaled; per-tick debug lines stay logcat-only
 * so a long drive does not push the rest of the history out of the journal.
 */
internal object HudLog {
    fun d(tag: String, msg: String): Int = Log.d(tag, msg)

    fun i(tag: String, msg: String): Int {
        DiagnosticJournal.record(Log.INFO, "$tag: $msg")
        return Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null): Int {
        DiagnosticJournal.record(Log.WARN, "$tag: $msg" + (tr?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
        return if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
    }
}
