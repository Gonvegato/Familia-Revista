package com.gonzalo.gamerate

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import kotlinx.coroutines.runBlocking
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

/**
 * Azabache con la cuenta de la familia de verdad (el emulador oficial de Firebase con las reglas de `nube/reglas.json`). [marfil] y
 * [hiedra] son otras apps de la familia (el núcleo a secas) que escriben sus libros y juegos en la misma cuenta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CuentaAzabacheTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var otra: Familia
    private lateinit var store: Store

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.inicia(ctx)
        store = Store(ctx)
        runBlocking { Account.signIn(store, "gonzalo", "secreto1") }
        otra = Emulador.dispositivo("gonzalo")
        listOf("libros", "juegos", "tiempo", "azabache", "ajustes").forEach { otra.coleccion(it); LaFamilia.coleccion(it) }
    }

    @After fun suelta() { LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null }

    private fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        shadowOf(Looper.getMainLooper()).idle()
        CuentaHermanas.refrescar(ctx, store)
    }

    private fun libro(kid: String) = otra.coleccion("libros").nodo(kid)!!

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (runCatching(cond).getOrDefault(false)) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    /** Como en el móvil: la hermana sube y Azabache sincroniza; lo demás lo hace la propia app (sin llamar a nada a mano). */
    private fun llega() { assertTrue(otra.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok) }

    @Test fun loQueCambiasEnMarfilLlegaSoloAAzabache() {
        // Azabache 1.2 ya tenía este libro de Marfil, como pendiente y dentro de una saga
        val clave = "libro:la comunidad del anillo:j r r tolkien"
        val kid = Hermanas.kidDe(clave)
        store.savePlays(listOf(Play(77L, "La comunidad del anillo", "", "", 0, emptyList(), creator = "J. R. R. Tolkien")), BOOKS.id)
        Hermanas.guardaEnlacesPublico(ctx, "marfil", mapOf(clave to 77L))
        store.saveSagas(listOf(Saga(5L, "El Señor de los Anillos", listOf(77L), kind = BOOKS.id)))
        Sincro.init(ctx)
        // En Marfil lo empiezas a leer
        otra.coleccion("libros").pon(kid, mapOf("clave" to clave, "t" to "La comunidad del anillo", "a" to "J. R. R. Tolkien", "estado" to "en_curso", "fr" to 0.2))
        llega()
        espera("sale en leyendo") { store.loadPlays(BOOKS.id).any { it.id == 77L && it.status == 1 } }
        // Lo terminas hoy
        otra.coleccion("libros").pon(kid, mapOf("estado" to "terminado", "fin" to listOf(System.currentTimeMillis())))
        llega()
        espera("pasa a leídos") { store.load(BOOKS.id).any { it.id == 77L } && store.loadPlays(BOOKS.id).none { it.id == 77L } }
        assertTrue("sin valorar", Hermanas.sinValorar(store.load(BOOKS.id).single { it.id == 77L }))
    }

    @Test fun siEntrasEnOtraAppAzabacheEntraSolaYTraeLoDeLaCuenta() {
        Sincro.init(ctx)
        // Azabache sin cuenta (como al pasar de la 1.2: entraste después en Marfil)
        Account.signOut(store)
        assertFalse(store.paired)
        otra.coleccion("libros").pon(Hermanas.kidDe("libro:dune:frank herbert"), mapOf("clave" to "libro:dune:frank herbert", "t" to "Dune", "a" to "Frank Herbert", "estado" to "en_curso"))
        assertTrue(otra.sincroniza().ok)
        // Entra la hermana (lo que hace Acceso.pideAHermanas con la cuenta de Marfil): Azabache la adopta y trae lo de la cuenta sin tocar nada
        kotlin.concurrent.thread { LaFamilia.entrar("gonzalo", "secreto1") }.join()
        espera("Azabache dentro") { Store(ctx).paired }
        espera("y con lo de Marfil") { Store(ctx).loadPlays(BOOKS.id).any { it.name == "Dune" && it.status == 1 } }
    }

    @Test fun laPortadaQuePonesEnHiedraLlegaSolaAAzabache() {
        Sincro.init(ctx)
        val h = otra.imagenes.guarda(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), "image/jpeg")
        otra.coleccion("juegos").pon("j_cccccccccccc", mapOf("t" to "Hades", "pl" to "PC", "estado" to "en_curso", "portada" to h))
        llega()
        espera("con la portada de Hiedra") { store.loadPlays(GAMES.id).singleOrNull { it.name == "Hades" }?.cover?.let { it.isNotBlank() && java.io.File(it).isFile } == true }
    }

    @Test fun unLibroQueEstasLeyendoNuncaSaleLeidoConUnCero() {
        val kid = Hermanas.kidDe("libro:dune:frank herbert")
        otra.coleccion("libros").pon(kid, mapOf("clave" to "libro:dune:frank herbert", "t" to "Dune", "a" to "Frank Herbert", "estado" to "en_curso", "fr" to 0.3))
        sincroniza()
        assertTrue("está en curso", store.loadPlays(BOOKS.id).any { it.name == "Dune" && it.status == 1 })
        assertFalse("no está entre los leídos", store.load(BOOKS.id).any { it.name == "Dune" })
        // Lo terminas en Marfil: pasa a leídos «sin valorar» (nunca con un 0 que no has puesto)
        otra.coleccion("libros").pon(kid, mapOf("estado" to "terminado", "fin" to listOf(System.currentTimeMillis())))
        sincroniza()
        val g = store.load(BOOKS.id).single { it.name == "Dune" }
        assertTrue("sin valorar", Hermanas.sinValorar(g))
        // Lo valoras en Azabache: la nota va al libro de la cuenta (la ve Marfil)
        store.save(store.load(BOOKS.id).map { if (it.name == "Dune") it.copy(general = 88, gfx = 80, story = 90, music = 70, gameplay = 85, review = "La especia.") else it }, BOOKS.id)
        AzabacheNube.sube(ctx, store)
        sincroniza()
        assertEquals(88L, libro(kid).entero("nota")); assertEquals("La especia.", libro(kid).texto("resena"))
        assertEquals("terminado", libro(kid).texto("estado"))
        // Lo relees en Marfil: vuelve a estar en curso aquí (y su nota se queda en el libro de la cuenta)
        otra.coleccion("libros").pon(kid, mapOf("estado" to "en_curso"))
        sincroniza()
        assertTrue(store.loadPlays(BOOKS.id).any { it.name == "Dune" && it.status == 1 })
        assertFalse(store.load(BOOKS.id).any { it.name == "Dune" })
        assertEquals(88L, libro(kid).entero("nota"))
        // Lo terminas otra vez: vuelve con su nota
        otra.coleccion("libros").pon(kid, mapOf("estado" to "terminado"))
        sincroniza()
        assertEquals(88, store.load(BOOKS.id).single { it.name == "Dune" }.general)
    }

    @Test fun lasNotasDeOtroAzabacheLlegan() {
        val id = "j_aaaaaaaaaaaa"
        otra.coleccion("juegos").pon(id, mapOf("t" to "Hades", "pl" to "PC", "estado" to "terminado", "nota" to 93L, "crit" to listOf(90L, 95L, 99L, 92L), "resena" to "Brutal"))
        sincroniza()
        val g = store.load(GAMES.id).single { it.name == "Hades" }
        assertEquals(93, g.general); assertEquals(99, g.music); assertEquals("Brutal", g.review)
        // Lo que dice Hiedra del estado manda: lo vuelves a jugar
        otra.coleccion("juegos").pon(id, mapOf("estado" to "en_curso"))
        sincroniza()
        assertTrue(store.loadPlays(GAMES.id).any { it.name == "Hades" })
    }

    @Test fun cambiarSoloElApunteNoPisaElEstadoQuePusoLaHermana() {
        val id = "j_bbbbbbbbbbbb"
        otra.coleccion("juegos").pon(id, mapOf("t" to "Celeste", "estado" to "en_curso"))
        sincroniza()
        // Mientras, Hiedra lo da por pasado; aquí aún no se ha enterado y apuntas algo en su ficha en curso
        otra.coleccion("juegos").pon(id, mapOf("estado" to "terminado"))
        assertTrue(otra.sincroniza().ok)
        store.savePlays(store.loadPlays(GAMES.id).map { if (it.name == "Celeste") it.copy(memos = it.memos + Memo(1L, "El capítulo 7 es durísimo")) else it }, GAMES.id)
        AzabacheNube.sube(ctx, store)
        sincroniza()
        assertEquals("el estado de Hiedra no se pisa", "terminado", otra.coleccion("juegos").nodo(id)!!.texto("estado"))
        assertTrue("el apunte de Azabache viaja en el propio juego", otra.coleccion("juegos").nodo(id)!!.texto("az").contains("durísimo"))
    }

    @Test fun unTokenMaloQueSePegaNoPisaElBuenoDeLaCuenta() {
        // El bueno está en la cuenta (puesto en otro móvil) y aquí aún no ha llegado
        otra.ponClave("kdrive_token", "bueno"); otra.ponClave("kdrive_id", "123")
        assertTrue(otra.sincroniza().ok)
        KDrive.save(ctx, "malo", "777")
        assertEquals("se comprueba el pegado", "malo", KDrive.token(ctx))
        KDrive.descarta(ctx) // la comprobación ha fallado
        sincroniza()
        assertEquals("bueno", otra.clave("kdrive_token")); assertEquals("bueno", KDrive.token(ctx)); assertEquals("123", KDrive.drive(ctx))
        // Uno que sí funciona pasa a la cuenta
        KDrive.save(ctx, "nuevo", "https://ksuite.infomaniak.com/kdrive/app/drive/456/files"); KDrive.confirma(ctx)
        sincroniza()
        assertEquals("nuevo", otra.clave("kdrive_token")); assertEquals("456", otra.clave("kdrive_id"))
    }

    @Test fun unaClaveCambiadaEnOtraAppNoVuelveALaVieja() {
        store.key = "sg1"
        sincroniza()
        assertEquals("la que pones en Azabache va a la cuenta al momento", "sg1", otra.clave("steamgriddb"))
        // La cambias en Hiedra; este Azabache sube lo suyo antes de haberla bajado
        otra.ponClave("steamgriddb", "sg2"); assertTrue(otra.sincroniza().ok)
        assertTrue(LaFamilia.sincronizaYa().ok)
        AzabacheNube.sube(ctx, store)
        sincroniza()
        assertEquals("sg2", otra.clave("steamgriddb"))
        AzabacheNube.baja(store)
        assertEquals("y aquí llega la nueva", "sg2", store.key)
    }

    @Test fun loPropioDeAzabacheLlegaATuOtroAzabache() {
        val pelis = Kind(42, "Películas", listOf("Imagen", "Guion", "Música", "Interpretación"), listOf("Pendiente", "Viendo", "Abandonada"), isBook = false, custom = true)
        store.kinds = store.kinds + pelis
        store.save(listOf(Game(7L, "Alien", "", 95, 90, 90, 90, 90, "Obra maestra", updated = 1_000L)), 42)
        AzabacheNube.sube(ctx, store)
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok)
        val n = otra.coleccion("azabache").nodo("items_42~7")!!
        assertEquals("Alien", org.json.JSONObject(n.texto("o")).optString("name"))
        assertTrue("las categorías también", otra.coleccion("azabache").nodo("kinds") != null)
        // En otro Azabache (los datos de la cuenta, mezclados en un Store vacío)
        val d = AzabacheNube.datos()
        assertTrue(d.toString(), d.optJSONArray("items_42")?.toString()?.contains("Alien") == true)
    }
}
