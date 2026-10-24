package com.andrerinas.openheadunit.connection
import org.junit.Assert.*
import org.junit.Test

class NativeGroupStartGateTest {
    @Test fun `disconnect and enabled broadcast cannot launch a second create`() {
        val ownership = P2pSessionOwnership()
        val gate = NativeGroupStartGate()
        ownership.start()
        val gen = ownership.generation
        assertTrue(gate.begin(gen))
        // The separate connection-state boolean may be reset by the removal broadcast.
        ownership.setEnabled(true)
        assertFalse(gate.begin(gen))
        assertTrue(gate.pending(gen))
    }
    @Test fun `old completion cannot unlock a restarted group attempt`() {
        val gate = NativeGroupStartGate()
        assertTrue(gate.begin(1))
        gate.reset()
        assertTrue(gate.begin(2))
        gate.finish(1)
        assertFalse(gate.begin(2))
        gate.finish(2)
        assertTrue(gate.begin(2))
    }
    @Test fun `old group cannot finish the next creation before its callback`() {
        val state = P2pSessionOwnership()
        val gate = NativeGroupStartGate()
        state.start(); state.expect("DIRECT-old"); state.created(state.generation)
        assertTrue(gate.begin(state.generation))
        state.beginCreate()
        assertTrue(state.owns(true, "DIRECT-old"))
        assertFalse(state.created)
        assertTrue(gate.pending(state.generation))
        state.expect("DIRECT-new")
        assertFalse(state.created)
        assertTrue(state.created(state.generation))
    }
    @Test fun `new start invalidates pending stop cleanup`() {
        val state = P2pSessionOwnership()
        state.start(); state.stop()
        val cleanupToken = state.generation
        state.start()
        assertFalse(state.current(cleanupToken))
    }
}
