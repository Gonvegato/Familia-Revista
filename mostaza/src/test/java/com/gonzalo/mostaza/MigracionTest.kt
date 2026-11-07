package com.gonzalo.mostaza

import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Fechas
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lo que Azabache tenía de películas y series pasa a Mostaza (`titulos`, `progreso`, `tiempo`), con la cuenta de verdad y TMDB y TVmaze de
 * mentira: nada se pierde (estados, fechas, notas, reseñas, portadas, el tiempo, los episodios vistos), los ids pasan a los de IMDb y los de
 * antes se quedan, los nodos de Azabache no se tocan, y en dos dispositivos (uno después del otro, o los dos a la vez) no sale nada repetido
 * ni vuelve lo que borraste.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionTest : ConCuenta() {
    private lateinit var web: AddonDeMentira
    private val pelis = 4242; private val series = 5151

    @Before fun prepara() {
        preparaCuenta()
        web = AddonDeMentira()
        web.respuestas["/3/movie/550/external_ids"] = """{"id":550,"imdb_id":"tt0137523"}"""
        web.respuestas["/shows/82"] = """{"id":82,"name":"Game of Thrones","externals":{"tvrage":24493,"thetvdb":121361,"imdb":"tt0944947"}}"""
        MigraAzabache.olvidaParaPruebas(ctx)
        MigraAzabache.http = SoloLocal; MigraAzabache.tmdbApi = web.base + "/3"; MigraAzabache.tvmazeApi = web.base
        otro.ponClave("tmdb", "CLAVE")
        siembraAzabache()
        // Mostaza abre lo de Azabache (lo lee para traerlo) y baja todo
        LaFamilia.coleccion("azabache")
        sincroniza(); sincroniza()
    }

    @After fun fin() { web.close(); MigraAzabache.olvidaParaPruebas(ctx); suelta() }

    /** Lo que deja Azabache 1.6.2 en la cuenta (`azabache/kinds` y cada ficha en `azabache/<lista>~<id>` con `o`), como lo escribe su `Propias`. */
    private fun siembraAzabache() {
        val az = otro.coleccion("azabache")
        val kinds = """[{"id":$pelis,"title":"Películas","criteria":["Guion","Actuación","Dirección","Música"],"progress":"Viendo","color":-13677700,"source":"movie"},
            {"id":$series,"title":"Series","criteria":["Guion","Personajes","Actuación","Ritmo"],"progress":"Viendo","color":-13734065,"source":"tv"},
            {"id":777,"title":"Discos","criteria":["Disfrute"],"progress":"Escuchando","color":0,"source":""}]"""
        az.pon("kinds", mapOf("v" to "[$kinds]"))
        az.pon("items_$pelis~550", mapOf("b" to 0L, "o" to """{"id":550,"name":"El club de la lucha","cover":"https://image.tmdb.org/t/p/w500/club.jpg","general":85,"gfx":80,"story":90,"music":70,"gameplay":88,
            "review":"La primera regla.","hero":"https://image.tmdb.org/t/p/w1280/club.jpg","start":19000,"end":19005,"creator":"David Fincher","fav":true,"platform":"Netflix","pages":0,"minutes":139,
            "updated":1700000000000,"ttbMain":0,"ttbExtra":0,"ttbFull":0,"ttbSrc":"","logo":""}"""))
        az.pon("items_$pelis~551", mapOf("b" to 1_600_000_000_000L, "o" to """{"id":551,"name":"Borrada en Jonsu","cover":"","general":50}"""))
        az.pon("iplays_$pelis~999", mapOf("b" to 0L, "o" to """{"id":999,"name":"Película rara","cover":"","hero":"","status":0,"entries":[],"chapter":0,"every":3,"start":0,"end":0,"updated":1700000100000}"""))
        az.pon("iplays_$pelis~123456789012345", mapOf("b" to 0L, "o" to """{"id":123456789012345,"name":"Corto casero","cover":"file:///no/existe.jpg","hero":"","status":2,"entries":[],"end":19100,"updated":1700000200000}"""))
        az.pon("iplays_$series~82", mapOf("b" to 0L, "o" to """{"id":82,"name":"Juego de tronos","cover":"https://static.tvmaze.com/82.jpg","hero":"","status":1,"start":19200,"updated":1700000300000,
            "entries":[{"time":1700000300000,"general":70,"gfx":70,"story":80,"music":60,"gameplay":75,"note":"Muy bien","chapter":1}],
            "parts":[{"n":"1×01 · Se acerca el invierno","d":true},{"n":"1×02 · El camino real","d":true},{"n":"1×03 · Lord Nieve","d":false}]}"""))
        az.pon("items_777~1", mapOf("b" to 0L, "o" to """{"id":1,"name":"Un disco","cover":"","general":90}"""))
        assertTrue(otro.sincroniza().ok)
    }

    private fun azabacheEnLaCuenta(): Map<String, Any?> = Emulador.lee("/users/$uid/familia/azabache").comoMapa()

    @Test fun pasaTodoYNoSePierdeNada() {
        val antes = azabacheEnLaCuenta()
        val r = MigraAzabache.haz(ctx)!!
        assertEquals("las cuatro de películas y series (ni la borrada ni la de otra categoría)", 4, r.titulos)
        assertEquals(4, r.nuevos); assertEquals(2, r.conImdb); assertEquals(2, r.episodios)
        sincroniza()
        val t = otro.coleccion("titulos")
        val club = t.nodo("tt0137523")!!
        assertEquals("El club de la lucha", club.texto("t")); assertEquals("movie", club.texto("tipo")); assertEquals("terminado", club.texto("estado")); assertTrue(club.si("fav"))
        assertEquals("tt0137523", club.texto("id")); assertEquals("tt0137523", club.texto("imdb")); assertEquals("y el id de antes", 550L, club.entero("tmdb"))
        assertEquals(85L, club.entero("nota")); assertEquals(listOf(80L, 90L, 70L, 88L), club.lista("crit").map { (it as Number).toLong() }); assertEquals("La primera regla.", club.texto("resena"))
        assertEquals("su número de siempre en Jonsu (sagas, diario, maquetas)", 550L, club.entero("azid")); assertEquals(pelis.toLong(), club.entero("azk"))
        assertEquals(Fechas.ms(19000), club.entero("an")); assertEquals(Fechas.ms(19005), club.entero("fin"))
        assertEquals("https://image.tmdb.org/t/p/w500/club.jpg", club.texto("pu")); assertEquals("https://image.tmdb.org/t/p/w1280/club.jpg", club.texto("fondo"))
        val az = Json.lee(club.texto("az")).comoMapa()
        assertEquals("lo demás de la ficha, tal cual", "David Fincher", az["creator"]); assertEquals("Netflix", az["platform"])
        assertEquals("el tiempo que tenía", 139 * 60_000L, otro.coleccion("tiempo").nodo("tt0137523~azabache")!!.entero("total"))
        val rara = t.nodo("tmdb:999")!!
        assertEquals("pendiente", rara.texto("estado")); assertEquals(999L, rara.entero("tmdb")); assertEquals("", rara.texto("imdb")); assertEquals("tmdb:999", rara.texto("id"))
        val corto = t.nodo("azabache:123456789012345")!!
        assertEquals("abandonado", corto.texto("estado")); assertEquals(Fechas.ms(19100), corto.entero("fin")); assertEquals("", corto.texto("pu"))
        val got = t.nodo("tt0944947")!!
        assertEquals("series", got.texto("tipo")); assertEquals("en_curso", got.texto("estado")); assertEquals(82L, got.entero("tvmaze")); assertEquals(Fechas.ms(19200), got.entero("an"))
        assertTrue("sus apuntes", Json.lee(got.texto("az")).comoMapa()["entries"].toString().contains("Muy bien"))
        val p = otro.coleccion("progreso")
        assertTrue(p.nodo("tt0944947:1:1")!!.si("visto")); assertTrue(p.nodo("tt0944947:1:2")!!.si("visto")); assertNull(p.nodo("tt0944947:1:3"))
        assertEquals("tt0944947", p.nodo("tt0944947:1:1")!!.texto("ref"))
        assertNull("lo borrado en Jonsu no pasa", t.todos().values.firstOrNull { it.texto("t") == "Borrada en Jonsu" })
        assertNull("ni lo de otras categorías", t.todos().values.firstOrNull { it.texto("t") == "Un disco" })
        // Los nodos de Azabache, intactos (solo se añade la marca de que ya está hecho)
        val despues = azabacheEnLaCuenta()
        assertEquals(antes, despues - "a_mostaza")
        assertNotNull(despues["a_mostaza"])
        // Mostaza lo ve
        espera("en la biblioteca") { Biblioteca.titulos.size == 4 }
        assertEquals(Estado.VISTO, Biblioteca.por("tt0137523")!!.estado)
        assertEquals(Estado.VIENDO, Biblioteca.por("tt0944947")!!.estado)
        assertTrue("una vez por dispositivo", MigraAzabache.hecha(ctx))
        assertNull(MigraAzabache.haz(ctx))
        // Lo que entró sin código de IMDb se completa en cuanto se sabe (y la ref no cambia)
        web.respuestas["/3/movie/999/external_ids"] = """{"imdb_id":"tt9999999"}"""
        MigraAzabache.completaImdb(ctx)
        sincroniza()
        assertEquals("tt9999999", otro.coleccion("titulos").nodo("tmdb:999")!!.texto("imdb"))
        espera("se encuentra por su código") { Biblioteca.por("tt9999999")?.ref == "tmdb:999" }
    }

    @Test fun enOtroDispositivoNoRepiteNiDevuelveLoBorrado() {
        MigraAzabache.haz(ctx)!!
        sincroniza()
        // Después, en Mostaza: vuelves a ver «El club de la lucha» y borras «Película rara»
        Biblioteca.cambiaEstado("tt0137523", Estado.VIENDO)
        Biblioteca.quita("tmdb:999")
        sincroniza()
        // El otro dispositivo hace su migración (con lo de la cuenta ya bajado)
        val r = MigraAzabache.migra(null, otro.coleccion("azabache"), otro.coleccion("titulos"), otro.coleccion("progreso"), otro.coleccion("tiempo"))
        assertEquals(0, r.nuevos)
        sincroniza()
        val t = otro.coleccion("titulos")
        assertEquals("ni una de más", 4, t.todos().size)
        assertEquals("lo que cambiaste en Hathor manda", "en_curso", t.nodo("tt0137523")!!.texto("estado"))
        assertTrue("lo que borraste no vuelve", t.nodo("tmdb:999")!!.borrado)
        assertEquals(2, otro.coleccion("progreso").vivos().size)
        assertEquals(1, otro.coleccion("tiempo").vivos().count { it.texto("fuente") == "azabache" })
    }

    @Test fun losDosALaVezTampocoRepiten() {
        // Los dos dispositivos migran sin haber visto lo del otro: las refs salen iguales (el código de IMDb, o el id de antes)
        val r1 = MigraAzabache.migra(ctx, LaFamilia.coleccion("azabache"), LaFamilia.coleccion("titulos"), LaFamilia.coleccion("progreso"), LaFamilia.coleccion("tiempo"))
        val r2 = MigraAzabache.migra(null, otro.coleccion("azabache"), otro.coleccion("titulos"), otro.coleccion("progreso"), otro.coleccion("tiempo"))
        assertEquals(4, r1.nuevos); assertEquals(4, r2.nuevos)
        sincroniza(); sincroniza()
        val t = otro.coleccion("titulos")
        assertEquals(setOf("tt0137523", "tmdb:999", "azabache:123456789012345", "tt0944947"), t.todos().keys)
        assertEquals(85L, t.nodo("tt0137523")!!.entero("nota"))
        assertFalse(t.vivos().any { it.texto("t").isBlank() })
    }
}
