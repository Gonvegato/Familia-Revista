package com.gonzalo.mostaza.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.gonzalo.familia.pc.TextoAireado
import com.gonzalo.familia.pc.revista.Apariencia
import com.gonzalo.familia.pc.revista.Paper
import com.gonzalo.familia.pc.revista.TemaFamilia
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.junit.After
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Capturas de Mostaza del ordenador, pintado de verdad (sin ventana) con una cuenta de ejemplo en el emulador: lo de la biblioteca lo
 * escribe «el móvil» (otro dispositivo de la cuenta, con su formato) y aquí se lee tal cual. Los pósteres los da un servidor de este
 * ordenador (imágenes hechas aquí); el catálogo de TMDB y las fuentes, fijos. Solo con `-Dcapturas=<carpeta>`:
 * `./gradlew -PsoloEscritorio :escritorio:mostaza:test --tests '*CapturasTest*' -Dcapturas=/tmp/capturas`.
 */
class CapturasTest : ConCuenta() {
    private var ss: ServerSocket? = null

    @After fun fin() { runCatching { ss?.close() }; Fichas.olvidaParaPruebas(); Busqueda.fijas = null; PortadaFija.tmdb = null; Tmdb.claveFija = null; suelta() }

    /** (la letra de los títulos de la revista, la que va dentro de la app: en un servidor puede no haber ninguna del sistema) */
    private val letra by lazy { javaClass.classLoader.getResourceAsStream("fuentes/display_semibold.ttf")!!.use { org.jetbrains.skia.Typeface.makeFromData(org.jetbrains.skia.Data.makeFromBytes(it.readBytes())) } }

