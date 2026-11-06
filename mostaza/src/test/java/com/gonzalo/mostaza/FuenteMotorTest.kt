package com.gonzalo.mostaza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Lo que eliges de los torrents (en tu cuenta) llega al motor como debe. */
class FuenteMotorTest {
    @Test fun losAjustesDeTuCuentaLleganAlMotor() {
        // De partida: solo con wifi, subida limitada, compartir mientras ves; al cerrar, nunca se queda compartiendo
        val porDefecto = FuenteMotor.paraMotor(AjustesTorrent())
        assertEquals(true, porDefecto.soloWifi)
        assertEquals(SUBIDA_POR_DEFECTO_KBS, porDefecto.subidaMaxKBs)
        assertEquals(0, porDefecto.bajadaMaxKBs)
        assertFalse(porDefecto.seguirCompartiendoAlParar)
        assertEquals(4096, porDefecto.cacheMaxMB)
        // «Sin límite» de subida: para el motor, mucho (él nunca sube sin límite)
        assertEquals(FuenteMotor.SIN_LIMITE_KBS, FuenteMotor.paraMotor(AjustesTorrent(subidaKBs = 0)).subidaMaxKBs)
        // Sin «compartir mientras ves»: lo mínimo
        assertEquals(0, FuenteMotor.paraMotor(AjustesTorrent(compartir = false, subidaKBs = 500)).subidaMaxKBs)
        // Lo demás, tal cual
        val m = FuenteMotor.paraMotor(AjustesTorrent(soloWifi = false, subidaKBs = 500, bajadaKBs = 2000, cacheMB = 8192))
        assertEquals(false, m.soloWifi); assertEquals(500, m.subidaMaxKBs); assertEquals(2000, m.bajadaMaxKBs); assertEquals(8192, m.cacheMaxMB)
    }
}
