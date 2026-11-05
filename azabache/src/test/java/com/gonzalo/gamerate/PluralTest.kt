package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Test

/** Las pestañas de la biblioteca van en plural (son listas), como en Hiedra y Marfil. */
class PluralTest {
    @Test fun losEstadosPasanAPlural() {
        assertEquals("Pendientes", enPlural("Pendiente"))
        assertEquals("Abandonados", enPlural("Abandonado"))
        assertEquals("Pasados", enPlural("Pasados"))
        assertEquals("", enPlural(""))
    }
}
