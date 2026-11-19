package com.gonzalo.azabache.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mau en Jonsu del ordenador: el resumen de ayer del saludo. */
class MauJonsuTest {
    private val min = 60_000L

    @Test fun resumenDeAyer() {
        assertEquals("Ayer: 45 min leyendo, 1 h 10 min jugando y 600 palabras.", MauJonsu.resumenDeAyer(DiaHoy(45 * min, 70 * min, 600)))
        assertEquals("Ayer: 2 h jugando.", MauJonsu.resumenDeAyer(DiaHoy(0, 120 * min, 0)))
        assertEquals("Ayer: 30 min leyendo y 1.204 palabras.", MauJonsu.resumenDeAyer(DiaHoy(30 * min, 0, 1204)))
        assertEquals("Ayer: 1 palabra.", MauJonsu.resumenDeAyer(DiaHoy(0, 0, 1)))
        assertNull(MauJonsu.resumenDeAyer(DiaHoy(50_000L, 0, 0)))
    }
}
