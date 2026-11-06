package com.gonzalo.azabache.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Las cifras del año, el tiempo de cada mes, «Hoy» y la afinidad con tu pareja: sin pantalla ni cuenta. */
class CifrasTest {
    private val hoy = LocalDate.of(2026, 11, 5)
    private fun ficha(id: Long, nombre: String, valorada: Boolean, nota: Int = 0, fin: LocalDate? = null, estado: Long = 0L, creador: String = "", sinNota: Boolean = false) =
        Ficha(0, mapOf("id" to id, "name" to nombre, "general" to nota.toLong(), "gfx" to nota.toLong(), "story" to 0L, "music" to 0L, "gameplay" to 0L,
            "end" to (fin?.toEpochDay() ?: 0L), "status" to estado, "creator" to creador), valorada, ref = "j_$id", sinNota = sinNota)

    @Test fun cifrasDeUnAno() {
        val l = listOf(
            ficha(1, "Hades", true, 95, hoy.minusDays(3), creador = "Supergiant"),
            ficha(2, "Hades II", true, 85, hoy.minusMonths(2), creador = "Supergiant"),
            ficha(3, "Celeste", true, 0, hoy.minusDays(2), sinNota = true),
            ficha(4, "Antiguo", true, 70, LocalDate.of(2025, 5, 1)),
            ficha(5, "Outer Wilds", false, estado = 1L),
            ficha(6, "Dejado", false, fin = hoy.minusDays(1), estado = 2L),
        )
        val c = cifrasDe(l, 2026, hoy)
        assertEquals(3, c.terminados)
        assertEquals(1, c.enCurso)
        assertEquals(1, c.abandonados)
        assertEquals(25, c.tasaAbandono)
        // Lo «sin valorar» no cuenta en la media (nunca es un 0)
        assertEquals(90.0, c.media!!, 0.001)
        assertEquals("Hades", c.mejor!!.nombre)
        assertEquals(1, c.de90)
        assertEquals(listOf("Supergiant" to 2), c.creadores)
        assertEquals(2, c.porMes[10])   // noviembre: Hades y Celeste
        assertEquals(4, cifrasDe(l, null, hoy).terminados)
    }

    @Test fun tiempoDeUnAno() {
        val porDia = mapOf(
            "2026-11-05" to mapOf("j_1" to 3_600_000L, "l_1" to 1_800_000L),
            "2026-11-04" to mapOf("j_1" to 1_200_000L),
            "2026-10-01" to mapOf("j_2" to 30_000L),
            "2025-12-31" to mapOf("j_1" to 9_000_000L),
        )
        val t = tiempoDe(porDia, setOf("j_1", "j_2"), 2026, hoy)
        assertEquals(4_830_000L, t.totalMs)
        assertEquals(2, t.dias)       // el día de 30 s no cuenta
        assertEquals(2, t.racha)
        assertEquals(4_800_000L, t.porMes[10])
        assertEquals("j_1", t.top.first().first)
    }

    @Test fun hoyYLosDiasSeguidos() {
        assertEquals("40 min leyendo · 2 h jugando · 500 palabras", textoHoy(DiaHoy(40 * 60_000L, 120 * 60_000L, 500)))
        assertEquals("", textoHoy(DiaHoy(30_000L, 0, 0)))
        val d = hoy.toEpochDay()
        val cuenta = setOf(d - 1, d - 2, d - 3, d - 5)
        // Hoy aún nada: cuenta desde ayer
        assertEquals(3, rachaDe(d) { it in cuenta })
        assertEquals(4, rachaDe(d) { it in cuenta + d })
    }

    @Test fun afinidadConTuPareja() {
        val mias = listOf(ficha(1, "Hades", true, 95), ficha(2, "Disco Elysium", true, 91), ficha(3, "Celeste", true, 0, sinNota = true))
        val suyas = listOf(ficha(11, "hades", true, 88), ficha(12, "Disco Elysium", true, 70), ficha(13, "Celeste", true, 80))
        val a = afinidadDe(mias, suyas)!!
        assertEquals(2, a.comunes)
        assertEquals(14.0, a.distancia, 0.001)
        assertEquals("Hades", a.masDeAcuerdo!!.first)
        assertEquals("Disco Elysium", a.masLejos!!.first)
        assertTrue(a.osEncantan.contains("Hades"))
        assertNull(afinidadDe(mias, emptyList()))
    }
}
