package com.gonzalo.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioridadesTest {
    private val mb = 1L shl 20
    // 100 piezas de 1 MB, un solo archivo; ventanas pequeñas para verlo claro (un plazo por segundo)
    private val mapa = MapaPiezas(mb.toInt(), 100, 100 * mb)
    private val peli = TramoArchivo(mapa, 0, 100 * mb)
    private val v = Ventanas(criticaBytes = 4 * mb, criticaMinPiezas = 2, criticaMaxPiezas = 10, lecturaBytes = 16 * mb, cabezaBytes = 2 * mb, colaBytes = 2 * mb, ritmoBs = mb)

    @Test fun alEmpezarCabezaYColaPrimero() {
        val plan = Prioridades.calcula(mapa, listOf(EnJuego(peli, emptyList())), { false }, v)
        assertEquals(listOf(7, 7), plan.prioridades.slice(0..1))
        assertEquals(listOf(7, 7), plan.prioridades.slice(98..99))
        assertTrue(plan.prioridades.slice(2..97).all { it == Prioridades.NORMAL })
        // Primero la cabeza (el reproductor la lee antes), luego la cola (el índice del MKV o el moov del MP4)
        assertEquals(mapOf(0 to 0, 1 to 1000, 98 to 2000, 99 to 3000), plan.plazos)
    }

    @Test fun aMitadDePiezaLaVentanaCogeUnaMas() {
        // Desde la mitad de la pieza 50, 4 MB llegan hasta la 54
        assertEquals(50..54, Prioridades.critica(peli, 50 * mb + 123, v))
    }

    @Test fun conUnLectorEnMedio() {
        val plan = Prioridades.calcula(mapa, listOf(EnJuego(peli, listOf(50 * mb))), { false }, v)
        val p = plan.prioridades
        // Lo de antes del lector, baja (salvo la cabeza)
        assertEquals(listOf(7, 7), p.slice(0..1))
        assertTrue(p.slice(2..49).all { it == Prioridades.BAJA })
        // La ventana crítica (4 MB = 4 piezas) a máxima y con plazos seguidos; la de lectura (16 MB), alta; luego normal; la cola, máxima
        assertTrue(p.slice(50..53).all { it == Prioridades.MAXIMA })
        assertTrue(p.slice(54..65).all { it == Prioridades.ALTA })
        assertTrue(p.slice(66..97).all { it == Prioridades.NORMAL })
        assertEquals(listOf(7, 7), p.slice(98..99))
        assertEquals(mapOf(50 to 0, 51 to 1000, 52 to 2000, 53 to 3000), plan.plazos)
    }

    @Test fun loQueYaEstaNoLlevaPlazo() {
        val tiene = setOf(50, 52)
        val plan = Prioridades.calcula(mapa, listOf(EnJuego(peli, listOf(50 * mb))), { it in tiene }, v)
        assertEquals(mapOf(51 to 1000, 53 to 3000), plan.plazos)
    }

    @Test fun trasUnSaltoLaVentanaSeMueve() {
        val antes = Prioridades.calcula(mapa, listOf(EnJuego(peli, listOf(10 * mb))), { false }, v)
        val despues = Prioridades.calcula(mapa, listOf(EnJuego(peli, listOf(80 * mb))), { false }, v)
        assertEquals(setOf(10, 11, 12, 13), antes.plazos.keys)
        assertEquals(setOf(80, 81, 82, 83), despues.plazos.keys)
        assertEquals(Prioridades.BAJA, despues.prioridades[20])
        assertEquals(Prioridades.MAXIMA, despues.prioridades[80])
    }

    @Test fun variosLectoresElMasRecientePrimero() {
        // El primero de la lista es el más reciente (el salto que acaba de pedir el reproductor)
        val plan = Prioridades.calcula(mapa, listOf(EnJuego(peli, listOf(70 * mb, 20 * mb))), { false }, v)
        assertEquals(0, plan.plazos[70])
        assertEquals(1000, plan.plazos[20])
        assertEquals(1000, plan.plazos[71])
        assertEquals(2000, plan.plazos[21])
        // Desde el primer lector (20) todo cuenta: lo de antes, baja
        assertEquals(Prioridades.BAJA, plan.prioridades[19])
        assertEquals(Prioridades.ALTA, plan.prioridades[30])
        assertEquals(Prioridades.NORMAL, plan.prioridades[40])
    }

    @Test fun losOtrosArchivosNada() {
        // Dos archivos: 0..30 MB y 30..100 MB; se ve el segundo
        val segundo = TramoArchivo(mapa, 30 * mb + 512, 70 * mb - 512)
        val plan = Prioridades.calcula(mapa, listOf(EnJuego(segundo, emptyList())), { false }, v)
        assertTrue(plan.prioridades.slice(0..29).all { it == Prioridades.NADA })
        // La pieza 30 es compartida: es la cabeza del segundo
        assertEquals(Prioridades.MAXIMA, plan.prioridades[30])
        assertEquals(0, plan.plazos[30])
        assertTrue(Prioridades.calcula(mapa, emptyList(), { false }, v).prioridades.all { it == 0 })
    }

    @Test fun ventanaCriticaConMinimoYMaximo() {
        // Piezas grandes (16 MB): la ventana crítica de 8 MB sería media pieza, pero son al menos 2
        val grande = MapaPiezas((16 * mb).toInt(), 10, 160 * mb)
        val t = TramoArchivo(grande, 0, 160 * mb)
        assertEquals(3..4, Prioridades.critica(t, 3 * 16 * mb, Ventanas()))
        // Piezas de 16 KB: 8 MB serían 512 piezas, pero son como mucho 48
        val pequena = MapaPiezas(16 * 1024, 10_000, 10_000L * 16 * 1024)
        val t2 = TramoArchivo(pequena, 0, pequena.tamTotal)
        assertEquals(100..147, Prioridades.critica(t2, 100L * 16 * 1024, Ventanas()))
        // Al final del archivo no se pasa
        assertEquals(9..9, Prioridades.critica(t, 159 * mb, Ventanas()))
        assertEquals(5000, Prioridades.pasoMs((16 * mb).toInt(), Ventanas()))
        assertEquals(100, Prioridades.pasoMs(16 * 1024, Ventanas()))
        assertEquals(1000, Prioridades.pasoMs(mb.toInt(), Ventanas()))
    }
}
