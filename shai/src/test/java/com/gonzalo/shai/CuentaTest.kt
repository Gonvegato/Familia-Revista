package com.gonzalo.shai

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tus carreras viajan con tu cuenta (contra el emulador oficial de Firebase, con las reglas de verdad): la colección `shai`, una carrera por
 * nodo `u…` y lo que pasa en ella en nodos `e…`; lo de otro móvil llega aquí. Tu clave de la API va **solo** a `/claves/<uid>` (que no ve ni
 * tu pareja): ni en `/users` ni en ningún otro sitio. La apariencia, en `ajustes/shai`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CuentaTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var otro: Familia
    private var uid = ""

    private fun espera(que: String, segundos: Int = 10, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + segundos * 1000L
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    private fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Before fun prepara() {
        Emulador.exige()
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        EspejoCuenta.olvidaParaPruebas()
        Emulador.exige()
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        Carreras.almacen = AlmacenCuenta()
        kotlinx.coroutines.runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        otro = Emulador.dispositivo("gonzalo")
        otro.coleccion("ajustes"); LaFamilia.coleccion("ajustes"); otro.coleccion("shai"); LaFamilia.coleccion("shai")
        EspejoCuenta.inicia(ctx, SincroP.GRUPOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { LaFamilia.sincronizaYa(); if (EspejoCuenta.intenta(ctx)) break; Thread.sleep(100) }
        assertTrue(EspejoCuenta.intenta(ctx))
        sincroniza()
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; Carreras.almacen = AlmacenMemoria(); ClaveApi.fija = null }

    @Test fun tusCarrerasViajanConLaCuenta() {
        val api = ApiDeMentira()
        ClaveApi.guarda("sk-ant-api03-SECRETA-1234")
        Motor.claude = Claude({ ClaveApi.lee() }, api)
        val (u, fallo) = Motor.crea(Ejemplo.universo())
        assertNull(fallo)
        val (e, _) = Motor.partido(u.id, Ejemplo.partido())
        sincroniza()
        val col = otro.coleccion("shai")
        assertEquals("Álex Cortés", Universo.deJson(col.nodo(u.id)!!.texto("o"))!!.personaje.nombre)
        val allí = Entrada.deJson(col.nodo(e.id)!!.texto("o"))!!
        assertEquals(u.id, col.nodo(e.id)!!.texto("un")); assertEquals("El Niño se sale", allí.prensa!!.titular)
        assertTrue("el gasto, también en la cuenta", col.nodo("gasto")!!.campos.isNotEmpty())

        // En el otro móvil contestas la decisión (cambia la entrada): llega aquí
        otro.coleccion("shai").pon(e.id, mapOf("o" to allí.copy(decision = allí.decision!!.copy(elegida = "Me quedo en casa", estado = Decision.RESUELTA)).aJson()))
        sincroniza()
        espera("llega la decisión") { Carreras.entrada(e.id)?.decision?.abierta == false }

        // La clave: solo en /claves; ni rastro en /users
        val claves = Emulador.pide("GET", "${Emulador.db}/claves/$uid.json?${Emulador.ns}", admin = true).second
        assertTrue(claves.contains("SECRETA"))
        val todo = Emulador.pide("GET", "${Emulador.db}/users/$uid.json?${Emulador.ns}", admin = true).second
        assertFalse("la clave no está en /users", todo.contains("SECRETA") || todo.contains("sk-ant"))
        assertTrue(otro.clave(ClaveApi.CLAVE).contains("SECRETA"))
    }

    @Test fun laAparienciaYElActualizador() {
        Skin.accentId = "verde"; Skin.guardar(ctx)
        Actualizador.ponActivo(ctx, false)
        sincroniza()
        val aj = otro.coleccion("ajustes")
        assertEquals("verde", (aj.nodo("shai")!!.mapa("skin")["skin"] as Map<*, *>)["acento"])
        assertEquals(false, aj.nodo("shai_actualizador")!!.si("on"))
    }
}
