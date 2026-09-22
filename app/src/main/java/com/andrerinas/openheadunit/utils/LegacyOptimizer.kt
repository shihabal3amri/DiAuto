package com.andrerinas.openheadunit.utils

import android.os.Build
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Performance optimizations for legacy devices (Android 4.x - 5.x).
 * Focuses on Thread Priority and Memory Management (Buffer Recycling).
 */
object LegacyOptimizer {

    private const val TAG = "LegacyOptimizer"
    private const val MAX_POOL_SIZE = 5
    private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer for video frames

    private val bufferPool = ConcurrentLinkedQueue<ByteArray>()

    /**
     * Boosts thread priority for critical streaming threads on old devices.
     */
    fun setHighPriority() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                AppLog.d("LegacyOptimizer: Thread priority boosted to URGENT_AUDIO")
            } catch (e: Exception) {
                AppLog.e("LegacyOptimizer: Failed to set thread priority", e)
            }
        }
    }

    /**
     * Retrieves a buffer from the pool or creates a new one if empty.
     */
    fun acquireBuffer(): ByteArray {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return ByteArray(BUFFER_SIZE)
        }
        
        return bufferPool.poll() ?: ByteArray(BUFFER_SIZE)
    }

    /**
     * Returns a buffer to the pool for reuse.
     */
    fun releaseBuffer(buffer: ByteArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (bufferPool.size < MAX_POOL_SIZE) {
                bufferPool.offer(buffer)
            }
        }
    }
}
