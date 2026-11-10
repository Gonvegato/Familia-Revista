package com.gonzalo.shai

import org.junit.Assert.*
import org.junit.Test

/** Las fases: el desplegable sale en las competiciones que las tienen, pesa en la fama y en lo que se cuenta, y la final cuenta para los récords. */
class FasesTest {
    @Test fun lasFasesDeCadaCompeticion() {
        assertEquals("Final", Competiciones.fases("UEFA Champions League")!!.last())
        assertTrue(Competiciones.fases("UEFA Champions League")!!.contains("Fase liga"))
        assertTrue(Competiciones.fases("Copa del Mundo")!!.contains("Dieciseisavos de final"))
        assertTrue(Competiciones.fases("Eurocopa")!!.contains("Octavos de final"))
        assertTrue(Competiciones.fases("Copa del Rey")!!.contains("Cuartos de final"))
        assertNull(Competiciones.fases("LaLiga EA Sports")); assertNull(Competiciones.fases("Amistoso")); assertNull(Competiciones.fases("Amistoso internacional"))
        assertTrue(Competiciones.esFinal("Final")); assertFalse(Competiciones.esFinal("Final de la repesca")); assertFalse(Competiciones.esFinal("Semifinales"))
    }

    @Test fun lasDeSeleccionesSalenEnLaLista() {
        val l = Competiciones.lista(Ejemplo.universo())
        assertTrue(l.containsAll(listOf("Copa del Mundo", "Eurocopa", "UEFA Nations League")))
        assertTrue(l.indexOf("Copa del Mundo") < l.indexOf(Competiciones.SELECCION))
    }

    @Test fun laFinalPesaMasQueLosGrupos() {
        val p = Partido("2026/27", "Copa del Mundo", "Portugal", true, 2, 1, 90, 2, 0, 8.5, fase = "Fase de grupos")
        val grupos = Vida.famaDelPartido(Rol.JUGADOR, p, 40, 19)
        val final = Vida.famaDelPartido(Rol.JUGADOR, p.copy(fase = "Final"), 40, 19)
        assertTrue("grupos $grupos, final $final", final > grupos)
        val t = Narrador.partido(Ejemplo.universo(), p.copy(fase = "Final"))
        assertTrue(t.contains("(Copa del Mundo, Final")); assertTrue(t.contains("ES UNA FINAL"))
        assertEquals("Final", Partido.de(p.copy(fase = "Final").aMapa()).fase)
    }

    @Test fun cadaTorneoDeSeleccionesSeQuedaConSuNombre() {
        val l = Competiciones.lista(Ejemplo.universo())
        assertEquals("Mundial 2026", Competiciones.sinPreguntar("Mundial 2026", l, emptyMap()))
        assertEquals("Copa del Mundo", Competiciones.sinPreguntar("copa del mundo", l, emptyMap()))
        assertEquals("Copa", Competiciones.sinPreguntar("Copa", l, emptyMap()))
    }
}
