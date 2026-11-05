package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El libro que se propone seguir leyendo (aviso del objetivo y atajo «Seguir leyendo»): el del día más reciente con lectura en Marfil. */
class LecturaAvisoTest {
    @Test fun elDelDiaMasRecienteYConMasRato() {
        val dias = mapOf(
            100L to mapOf(1L to 600_000L, 2L to 1_200_000L),
            103L to mapOf(3L to 300_000L, 4L to 900_000L),
        )
        assertEquals(4L, LecturaAviso.ultimoDe(dias, emptySet()))
    }

    @Test fun unoTerminadoNoSePropone() {
        val dias = mapOf(103L to mapOf(4L to 900_000L), 100L to mapOf(2L to 1_200_000L))
        assertEquals(2L, LecturaAviso.ultimoDe(dias, setOf(4L)))
        assertNull(LecturaAviso.ultimoDe(dias, setOf(2L, 4L)))
    }

    @Test fun sinLecturaNoHayNada() {
        assertNull(LecturaAviso.ultimoDe(emptyMap(), emptySet()))
        assertNull(LecturaAviso.ultimoDe(mapOf(100L to mapOf(1L to 0L)), emptySet()))
    }
}
