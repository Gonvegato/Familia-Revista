package com.gonzalo.gamerate

import com.gonzalo.mau.GuardadoEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Mau en Jonsu: el resumen de ayer del saludo y los retos que se celebran (una vez cada uno). */
class MauJonsuTest {
    private val min = 60_000L
    private val dia = LocalDate.of(2026, 11, 18)

    private fun rato(h: Hermana, ms: Long, palabras: Int = 0) = Rato(h, 0L, "", "", ms, palabras)

    @Test fun resumenDeAyer() {
        assertEquals("Ayer: 45 min leyendo, 1 h 10 min jugando y 600 palabras.",
            MauJonsu.resumenDeAyer(listOf(rato(Hermana.BASTET, 70 * min), rato(Hermana.SESHAT, 30 * min), rato(Hermana.SESHAT, 15 * min), rato(Hermana.THOT, 0, 600))))
        assertEquals("Ayer: 2 h viendo.", MauJonsu.resumenDeAyer(listOf(rato(Hermana.HATHOR, 120 * min))))
        assertEquals("Ayer: 1.204 palabras.", MauJonsu.resumenDeAyer(listOf(rato(Hermana.THOT, 0, 1204))))
        assertEquals("Ayer: 20 min leyendo, 1 h jugando, 52 min viendo y 1 palabra.",
            MauJonsu.resumenDeAyer(listOf(rato(Hermana.SESHAT, 20 * min), rato(Hermana.BASTET, 60 * min), rato(Hermana.HATHOR, 52 * min), rato(Hermana.THOT, 0, 1))))
        // Menos de un minuto no cuenta: solo el saludo
        assertNull(MauJonsu.resumenDeAyer(listOf(rato(Hermana.SESHAT, 50_000L))))
        assertNull(MauJonsu.resumenDeAyer(emptyList()))
    }

    @Test fun conY() {
        assertEquals("", MauJonsu.conY(emptyList()))
        assertEquals("a", MauJonsu.conY(listOf("a")))
        assertEquals("a y b", MauJonsu.conY(listOf("a", "b")))
        assertEquals("a, b y c", MauJonsu.conY(listOf("a", "b", "c")))
    }

    @Test fun retosCumplidosUnaVez() {
        val g = GuardadoEnMemoria()
        val metas = MetasCuenta(30, 20, 0, 500)
        // Pendientes: nada que celebrar (y lo de Hathor, sin meta, ni se mira)
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(metas, 29, 5, 3, 100, dia), g, dia))
        // Se cumple el de libros
        assertEquals("¡Reto cumplido: Libros 2026!", MauJonsu.retosCumplidos(retosDeTodas(metas, 30, 5, 3, 100, dia), g, dia))
        // Ya celebrado: no otra vez (ni aunque mientras se carga la cuenta parezca pendiente)
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(metas, 31, 5, 3, 100, dia), g, dia))
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(metas, 0, 5, 3, 100, dia), g, dia))
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(metas, 31, 5, 3, 100, dia), g, dia))
        // Dos a la vez, juntos
        assertEquals("¡Retos cumplidos: Juegos 2026 y 500 palabras hoy!", MauJonsu.retosCumplidos(retosDeTodas(metas, 31, 20, 3, 512, dia), g, dia))
        // La meta de palabras es de cada día: mañana, otra vez
        val manana = dia.plusDays(1)
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(metas, 31, 20, 3, 0, manana), g, manana))
        assertEquals("¡Reto cumplido: 500 palabras hoy!", MauJonsu.retosCumplidos(retosDeTodas(metas, 31, 20, 3, 640, manana), g, manana))
    }

    @Test fun loYaCumplidoAlMirarPorPrimeraVezNoSeCelebra() {
        val g = GuardadoEnMemoria()
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(MetasCuenta(10, 0, 0, 500), 12, 0, 0, 600, dia), g, dia))
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(MetasCuenta(10, 0, 0, 500), 13, 0, 0, 700, dia), g, dia))
        // El año que viene, el reto de libros vuelve a empezar
        val enero = LocalDate.of(2027, 1, 2)
        assertNull(MauJonsu.retosCumplidos(retosDeTodas(MetasCuenta(10, 0, 0, 500), 1, 0, 0, 0, enero), g, enero))
        assertEquals("¡Reto cumplido: Libros 2027!", MauJonsu.retosCumplidos(retosDeTodas(MetasCuenta(10, 0, 0, 500), 10, 0, 0, 0, enero), g, enero))
    }
}
