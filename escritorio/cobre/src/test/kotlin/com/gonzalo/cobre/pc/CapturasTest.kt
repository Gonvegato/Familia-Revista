package com.gonzalo.cobre.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Apariencia
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.Skin
import com.gonzalo.familia.pc.revista.TemaFamilia
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Red
import com.gonzalo.iptv.Respuesta
import com.gonzalo.iptv.TipoLista
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.junit.After
import org.junit.Assume
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Capturas de Cobre del ordenador, pintado de verdad (sin ventana) con una cuenta de ejemplo en el emulador y un servidor de Xtream de
 * mentira (con canales, guía y videoclub inventados: Cobre no trae ninguno). Los logos y los pósteres los da un servidor de este ordenador
 * (imágenes hechas aquí). Solo con `-Dcapturas=<carpeta>`:
 * `./gradlew -PsoloEscritorio :escritorio:cobre:test --tests '*CapturasTest*' -Dcapturas=/tmp/capturas`.
 */
class CapturasTest : ConCuenta() {
    private var ss: ServerSocket? = null

    @After fun fin() { runCatching { ss?.close() }; suelta() }

    private val letra: org.jetbrains.skia.Typeface? by lazy { javaClass.classLoader.getResourceAsStream("fuentes/display_semibold.ttf")?.use { org.jetbrains.skia.FontMgr.default.makeFromData(org.jetbrains.skia.Data.makeFromBytes(it.readBytes())) } }

