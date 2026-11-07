package com.gonzalo.cobre

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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.CanalGuia
import com.gonzalo.iptv.Guia
import com.gonzalo.iptv.JsonFlujo
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Programa
import com.gonzalo.iptv.Progreso
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Las listas, los canales, la guía y el videoclub de mentira para las capturas (como si fueran tu lista M3U y tu servidor de Xtream): sin
 * red. Los logos y los pósteres son degradados con el nombre, hechos aquí. Ningún canal ni ninguna lista de verdad.
 */
class Escaparate(private val ctx: Context) {
    private fun imagen(nombre: String, a: Int, b: Int, ancho: Int, alto: Int, letra: Float): String {
        val f = File(ctx.cacheDir, "img_${nombre.hashCode()}_$ancho.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, ancho.toFloat(), alto.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, ancho.toFloat(), alto.toFloat(), a, b, Shader.TileMode.CLAMP) })
            val t = Paint().apply { color = 0xEEFFFFFF.toInt(); textSize = letra; isAntiAlias = true; isFakeBoldText = true }
            var y = alto * 0.72f
            nombre.split(' ').chunked(2).forEach { w -> drawText(w.joinToString(" "), ancho * 0.08f, y, t); y += letra * 1.15f }
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }
    private val colores = listOf(0xFFB8502E to 0xFF3A160A, 0xFF2F5D8A to 0xFF0B1B2E, 0xFF6F8A6B to 0xFF1F2A1E, 0xFF8C2A22 to 0xFF1E0A08, 0xFFD9A53A to 0xFF5A3A0A, 0xFF4A5560 to 0xFF14181C, 0xFF7B1E2B to 0xFF200A0E)
    fun logo(n: String, i: Int) = colores[i % colores.size].let { (a, b) -> imagen(n, a.toInt(), b.toInt(), 160, 160, 34f) }
    fun poster(n: String, i: Int) = colores[i % colores.size].let { (a, b) -> imagen(n, a.toInt(), b.toInt(), 300, 450, 30f) }

    val lista = Lista("lcasa000001", "Mi servidor", TipoLista.XTREAM, "http://tv.ejemplo.test:8080", "usuario", "contrasena")
    val m3u = Lista("lm3u0000001", "Lista de la tele", TipoLista.M3U, "http://lista.ejemplo.test/get.m3u?token=abc")

    private val nombres = listOf(
        "Nacionales" to listOf("La 1", "La 2", "Antena 3", "Cuatro", "Telecinco", "laSexta", "24 Horas"),
        "Autonómicas" to listOf("Telemadrid", "TV3", "Canal Sur", "À Punt", "ETB 1"),
        "Deportes" to listOf("Teledeporte", "DAZN LaLiga", "Eurosport 1", "Real Madrid TV"),
        "Cine y series" to listOf("Paramount Network", "AXN", "TCM", "Cosmo"),
        "Infantil" to listOf("Clan", "Boing", "Disney Junior"),
    )
    val canales: List<Canal> = nombres.flatMap { (g, l) -> l.map { g to it } }.mapIndexed { i, (g, n) ->
        Canal("x${100 + i}", n, g, logo = logo(n, i), tvgId = "c$i.es", numero = i + 1, idXtream = 100L + i)
    }

    private val titulos = listOf("Telediario 1", "La revuelta", "El hormiguero", "Saber y ganar", "Informe semanal", "Cine: La sociedad de la nieve", "Fútbol: Liga EA Sports",
        "Documental: Planeta azul", "Masterchef", "Aquí la Tierra", "Noticias", "El tiempo", "Pasapalabra", "La ruleta de la suerte", "Los Simpson", "Cifras y letras")

    fun guia(): Guia {
        val f = File(ctx.cacheDir, "guia_capturas.bin")
        val ahora = System.currentTimeMillis(); val media = 30 * 60_000L
        val base = ahora / media * media - 3 * media
        Guia.Escritor(f).use { e ->
            canales.forEachIndexed { i, c ->
                e.canal(CanalGuia(c.tvgId, listOf(c.nombre)))
                var t = base - (i % 3) * 20 * 60_000L
                var k = i
                while (t < ahora + 10 * 3_600_000L) {
                    val largo = listOf(30, 45, 60, 90, 25, 120)[(k * 7 + i) % 6] * 60_000L
                    e.programa(Programa(c.tvgId, t, t + largo, titulos[k % titulos.size], descripcion = "Un programa de mentira para las capturas, con su descripción de dos líneas para ver cómo queda en la banda del reproductor.", categoria = "Entretenimiento"))
                    t += largo; k++
                }
            }
            e.cierra()
        }
        return Guia.abre(f)!!
    }

    /** Un servidor de Xtream de mentira para el videoclub (con la forma de las respuestas de verdad). */
    val pelis = listOf("Dune: Parte dos" to "2024", "Oppenheimer" to "2023", "La sociedad de la nieve" to "2023", "Cadena perpetua" to "1994", "El padrino" to "1972", "Perfect Days" to "2023",
        "Anatomía de una caída" to "2023", "Robot Dreams" to "2023", "El 47" to "2024", "As bestas" to "2022", "Alcarràs" to "2022", "Campeones" to "2018")
    val series = listOf("Juego de tronos" to "2011", "The Bear" to "2022", "Shōgun" to "2024", "Separación" to "2022", "La casa de papel" to "2017", "Arde Madrid" to "2018")
    val red = object : Red {
        override fun abre(url: String, cabeceras: Map<String, String>): Respuesta {
            val a = Regex("action=([a-z_]+)").find(url)?.groupValues?.get(1)
            val cuerpo: Any = when (a) {
                "get_vod_categories" -> listOf(mapOf("category_id" to "1", "category_name" to "Estrenos"), mapOf("category_id" to "2", "category_name" to "Cine español"), mapOf("category_id" to "3", "category_name" to "Clásicos"))
                "get_vod_streams" -> pelis.mapIndexed { i, (n, y) -> mapOf("stream_id" to 5000 + i, "name" to n, "year" to y, "stream_icon" to poster(n, i), "rating" to "7.${i % 9}", "added" to (1_700_000_000 + i * 1000).toString(), "category_id" to (1 + i % 3).toString(), "container_extension" to "mkv") }
                "get_series_categories" -> listOf(mapOf("category_id" to "10", "category_name" to "Series"))
                "get_series" -> series.mapIndexed { i, (n, y) -> mapOf("series_id" to 3000 + i, "name" to n, "year" to y, "cover" to poster(n, i + 3), "rating" to "8.${i}", "last_modified" to (1_700_000_000 + i).toString(), "category_id" to "10", "genre" to "Drama") }
                "get_vod_info" -> mapOf("info" to mapOf("plot" to "Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra quienes destruyeron a su familia. Ante la elección entre el amor de su vida y el destino del universo, intenta evitar un futuro terrible que solo él puede prever.",
                    "duration_secs" to 9960, "genre" to "Ciencia ficción, Aventura", "director" to "Denis Villeneuve", "cast" to "Timothée Chalamet, Zendaya, Rebecca Ferguson, Javier Bardem", "releasedate" to "2024-02-27", "country" to "Estados Unidos"),
                    "movie_data" to mapOf("stream_id" to 5000, "container_extension" to "mkv"))
                "get_series_info" -> mapOf("seasons" to listOf(mapOf("season_number" to 1, "name" to "Temporada 1"), mapOf("season_number" to 2, "name" to "Temporada 2")), "info" to mapOf("plot" to "Nueve familias nobles luchan por el control de Poniente."),
                    "episodes" to (1..2).associate { t -> t.toString() to (1..6).map { e -> mapOf("id" to 9000 + t * 10 + e, "episode_num" to e, "season" to t, "title" to "Juego de tronos - S0${t}E0$e - " + listOf("El comienzo", "Lo que queda", "La tormenta", "El regreso", "Sin vuelta", "La noche")[e - 1], "container_extension" to "mkv", "info" to mapOf("duration_secs" to 3420)) } })
                else -> emptyList<Any>()
            }
            return Respuesta(200, ByteArrayInputStream(JsonFlujo.escribe(cuerpo).toByteArray()))
        }
    }
}

/**
 * Capturas de las pantallas principales de Cobre (solo con -Proborazzi.test.record=true): en el móvil, en la tablet y en la tele (1920×1080),
 * en claro y en oscuro. Se dejan en la carpeta de `-Dcapturas` (o en build/outputs/roborazzi) como `cobre-<aparato>-<tema>-<pantalla>.png`.
 */
abstract class CapturasBase {
    abstract val aparato: String
    abstract val oscuro: Boolean
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir: String get() = System.getProperty("capturas")?.takeIf { it.isNotBlank() } ?: "build/outputs/roborazzi"

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(700); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("$dir/cobre-$aparato-${if (oscuro) "oscuro" else "claro"}-$nombre.png")
    }

