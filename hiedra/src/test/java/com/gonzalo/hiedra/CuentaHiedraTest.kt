package com.gonzalo.hiedra

import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Hiedra con la cuenta de la familia de verdad (el emulador oficial de Firebase con las reglas de `nube/reglas.json`): lo que hace
 * Hiedra llega a la cuenta como dice `nube/ESQUEMA.md` y lo que hace otra app u otro móvil ([otro]) llega a Hiedra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CuentaHiedraTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var otro: Familia

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
        Biblioteca.cargada = false; Biblioteca.cargar(ctx); Sagas.cargada = false; Sagas.cargar(ctx)
        otro = Emulador.dispositivo("gonzalo")
        listOf("juegos", "tiempo", "ajustes", "sagas").forEach { otro.coleccion(it) }
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null }

    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun unJuegoEsElMismoEnHiedraYEnAzabache() {
        val (j, _) = Biblioteca.anadirLanzador("Hades", Lanzador.app("com.supergiant.hades"), "Android")
        Biblioteca.poner(j.conEstado(Estado.JUGANDO))
        sincroniza()
        val n = otro.coleccion("juegos").nodo(j.id)!!
        assertEquals("Hades", n.texto("t")); assertEquals("en_curso", n.texto("estado")); assertEquals("android", n.texto("menu"))
        assertEquals("com.supergiant.hades", n.lista("ln").first().let { (it as Map<*, *>)["pk"] })
        // Azabache lo valora y lo da por pasado: Hiedra lo ve tal cual
        otro.coleccion("juegos").pon(j.id, mapOf("nota" to 93L, "estado" to "terminado"))
        sincroniza()
        espera("llega el estado de fuera") { Biblioteca.por(j.id)?.estado == Estado.PASADO }
        assertEquals(93, NotaAzabache.de(NotaAzabache.cargar(ctx), Biblioteca.por(j.id)!!)!!.general)
        // Un juego sin valorar no tiene nota
        val (k, _) = Biblioteca.anadirLanzador("Celeste", Lanzador.app("com.celeste"), "Android")
        assertEquals(null, NotaAzabache.de(NotaAzabache.cargar(ctx), k))
    }

    @Test fun lasHorasDeCadaMovilSeSuman() {
        val (j, _) = Biblioteca.anadirLanzador("Zelda", Lanzador.rom("gba", "Zelda"), "GBA")
        val hoy = java.time.LocalDate.now().toString()
        Biblioteca.poner(Biblioteca.por(j.id)!!.copy(propias = Horas(3_600_000L, 2, mapOf(hoy to 3_600_000L)), externas = mapOf(Fuentes.STEAM to Horas(600_000L))))
        sincroniza()
        val mio = otro.coleccion("tiempo").nodo("${j.id}~${Cuenta.dispositivo}")!!
        assertEquals(3_600_000L, mio.entero("total")); assertEquals(3_600_000L, mio.entero("d$hoy")); assertEquals(2L, mio.entero("veces"))
        assertEquals(600_000L, otro.coleccion("tiempo").nodo("${j.id}~steam")!!.entero("total"))
        // Otro móvil juega media hora: aquí se ve sumado
        otro.coleccion("tiempo").pon("${j.id}~tablet", mapOf("ref" to j.id, "fuente" to "tablet", "total" to 1_800_000L, "d$hoy" to 1_800_000L))
        sincroniza()
        espera("las horas de la tablet se suman") { Biblioteca.por(j.id)?.msJugados == 3_600_000L + 600_000L + 1_800_000L }
        assertEquals(5_400_000L, Biblioteca.por(j.id)!!.dias()[hoy])
    }

    @Test fun laPortadaVaYViene() {
        val (j, _) = Biblioteca.anadirLanzador("Metroid", Lanzador.rom("gba", "Metroid"), "GBA")
        val f = File(ctx.cacheDir, "metroid.png").apply { writeBytes(ByteArrayOutputStream().also { Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2F5D8A.toInt()) }.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()) }
        Biblioteca.poner(Biblioteca.por(j.id)!!.copy(portada = f.absolutePath))
        sincroniza()
        val h = otro.coleccion("juegos").nodo(j.id)!!.texto("portada")
        assertTrue("la portada es una imagen de la cuenta", h.isNotBlank())
        assertArrayEquals(LaFamilia.familia.imagenes.local(h), otro.imagenes.baja(h))
        assertTrue("aquí se ve la de la cuenta", Biblioteca.por(j.id)!!.portada.endsWith(h))
        // «Usar la portada automática» la quita en todas partes
        Biblioteca.poner(Biblioteca.por(j.id)!!.copy(portada = ""))
        sincroniza()
        assertEquals("", otro.coleccion("juegos").nodo(j.id)!!.texto("portada"))
    }

    @Test fun laMigracionTraeLoDeAntesYJuntaLosRepetidos() {
        val uid = LaFamilia.familia.sesion.uid
        // Otro Hiedra subió su biblioteca (formato viejo) con un Baldur's Gate 3 importado con otro id…
        val suyo = Juego("j_bbbbbbbbbbbb", "Baldur's Gate 3", plataforma = "PC", estado = Estado.PASADO, finalizado = 900L, mod = 1_000L, otrasClaves = listOf("juego:baldursgate3"), anadido = 50L)
        Emulador.escribe("/users/$uid/hiedra/dev/tablet", mapOf("name" to "Tablet", "data" to Mezcla.aJson(Mezcla.aBlob("tablet", listOf(suyo), emptyMap()))))
        // …y este móvil tenía el suyo (con otro id y sus horas)
        val mio = Juego("j_aaaaaaaaaaaa", "Baldur's Gate 3", plataforma = "PC", estado = Estado.PENDIENTE, mod = 500L, otrasClaves = listOf("juego:baldursgate3"), propias = Horas(7_200_000L), anadido = 40L)
        File(ctx.filesDir, "biblioteca.json").writeText(JSONObject().put("juegos", JSONArray().put(Biblioteca.aJuego(Biblioteca.aJson(mio).toString().let { JSONObject(it) }).let { Biblioteca.aJson(it) })).toString())
        Migracion.migra(ctx)
        sincroniza()
        espera("se queda uno solo") { Biblioteca.juegos.count { it.titulo == "Baldur's Gate 3" } == 1 }
        val bg = Biblioteca.juegos.single { it.titulo == "Baldur's Gate 3" }
        assertEquals("el de id más bajo", "j_aaaaaaaaaaaa", bg.id)
        assertEquals("pasado gana (el cambio más reciente y el estado más avanzado)", Estado.PASADO, bg.estado)
        assertEquals("con sus horas", 7_200_000L, bg.msJugados)
        sincroniza()
        assertTrue("el otro queda borrado en la cuenta", otro.coleccion("juegos").nodo("j_bbbbbbbbbbbb")!!.borrado)
    }
}