    /** Una imagen hecha aquí: un color, un filete y un texto (un póster o un logo). */
    private fun imagen(texto: String, color: Int, ancho: Int, alto: Int, tam: Float): ByteArray {
        val s = Surface.makeRasterN32Premul(ancho, alto)
        val c = s.canvas
        c.clear(color)
        val blanco = Paint().apply { this.color = 0xFFFFFFFF.toInt() }
        if (alto > ancho) c.drawRect(org.jetbrains.skia.Rect.makeXYWH(24f, alto - 150f, 60f, 6f), blanco)
        val f = Font(letra, tam)
        texto.split(' ').chunked(2).forEachIndexed { i, l -> c.drawString(l.joinToString(" "), if (alto > ancho) 24f else 10f, if (alto > ancho) alto - 110f + i * 40f else alto / 2f + 8f + i * (tam + 2), f, blanco) }
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private fun sirve(imagenes: Map<String, ByteArray>): String {
        val s = ServerSocket(0, 50, InetAddress.getLoopbackAddress()); ss = s
        Thread({ while (!s.isClosed) runCatching { val k = s.accept(); Thread { k.use {
            val ruta = k.getInputStream().bufferedReader().readLine().orEmpty().split(' ').getOrElse(1) { "/" }
            val b = imagenes[ruta]
            k.getOutputStream().write((if (b == null) "HTTP/1.1 404 No\r\nContent-Length: 0\r\n\r\n" else "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
            if (b != null) k.getOutputStream().write(b)
        } }.start() } }, "capturas-imagenes").apply { isDaemon = true; start() }
        return "http://127.0.0.1:${s.localPort}"
    }

    private fun xmltv(t: Long) = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(Instant.ofEpochMilli(t).atOffset(ZoneOffset.UTC)) + " +0000"

    @Test fun capturas() {
        val salida = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", salida != null)
        salida!!.mkdirs()
        preparaCuenta()
        val colores = listOf(0xFF8E3B2E, 0xFF2E4A6B, 0xFF4F6B3A, 0xFF6B4A2E, 0xFF3A2E6B, 0xFF6B2E55, 0xFF2E6B66, 0xFF7A6A2A, 0xFF5A5A5A, 0xFF1F3F5F).map { it.toInt() }
        // Lo que tiene el servidor de mentira: canales por grupos, una guía de hoy, películas y series (todo inventado)
        val grupos = listOf("1" to "Nacionales", "2" to "Deportes", "3" to "Cine", "4" to "Infantil", "5" to "Música")
        val nombres = listOf(
            "1" to listOf("La Primera HD", "Canal Dos", "Tele Norte", "Antena Sur", "Cuatro Vientos", "Noticias 24"), "2" to listOf("Deportes Uno", "Fútbol Total", "Motor TV", "Baloncesto+"),
            "3" to listOf("Cine Clásico", "Estrenos", "Cine Español"), "4" to listOf("Peques", "Dibujos 24"), "5" to listOf("Música Hoy", "Rock FM TV"))
        val imagenes = HashMap<String, ByteArray>()
        val canales = ArrayList<Map<String, Any?>>()
        var n = 0
        for ((g, ls) in nombres) for (nom in ls) {
            n++
            imagenes["/logo$n.png"] = imagen(nom.split(' ').joinToString("") { it.take(1) }, colores[n % colores.size], 120, 120, 44f)
            canales += mapOf("num" to n, "name" to nom, "stream_type" to "live", "stream_id" to 100 + n, "stream_icon" to "/logo$n.png", "epg_channel_id" to "c$n.es", "category_id" to g)
        }
        val programas = listOf("Telediario", "El tiempo", "Saber y ganar", "Documental: los ríos", "La película de la tarde", "Concurso", "Informativo", "Magacín", "Directo deportivo", "Cine de noche", "Dibujos animados", "Los éxitos")
        val ahora = System.currentTimeMillis()
        val media = 30 * 60_000L
        val guia = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n")
            for (i in 1..n) append("<channel id=\"c$i.es\"><display-name>${canales[i - 1]["name"]}</display-name></channel>\n")
            for (i in 1..n) {
                var t = ahora / media * media - media * (i % 3 + 1)
                var k = i
                while (t < ahora + 10 * 3_600_000L) {
                    val dur = media * (1 + (k % 4))
                    append("<programme start=\"${xmltv(t)}\" stop=\"${xmltv(t + dur)}\" channel=\"c$i.es\"><title>${programas[k % programas.size]}</title><desc>Lo de hoy, contado con calma.</desc><category>Actualidad</category></programme>\n")
                    t += dur; k++
                }
            }
            append("</tv>\n")
        }
        val pelis = listOf("La casa del lago", "El último verano", "Tres días en Lisboa", "Noche de estreno", "El faro", "La sombra del viento", "Marea alta", "Los años felices", "El viaje", "Ciudad dormida")
        val series = listOf("La familia Robles", "Distrito Norte", "Los vecinos", "El internado del monte", "Puerto Azul")
        pelis.forEachIndexed { i, t -> imagenes["/p$i.png"] = imagen(t, colores[i % colores.size], 342, 513, 34f) }
        series.forEachIndexed { i, t -> imagenes["/s$i.png"] = imagen(t, colores[(i + 4) % colores.size], 342, 513, 34f) }
        val base = sirve(imagenes)
        fun conBase(m: Map<String, Any?>) = m.mapValues { (k, v) -> if (k == "stream_icon" || k == "cover") "$base$v" else v }
        val respuestas: (String) -> String? = { u ->
            val accion = Regex("action=([a-z_]+)").find(u)?.groupValues?.get(1)
            when {
                u.contains("/xmltv.php") -> guia
                !u.contains("/player_api.php") -> null
                accion == null -> """{"user_info":{"auth":1,"status":"Active","exp_date":"1830000000","max_connections":"2","active_cons":"0"},"server_info":{"timezone":"Europe/Madrid"}}"""
                accion == "get_live_categories" -> Json.escribe(grupos.map { mapOf("category_id" to it.first, "category_name" to it.second) })
                accion == "get_live_streams" -> Json.escribe(canales.map { conBase(it) })
                accion == "get_vod_categories" -> """[{"category_id":"10","category_name":"Drama"},{"category_id":"11","category_name":"Comedia"},{"category_id":"12","category_name":"Thriller"}]"""
                accion == "get_vod_streams" -> Json.escribe(pelis.mapIndexed { i, t -> mapOf("num" to i + 1, "name" to t, "stream_type" to "movie", "stream_id" to 5000 + i, "stream_icon" to "$base/p$i.png",
                    "rating" to "${6 + i % 4}.${i % 10}", "added" to "${ahora / 1000 - i * 86_400}", "category_id" to "${10 + i % 3}", "container_extension" to "mkv", "year" to "${2015 + i}") })
                accion == "get_series_categories" -> """[{"category_id":"20","category_name":"Drama"},{"category_id":"21","category_name":"Comedia"}]"""
                accion == "get_series" -> Json.escribe(series.mapIndexed { i, t -> mapOf("num" to i + 1, "name" to t, "series_id" to 3000 + i, "cover" to "$base/s$i.png", "plot" to "Una historia de $t: la de siempre, contada como nunca.",
                    "genre" to "Drama", "releaseDate" to "${2018 + i}-01-01", "rating" to "8.${i}", "last_modified" to "${ahora / 1000 - i * 3600}", "category_id" to "${20 + i % 2}") })
                accion == "get_vod_info" -> Json.escribe(mapOf("info" to mapOf("description" to "Dos hermanos vuelven a la casa del lago donde pasaban los veranos de niños. Allí, entre lo que se dijeron y lo que callaron, tendrán que decidir qué hacer con ella.",
                    "duration_secs" to 6720, "genre" to "Drama", "cast" to "Ana Ruiz, Pablo Merino, Lucía Sanz", "director" to "Marta Gil", "releasedate" to "2015-10-16", "country" to "España"),
                    "movie_data" to mapOf("stream_id" to 5000, "container_extension" to "mkv")))
                accion == "get_series_info" -> Json.escribe(mapOf("seasons" to listOf(mapOf("season_number" to 1, "name" to "Temporada 1"), mapOf("season_number" to 2, "name" to "Temporada 2")),
                    "info" to mapOf("name" to series[0], "plot" to "Los Robles llevan tres generaciones con la panadería del barrio. Este año, todo cambia.", "genre" to "Drama"),
                    "episodes" to mapOf("1" to (1..8).map { e -> mapOf("id" to "${9000 + e}", "episode_num" to e, "title" to "${series[0]} - S01E0$e - ${listOf("El horno", "La herencia", "Domingo", "La feria", "Harina", "El pleito", "Navidad", "La vuelta")[e - 1]}",
                        "container_extension" to "mkv", "season" to 1, "info" to mapOf("duration_secs" to 3000)) },
                        "2" to (1..3).map { e -> mapOf("id" to "${9100 + e}", "episode_num" to e, "title" to "Episodio $e", "container_extension" to "mkv", "season" to 2, "info" to mapOf("duration_secs" to 3000)) })))
                else -> "[]"
            }
        }
        Tele.red = Red { u, _ -> respuestas(u)?.let { Respuesta(200, ByteArrayInputStream(it.toByteArray())) } ?: Respuesta(404, ByteArrayInputStream(ByteArray(0))) }
        val xtream = Lista("lxtream0001", "Mi servidor", TipoLista.XTREAM, "http://tv.ejemplo.test:8080", "pepe", "s3cr3t0")
        val m3u = Lista("lm3u0000001", "La de casa", TipoLista.M3U, "http://lista.ejemplo.test/lista.m3u?token=abc123")
        Listas.pon(xtream); Listas.pon(m3u)
        Tele.almacen.actualizaCanales(xtream); Tele.almacen.actualizaGuia(xtream); Tele.almacen.actualizaPeliculas(xtream); Tele.almacen.actualizaSeries(xtream)
        AjustesDirecto.ponDonde(xtream.id, "")
        val cg = Tele.cargada(xtream)!!
        listOf("x101", "x107", "x111", "x104").forEach { k -> MiCuenta.alternaFavorito(xtream.id, cg.porClave.getValue(k)) }
        // Lo visto, desde «el móvil»
        val pr = otro.coleccion(DatosCuenta.PROGRESO)
        pr.pon("${xtream.id}~p5002", mapOf("lista" to xtream.id, "v" to "p5002", "tipo" to "pelicula", "id" to 5002L, "ext" to "mkv", "t" to pelis[2], "pos" to 40 * 60_000L, "dur" to 100 * 60_000L, "visto" to false, "ul" to ahora - 3_600_000))
        pr.pon("${xtream.id}~e9003", mapOf("lista" to xtream.id, "v" to "e9003", "tipo" to "episodio", "id" to 9003L, "ext" to "mkv", "t" to "Domingo", "serie" to 3000L, "st" to series[0], "temp" to 1L, "ep" to 3L,
            "pos" to 20 * 60_000L, "dur" to 50 * 60_000L, "visto" to false, "ul" to ahora - 600_000))
        (1..2).forEach { e -> pr.pon("${xtream.id}~e900$e", mapOf("lista" to xtream.id, "v" to "e900$e", "tipo" to "episodio", "id" to 9000L + e, "ext" to "mkv", "t" to "x", "serie" to 3000L, "st" to series[0],
            "temp" to 1L, "ep" to e.toLong(), "pos" to 50 * 60_000L, "dur" to 50 * 60_000L, "visto" to true, "ul" to ahora - 86_400_000L * (3 - e))) }
        check(otro.sincroniza().ok); sincroniza()
        Apariencia.inicia("cobre")
        Skin.accentId = "cobre"

        fun captura(nombre: String, pant: Pantalla, oscuro: Boolean = false, antes: () -> Unit = {}) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            antes()
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppCobre(pant) } } } }
            var img = escena.render(0)
            // (las imágenes y lo leído del disco llegan por detrás: se le da un rato)
            repeat(14) { i -> Thread.sleep(200); img = escena.render((i + 1) * 300_000_000L) }
            File(salida, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("directo", Pantalla.Directo)
        captura("directo-oscuro", Pantalla.Directo, oscuro = true) { AjustesDirecto.ponDonde(xtream.id, "Deportes") }
        AjustesDirecto.ponDonde(xtream.id, "")
        captura("guia", Pantalla.Guia)
        captura("guia-oscura", Pantalla.Guia, oscuro = true)
        captura("peliculas", Pantalla.Peliculas)
        captura("series", Pantalla.Series)
        captura("ficha-pelicula", Pantalla.FichaPelicula(xtream, Tele.almacen.pelicula(xtream, 5000)!!, Pantalla.Peliculas))
        captura("ficha-serie", Pantalla.FichaSerie(xtream, Tele.almacen.serie(xtream, 3000)!!, Pantalla.Series))
        captura("ajustes-listas", Pantalla.Ajustes(0))
        captura("ajustes-lista-nueva", Pantalla.Ajustes(0, Lista("lnueva00001", "Mi servidor nuevo", TipoLista.XTREAM, "http://otro.ejemplo.test:8080", "pepe", "contraseña-larga")))
        captura("ajustes-reproductor", Pantalla.Ajustes(2))
        // La banda del reproductor (mientras mpv tiene su ventana): en directo, con la lista de canales; y una serie
        val e = Reproductor.estado
        captura("viendo-directo", Pantalla.Directo, oscuro = true) {
            val c: Canal = cg.porClave.getValue("x102")
            e.activo = true; e.directo = true; e.listaId = xtream.id; e.titulo = c.nombre; e.subtitulo = c.grupo; e.numero = c.numero; e.logo = c.logo; e.reproduciendo = true
            Tele.ahoraYDespuesRapido(cg, c).let { (a, d) -> e.ahora = a; e.despues = d }
            e.lista = Tele.visibles(cg, c.grupo); e.enLista = 1
        }
        captura("viendo-serie", Pantalla.FichaSerie(xtream, Tele.almacen.serie(xtream, 3000)!!, Pantalla.Series)) {
            e.lista = null; e.directo = false; e.titulo = series[0]; e.subtitulo = "T1 · E3 · Domingo"; e.numero = 0; e.logo = ""; e.pos = 20 * 60_000L; e.dur = 50 * 60_000L
            e.ahora = null; e.despues = null; e.siguiente = null
        }
        e.activo = false
        // El fútbol (como Nut 0.9 del móvil), con unos partidos fijos de ejemplo (sin red: los escudos, la forma de escudo de cada uno)
        fun eq(id: String, n: String, c: String = n) = com.gonzalo.iptv.futbol.Equipo(id, n, c)
        val bar = eq("83", "Barcelona"); val atm = eq("1068", "Atlético de Madrid", "Atlético"); val rma = eq("86", "Real Madrid"); val val_ = eq("94", "Valencia")
        val bet = eq("244", "Real Betis", "Betis"); val sev = eq("243", "Sevilla"); val esp = eq("164", "España")
        val vivo = com.gonzalo.iptv.futbol.Partido("p1", "esp.1", ahora - 67 * 60_000L, atm, bar, com.gonzalo.iptv.futbol.Estado.EN_JUEGO, 1, 2, reloj = "67'", estadio = "Riyadh Air Metropolitano")
        val luego = com.gonzalo.iptv.futbol.Partido("p2", "esp.1", ahora + 3 * 3_600_000L, rma, val_, estadio = "Santiago Bernabéu")
        val fin = com.gonzalo.iptv.futbol.Partido("p3", "esp.1", ahora - 4 * 3_600_000L, bet, sev, com.gonzalo.iptv.futbol.Estado.FINAL, 2, 2)
        val antes = (1..5).map { i -> com.gonzalo.iptv.futbol.Partido("a$i", "esp.1", ahora - i * 7 * 86_400_000L, if (i % 2 == 0) bar else eq("9$i", "Rival $i"), if (i % 2 == 0) eq("9$i", "Rival $i") else bar, com.gonzalo.iptv.futbol.Estado.FINAL, i % 3, (i + 1) % 3) }
        val ficha = com.gonzalo.iptv.futbol.FichaPartido(vivo, listOf(
            com.gonzalo.iptv.futbol.Evento("8'", com.gonzalo.iptv.futbol.TipoEvento.GOL, "83", "Raphinha", "Fermín López"), com.gonzalo.iptv.futbol.Evento("41'", com.gonzalo.iptv.futbol.TipoEvento.AMARILLA, "1068", "Koke"), com.gonzalo.iptv.futbol.Evento("52'", com.gonzalo.iptv.futbol.TipoEvento.GOL, "1068", "Julián Álvarez"),
            com.gonzalo.iptv.futbol.Evento("64'", com.gonzalo.iptv.futbol.TipoEvento.GOL, "83", "Lamine Yamal", "Pedri"), com.gonzalo.iptv.futbol.Evento("66'", com.gonzalo.iptv.futbol.TipoEvento.CAMBIO, "1068", "Sorloth", "Griezmann")),
            emptyList(), listOf(com.gonzalo.iptv.futbol.Cifra("Posesión", "42 %", "58 %"), com.gonzalo.iptv.futbol.Cifra("Tiros a puerta", "3", "7"), com.gonzalo.iptv.futbol.Cifra("Tiros", "7", "13")))
        val tabla = com.gonzalo.iptv.futbol.Clasificacion("esp.1", listOf("" to listOf(com.gonzalo.iptv.futbol.Fila(1, bar, 12, 10, 0, 2, 40, 6, 34, 30), com.gonzalo.iptv.futbol.Fila(2, atm, 12, 9, 1, 2, 24, 10, 14, 28), com.gonzalo.iptv.futbol.Fila(3, rma, 12, 8, 3, 1, 26, 8, 18, 27),
            com.gonzalo.iptv.futbol.Fila(4, bet, 12, 6, 5, 1, 18, 14, 4, 23), com.gonzalo.iptv.futbol.Fila(5, sev, 12, 5, 4, 3, 15, 15, 0, 19))))
        Futbol.fijo = Futbol.Fijo(equipos = listOf(bar to "esp.1", esp to "uefa.nations"), rivales = listOf(rma to "esp.1"), partidos = listOf(vivo, luego) + antes,
            partidosDia = listOf(vivo, luego, fin), fichas = mapOf("p1" to ficha), clasificaciones = mapOf("esp.1" to tabla))
        fun capturaF(nombre: String, pagina: Int = 0, oscuro: Boolean = false, contenido: (@androidx.compose.runtime.Composable () -> Unit)? = null) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) {
                if (contenido != null) contenido() else AppCobre(paginaInicial = pagina)
            } } } }
            var img = escena.render(0)
            repeat(14) { i -> Thread.sleep(200); img = escena.render((i + 1) * 300_000_000L) }
            File(salida, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        val nav = Nav({}, {}, {}, { Pantalla.Directo })
        capturaF("futbol-portada", 0)
        capturaF("futbol-hoy", 1)
        capturaF("futbol-tus-equipos", 3)
        capturaF("futbol-ficha") { FichaPartidoC(vivo, nav, {}, {}) }
        capturaF("futbol-ficha-noche", oscuro = true) { FichaPartidoC(vivo, nav, {}, {}) }
        capturaF("futbol-equipo") { FichaEquipoC(bar, "esp.1", nav, {}, {}) }
        Futbol.fijo = null
    }
}
