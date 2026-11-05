package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** «Sesiones» de una ficha: las mismas pruebas que en Azabache del ordenador (`SesionesTest`). */
class SesionesTest {
    private val hoy = LocalDate.of(2026, 11, 5).toEpochDay()   // un jueves
    private fun min(m: Int) = m * 60_000L

    @Test fun sinNadaNoHaySesiones() {
        assertNull(sesionesDe(emptyMap(), hoy))
        // Menos de un minuto no cuenta como día
        assertNull(sesionesDe(mapOf(hoy to 30_000L), hoy))
    }

    @Test fun pocasSemanasPorDia() {
        val s = sesionesDe(mapOf(hoy to min(30), hoy - 2 to min(90), hoy - 5 to min(20), hoy - 6 to 20_000L), hoy)!!
        assertEquals(1, s.unidad)
        assertEquals(14, s.barras.size)
        assertEquals(hoy - 13, s.desde)
        assertEquals(hoy, s.hasta)
        assertEquals(min(90), s.barras[11])
        assertEquals(3, s.dias)
        assertEquals(min(140), s.totalMs)
        assertEquals(min(90), s.masLargaMs)
        assertEquals(hoy - 2, s.diaMasLarga)
        assertEquals(hoy - 5, s.primera)
        assertEquals(hoy, s.ultima)
    }

    @Test fun variosMesesPorSemana() {
        // De hace 60 días a hoy: barras de lunes a domingo, la última la de esta semana
        val s = sesionesDe(mapOf(hoy - 60 to min(60), hoy - 1 to min(30), hoy to min(15)), hoy)!!
        assertEquals(7, s.unidad)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), s.desde + (s.barras.size - 1) * 7L)
        assertEquals(min(45), s.barras.last())
        assertEquals(min(60), s.barras.first())
        assertEquals(LocalDate.ofEpochDay(s.desde).dayOfWeek, java.time.DayOfWeek.MONDAY)
    }

    @Test fun terminadoLlegaHastaElFinNoHastaHoy() {
        val fin = hoy - 100
        val s = sesionesDe(mapOf(fin - 5 to min(60), fin - 2 to min(45)), hoy, fin)!!
        assertEquals(1, s.unidad)
        assertEquals(fin, s.hasta)
        assertEquals(min(45), s.barras[s.barras.size - 3])
    }

    @Test fun masDeVeinteSemanasPorMes() {
        val s = sesionesDe(mapOf(hoy - 400 to min(60), hoy - 200 to min(30), hoy to min(10)), hoy)!!
        assertEquals(30, s.unidad)
        assertEquals(LocalDate.of(2025, 10, 1).toEpochDay(), s.desde)
        assertEquals(14, s.barras.size)
        assertEquals(min(10), s.barras.last())
        // Como mucho los dos últimos años
        assertEquals(24, sesionesDe(mapOf(hoy - 2000 to min(60), hoy to min(10)), hoy)!!.barras.size)
    }

    @Test fun haceDias() {
        assertEquals("hoy", haceDias(hoy, hoy))
        assertEquals("ayer", haceDias(hoy - 1, hoy))
        assertEquals("hace 4 días", haceDias(hoy - 4, hoy))
        assertEquals("16 oct", haceDias(LocalDate.of(2026, 10, 16).toEpochDay(), hoy))
        assertEquals("16 oct 2025", haceDias(LocalDate.of(2025, 10, 16).toEpochDay(), hoy))
    }
}
