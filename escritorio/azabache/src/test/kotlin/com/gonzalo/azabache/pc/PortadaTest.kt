package com.gonzalo.azabache.pc

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** «Buscar en todo» y «Tal día como hoy» de la portada: las mismas reglas que en el móvil (`libMatches`, `efemerides`). */
class PortadaTest {
    @Test fun buscarSinTildesYCadaPalabraEnCualquierCampo() {
        assertTrue(coincide("fundacion asimov", "Fundación", "Isaac Asimov"))
        assertTrue(coincide("  ZELDA  ", "The Legend of Zelda: Tears of the Kingdom", "Nintendo"))
        assertFalse(coincide("fundacion herbert", "Fundación", "Isaac Asimov"))
        assertTrue(coincide("", "lo que sea"))
    }

    @Test fun talDiaComoHoyDeOtrosAnos() {
        val hoy = LocalDate.of(2026, 11, 6)
        fun f(id: Long, fin: LocalDate?, valorada: Boolean = true, nota: Long = 80) =
            GAMES to Ficha(0, mapOf("id" to id, "name" to "J$id", "end" to (fin?.toEpochDay() ?: 0L), "general" to nota), valorada)
        val l = listOf(f(1, hoy.minusYears(3)), f(2, hoy.minusYears(1)), f(3, hoy), f(4, hoy.minusYears(2).minusDays(1)), f(5, hoy.minusYears(5), valorada = false), f(6, null))
        val e = efemerides(l, hoy)
        assertEquals(listOf(2L to 1, 1L to 3), e.map { it.second.id to it.third })
        assertEquals("Lo pasaste", verboDeTerminar(GAMES)); assertEquals("Lo leíste", verboDeTerminar(BOOKS))
    }
}
