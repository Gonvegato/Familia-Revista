package com.gonzalo.hiedra.pc

import org.junit.Assert.*
import org.junit.Test

/** Mau al volver de jugar: lo comenta si fue un rato (5 min o más) y no hace mucho. */
class MauHiedraTest {
    private val M = 60_000L

    @Test fun soloSiFueUnRato() {
        assertNull(MauHiedra.jugado("Celeste", 5 * M - 1, 0))
        val j = MauHiedra.jugado("Celeste", 83 * M, 10 * M)!!
        assertEquals(83, j.minutos); assertEquals("Celeste", j.nombre); assertEquals(10 * M, j.fin)
    }

    @Test fun loDeHaceHorasYaNoSeDice() {
        val j = MauHiedra.jugado("Celeste", 20 * M, 0)!!
        assertTrue(MauHiedra.decirlo(j, 60 * M))
        assertFalse(MauHiedra.decirlo(j, MauHiedra.CADUCA + 1))
    }

    @Test fun alAcabarUnaPartidaQuedaPorDecir() {
        MauHiedra.porDecir = null
        javax.swing.SwingUtilities.invokeAndWait { MauHiedra.alTerminar(Pc.Partida("local:1", "Hades", "", 0, ""), 30 * M, 30 * M) }
        assertEquals(30, MauHiedra.porDecir?.minutos)
        javax.swing.SwingUtilities.invokeAndWait { MauHiedra.porDecir = null; MauHiedra.alTerminar(Pc.Partida("local:1", "Hades", "", 0, ""), 2 * M, 2 * M) }
        assertNull(MauHiedra.porDecir)
    }
}
