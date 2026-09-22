package com.andrerinas.openheadunit.view

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionRendererPolicyTest {
    @Test fun `verified DiLink uses direct surface output on first install`() {
        assertEquals(0, ProjectionRendererPolicy.resolve(null, "DiLink5.1"))
    }
    @Test fun `unknown devices retain compatible texture default`() {
        assertEquals(1, ProjectionRendererPolicy.resolve(null, "Other"))
        assertEquals(1, ProjectionRendererPolicy.resolve(null, "DiLink6"))
    }
    @Test fun `upgrades preserve every explicitly selected renderer`() {
        for (mode in 0..2) assertEquals(mode, ProjectionRendererPolicy.resolve(mode, "DiLink5.1"))
    }
    @Test fun `invalid imported preferences fall back instead of crashing`() {
        assertEquals(0, ProjectionRendererPolicy.resolve(99, "DiLink5.1"))
        assertEquals(1, ProjectionRendererPolicy.resolve(-1, "Other"))
    }
}
