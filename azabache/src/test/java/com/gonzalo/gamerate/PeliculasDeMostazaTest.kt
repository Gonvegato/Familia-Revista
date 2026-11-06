package com.gonzalo.gamerate

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Las películas y las series son de Mostaza (Azabache 1.6.3): en cuanto Mostaza ha traído lo de aquí (la marca `azabache/a_mostaza`),
 * Azabache lee sus categorías «Películas» y «Series» de los nodos `titulos` de Mostaza, sin copia, con el mismo número de ficha de siempre
 * (`azid`: sagas, diario y maquetas lo siguen encontrando), y lo que valoras o cambias aquí va a esos nodos. Antes de la marca, todo como
 * siempre. Con la cuenta de verdad (el emulador de Firebase); [mostaza] es Mostaza (otro dispositivo de la familia, con el núcleo a secas).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeliculasDeMostazaTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var mostaza: Familia
    private lateinit var store: Store
    private var uid = ""
    private val pelis = customKind(4242, "Películas", listOf("Guion", "Actuación", "Dirección", "Música"), "Viendo", 0, SRC_MOVIE)
    private val series = customKind(5151, "Series", listOf("Guion", "Personajes", "Actuación", "Ritmo"), "Viendo", 0, SRC_TV)

    @Before fun prepara() {
        Emulador.limpia()
        uid = Emulador.cuenta("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.configPruebas = Emulador.cfg
        Cosas.olvidaParaPruebas(); Cosas.migrarEnElMomento = true
        Propias.olvidaParaPruebas(); Propias.migrarEnElMomento = true
        CosasMostaza.olvidaParaPruebas()
        LaFamilia.inicia(ctx)
        store = Store(ctx)
        runBlocking { Account.signIn(store, "gonzalo", "secreto1") }
        mostaza = Emulador.dispositivo("gonzalo")
        listOf("titulos", "progreso", "tiempo", "azabache", "juegos", "libros").forEach { mostaza.coleccion(it); LaFamilia.coleccion(it) }
        sincroniza()
        store.kinds = listOf(pelis, series)
        Mode.kinds = store.kinds
    }

    @After fun suelta() { CosasMostaza.olvidaParaPruebas(); LaFamilia.reiniciaParaPruebas(); LaFamilia.configPruebas = null }

    private fun sincroniza() {
        assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(mostaza.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun espera(que: String, cond: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < hasta) { shadowOf(Looper.getMainLooper()).idle(); if (runCatching(cond).getOrDefault(false)) return; Thread.sleep(50) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(que, cond())
    }

    @Test fun antesDeMostazaTodoComoSiempreYDespuesLasDeMostaza() {
        // Azabache 1.6.2: «El club de la lucha» (de TMDB, id 550), valorada
        store.save(listOf(Game(550L, "El club de la lucha", "https://image.tmdb.org/club.jpg", 85, 80, 90, 70, 88, "La primera regla.", end = 19005L, start = 19000L, creator = "David Fincher", platform = "Netflix")), pelis.id)
        sincroniza()
        assertEquals(listOf(550L), store.load(pelis.id).map { it.id })
        assertFalse(CosasMostaza.activa(ctx))
        val viejo = Emulador.lee("/users/$uid/familia/azabache/items_${pelis.id}~550").comoMapa()
        assertTrue(viejo.isNotEmpty())
        // Mostaza la trae (con su número de siempre) y deja la marca
        mostaza.coleccion("titulos").pon("tt0137523", mapOf("id" to "tt0137523", "tipo" to "movie", "t" to "El club de la lucha", "estado" to "terminado", "imdb" to "tt0137523", "tmdb" to 550L,
            "azid" to 550L, "azk" to pelis.id.toLong(), "nota" to 85L, "crit" to listOf(80L, 90L, 70L, 88L), "resena" to "La primera regla.", "fin" to Fechas.ms(19005), "an" to Fechas.ms(19000),
            "pu" to "https://image.tmdb.org/club.jpg", "az" to """{"creator":"David Fincher","platform":"Netflix"}"""), 1_700_000_000_000L)
        mostaza.coleccion("tiempo").pon("tt0137523~mostaza-a", mapOf("ref" to "tt0137523", "fuente" to "mostaza-a", "total" to 139 * 60_000L))
        // Y una que has empezado en Mostaza (sin número de Azabache: el de TMDB)
        mostaza.coleccion("titulos").pon("tt0133093", mapOf("id" to "tt0133093", "tipo" to "movie", "t" to "Matrix", "estado" to "en_curso", "imdb" to "tt0133093", "tmdb" to 603L, "an" to System.currentTimeMillis()))
        // Una serie con un episodio visto en Mostaza (y sus capítulos de antes en Azabache, en «az»)
        mostaza.coleccion("titulos").pon("tt0944947", mapOf("id" to "tt0944947", "tipo" to "series", "t" to "Juego de tronos", "estado" to "en_curso", "tvmaze" to 82L, "azid" to 82L, "azk" to series.id.toLong(),
            "az" to """{"parts":[{"n":"1×01 · Se acerca el invierno","d":false},{"n":"1×02 · El camino real","d":false}]}"""))
        mostaza.coleccion("progreso").pon("tt0944947:1:1", mapOf("ref" to "tt0944947", "v" to "tt0944947:1:1", "temp" to 1L, "ep" to 1L, "visto" to true))
        mostaza.coleccion("azabache").pon("a_mostaza", mapOf("v" to "[1]", "por" to "mostaza"))
        sincroniza()
        espera("Azabache lee las de Mostaza") { CosasMostaza.activa(ctx) && store.load(pelis.id).any { it.id == 550L } && store.loadPlays(pelis.id).any { it.name == "Matrix" } }
        val club = store.load(pelis.id).single { it.id == 550L }
        assertEquals("El club de la lucha", club.name); assertEquals(85, club.general); assertEquals(90, club.story); assertEquals("La primera regla.", club.review)
        assertEquals("David Fincher", club.creator); assertEquals("Netflix", club.platform); assertEquals(19005L, club.end); assertEquals(139, club.minutes)
        assertEquals("la de Mostaza: el número de TMDB", 603L, store.loadPlays(pelis.id).single { it.name == "Matrix" }.id)
        assertEquals(1, store.loadPlays(pelis.id).single { it.name == "Matrix" }.status)
        val got = store.loadPlays(series.id).single()
        assertEquals(82L, got.id); assertEquals("lo visto en Mostaza manda", listOf(true, false), got.parts.map { it.done })
        assertEquals(pelis to 550L, CosasMostaza.buscaRef(ctx, listOf(pelis, series), "tt0137523"))
        // Valoras «Matrix» al terminarla aquí: va al nodo de Mostaza
        val antes = store.loadPlays(pelis.id)
        store.savePlays(antes.filter { it.name != "Matrix" }, pelis.id, antes)
        store.save(store.load(pelis.id) + Game(603L, "Matrix", "", 95, 90, 90, 95, 99, "Toma la pastilla roja.", end = 19500L), pelis.id)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        sincroniza()
        val m = mostaza.coleccion("titulos").nodo("tt0133093")!!
        assertEquals("terminado", m.texto("estado")); assertEquals(95L, m.entero("nota")); assertEquals("Toma la pastilla roja.", m.texto("resena")); assertEquals(Fechas.ms(19500), m.entero("fin"))
        assertFalse("no se borra al pasar de una lista a otra", m.borrado)
        assertEquals("y queda su número", 603L, m.entero("azid"))
        // Una nueva desde Azabache (sin Mostaza en este móvil): a la cuenta de Mostaza, con su id de TMDB
        store.savePlays(store.loadPlays(pelis.id) + Play(680L, "Pulp Fiction", "", "", 0, emptyList()), pelis.id)
        sincroniza()
        val pulp = mostaza.coleccion("titulos").nodo("tmdb:680")!!
        assertEquals("movie", pulp.texto("tipo")); assertEquals(680L, pulp.entero("tmdb")); assertEquals("pendiente", pulp.texto("estado")); assertEquals(680L, pulp.entero("azid"))
        // Quitarla aquí la quita en Mostaza (borrada, como al quitarla allí)
        val ps = store.loadPlays(pelis.id)
        store.savePlays(ps.filter { it.id != 680L }, pelis.id, ps)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        sincroniza()
        assertTrue(mostaza.coleccion("titulos").nodo("tmdb:680")!!.borrado)
        // Lo de antes de Azabache, intacto
        assertEquals(viejo, Emulador.lee("/users/$uid/familia/azabache/items_${pelis.id}~550").comoMapa())
        assertNotNull(Json.lee(viejo["o"].toString()))
    }
}
