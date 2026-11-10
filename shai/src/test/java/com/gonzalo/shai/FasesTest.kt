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

/** La prórroga y los penaltis: se guardan, Claude los recibe y pesan; y el Barça, al día. */
class PenaltisTest {
    @Test fun unaTandaDePenaltisEnCuartos() {
        val p = Partido("2026/27", "Copa del Mundo", "Rumanía", true, 2, 2, 120, 2, 1, 8.8, fase = "Cuartos de final", prorroga = true, penaltisFavor = 5, penaltisContra = 4, suPenalti = 1)
        assertTrue(p.huboPenaltis); assertTrue(p.ganaPenaltis)
        assertEquals(p, Partido.de(p.aMapa()))
        val t = p.resumen(Rol.JUGADOR)
        assertTrue(t, t.contains("2-2 tras la prórroga (empate) y se decidió en los PENALTIS: 5-4 en la tanda (su equipo gana la tanda y pasa); él tiró y MARCÓ su penalti"))
        // (un partido discreto, para que no llegue al tope de la jornada: marcar el penalti y pasar suma; fallarlo y caer resta)
        val discreto = p.copy(goles = 0, asistencias = 0, nota = 6.8)
        assertTrue(Vida.famaDelPartido(Rol.JUGADOR, discreto, 40, 25) > Vida.famaDelPartido(Rol.JUGADOR, discreto.copy(suPenalti = 2, penaltisFavor = 3), 40, 25))
        assertTrue(Competiciones.eliminatoria("Copa del Mundo", "Cuartos de final"))
        assertFalse(Competiciones.eliminatoria("Copa del Mundo", "Fase de grupos")); assertFalse(Competiciones.eliminatoria("LaLiga EA Sports", ""))
        assertTrue(Competiciones.eliminatoria("Copa del Rey", ""))
        // (un partido de antes, sin los campos nuevos, se lee sin penaltis)
        assertFalse(Partido.de(p.aMapa() - "pnf" - "pnc").huboPenaltis)
    }

    @Test fun elBarcaYaNoEstaEnProblemasEconomicos() {
        assertTrue(Narrador.contexto(Ejemplo.universo(), emptyList(), emptyList()).contains("FC Barcelona volvió en 2026 a la regla 1:1"))
    }
}

/** La apuesta: cualquier grande que pueda permitírselo (también su club), sin favoritos. */
class ApuestaTextoTest {
    @Test fun cualquierGrandePuedeApostarTambienSuClub() {
        val u = Ejemplo.universo().copy(valor = 150_000_000)
        val t = Narrador.sueldoDeChaval(u)
        assertTrue(t.contains("también su propio club")); assertTrue(t.contains("no favorezcas a ninguno"))
        assertFalse(t.contains("estrellas en ese puesto"))
        assertTrue(Narrador.AL_DIA.contains("puede hacer una apuesta como cualquier otro grande"))
    }
}
