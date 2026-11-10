package com.gonzalo.marfil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Cómo vas con el reto del año y las cifras de la semana de la portada. */
class RetoYSemanaTest {
    @Test fun elRitmoDelReto() {
        // 9 de noviembre de 2026: día 313 de 365. Con un reto de 30, a estas alturas tocarían 25 (30 × 313 / 365 = 25,7)
        val hoy = LocalDate.of(2026, 11, 9)
        val r = ritmoReto(30, 13, hoy)
        assertEquals(-12, r.diferencia); assertEquals(17, r.quedan); assertEquals(53, r.dias)
        assertEquals("VAS 12 POR DETRÁS · TE QUEDAN 17 EN 53 DÍAS", r.texto())
        assertEquals("VAS AL DÍA · TE QUEDAN 5 EN 53 DÍAS", ritmoReto(30, 25, hoy).texto())
        // 28 × 313 / 365 = 24,01 → tocarían 24; con 27 vas 3 por delante y te queda 1
        assertEquals("VAS 3 POR DELANTE · TE QUEDA 1 EN 53 DÍAS", ritmoReto(28, 27, hoy).texto())
        assertEquals("¡RETO CUMPLIDO!", ritmoReto(12, 12, hoy).texto())
        assertEquals("¡RETO CUMPLIDO! Y 2 DE PROPINA", ritmoReto(12, 14, hoy).texto())
        // El 31 de diciembre queda un día (hoy) y a final de año se espera el reto entero
        val fin = ritmoReto(30, 29, LocalDate.of(2026, 12, 31))
        assertEquals(-1, fin.diferencia); assertEquals("VAS 1 POR DETRÁS · TE QUEDA 1 EN 1 DÍA", fin.texto())
        // El 1 de enero, nada esperado aún
        assertEquals("VAS AL DÍA · TE QUEDAN 30 EN 365 DÍAS", ritmoReto(30, 0, LocalDate.of(2026, 1, 1)).texto())
    }

    @Test fun lasCifrasDeLaSemana() {
        assertEquals("0′", minutosCorto(0)); assertEquals("52′", minutosCorto(52)); assertEquals("3 H 01", minutosCorto(181))
        assertEquals("nada", minutosDia(0)); assertEquals("51 min", minutosDia(51)); assertEquals("2 h 10", minutosDia(130)); assertEquals("1 h", minutosDia(60))
        val hoy = LocalDate.of(2026, 11, 9)
        val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.zip(listOf(0L, 0L, 130L, 51L, 0L, 0L, 0L))
        assertEquals("3 H 01", minutosCorto(semana.sumOf { it.second }))
    }
}
