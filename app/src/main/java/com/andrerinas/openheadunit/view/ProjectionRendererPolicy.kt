package com.andrerinas.openheadunit.view

/** Preserve explicit choices. Only the verified DiLink 5.1 gets direct surface output by default. */
object ProjectionRendererPolicy {
    fun resolve(storedMode: Int?, model: String): Int {
        if (storedMode in 0..2) return storedMode!!
        return if (model.equals("DiLink5.1", ignoreCase = true)) 0 else 1
    }
}
