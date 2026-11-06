package com.gonzalo.marfil

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Lo que eliges en Marfil (el orden de cada estante, tener aquí los libros de tu kDrive, la web de imágenes, «actualizar sola») vive en tu
 * cuenta (`ajustes/marfil_*`) y llega a tus otros dispositivos ([otro]); y lo leído en un escrito de Cobalto, también. Con la cuenta de
 * verdad (el emulador de Firebase con las reglas de `nube/reglas.json`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesCuentaTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var otro: Familia

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (cond()) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    private fun entra() {
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        EspejoCuenta.olvidaParaPruebas()
        Skin.cargar(ctx); Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gonzalo", "secreto1") }
    }

    private fun arranca() {
        EspejoCuenta.inicia(ctx, SincroP.GRUPOS)
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { LaFamilia.sincronizaYa(); if (EspejoCuenta.intenta(ctx)) break; Thread.sleep(100) }
        assertTrue("lo de antes pasa a la cuenta", EspejoCuenta.intenta(ctx))
        sincroniza()
    }

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        otro = Emulador.dispositivo("gonzalo")
        listOf("ajustes", "lectura", "marcas", "tiempo", "capitulos").forEach { otro.coleccion(it) }
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null; EspejoCuenta.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otro.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok); shadowOf(Looper.getMainLooper()).idle() }

    private fun enLaCuenta(nodo: String) = otro.coleccion("ajustes").nodo(nodo)

    @Test fun loQueEligesAquiLlegaAlOtroYAlReves() {
        entra(); arranca()
        OrdenGuardado.pon(ctx, Estado.LEYENDO, OrdenLibros.TITULO)
        NubeLibros.ponAutoBajar(ctx, false)
        EspejoCuenta.prefs(ctx, Internet.GRUPO).edit().putString("fuente", "google").apply()
        Actualizador.ponActivo(ctx, false)
        sincroniza()
        assertEquals("TITULO", enLaCuenta("marfil_orden")!!.texto("LEYENDO"))
        assertEquals(false, enLaCuenta("marfil_nube")!!["auto_bajar"])
        assertEquals("google", enLaCuenta("marfil_internet")!!.texto("fuente"))
        assertEquals(false, enLaCuenta("marfil_actualizador")!!["on"])
        // Otro dispositivo cambia cosas: llegan aquí
        val a = otro.coleccion("ajustes")
        a.pon("marfil_orden", mapOf("PENDIENTE" to "AUTOR"))
        a.pon("marfil_actualizador", mapOf("on" to true))
        sincroniza()
        espera("llega el orden") { OrdenGuardado.de(ctx, Estado.PENDIENTE) == OrdenLibros.AUTOR }
        assertEquals(OrdenLibros.TITULO, OrdenGuardado.de(ctx, Estado.LEYENDO))
        assertTrue(Actualizador.activo(ctx))
        assertTrue(!NubeLibros.autoBajar(ctx))
    }

    @Test fun cambiosALaVezGanaElMasReciente() {
        entra(); arranca()
        val a = otro.coleccion("ajustes")
        // A la vez: aquí Leyendo por título (ahora); allí Leyendo por autor (hace un minuto) y Leídos por páginas
        OrdenGuardado.pon(ctx, Estado.LEYENDO, OrdenLibros.TITULO)
        a.pon("marfil_orden", mapOf("LEYENDO" to "AUTOR", "LEIDO" to "PAGINAS"), System.currentTimeMillis() - 60_000L)
        sincroniza()
        espera("de cada estante, el cambio más reciente") { OrdenGuardado.de(ctx, Estado.LEIDO) == OrdenLibros.PAGINAS }
        assertEquals(OrdenLibros.TITULO, OrdenGuardado.de(ctx, Estado.LEYENDO))
        assertEquals("TITULO", enLaCuenta("marfil_orden")!!.texto("LEYENDO"))
        // Al revés: el de allí, más nuevo
        OrdenGuardado.pon(ctx, Estado.LEIDO, OrdenLibros.TITULO)
        a.pon("marfil_orden", mapOf("LEIDO" to "TERMINADO"), System.currentTimeMillis() + 60_000L)
        sincroniza()
        espera("gana lo de allí") { OrdenGuardado.de(ctx, Estado.LEIDO) == OrdenLibros.TERMINADO }
    }

    @Test fun loDeAntesDeLosDosDispositivosSeJunta() {
        val antesAqui = System.currentTimeMillis() - 3_600_000L
        val o = otro.coleccion("ajustes")
        o.pon("marfil_orden", mapOf("PENDIENTE" to "AUTOR"), antesAqui - 3_600_000L)
        assertTrue(otro.sincroniza().ok)
        o.pon("marfil_orden", mapOf("LEIDO" to "PAGINAS"), antesAqui + 600_000L)
        assertTrue(otro.sincroniza().ok)
        // Este móvil, con lo suyo de antes (la versión que aún no lo subía)
        ctx.getSharedPreferences("marfil_orden", Context.MODE_PRIVATE).edit().putString("PENDIENTE", "TITULO").putString("LEIDO", "TITULO").putString("ABANDONADO", "AUTOR").commit()
        ctx.getSharedPreferences("marfil_nube", Context.MODE_PRIVATE).edit().putBoolean("auto_bajar", false).putLong("fallo-s:x", 5L).commit()
        ctx.getSharedPreferences("espejo_cuenta", Context.MODE_PRIVATE).edit().putLong("hora:marfil_orden", antesAqui).putLong("hora:marfil_nube", antesAqui).commit()
        entra(); arranca()
        val orden = enLaCuenta("marfil_orden")!!
        assertEquals("de aquí, más nuevo", "TITULO", orden.texto("PENDIENTE"))
        assertEquals("de allí, más nuevo", "PAGINAS", orden.texto("LEIDO"))
        assertEquals("solo lo tenía este", "AUTOR", orden.texto("ABANDONADO"))
        enLaCuenta("marfil_nube")!!.let { n -> assertEquals(false, n["auto_bajar"]); assertTrue("los fallos son de aquí", n.campos.keys == setOf("auto_bajar")) }
        espera("aquí, lo del otro") { OrdenGuardado.de(ctx, Estado.LEIDO) == OrdenLibros.PAGINAS }
        assertEquals(OrdenLibros.TITULO, OrdenGuardado.de(ctx, Estado.PENDIENTE))
    }

    /** Lo leído en una obra de Cobalto (por dónde vas, un subrayado) se sigue en otro dispositivo, como en un libro. */
    @Test fun loLeidoEnUnEscritoViajaComoElDeUnLibro() {
        entra()
        Biblioteca.cargada = false; Biblioteca.cargar(ctx)
        listOf("capitulos", "lectura", "marcas", "tiempo").forEach { LaFamilia.coleccion(it) }
        otro.coleccion("capitulos").pon("novela~c1", mapOf("ref" to "novela", "t" to "La casa", "a" to "Gonzalo", "c" to "Uno", "n" to 1L, "pa" to "", "p" to 3L, "x" to "Era de noche.", "ue" to 100L))
        sincroniza()
        Escritos.refrescar(ctx)
        val l = Escritos.preparar(ctx, Escritos.obras(ctx).single())
        val id = Escritos.idDe("novela")
        assertEquals(id, l.id)
        val nota = JSONObject().put("value", "epubcfi(/6/2!/4/2)").put("created", 1_000L).put("text", "Era de noche").put("color", "yellow")
        EstadoLectura.ponerSinAvisar(ctx, id, JSONObject().put("cfi", "epubcfi(/6/2!/4/2)").put("fraction", 0.4).put("read", System.currentTimeMillis()).put("notes", JSONArray().put(nota)))
        LecturaNube.sube(ctx, id, EstadoLectura.estado(ctx, id))
        sincroniza()
        assertEquals(0.4, otro.coleccion("lectura").nodo(id)!!.decimal("fraction"), 0.0001)
        assertTrue("el subrayado", otro.coleccion("marcas").vivos().any { it.texto("ref") == id && it.texto("tipo") == "notes" })
        // En el otro dispositivo sigues leyendo: aquí se abre donde lo dejaste
        otro.coleccion("lectura").pon(id, mapOf("ref" to id, "cfi" to "epubcfi(/6/4!/4/2)", "fraction" to 0.8, "read" to System.currentTimeMillis() + 5_000L))
        sincroniza()
        LecturaNube.baja(ctx, setOf(id))
        espera("por dónde vas, el de allí") { EstadoLectura.estado(ctx, id).optDouble("fraction") == 0.8 }
        assertEquals("y el subrayado se queda", 1, EstadoLectura.estado(ctx, id).optJSONArray("notes")?.length())
        assertTrue("la obra sigue sin ser un libro de la cuenta", LaFamilia.coleccion("libros").nodo(id) == null)
    }
}
