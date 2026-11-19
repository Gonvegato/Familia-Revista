package com.gonzalo.cobalto

import com.gonzalo.cobalto.MauEscribir.Meta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lo que Mau dice de la meta del día (MauEscribir.kt: el mismo archivo en el móvil y en el ordenador, y esta prueba la pasan los dos):
 * al pasar la mitad, al quedarse a ≤ 10 % y al cumplirla; si se cruzan varias de golpe, solo la más alta.
 */
class MauEscribirTest {
    @Test fun laMitadElCasiYLaMeta() {
        assertNull(MauEscribir.cruce(0, 249, 500))
        assertEquals(Meta.MITAD, MauEscribir.cruce(240, 250, 500))
        assertNull(MauEscribir.cruce(250, 300, 500))
        // A falta de 50 (el 10 %), ya es «casi»
        assertNull(MauEscribir.cruce(300, 449, 500))
        assertEquals(Meta.CASI, MauEscribir.cruce(440, 450, 500))
        assertNull(MauEscribir.cruce(450, 499, 500))
        assertEquals(Meta.CUMPLIDA, MauEscribir.cruce(499, 500, 500))
        assertNull(MauEscribir.cruce(500, 800, 500))
    }

    @Test fun deGolpeSoloLaMasAlta() {
        assertEquals(Meta.CUMPLIDA, MauEscribir.cruce(0, 700, 500))
        assertEquals(Meta.CASI, MauEscribir.cruce(100, 460, 500))
        // (una meta impar: la mitad de 301 es 151)
        assertNull(MauEscribir.cruce(0, 150, 301))
        assertEquals(Meta.MITAD, MauEscribir.cruce(0, 151, 301))
    }

    @Test fun sinMetaOSinEscribirNada() {
        assertNull(MauEscribir.cruce(0, 1000, 0))
        assertNull(MauEscribir.cruce(300, 300, 500))
        // (al cambiar de día las palabras vuelven a 0: no es cruzar nada)
        assertNull(MauEscribir.cruce(600, 0, 500))
        // Una meta muy pequeña: «casi» es ya cumplirla
        assertEquals(Meta.CUMPLIDA, MauEscribir.cruce(4, 5, 5))
    }

    @Test fun sinMauNoPasaNada() {
        // Sin Mau arrancado (las pruebas, el widget), todo lo de Mau se queda en nada y no falla
        MauEscribir.reiniciaParaPruebas()
        MauEscribir.miraMeta(0, 500, dia = 1L)
        MauEscribir.miraMeta(600, 500, dia = 1L)
        MauEscribir.animaSiNoHasEscrito(0, 18)
        MauEscribir.atascada()
        MauEscribir.pon()
        MauEscribir.reiniciaParaPruebas()
    }
}