    /** Un póster hecho aquí: un color, un filete y el título. */
    private fun poster(titulo: String, color: Int, ancho: Int = 342, alto: Int = 513): ByteArray {
        val s = Surface.makeRasterN32Premul(ancho, alto)
        val c: Canvas = s.canvas
        c.clear(color)
        c.drawRect(org.jetbrains.skia.Rect.makeXYWH(24f, alto - 150f, 60f, 6f), Paint().apply { this.color = 0xFFFFFFFF.toInt() })
        val f = Font(letra, if (ancho > alto) 54f else 34f)
        titulo.split(' ').chunked(2).forEachIndexed { i, l -> c.drawString(l.joinToString(" "), 24f, alto - 110f + i * 40f, f, Paint().apply { this.color = 0xFFFFFFFF.toInt() }) }
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)!!.bytes
    }

    /** Sirve [imagenes] (ruta → bytes) en 127.0.0.1. Devuelve la dirección base. */
    private fun sirve(imagenes: Map<String, ByteArray>): String {
        val s = ServerSocket(0, 50, InetAddress.getLoopbackAddress()); ss = s
        Thread({ while (!s.isClosed) runCatching { val k = s.accept(); Thread { k.use {
            val ruta = k.getInputStream().bufferedReader().readLine().orEmpty().split(' ').getOrElse(1) { "/" }
            val b = imagenes[ruta]
            k.getOutputStream().write((if (b == null) "HTTP/1.1 404 No\r\nContent-Length: 0\r\n\r\n" else "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
            if (b != null) k.getOutputStream().write(b)
        } }.start() } }, "capturas-imagenes").apply { isDaemon = true; start() }
        return "http://127.0.0.1:${s.localPort}"
    }

    @Test fun capturas() {
        val salida = System.getProperty("capturas")?.let { File(it) }
        Assume.assumeTrue("Solo con -Dcapturas=<carpeta>", salida != null)
        salida!!.mkdirs()
        preparaCuenta()
        val colores = listOf(0xFF8E3B2E, 0xFF2E4A6B, 0xFF4F6B3A, 0xFF6B4A2E, 0xFF3A2E6B, 0xFF6B2E55, 0xFF2E6B66, 0xFF7A6A2A, 0xFF5A5A5A, 0xFF1F3F5F).map { it.toInt() }
        val pelis = listOf("Cadena perpetua" to "1994", "El padrino" to "1972", "Parásitos" to "2019", "La ciudad de Dios" to "2002", "El viaje de Chihiro" to "2001",
            "Amélie" to "2001", "El laberinto del fauno" to "2006", "Toro salvaje" to "1980", "Los otros" to "2001", "Relatos salvajes" to "2014")
        val series = listOf("Juego de tronos" to "2011–2019", "The Wire" to "2002–2008", "Fleabag" to "2016–2019", "Mad Men" to "2007–2015", "Better Call Saul" to "2015–2022")
        val imagenes = HashMap<String, ByteArray>()
        (pelis + series).forEachIndexed { i, (t, _) -> imagenes["/p$i.jpg"] = poster(t, colores[i % colores.size]); imagenes["/f$i.jpg"] = poster(t, colores[(i + 3) % colores.size], 1280, 720) }
        val base = sirve(imagenes)
        fun breve(i: Int): MetaBreve { val (t, a) = (pelis + series)[i]; val serie = i >= pelis.size
            return MetaBreve("tt${9000000 + i}", if (serie) "series" else "movie", t, "$base/p$i.jpg", "poster", "$base/f$i.jpg", a, listOf("Drama"), "8.${i % 10}", "Una historia que se queda contigo: la de $t, contada como solo se cuenta una vez.") }
        // Lo de tu biblioteca, escrito desde «el móvil»
        val ahora = System.currentTimeMillis(); val dia = 86_400_000L
        val t = otro.coleccion("titulos"); val p = otro.coleccion("progreso"); val ti = otro.coleccion("tiempo")
        fun titulo(i: Int, estado: String, extra: Map<String, Any?> = emptyMap()) { val b = breve(i)
            t.pon(b.id, mapOf("id" to b.id, "tipo" to b.tipo, "t" to b.nombre, "ano" to b.ano, "estado" to estado, "an" to ahora - (20 - i) * dia, "fav" to false, "pu" to b.poster, "fondo" to b.fondo, "imdb" to b.id) + extra) }
        titulo(10, "en_curso", mapOf("ul" to ahora - dia, "dur" to 55L)); titulo(0, "en_curso", mapOf("ul" to ahora - 2 * dia, "dur" to 142L)); titulo(12, "en_curso", mapOf("ul" to ahora - 3 * dia))
        titulo(1, "pendiente"); titulo(2, "pendiente"); titulo(13, "pendiente"); titulo(4, "pendiente")
        titulo(5, "terminado", mapOf("fin" to ahora - 9 * dia, "nota" to 88L, "resena" to "Pequeña y perfecta.")); titulo(6, "terminado", mapOf("fin" to ahora - 30 * dia)); titulo(11, "terminado", mapOf("fin" to ahora - 60 * dia))
        titulo(7, "abandonado")
        p.pon("tt9000000", mapOf("ref" to "tt9000000", "v" to "tt9000000", "pos" to 61 * 60_000L, "dur" to 142 * 60_000L, "ul" to ahora - 2 * dia))
        p.pon("tt9000010:1:3", mapOf("ref" to "tt9000010", "v" to "tt9000010:1:3", "temp" to 1L, "ep" to 3L, "pos" to 20 * 60_000L, "dur" to 55 * 60_000L, "ul" to ahora - dia))
        p.pon("tt9000010:1:1", mapOf("ref" to "tt9000010", "v" to "tt9000010:1:1", "temp" to 1L, "ep" to 1L, "pos" to 55 * 60_000L, "dur" to 55 * 60_000L, "visto" to true, "cuando" to ahora - 5 * dia, "ul" to ahora - 5 * dia))
        p.pon("tt9000010:1:2", mapOf("ref" to "tt9000010", "v" to "tt9000010:1:2", "temp" to 1L, "ep" to 2L, "pos" to 55 * 60_000L, "dur" to 55 * 60_000L, "visto" to true, "cuando" to ahora - 4 * dia, "ul" to ahora - 4 * dia))
        ti.pon("tt9000010~mostaza-1a2b3c4d", mapOf("ref" to "tt9000010", "fuente" to "mostaza-1a2b3c4d", "t" to "Juego de tronos", "total" to 150 * 60_000L, "veces" to 3L))
        check(otro.sincroniza().ok); sincroniza()
        // El catálogo de TMDB (fijo) y una ficha de película y otra de serie con lo que añade TMDB
        Tmdb.claveFija = "de-prueba"
        PortadaFija.tmdb = listOf(FilaTmdb("Tendencias de la semana", (0 until 10).map { breve(it) }), FilaTmdb("Series del momento", (10 until 15).map { breve(it) }),
            FilaTmdb("En Filmin", listOf(2, 3, 5, 6, 8, 9).map { breve(it) }))
        val reparto = listOf("Tim Robbins" to "Andy Dufresne", "Morgan Freeman" to "Ellis «Red» Redding", "Bob Gunton" to "Warden Norton", "William Sadler" to "Heywood").map { (n, r) -> Persona(n, r) }
        val mPeli = Meta("tt9000000", "movie", "Cadena perpetua", "$base/p0.jpg", "$base/f0.jpg", "", "Andy Dufresne, un banquero condenado por un crimen que no cometió, pasa diecinueve años en la prisión de Shawshank. Allí, con la amistad de Red, se aferra a lo único que no le pueden quitar.",
            "1994", 0L, "142 min", listOf("Drama", "Crimen"), reparto.map { it.nombre }, listOf("Frank Darabont"), listOf("Stephen King"), "8.7", "Estados Unidos", "en", imdb = "tt9000000", trailers = listOf("abc"))
        val eps = (1..10).map { n -> Video("tt9000010:1:$n", listOf("Se acerca el invierno", "El camino real", "Lord Nieve", "Tullidos, bastardos y cosas rotas", "El lobo y el león", "Una corona de oro", "Ganas o mueres", "Por el lado de la punta", "Baelor", "Fuego y sangre")[n - 1], 1, n, ahora - (400 - n * 7) * dia) } +
            (1..3).map { n -> Video("tt9000010:2:$n", "Episodio $n", 2, n, ahora - (30 - n) * dia) }
        val mSerie = Meta("tt9000010", "series", "Juego de tronos", "$base/p10.jpg", "$base/f10.jpg", "", "Nueve familias nobles luchan por el control de las tierras de Poniente, mientras un antiguo enemigo vuelve tras milenios de letargo.",
            "2011–2019", 0L, "55 min", listOf("Drama", "Fantasía"), listOf("Emilia Clarke", "Kit Harington", "Peter Dinklage"), listOf("David Benioff", "D. B. Weiss"), videos = eps, nota = "8.4", imdb = "tt9000010")
        Fichas.fijas = mapOf("tt9000000" to (mPeli to Extras("El miedo te hace prisionero. La esperanza te hace libre.", (1..6).map { breve(it) }, listOf(Plataforma(1, "Filmin"), Plataforma(2, "Movistar Plus+")), reparto)),
            "tt9000010" to (mSerie to Extras("Se acerca el invierno.", (11..14).map { breve(it) }, listOf(Plataforma(3, "Max")), emptyList(), true)))
        Busqueda.fijas = listOf(
            Candidata(Fuente(nombre = "TorBox\n1080p", descripcion = "Cadena.perpetua.1994.1080p.BluRay.x264.Castellano.AC3.mkv\n💾 9.8 GB ⚡", url = "https://tb/1", tamano = 9_800_000_000L), "Comet", 0, 0),
            Candidata(Fuente(nombre = "TorBox\n4K HDR", descripcion = "The.Shawshank.Redemption.1994.2160p.UHD.HDR.x265.MULTI.mkv\n💾 24.1 GB ⚡", url = "https://tb/2", tamano = 24_100_000_000L), "Comet", 0, 1),
            Candidata(Fuente(nombre = "Torrentio\n720p", descripcion = "Cadena.Perpetua.1994.720p.Latino.mkv\n👤 41 💾 2.1 GB", infoHash = "a".repeat(40)), "Torrentio", 1, 0),
            Candidata(Fuente(nombre = "Torrentio\n1080p", descripcion = "The.Shawshank.Redemption.1994.1080p.BluRay.English.mkv\n👤 210 💾 3.4 GB", infoHash = "b".repeat(40)), "Torrentio", 1, 1))
        Apariencia.inicia("mostaza")

        fun captura(nombre: String, pant: Pantalla, oscuro: Boolean = false, antes: () -> Unit = {}) {
            com.gonzalo.familia.pc.revista.Modo.eleccion = if (oscuro) 2 else 1
            antes()
            val escena = ImageComposeScene(1440, 900, Density(1f)) { TemaFamilia { TextoAireado { Box(Modifier.fillMaxSize().background(Paper)) { AppMostaza(pant) } } } }
            var img = escena.render(0)
            // (las imágenes llegan por la red de este ordenador: se le da un rato)
            repeat(12) { i -> Thread.sleep(200); img = escena.render((i + 1) * 300_000_000L) }
            File(salida, "$nombre.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            escena.close()
        }
        captura("portada", Pantalla.Portada)
        captura("portada-oscura", Pantalla.Portada, oscuro = true)
        captura("viendo", Pantalla.Seccion(Estado.VIENDO))
        captura("visto", Pantalla.Seccion(Estado.VISTO))
        captura("ficha-pelicula", Pantalla.Ficha(breve(0), Pantalla.Portada))
        captura("ficha-serie", Pantalla.Ficha(breve(10), Pantalla.Portada))
        captura("fuentes", Pantalla.Fuentes(breve(0), mPeli, null, Pantalla.Portada))
        captura("addons", Pantalla.Addons)
        captura("ajustes-fuentes", Pantalla.Ajustes(1))
        captura("ajustes-reproductor", Pantalla.Ajustes(2))
        // La banda del reproductor (mientras mpv tiene su ventana): lo que se ve abajo
        captura("viendo-con-mpv", Pantalla.Ficha(breve(10), Pantalla.Portada)) {
            val e = Reproductor.estado
            e.activo = true; e.titulo = "Juego de tronos"; e.subtitulo = "T1 · E3 · Lord Nieve"; e.fuente = "1080p · Castellano ✓ · 2,1 GB"; e.reproduciendo = true
            e.pos = 20 * 60_000L; e.dur = 55 * 60_000L; e.mensaje = "Ha entrado en «Viendo»"
            e.pistas = listOf(PistaMpv(1, "audio", "eng", "", false, false, "aac"), PistaMpv(2, "audio", "spa", "Castellano", true, false, "ac3", 6), PistaMpv(1, "sub", "spa", "", false, false, "subrip"))
            e.candidatas = Busqueda.fijas!!; e.elegida = Busqueda.fijas!!.first()
        }
        Reproductor.estado.activo = false
    }
}
