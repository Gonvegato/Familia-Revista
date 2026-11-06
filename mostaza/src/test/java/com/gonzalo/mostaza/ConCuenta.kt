package com.gonzalo.mostaza

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/**
 * Lo común de las pruebas con la cuenta de verdad (el emulador oficial de Firebase con las reglas de `nube/reglas.json`): Mostaza dentro
 * de la cuenta «gonzalo» y [otro], otro dispositivo (u otra app de la familia: Azabache) de la misma cuenta.
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
        Biblioteca.olvidaParaPruebas()
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar(usuario, "secreto1") }
        Biblioteca.cargar(ctx)
        Addons.cargar(ctx)
        Addons.http = SoloLocal
    }

    /** En las pruebas no se sale a internet: solo los servidores de mentira de este ordenador. */
    object SoloLocal : Http {
        override fun get(url: String, cabeceras: Map<String, String>): String =
            if (url.startsWith("http://127.0.0.1")) HttpJava(3_000).get(url, cabeceras) else throw java.io.IOException("Sin internet en las pruebas")
    }

    protected fun preparaCuenta(colecciones: List<String> = listOf("titulos", "progreso", "tiempo", "ajustes", "azabache")) {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        entra()
        otro = Emulador.dispositivo("gonzalo")
        colecciones.forEach { otro.coleccion(it) }
    }

    protected fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; Biblioteca.olvidaParaPruebas(); Addons.olvidaParaPruebas() }
}
