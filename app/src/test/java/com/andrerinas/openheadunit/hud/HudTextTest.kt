package com.andrerinas.openheadunit.hud

import org.junit.Assert.assertEquals
import org.junit.Test

class HudTextTest {
    @Test fun `distances the HUD would show as now are shown as 20 m`() {
        assertEquals(20, HudText.distance(0))
        assertEquals(20, HudText.distance(10))
        assertEquals(20, HudText.distance(19))
        assertEquals(20, HudText.distance(20))
        assertEquals(480, HudText.distance(480))
    }

    @Test fun `romanian diacritics fold to letters the HUD can draw`() {
        assertEquals("Str. Studentilor", HudText.street("Str. Studenților"))
        assertEquals("Stefan cel Mare si Sfant", HudText.street("Ștefan cel Mare și Sfânt"))
        assertEquals("Mircea cel Batran", HudText.street("Mircea cel Bătrân"))
        assertEquals("Soseaua Hincesti", HudText.street("Șoseaua Hîncești"))
    }

    @Test fun `non latin scripts pass through unchanged`() {
        assertEquals("ул. Йошкар-Ола", HudText.street("ул. Йошкар-Ола"))
        assertEquals("长安街", HudText.street("长安街"))
        assertEquals("Bd. Moscova", HudText.street("Bd. Moscova"))
    }
}
