package com.gonzalo.marfil

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Lo que subrayas, anotas y marcas en Marfil del ordenador llega al lector del móvil: los nodos que escribe el ordenador DE VERDAD
 * (`herramientas/casos/marfil-marcas.json`, que deja su `MarcasTest`) se ponen en la cuenta (el emulador de Firebase) con sus horas, y el
 * código de Android (LecturaNube, MezclaLectura, AjustesLector) los lee: el subrayado con su nota en el sitio de siempre (el mismo id que
 * calcula el móvil, para que una edición aquí y allí sea la misma marca), el quitado no vuelve, el marcador está, y los ajustes del lector
 * (los del libro y los de partida) son los que elegiste en el ordenador.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarcasEscritorioTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var otro: Familia

    private fun raiz(): File { var d: File? = File("").absoluteFile; while (d != null && !File(d, "herramientas/casos/marfil-marcas.json").exists()) d = d.parentFile; return d ?: error("no encuentro herramientas/casos") }
    private val caso: JSONObject by lazy { JSONObject(File(raiz(), "herramientas/casos/marfil-marcas.json").readText()) }

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
        LaFamilia.coleccion("ajustes")   // (la que escucha SincroP: los ajustes del lector)

        otro = Emulador.dispositivo("gonzalo")
        listOf("libros", "lectura", "marcas", "tiempo", "ajustes").forEach { otro.coleccion(it) }
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null }

    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok); shadowOf(Looper.getMainLooper()).idle() }

    /** Un nodo de la cuenta tal cual (con su `m`: la hora de cada campo), escrito por «el ordenador» campo a campo con su hora. */
    private fun ponTalCual(col: String, ref: String, n: JSONObject) {
        val m = n.optJSONObject("m") ?: JSONObject()
        for (k in n.keys()) if (k != "m" && k != "u") otro.coleccion(col).pon(ref, mapOf(k to LecturaNube.aKotlin(n.get(k))), m.optLong(k, 1L).coerceAtLeast(1L))
    }

    @Test fun loQueSubrayasEnElOrdenadorLlegaAlLectorDelMovil() {
        val kid = caso.getString("kid")
        Biblioteca.poner(Libro("x", caso.getString("titulo"), caso.getString("autor"), archivo = "dune.epub", anadido = 5L))
        assertEquals("el mismo libro", kid, Biblioteca.kid(claveDe(caso.getString("titulo"), caso.getString("autor"))))
        val nodos = caso.getJSONObject("nodos")
        for (ref in nodos.keys()) ponTalCual("marcas", ref, nodos.getJSONObject(ref))
        ponTalCual("lectura", kid, caso.getJSONObject("lectura"))
        ponTalCual("ajustes", "marfil", caso.getJSONObject("ajustes"))
        sincroniza()
        espera("el subrayado del ordenador está en el lector") { EstadoLectura.estado(ctx, kid).optJSONArray("notes")?.length() == 1 }
        val st = EstadoLectura.estado(ctx, kid)
        val n = st.getJSONArray("notes").getJSONObject(0)
        // Lo que usa lector.js para pintarlo y para su menú (index es el número del capítulo: se compara con ===)
        assertEquals("El miedo mata la mente.", n.getString("text")); assertEquals("La letanía contra el miedo.", n.getString("note"))
        assertTrue(n.getString("value").startsWith("epubcfi(/6/2!/4/4,"))
        assertTrue(n.get("index") is Number); assertEquals(0, n.getInt("index"))
        assertEquals("Arrakis", n.getString("label")); assertEquals(1_700_000_000_000L, n.getLong("created"))
        // Es la misma marca para los dos (el móvil la nombra igual): editarla aquí la cambia allí, no hace otra
        val ref = nodos.keys().asSequence().first { nodos.getJSONObject(it).optJSONObject("n")?.optString("text") == "El miedo mata la mente." }
        assertEquals(ref, LecturaNube.idMarca(kid, n))
        // El que quitaste en el ordenador no vuelve (queda como borrado, con su hora)
        assertTrue(st.optJSONObject("borr_notes")?.length() == 1)
        // El marcador
        val mk = st.getJSONArray("marks").getJSONObject(0)
        assertEquals("Capítulo 2", mk.getString("label")); assertEquals(0.75, mk.getDouble("fraction"), 1e-9); assertEquals(1, mk.getInt("index"))
        assertTrue(mk.getString("value").startsWith("epubcfi(/6/4!"))
        // En la ficha del libro, «Subrayados y notas»
        val ficha = notasDe(st)
        assertEquals(1, ficha.size); assertEquals("La letanía contra el miedo.", ficha[0].nota)
        // Los ajustes del lector: los del libro (los que elegiste en el ordenador para él) y, para los demás, los de partida
        espera("los ajustes del libro") { EstadoLectura.estado(ctx, kid).optJSONObject("settings")?.optString("theme") == "sepia" }
        assertEquals("garamond", EstadoLectura.estado(ctx, kid).getJSONObject("settings").getString("font"))
        // (lo que hace SincroP al cambiar `ajustes/marfil`: AjustesLector.aplicar con su campo `lector`)
        assertTrue(AjustesLector.aplicar(ctx, LaFamilia.coleccion("ajustes").nodo("marfil")!!.texto("lector")))
        assertEquals("garamond", JSONObject(ctx.getSharedPreferences("lector", 0).getString("ajustes", "{}")!!).optString("font"))
        assertEquals("sepia", JSONObject(ctx.getSharedPreferences("lector", 0).getString("ajustes", "{}")!!).optString("theme"))
        assertFalse("lo de cada dispositivo no se toca", JSONObject(ctx.getSharedPreferences("lector", 0).getString("ajustes", "{}")!!).has("brightness"))
    }
}
