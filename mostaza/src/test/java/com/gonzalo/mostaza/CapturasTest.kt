package com.gonzalo.mostaza

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.gonzalo.familia.nucleo.Json
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Los catálogos, fichas y fuentes de mentira para las capturas (como si fueran Cinemeta y un addon de fuentes con tu debrid): sin red.
 * Los pósteres son degradados con el título, hechos aquí.
 */
class Escaparate(private val ctx: Context) : Http {
    class P(val id: String, val tipo: String, val nombre: String, val ano: String, val a: Int, val b: Int, val generos: List<String>, val desc: String, val dur: String = "")

    val pelis = listOf(
        P("tt15239678", "movie", "Dune: Parte dos", "2024", 0xFFD9A53A.toInt(), 0xFF5A3A0A.toInt(), listOf("Ciencia ficción", "Aventura"), "Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra quienes destruyeron a su familia.", "166 min"),
        P("tt15398776", "movie", "Oppenheimer", "2023", 0xFFE0603A.toInt(), 0xFF2B1410.toInt(), listOf("Drama", "Historia"), "La historia del físico J. Robert Oppenheimer y su papel en el desarrollo de la bomba atómica.", "180 min"),
        P("tt16277242", "movie", "La sociedad de la nieve", "2023", 0xFFB8C4CC.toInt(), 0xFF2F3E4A.toInt(), listOf("Drama", "Aventura"), "En 1972, un avión con un equipo de rugby uruguayo se estrella en los Andes.", "144 min"),
        P("tt0111161", "movie", "Cadena perpetua", "1994", 0xFF2F5D8A.toInt(), 0xFF0B1B2E.toInt(), listOf("Drama"), "Un banquero condenado a cadena perpetua entabla amistad con otro preso.", "142 min"),
        P("tt0068646", "movie", "El padrino", "1972", 0xFF8C2A22.toInt(), 0xFF1E0A08.toInt(), listOf("Crimen", "Drama"), "El patriarca de una dinastía del crimen organizado cede el control a su hijo.", "175 min"),
        P("tt27503384", "movie", "Perfect Days", "2023", 0xFF6F8A6B.toInt(), 0xFF1F2A1E.toInt(), listOf("Drama"), "Hirayama limpia baños públicos en Tokio y vive una vida sencilla.", "124 min"),
        P("tt17009710", "movie", "Anatomía de una caída", "2023", 0xFFC9C2B8.toInt(), 0xFF3E3A35.toInt(), listOf("Drama", "Misterio"), "Una escritora es sospechosa de la muerte de su marido.", "151 min"),
    )
    val series = listOf(
        P("tt0944947", "series", "Juego de tronos", "2011–2019", 0xFF3A3F48.toInt(), 0xFF0E1014.toInt(), listOf("Drama", "Fantasía"), "Nueve familias nobles luchan por el control de Poniente.", "57 min"),
        P("tt14452776", "series", "The Bear", "2022–", 0xFF2E6B4F.toInt(), 0xFF0B2016.toInt(), listOf("Comedia", "Drama"), "Un joven chef vuelve a Chicago para llevar el restaurante de su familia.", "30 min"),
        P("tt2788316", "series", "Shōgun", "2024–", 0xFF7B1E2B.toInt(), 0xFF200A0E.toInt(), listOf("Drama", "Historia"), "En el Japón de 1600, un marinero inglés naufraga y se ve en medio de una guerra.", "60 min"),
        P("tt11280740", "series", "Separación", "2022–", 0xFF4A5560.toInt(), 0xFF14181C.toInt(), listOf("Drama", "Misterio"), "Unos empleados separan quirúrgicamente sus recuerdos del trabajo de los de casa.", "55 min"),
        P("tt6468322", "series", "La casa de papel", "2017–2021", 0xFFB3122E.toInt(), 0xFF2A0509.toInt(), listOf("Acción", "Crimen"), "Un misterioso profesor planea el mayor atraco de la historia.", "70 min"),
    )
    val todas get() = pelis + series