    private fun esperaTexto(t: String) {
        runCatching { rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
    }

    @Test fun pantallas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        iniciar(ctx)
        val e = Escaparate(ctx)
        Tele.red = e.red
        Listas.guarda(listOf(e.lista, e.m3u))
        Tele.almacen.actualizaPeliculas(e.lista); Tele.almacen.actualizaSeries(e.lista)
        val guia = e.guia()
        Tele.fijas = mapOf(e.lista.id to Cargada(e.lista, e.canales.map { it.grupo }.distinct(), e.canales, guia), e.m3u.id to Cargada(e.m3u, emptyList(), e.canales.take(6), null))
        AjustesDirecto.ponDonde(ctx, e.lista.id, "")
        val ahora = System.currentTimeMillis()
        MiCuenta.fijo = MiCuenta.Fijo(
            favoritos = listOf(0, 2, 13, 7, 16, 9).mapIndexed { k, i -> val c = e.canales[i]; Favorito("${e.lista.id}~${c.clave}", e.lista.id, c.clave, c.nombre, k.toLong()) },
            progresos = listOf(
                Progreso(e.lista.id, "e9015", 9015, "mkv", "Sin vuelta", 3000, "Juego de tronos", 1, 5, 34 * 60_000L, 57 * 60_000L, false, ahora - 3_600_000L),
                Progreso(e.lista.id, "p5001", 5001, "mkv", "Oppenheimer", pos = 72 * 60_000L, dur = 180 * 60_000L, ultima = ahora - 86_400_000L),
                Progreso(e.lista.id, "e9021", 9021, "mkv", "El comienzo", 3001, "The Bear", 2, 1, 30 * 60_000L, 30 * 60_000L, true, ahora - 2 * 86_400_000L),
            ),
            ultimo = Favorito("ultimo", e.lista.id, e.canales[2].clave, e.canales[2].nombre, ahora),
        )
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        val c = e.canales[0]
        val (ya, luego) = guia.ahoraYDespues(c.tvgId, ahora)
        val rep = EstadoReproductor().apply { titulo = c.nombre; numero = 1; subtitulo = c.grupo; logo = c.logo; directo = true; reproduciendo = true; cargando = false; this.ahora = ya; despues = luego }
        val repVod = EstadoReproductor().apply { titulo = "Juego de tronos"; subtitulo = "T1 · E5 · Sin vuelta"; directo = false; reproduciendo = true; cargando = false; pos = 34 * 60_000L; dur = 57 * 60_000L }
        // En la tele se usa el mando (sin modo táctil): así el foco se ve, como en una tele de verdad
        if (aparato == "tele") runCatching { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
        var cual by mutableIntStateOf(0)
        rule.setContent {
            CobreTheme {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    when (cual) {
                        0 -> PantPortada(alIr = {}, alFicha = {})
                        1 -> PantDirecto(alVolver = {}, alIr = {})
                        99 -> PantFavoritos(alVolver = {}, alIr = {})
                        2 -> PantGuia(alVolver = {}, alIr = {})
                        3 -> PantVideoclub(false, alVolver = {}, alIr = {}, alFicha = {})
                        4 -> PantFichaPelicula(e.lista, Tele.almacen.pelicula(e.lista, 5000)!!, alCerrar = {})
                        5 -> PantFichaSerie(e.lista, Tele.almacen.serie(e.lista, 3000)!!, alCerrar = {})
                        6 -> PantListas(alVolver = {})
                        7 -> PantAjustes(alVolver = {})
                        8 -> PantVideoclub(true, alVolver = {}, alIr = {}, alFicha = {})
                        else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2F3E4A), Color(0xFF0E1014))))) {
                            ControlesReproductor(if (cual == 11) repVod else rep, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        esperaTexto("Tus favoritos"); foto("1-portada")
        cual = 1; esperaTexto("Telediario"); foto("2-directo")
        cual = 99; esperaTexto("Telediario"); foto("2b-favoritos")
        cual = 2; esperaTexto("Ahora"); foto("3-guia")
        cual = 3; esperaTexto("Oppenheimer"); foto("4-peliculas")
        cual = 8; esperaTexto("The Bear"); foto("5-series")
        cual = 4; esperaTexto("Villeneuve"); foto("6-ficha-pelicula")
        cual = 5; esperaTexto("Temporada 2"); foto("7-ficha-serie")
        cual = 6; esperaTexto("Mi servidor"); foto("8-listas")
        rule.onAllNodesWithText("AÑADIR UNA LISTA")[0].performClick(); esperaTexto("Contraseña")
        // (lo de mentira que escribirías: el usuario y la contraseña salen tapados)
        listOf("Casa", "http://tv.ejemplo.test:8080", "usuario", "contraseña-de-mentira").forEachIndexed { i, t -> rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[i].performTextInput(t) }
        foto("9-anadir-lista")
        cual = 7; foto("10-ajustes")
        rule.onAllNodesWithText("REPRODUCTOR")[0].performClick(); foto("11-ajustes-reproductor")
        cual = 10; foto("12-reproductor-directo")
        rep.visibles = false; rep.lista = e.canales.take(12); rep.enLista = 0; rep.guiaDe = { x -> guia.ahoraYDespues(x.tvgId, ahora) }
        foto("13-reproductor-canales")
        rep.lista = null; rep.confirmaOtro = true; rep.visibles = true
        foto("14-reproductor-otro")
        cual = 11; foto("15-reproductor-serie")
        repVod.visibles = false; repVod.siguiente = "T1 · E6 · La noche"; repVod.cuentaAtras = 6
        foto("16-reproductor-siguiente")
    }
}

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasMovilClaroTest : CapturasBase() { override val aparato = "movil"; override val oscuro = false }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasMovilOscuroTest : CapturasBase() { override val aparato = "movil"; override val oscuro = true }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class CapturasTabletClaroTest : CapturasBase() { override val aparato = "tablet"; override val oscuro = false }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class CapturasTabletOscuroTest : CapturasBase() { override val aparato = "tablet"; override val oscuro = true }

/** La tele: 1920×1080 (960×540 dp a 2×), en modo televisión (el foco del mando se ve desde el principio). */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class CapturasTeleOscuroTest : CapturasBase() { override val aparato = "tele"; override val oscuro = true }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class CapturasTeleClaroTest : CapturasBase() { override val aparato = "tele"; override val oscuro = false }

/** Con la letra grande (el tamaño de texto del móvil al 130 %): los nombres de los canales y sus números se tienen que leer. */
@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f)
class CapturasMovilLetraGrandeTest : CapturasBase() { override val aparato = "movil-letra-grande"; override val oscuro = true }
