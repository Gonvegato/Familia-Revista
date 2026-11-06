package com.gonzalo.torrent

import org.junit.Assert.assertEquals
import org.junit.Test

class RangosPiezasTest {
    @Test fun rangos() {
        val t = 1000L
        assertEquals(Rango.Todo, Rango.analiza(null, t))
        assertEquals(Rango.Todo, Rango.analiza("", t))
        assertEquals(Rango.Parte(0, 999), Rango.analiza("bytes=0-", t))
        assertEquals(Rango.Parte(100, 199), Rango.analiza("bytes=100-199", t))
        assertEquals(Rango.Parte(100, 999), Rango.analiza("bytes=100-5000", t))
        assertEquals(Rango.Parte(999, 999), Rango.analiza("bytes=999-999", t))
        assertEquals(Rango.Parte(900, 999), Rango.analiza("bytes=-100", t))
        assertEquals(Rango.Parte(0, 999), Rango.analiza("bytes=-5000", t))
        assertEquals(Rango.Parte(5, 9), Rango.analiza("BYTES= 5 - 9 ", t))
        // Solo el primer tramo
        assertEquals(Rango.Parte(0, 9), Rango.analiza("bytes=0-9,20-29", t))
        // Después del final
        assertEquals(Rango.Imposible, Rango.analiza("bytes=1000-", t))
        assertEquals(Rango.Imposible, Rango.analiza("bytes=2000-3000", t))
        assertEquals(Rango.Imposible, Rango.analiza("bytes=-0", t))
        // Lo que no se entiende se ignora: el archivo entero
        assertEquals(Rango.Todo, Rango.analiza("bytes=9-5", t))
        assertEquals(Rango.Todo, Rango.analiza("bytes=-", t))
        assertEquals(Rango.Todo, Rango.analiza("bytes=a-b", t))
        assertEquals(Rango.Todo, Rango.analiza("items=0-5", t))
        assertEquals(Rango.Todo, Rango.analiza("bytes=99999999999999999999999-", t))
        assertEquals(100L, (Rango.analiza("bytes=-100", t) as Rango.Parte).bytes)
    }

    @Test fun piezasDeUnArchivoEnMedio() {
        // Tres archivos seguidos: 1000 + 2500 + 700 bytes, en piezas de 1024
        val m = MapaPiezas(1024, 5, 4200)
        val b = TramoArchivo(m, 1000, 2500)
        assertEquals(0, b.primera)
        assertEquals(3, b.ultima)
        assertEquals(4, b.numPiezas)
        assertEquals(0, b.piezaEn(0))
        assertEquals(0, b.piezaEn(23))
        assertEquals(1, b.piezaEn(24))
        assertEquals(3, b.piezaEn(2499))
        assertEquals(3, b.piezaEn(99999))  // se queda en el archivo
        // Del byte 0 del archivo (1000 del torrent) al final de su pieza (1024): 24 bytes
        assertEquals(24L, b.hastaFinDePieza(0))
        assertEquals(1024L, b.hastaFinDePieza(24))
        // La última pieza del archivo acaba donde acaba él (3500), no la pieza (4096)
        assertEquals(1L, b.hastaFinDePieza(2499))
        assertEquals(2500L - 2072, b.hastaFinDePieza(2072))
        assertEquals(1..2, b.piezas(100, 1500))
        assertEquals(3..3, b.piezas(2499, 1000))
        assertEquals(0..0, b.piezas(0, 0))
        assertEquals(0L, b.inicioEnArchivo(0))
        assertEquals(24L, b.inicioEnArchivo(1))
        val ultimo = TramoArchivo(m, 3500, 700)
        assertEquals(3, ultimo.primera)
        assertEquals(4, ultimo.ultima)
        assertEquals(4200L, m.fin(4))
    }

    @Test fun listosDesde() {
        val m = MapaPiezas(100, 10, 1000)
        val a = TramoArchivo(m, 50, 900)  // piezas 0..9
        val tiene = setOf(0, 1, 2, 4)
        // Desde el byte 0 del archivo (50 del torrent): piezas 0, 1 y 2 seguidas, hasta el 300 del torrent = 250 del archivo
        assertEquals(250L, a.listosDesde(0) { it in tiene })
        assertEquals(200L, a.listosDesde(50) { it in tiene })
        assertEquals(0L, a.listosDesde(300) { it in tiene })
        assertEquals(900L - 360, a.listosDesde(360) { true })
        assertEquals(0L, a.listosDesde(900) { true })
    }
}