    fun poster(p: P): String {
        val f = File(ctx.cacheDir, "poster_${p.id}.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, p.a, p.b, Shader.TileMode.CLAMP) })
            val t = Paint().apply { color = 0xEEFFFFFF.toInt(); textSize = 30f; isAntiAlias = true; isFakeBoldText = true }
            var y = 340f
            p.nombre.split(' ').chunked(2).forEach { w -> drawText(w.joinToString(" "), 22f, y, t); y += 36f }
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    /** Un fondo a lo ancho de ejemplo (como los backdrops de TMDB: una imagen sin el título escrito), con los colores del póster. */
    fun fondo(p: P): String {
        val f = File(ctx.cacheDir, "fondo_${p.id}.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, 640f, 360f, Paint().apply { shader = LinearGradient(0f, 0f, 640f, 360f, p.b, p.a, Shader.TileMode.CLAMP) })
            drawCircle(460f, 120f, 60f, Paint().apply { color = 0x33FFFFFF; isAntiAlias = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun breve(p: P) = mapOf("id" to p.id, "type" to p.tipo, "name" to p.nombre, "poster" to poster(p), "releaseInfo" to p.ano, "genres" to p.generos)

    private fun meta(p: P): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>(breve(p))
        m["description"] = p.desc; m["runtime"] = p.dur; m["imdbRating"] = "8.4"; m["background"] = fondo(p)
        m["cast"] = listOf("Timothée Chalamet", "Zendaya", "Rebecca Ferguson"); m["director"] = listOf("Denis Villeneuve"); m["country"] = "Estados Unidos"
        if (p.tipo == "series") {
            val ahora = System.currentTimeMillis()
            m["videos"] = (1..3).flatMap { t -> (1..(if (t == 3) 4 else 8)).map { e ->
                mapOf("id" to "${p.id}:$t:$e", "title" to listOf("El comienzo", "Lo que queda", "La tormenta", "El regreso", "Sin vuelta", "La noche", "Fuego", "El final")[e - 1],
                    "season" to t, "episode" to e, "released" to java.time.Instant.ofEpochMilli(ahora - ((3 - t) * 365 + (8 - e) * 7) * 86_400_000L + if (t == 3 && e == 4) 40 * 86_400_000L else 0L).toString())
            } }
        }
        return m
    }

    val manifiestoCinemeta = mapOf("id" to "com.linvo.cinemeta", "version" to "3.0.14", "name" to "Cinemeta", "description" to "El addon oficial para catálogos de películas y series",
        "types" to listOf("movie", "series"), "idPrefixes" to listOf("tt"), "resources" to listOf("catalog", "meta"),
        "catalogs" to listOf(
            mapOf("type" to "movie", "id" to "top", "name" to "Populares", "extra" to listOf(mapOf("name" to "genre", "options" to listOf("Acción", "Drama", "Ciencia ficción", "Historia")), mapOf("name" to "search"), mapOf("name" to "skip"))),
            mapOf("type" to "series", "id" to "top", "name" to "Populares", "extra" to listOf(mapOf("name" to "genre", "options" to listOf("Drama", "Comedia")), mapOf("name" to "search"))),
            mapOf("type" to "movie", "id" to "year", "name" to "Por año", "extra" to listOf(mapOf("name" to "genre", "isRequired" to true, "options" to listOf("2026", "2025", "2024", "2023", "2022", "2021"))))))
    val manifiestoFuentes = mapOf("id" to "org.casa.fuentes", "version" to "1.4.0", "name" to "Fuentes de casa", "description" to "Fuentes directas a través de tu debrid",
        "types" to listOf("movie", "series"), "idPrefixes" to listOf("tt"), "resources" to listOf("stream"), "catalogs" to emptyList<Any>(), "behaviorHints" to mapOf("configurable" to true))
    val manifiestoSubs = mapOf("id" to "org.stremio.opensubtitlesv3", "version" to "1.0.0", "name" to "OpenSubtitles v3", "description" to "Subtítulos", "types" to listOf("movie", "series"),
        "resources" to listOf("subtitles"), "catalogs" to emptyList<Any>(), "idPrefixes" to listOf("tt"))
    val fuentes = listOf(
        mapOf("name" to "[RD+] Casa\n4K", "description" to "Dune.Parte.Dos.2024.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD-ABC\n💾 64.2 GB\n🇬🇧 / 🇪🇸", "url" to "https://debrid.example/1.mkv", "behaviorHints" to mapOf("bingeGroup" to "casa-4k")),
        mapOf("name" to "[RD+] Casa\n1080p", "description" to "Dune Parte Dos (2024) [1080p] [Castellano] [AC3 5.1] x264\n💾 4.4 GB", "url" to "https://debrid.example/2.mkv", "behaviorHints" to mapOf("bingeGroup" to "casa-1080p")),
        mapOf("name" to "[RD+] Casa\n1080p", "description" to "Dune.Part.Two.2024.1080p.WEB-DL.LATINO.DD5.1.x264\n💾 3,2 GB", "url" to "https://debrid.example/3.mkv"),
        mapOf("name" to "Casa\n720p", "description" to "Dune.Part.Two.2024.720p.WEBRip.x265.VOSE\n👤 230 💾 1.6 GB", "infoHash" to "0123456789abcdef0123456789abcdef01234567"),
        mapOf("name" to "Tráiler", "ytId" to "U2Qp5pL3ovA"))

    /** Lo que contestaría TMDB (con la forma de su documentación), con las mismas películas y series. */
    private fun tmdb(u: String, q: String): String {
        fun item(p: P) = mapOf("id" to (todas.indexOf(p) + 100), "media_type" to if (p.tipo == "series") "tv" else "movie", (if (p.tipo == "series") "name" else "title") to p.nombre,
            "poster_path" to poster(p), "backdrop_path" to poster(p), (if (p.tipo == "series") "first_air_date" else "release_date") to "${p.ano.take(4)}-03-01",
            "vote_average" to 8.1, "overview" to p.desc, "genre_ids" to listOf(18))
        fun lista(l: List<P>) = Json.escribe(mapOf("page" to 1, "total_pages" to 1, "results" to l.map { item(it) }))
        val ruta = u.substringAfter("/3/")
        return when {
            ruta.startsWith("genre/") -> """{"genres":[{"id":18,"name":"Drama"},{"id":28,"name":"Acción"},{"id":35,"name":"Comedia"},{"id":878,"name":"Ciencia ficción"}]}"""
            ruta.startsWith("watch/providers") -> """{"results":[{"display_priorities":{"ES":0},"provider_id":8,"provider_name":"Netflix","logo_path":""},{"display_priorities":{"ES":1},"provider_id":119,"provider_name":"Prime Video","logo_path":""}]}"""
            ruta.startsWith("search/") -> lista(todas.filter { it.nombre.contains("d", true) })
            ruta.startsWith("trending/tv") || ruta.startsWith("tv/") && !ruta.substringAfter("tv/").first().isDigit() || ruta.startsWith("discover/tv") -> lista(series)
            ruta.startsWith("find/") -> todas.firstOrNull { ruta.contains(it.id) }?.let { p -> Json.escribe(mapOf("movie_results" to if (p.tipo == "movie") listOf(item(p)) else emptyList<Any>(), "tv_results" to if (p.tipo == "series") listOf(item(p)) else emptyList<Any>())) } ?: """{"movie_results":[],"tv_results":[]}"""
            ruta.contains("/recommendations") -> lista(pelis.drop(2))
            Regex("^(movie|tv)/(\\d+)$").find(ruta) != null -> {
                val (k, n) = Regex("^(movie|tv)/(\\d+)$").find(ruta)!!.destructured
                val p = todas.getOrNull(n.toInt() - 100) ?: throw java.io.IOException("404")
                val m = LinkedHashMap<String, Any?>(item(p)); m.remove("media_type")
                m["imdb_id"] = p.id; m["external_ids"] = mapOf("imdb_id" to p.id); m["tagline"] = "Lo que pasó de verdad."; m["runtime"] = p.dur.filter(Char::isDigit).toIntOrNull() ?: 0
                m["genres"] = p.generos.map { mapOf("id" to 1, "name" to it) }; m["production_countries"] = listOf(mapOf("name" to "Estados Unidos"))
                m["credits"] = mapOf("cast" to listOf("Timothée Chalamet" to "Paul Atreides", "Zendaya" to "Chani", "Rebecca Ferguson" to "Lady Jessica", "Javier Bardem" to "Stilgar").mapIndexed { i, (a, b) -> mapOf("name" to a, "character" to b, "order" to i, "profile_path" to poster(p)) },
                    "crew" to listOf(mapOf("name" to "Denis Villeneuve", "job" to "Director")))
                m["videos"] = mapOf("results" to listOf(mapOf("iso_639_1" to "es", "key" to "U2Qp5pL3ovA", "site" to "YouTube", "type" to "Trailer", "official" to true)))
                m["recommendations"] = mapOf("results" to pelis.drop(3).map { item(it) })
                m["watch/providers"] = mapOf("results" to mapOf("ES" to mapOf("flatrate" to listOf(mapOf("provider_id" to 8, "provider_name" to "Netflix", "logo_path" to "", "display_priority" to 0)))))
                if (k == "tv") { m["seasons"] = listOf(mapOf("season_number" to 1), mapOf("season_number" to 2)); m["created_by"] = listOf(mapOf("name" to "David Benioff")) }
                Json.escribe(m)
            }
            ruta.startsWith("configuration") -> "{}"
            else -> lista(pelis)
        }
    }

    override fun get(url: String, cabeceras: Map<String, String>): String {
        if (url.contains("api.themoviedb.org/3/")) return tmdb(url.substringBefore('?'), url.substringAfter('?', ""))
        val u = url.substringBefore('?')
        fun metas(l: List<P>) = Json.escribe(mapOf("metas" to l.map { breve(it) }))
        return when {
            u.endsWith("cinemeta.example/manifest.json") -> Json.escribe(manifiestoCinemeta)
            u.endsWith("fuentes.example/clave/manifest.json") -> Json.escribe(manifiestoFuentes)
            u.endsWith("subs.example/manifest.json") -> Json.escribe(manifiestoSubs)
            u.contains("/catalog/movie/top/search=") || u.contains("/catalog/series/top/search=") -> metas(todas.filter { it.nombre.contains("d", true) }.filter { u.contains("/${it.tipo}/") })
            u.contains("/catalog/movie/top") -> metas(pelis)
            u.contains("/catalog/series/top") -> metas(series)
            u.contains("/catalog/movie/year") -> metas(pelis.filter { it.ano == "2023" })
            u.contains("/meta/") -> todas.firstOrNull { u.contains("/${it.id}.json") }?.let { Json.escribe(mapOf("meta" to meta(it))) } ?: throw java.io.IOException("404")
            u.contains("/stream/") -> Json.escribe(mapOf("streams" to fuentes))
            u.contains("/subtitles/") -> """{"subtitles":[]}"""
            else -> throw java.io.IOException("404")
        }
    }

    val addons = listOf(AddonInstalado("https://cinemeta.example/manifest.json", "Cinemeta"), AddonInstalado("https://fuentes.example/clave/manifest.json", "Fuentes de casa"), AddonInstalado("https://subs.example/manifest.json", "OpenSubtitles v3"))

    fun titulo(p: P, e: Estado, an: Long, ul: Long = 0L) = Titulo(p.id, p.id, p.tipo, p.nombre, p.ano, e, anadido = an, ultima = ul, poster = poster(p), generos = p.generos, imdb = p.id)
}

/**
 * Capturas de las pantallas principales de Mostaza (solo con -Proborazzi.test.record=true): en el móvil y en la tablet (tumbada y de pie),
 * en claro y en oscuro. Se dejan en la carpeta de `-Dcapturas` (o en build/outputs/roborazzi) como `mostaza-<aparato>-<tema>-<pantalla>.png`.
 */
abstract class CapturasBase {
    abstract val aparato: String
    abstract val oscuro: Boolean
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(700); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/mostaza-$aparato-${if (oscuro) "oscuro" else "claro"}-$nombre.png")
    }

    private fun esperaTexto(t: String) {
        runCatching { rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
    }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        iniciar(ctx)
        val e = Escaparate(ctx)
        Addons.http = e
        Addons.guarda(e.addons)
        val ahora = System.currentTimeMillis(); val dia = 86_400_000L
        val p = e.pelis; val s = e.series
        Biblioteca.fijaParaPruebas(listOf(
            e.titulo(s[0], Estado.VIENDO, ahora - 60 * dia, ahora - 3_600_000L), e.titulo(p[1], Estado.VIENDO, ahora - 5 * dia, ahora - dia),
            e.titulo(p[0], Estado.PENDIENTE, ahora - 2 * dia), e.titulo(s[2], Estado.PENDIENTE, ahora - 3 * dia), e.titulo(p[5], Estado.PENDIENTE, ahora - 4 * dia),
            e.titulo(p[3], Estado.VISTO, ahora - 300 * dia).copy(fin = ahora - 200 * dia), e.titulo(p[4], Estado.VISTO, ahora - 280 * dia).copy(fin = ahora - 100 * dia), e.titulo(s[1], Estado.VISTO, ahora - 90 * dia).copy(fin = ahora - 20 * dia),
            e.titulo(s[4], Estado.ABANDONADO, ahora - 400 * dia),
        ), listOf(
            Progreso("tt0944947:2:5", "tt0944947", 2, 5, 34 * 60_000L, 57 * 60_000L, false, 0L, ahora - 3_600_000L),
            Progreso("tt0944947:2:4", "tt0944947", 2, 4, 57 * 60_000L, 57 * 60_000L, true, ahora - 86_400_000L, ahora - 86_400_000L),
            Progreso("tt0944947:1:1", "tt0944947", 1, 1, 57 * 60_000L, 57 * 60_000L, true, ahora - 30 * dia, ahora - 30 * dia),
            Progreso("tt15398776", "tt15398776", 0, 0, 72 * 60_000L, 180 * 60_000L, false, 0L, ahora - dia),
        ))
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        val rep = EstadoReproductor().apply { titulo = "Juego de tronos"; subtitulo = "T2 · E5 · Sin vuelta"; fuente = "1080p · Castellano · 4,4 GB · Fuentes de casa"; reproduciendo = true; cargando = false; pos = 34 * 60_000L; dur = 57 * 60_000L }
        var cual by mutableIntStateOf(0)
        rule.setContent {
            MostazaTheme {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    when (cual) {
                        0 -> PantPrincipal(0, {}, {}, {}, {}, {})
                        1 -> PantPrincipal(1, {}, {}, {}, {}, {})
                        2 -> PantPrincipal(3, {}, {}, {}, {}, {})
                        3 -> PantExplorar(alVolver = {}, alAbrir = {})
                        4 -> PantBuscar(alVolver = {}, alAbrir = {})
                        5 -> PantFicha(Biblioteca.titulos[2].breve(), alCerrar = {}, alAbrir = {})
                        6 -> PantFicha(Biblioteca.titulos[0].breve(), alCerrar = {}, alAbrir = {})
                        7 -> PantFuentes(Biblioteca.titulos[2].breve(), null, null, alCerrar = {})
                        8 -> PantAddons(alVolver = {})
                        9 -> PantEstilo(alVolver = {})
                        11 -> PantFicha(MetaBreve("tmdb:movie:100", "movie", "Dune: Parte dos"), alCerrar = {}, alAbrir = {})
                        else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3F48), Color(0xFF0E1014))))) {
                            ControlesReproductor(rep, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        esperaTexto("Populares"); foto("1-portada")
        cual = 1; foto("2-viendo")
        cual = 2; foto("3-visto")
        cual = 3; esperaTexto("Oppenheimer"); foto("4-explorar")
        cual = 4; rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("du"); rule.onAllNodesWithText("BUSCAR")[0].performClick(); esperaTexto("Películas · Cinemeta"); foto("5-buscar")
        cual = 5; esperaTexto("Paul Atreides"); foto("6-ficha-pelicula")
        cual = 6; esperaTexto("Temporada 2"); foto("7-ficha-serie")
        cual = 7; esperaTexto("Castellano"); foto("8-fuentes")
        cual = 8; esperaTexto("Fuentes de casa"); foto("9-addons")
        cual = 9; foto("10-ajustes")
        rule.onAllNodesWithText("FUENTES")[0].performClick(); foto("11-ajustes-fuentes")
        cual = 10; foto("12-reproductor")
        // Con el catálogo de TMDB (como si tuvieras tu clave puesta)
        TmdbDatos.imagen = { path, _ -> path }
        Tmdb.http = e; Tmdb.claveFija = "prueba"; Tmdb.guarda("prueba")
        cual = 0; esperaTexto("Tendencias"); foto("15-portada-tmdb")
        cual = 3; esperaTexto("Oppenheimer"); foto("16-explorar-tmdb")
        cual = 11; esperaTexto("Lady Jessica"); foto("17-ficha-tmdb")
        cual = 9; rule.onAllNodesWithText("CATÁLOGO")[0].performClick(); foto("18-ajustes-catalogo")
        Tmdb.claveFija = null; cual = 10
        rep.eleccion = Triple("Subtítulos", listOf("Sin subtítulos" to false, "Español" to true, "Inglés" to false, "English [CC]" to false)) { _ -> }
        foto("13-reproductor-subtitulos")
        rep.eleccion = null; rep.visibles = false; rep.siguiente = "T2 · E6 · La noche"; rep.cuentaAtras = 6
        foto("14-reproductor-siguiente")
    }
}

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasMovilClaroTest : CapturasBase() { override val aparato = "movil"; override val oscuro = false }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasMovilOscuroTest : CapturasBase() { override val aparato = "movil"; override val oscuro = true }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class CapturasTabletHorizontalClaroTest : CapturasBase() { override val aparato = "tablet-h"; override val oscuro = false }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class CapturasTabletHorizontalOscuroTest : CapturasBase() { override val aparato = "tablet-h"; override val oscuro = true }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w840dp-h1260dp-port-xhdpi")
class CapturasTabletVerticalClaroTest : CapturasBase() { override val aparato = "tablet-v"; override val oscuro = false }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w840dp-h1260dp-port-xhdpi")
class CapturasTabletVerticalOscuroTest : CapturasBase() { override val aparato = "tablet-v"; override val oscuro = true }
