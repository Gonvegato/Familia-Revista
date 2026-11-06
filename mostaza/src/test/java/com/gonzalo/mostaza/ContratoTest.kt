package com.gonzalo.mostaza

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Json
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * El contrato con Azabache, de verdad: lo que Mostaza deja en la cuenta (`titulos`, `progreso`, `tiempo`), escrito con su propio código
 * (añadir, ver, marcar, quitar) y con la valoración que pone Azabache en el mismo nodo, se guarda tal cual en
 * `build/outputs/contrato/mostaza.json`. Azabache lo lee con su prueba `ContratoHermanasTest` (copia en `herramientas/casos/hermanas/`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContratoTest : ConCuenta() {
    private var reloj = 1_000_000L
    @Before fun prepara() { preparaCuenta() }
    @After fun fin() { suelta() }

    private fun ve(v: Visionado, ms: Long, desde: Long, d: Long): Long {
        v.sesion.reproduciendo(true)
        var pos = desde; var t = 0L
        while (t < ms) { reloj += 5_000; t += 5_000; pos = (pos + 5_000).coerceAtMost(d); v.tick(pos, d) }
        v.sesion.reproduciendo(false)
        return pos
    }

    @Test fun loQueEscribeMostaza() {
        val peli = MetaBreve("tt0111161", "movie", "Cadena perpetua", poster = "https://images.metahub.space/poster/small/tt0111161/img", ano = "1994", generos = listOf("Drama"))
        val mp = Meta("tt0111161", "movie", "Cadena perpetua", poster = peli.poster, fondo = "https://images.metahub.space/background/medium/tt0111161/img", ano = "1994", duracion = "142 min", imdb = "tt0111161", tmdb = 278L)
        val d = 142 * 60_000L
        // La ves entera (empiezas por el minuto 128): entra sola y queda vista
        var v = Visionado(ctx, peli, mp, null, reloj = { reloj })
        var pos = ve(v, 14 * 60_000L, 128 * 60_000L, d); v.termina(pos, d)
        // Azabache la valora (en el mismo nodo, como hace su CosasMostaza)
        sincroniza()
        otro.coleccion("titulos").pon("tt0111161", mapOf("nota" to 92L, "crit" to listOf(90L, 95L, 85L, 80L), "resena" to "La esperanza es algo bueno.", "azid" to 278L, "azk" to 4242L))
        // Una serie: la añades, ves el primer episodio entero y marcas el segundo
        val ahora = System.currentTimeMillis()
        val eps = listOf(Video("tt0944947:1:1", "Se acerca el invierno", 1, 1, ahora - 400 * 86_400_000L), Video("tt0944947:1:2", "El camino real", 1, 2, ahora - 390 * 86_400_000L), Video("tt0944947:1:3", "Lord Nieve", 1, 3, ahora - 380 * 86_400_000L))
        val serie = MetaBreve("tt0944947", "series", "Juego de tronos", poster = "https://images.metahub.space/poster/small/tt0944947/img", ano = "2011–2019")
        val ms = Meta("tt0944947", "series", "Juego de tronos", ano = "2011–2019", duracion = "57 min", videos = eps, imdb = "tt0944947", tmdb = 1399L)
        Biblioteca.anade(serie, ms)
        v = Visionado(ctx, serie, ms, eps[0], reloj = { reloj }); pos = ve(v, 12 * 60_000L, 45 * 60_000L, 57 * 60_000L); v.termina(pos, 57 * 60_000L)
        Biblioteca.marcaVisto(Biblioteca.por(serie.id)!!, eps[1], true, ms)
        // Una que añades y quitas
        Biblioteca.anade(MetaBreve("tt0068646", "movie", "El padrino", ano = "1972"))
        Biblioteca.quita("tt0068646")
        sincroniza()
        val cuenta = { c: String -> Emulador.lee("/users/$uid/familia/$c") }
        val out = linkedMapOf("app" to "mostaza", "version" to VERSION, "dispositivo" to Cuenta.dispositivo, "pelicula" to "tt0111161", "serie" to "tt0944947", "borrada" to "tt0068646",
            "titulos" to cuenta("titulos"), "progreso" to cuenta("progreso"), "tiempo" to cuenta("tiempo"))
        val f = File("build/outputs/contrato/mostaza.json").apply { parentFile.mkdirs() }
        f.writeText(Json.escribe(out))
        assertTrue(f.readText().contains("\"nota\":92"))
    }
}
