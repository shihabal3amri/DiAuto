package com.andrerinas.openheadunit.decoder

/** Spaces short decode bursts without accumulating a delayed presentation queue. */
internal class FrameReleasePacer(refreshRate: Float) {
    private val interval = (1_000_000_000.0 / refreshRate.takeIf { it.isFinite() && it >= 24f && it <= 240f }.let { it ?: 60f }).toLong()
    private var next = 0L

    /** null means the surface already has two refresh intervals of scheduled work. */
    fun schedule(now: Long): Long? {
        val target = maxOf(now, next)
        if (target - now > interval * 2) return null
        next = target + interval
        return target
    }
}
