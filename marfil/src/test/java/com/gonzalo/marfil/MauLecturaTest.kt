package com.gonzalo.marfil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuándo habla Mau leyendo: al acercarse el final del capítulo y tras mucho rato seguido. */
class MauLecturaTest {
    @Test fun avisaUnaVezAlQuedarTresPaginasLeyendoHaciaDelante() {
        val m = MauLectura()
        assertNull(m.pagina(4, 6))          // (al abrir el libro: aún no se ha leído nada)
        assertNull(m.pagina(4, 5))
        assertNull(m.pagina(4, 4))
        assertEquals(3, m.pagina(4, 3))
        assertNull(m.pagina(4, 2))          // una vez por capítulo
        assertNull(m.pagina(4, 3))          // (ni volviendo atrás y otra vez adelante)
        assertNull(m.pagina(4, 2))
        assertNull(m.pagina(5, 9))
        assertNull(m.pagina(5, 8))
        assertEquals(2, m.pagina(5, 2))     // en el siguiente capítulo, otra vez (aunque pases de 8 a 2 de golpe)
        assertNull(m.pagina(5, 1))
    }

    @Test fun abrirOSaltarCercaDelFinalNoAvisa() {
        val m = MauLectura()
        assertNull(m.pagina(2, 2))          // abierto ahí
        val n = MauLectura()
        assertNull(n.pagina(1, 10))
        assertNull(n.pagina(3, 1))          // salto con el índice a otro capítulo
        assertNull(n.pagina(3, 0))          // la última no se dice («te quedan 0»)
    }

    @Test fun laRachaSeDiceALaMediaHoraLaHoraYLasDosHoras() {
        val m = MauLectura()
        val dichos = (0..130).mapNotNull { m.racha(it) }
        assertEquals(listOf(30, 60, 120), dichos)
        // Una pausa larga la corta: la siguiente racha vuelve a contar
        assertNull(m.racha(0))
        assertEquals(listOf(30), (1..40).mapNotNull { m.racha(it) })
    }

    @Test fun deNocheEsDeLasDoceALasSeis() {
        assertTrue(MauLectura.deNoche(0)); assertTrue(MauLectura.deNoche(5))
        assertFalse(MauLectura.deNoche(6)); assertFalse(MauLectura.deNoche(23))
    }
}
