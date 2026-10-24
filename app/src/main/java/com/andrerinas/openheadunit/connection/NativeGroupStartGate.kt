package com.andrerinas.openheadunit.connection

/** Tracks asynchronous group creation independently of connection-state broadcasts. */
internal class NativeGroupStartGate {
    private var token: Int? = null
    @Synchronized fun begin(generation: Int): Boolean {
        if (token == generation) return false
        token = generation
        return true
    }
    @Synchronized fun pending(generation: Int) = token == generation
    @Synchronized fun finish(generation: Int) {
        if (token == generation) token = null
    }
    @Synchronized fun reset() { token = null }
}
