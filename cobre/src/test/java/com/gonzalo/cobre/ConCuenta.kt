package com.gonzalo.cobre

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/**
 * Lo común de las pruebas con la cuenta de verdad (el emulador oficial de Firebase con las reglas de `nube/reglas.json`): Cobre dentro de
 * la cuenta «gonzalo» y [otro], otro dispositivo de la misma cuenta (otro Cobre, o Cobre del ordenador).
 */
abstract class ConCuenta {
    protected val ctx: Context get() = ApplicationProvider.getApplicationContext()
    protected lateinit var otro: Familia
    protected var uid = ""

    protected fun espera(que: String, segundos: Int = 10, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + segundos * 1000L
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    protected fun entra(usuario: String = "gonzalo") {
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        EspejoCuenta.olvidaParaPruebas()
        Listas.olvidaParaPruebas(); Tele.olvidaParaPruebas()
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar(usuario, "secreto1") }
        Listas.cargar(ctx); Tele.cargar(ctx)
        Tele.red = SoloLocal
    }

    /** En las pruebas no se sale a internet. */
    object SoloLocal : Red {
        override fun abre(url: String, cabeceras: Map<String, String>): Respuesta = throw com.gonzalo.iptv.IptvError("Sin internet en las pruebas")
    }

    protected fun preparaCuenta(colecciones: List<String> = listOf(DatosCuenta.FAVORITOS, DatosCuenta.GRUPOS, DatosCuenta.PROGRESO, DatosCuenta.ESTADO, "ajustes")) {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        entra()
        otro = Emulador.dispositivo("gonzalo")
        colecciones.forEach { otro.coleccion(it); LaFamilia.coleccion(it) }
    }

    protected fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; Listas.olvidaParaPruebas(); Tele.olvidaParaPruebas(); MiCuenta.fijo = null }
}
