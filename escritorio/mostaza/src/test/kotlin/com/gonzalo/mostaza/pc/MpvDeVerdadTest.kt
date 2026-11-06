package com.gonzalo.mostaza.pc

import com.gonzalo.familia.nucleo.Emulador
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El reproductor de verdad: mpv abierto por Mostaza (sin ventana ni sonido: `--vo=null --ao=null`), con vídeos hechos aquí con ffmpeg y
 * servidos por un servidor de este ordenador que solo los da con la cabecera que pide la fuente (como un debrid que pide su clave), y la
 * cuenta de verdad (el emulador). Se comprueba lo que hace el reproductor del móvil: las cabeceras llegan, la dirección y la cabecera no
 * salen en la línea de órdenes de mpv, se ven las pistas de verdad (se pone el audio en castellano, se recuerda lo de cada fuente), el
 * vídeo de aviso del debrid se reconoce y se pasa a otra, si la que eligió solo no está en tu idioma se prueba otra, se sigue donde lo
 * dejaste, y a los 10 minutos de verdad entra en tu biblioteca con su tiempo y su posición. Sin mpv o sin ffmpeg, se salta.
 */
class MpvDeVerdadTest : ConCuenta() {
    private lateinit var servidor: Servidor
    private val videos = File(System.getProperty("java.io.tmpdir"), "mostaza-videos-prueba").also { it.mkdirs() }

    private fun hay(p: String) = runCatching { ProcessBuilder(p, "-version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() } == 0 }.getOrDefault(false)

    /** Un vídeo de [segundos] con sus audios ([audios]: idioma y nombre), pequeño (64×48, 2 imágenes por segundo). */
    private fun video(nombre: String, segundos: Int, audios: List<Pair<String, String>>): File {
        val f = File(videos, nombre)
        if (f.isFile && f.length() > 0) return f
        val o = mutableListOf("ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=orange:size=64x48:rate=2", "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-t", "$segundos", "-map", "0:v")
        audios.forEach { _ -> o += listOf("-map", "1:a") }
        o += listOf("-c:v", "libx264", "-preset", "ultrafast", "-g", "4", "-c:a", "aac", "-b:a", "8k")
        audios.forEachIndexed { i, (l, t) -> o += listOf("-metadata:s:a:$i", "language=$l"); if (t.isNotBlank()) o += listOf("-metadata:s:a:$i", "title=$t") }
        o += f.absolutePath
        val p = ProcessBuilder(o).redirectErrorStream(true).start(); p.inputStream.readBytes(); check(p.waitFor() == 0) { "ffmpeg no ha podido hacer $nombre" }
        return f
    }

    @Before fun prepara() {
        assumeTrue("Sin mpv en este ordenador", Mpv.candidatos().any { it.isFile && it.canExecute() })
        assumeTrue("Sin ffmpeg (para hacer los vídeos de prueba)", hay("ffmpeg"))
        preparaCuenta()
        video("castellano.mkv", 15 * 60, listOf("eng" to "", "spa" to "Castellano"))
        video("ingles.mkv", 15 * 60, listOf("eng" to "English"))
        video("aviso.mkv", 60, listOf("eng" to ""))
        servidor = Servidor(videos, "X-Clave" to "secreta-del-debrid").also { it.arranca() }
        Mpv.extra = listOf("--vo=null", "--ao=null")
        PantallaCompleta.pon(false)
    }

    @After fun fin() {
        runCatching { Reproductor.alCerrarLaApp() }
        Reproductor.relojPruebas = null; Reproductor.pulsoMs = 1_000L; Mpv.extra = emptyList()
        if (::servidor.isInitialized) servidor.para()
        suelta()
    }

    private fun fuente(archivo: String, nombre: String) = Fuente(nombre = nombre, url = "${servidor.base}/$archivo", cabeceras = mapOf("X-Clave" to "secreta-del-debrid"), archivo = archivo, tamano = File(videos, archivo).length())

    @Test fun pistasDeVerdadAvisoDelDebridYProbarOtra() {
        val aviso = Candidata(fuente("aviso.mkv", "Peli [1080p] MULTI"), "Debrid", 0, 0)
        val ingles = Candidata(fuente("ingles.mkv", "Peli [1080p]"), "Debrid", 0, 1)
        val buena = Candidata(fuente("castellano.mkv", "Peli [720p]"), "Debrid", 0, 2)
        val b = MetaBreve("tt7000001", "movie", "Una de prueba", ano = "2026")
        val m = Meta("tt7000001", "movie", "Una de prueba", duracion = "96 min", imdb = "tt7000001")
        assertEquals(null, Reproductor.pon(b, m, null, aviso, listOf(aviso, ingles, buena), automatica = true))
        // La línea de órdenes de mpv, como la ve cualquier programa del ordenador: ni la dirección ni la clave
        espera("mpv se abre", 15) { Reproductor.pidParaPruebas() != null }
        val linea = File("/proc/${Reproductor.pidParaPruebas()}/cmdline").takeIf { it.isFile }?.readText()?.replace('\u0000', ' ')
        if (linea != null) { assertFalse(linea, linea.contains("127.0.0.1")); assertFalse(linea, linea.contains("secreta")); assertTrue(linea.contains("--input-ipc-server=")) }
        // El aviso (1 minuto para una película de 96): se apunta como rota en la cuenta y se pasa a otra; la inglesa no tiene castellano: otra
        val e = Reproductor.estado
        fun como() = "elegida=${e.elegida?.fuente?.archivo} aviso=${e.aviso} mensaje=${e.mensaje} pistas=${e.pistas} peticiones=${servidor.peticiones}"
        espera("acaba en la de castellano", 40, ::como) {
            e.elegida === buena && e.mensaje == null && e.pistas.any { it.tipo == "audio" && it.elegida && it.idioma == "spa" } && MemoriaPistas.busca()(Elector.clave(buena.fuente)) != null
        }
        val audio = Reproductor.estado.pistas.first { it.tipo == "audio" && it.elegida }
        assertEquals("pone el audio en castellano (mpv habría puesto el primero)", "spa", audio.idioma)
        val memoria = MemoriaPistas.busca()
        assertEquals(setOf(Idioma.INGLES), memoria(Elector.clave(ingles.fuente))?.idiomas)
        assertTrue(memoria(Elector.clave(buena.fuente))!!.idiomas.contains(Idioma.CASTELLANO))
        assertTrue("la del aviso, rota (en la cuenta)", FuentesRotas.busca()(Elector.clave(aviso.fuente)))
        assertTrue("todas las peticiones con la cabecera de la fuente", servidor.peticiones.isNotEmpty() && servidor.peticiones.all { it.second })
        Reproductor.para()
        espera("se cierra", 15) { !Reproductor.estado.activo }
        sincroniza()
        assertNotNull("la rota, en ajustes/mostaza_rotas", otro.coleccion("ajustes").nodo("mostaza_rotas")?.campos?.get(FuentesRotas.campo(Elector.clave(aviso.fuente))))
        assertTrue("unos segundos no entran en tu biblioteca (regla 1)", otro.coleccion("titulos").vivos().isEmpty())
        assertTrue(otro.coleccion("progreso").vivos().isEmpty())
    }

    @Test fun seSigueDondeLoDejasteYALosDiezMinutosEntra() {
        // (el reloj de la sesión va cien veces más deprisa: los diez minutos de verdad son unos segundos)
        val inicio = Reloj.ahora()
        Reproductor.relojPruebas = { inicio + (Reloj.ahora() - inicio) * 100 }
        Reproductor.pulsoMs = 100
        val c = Candidata(fuente("castellano.mkv", "Peli [720p] Castellano"), "Debrid", 0, 0)
        val b = MetaBreve("tt7000002", "movie", "Otra de prueba", ano = "2026")
        val m = Meta("tt7000002", "movie", "Otra de prueba", duracion = "15 min", imdb = "tt7000002")
        Reanudar.pon("tt7000002", 120_000L, 900_000L)
        assertEquals(null, Reproductor.pon(b, m, null, c, listOf(c)))
        espera("empieza por donde lo dejaste", 20) { Reproductor.estado.reproduciendo && Reproductor.estado.pos >= 119_000L }
        espera("a los diez minutos de verdad, en «Viendo»", 40) { Biblioteca.por("tt7000002")?.estado == Estado.VIENDO }
        Reproductor.para()
        espera("se cierra", 15) { !Reproductor.estado.activo }
        sincroniza()
        val t = otro.coleccion("titulos").nodo("tt7000002")!!
        assertEquals("en_curso", t.texto("estado")); assertEquals("movie", t.texto("tipo")); assertEquals(15L, t.entero("dur"))
        val p = otro.coleccion("progreso").nodo("tt7000002")!!
        assertTrue("la posición viaja: ${p.entero("pos")}", p.entero("pos") >= 120_000L); assertTrue("lo que dura: ${p.entero("dur")}", kotlin.math.abs(p.entero("dur") - 900_000L) < 2_000L)
        val ti = otro.coleccion("tiempo").nodo("tt7000002~${Biblioteca.dispositivo}")!!
        assertTrue("al menos los diez minutos: ${ti.entero("total")}", ti.entero("total") >= 600_000L); assertEquals(1L, ti.entero("veces"))
    }

    @Test fun sinAudioEnTuIdiomaPoneLosSubtitulosEnEspanolDeLaFuente() {
        File(videos, "es.srt").writeText("1\n00:00:00,000 --> 00:15:00,000\nHola.\n")
        val c = Candidata(fuente("ingles.mkv", "Peli [1080p] English").copy(subtitulos = listOf(Subtitulo("es", "${servidor.base}/es.srt", "spa", "Español"))), "Debrid", 0, 0)
        val b = MetaBreve("tt7000003", "movie", "Una en inglés", ano = "2026")
        assertEquals(null, Reproductor.pon(b, Meta("tt7000003", "movie", "Una en inglés", duracion = "15 min"), null, c, listOf(c)))
        val e = Reproductor.estado
        espera("con los subtítulos en español (los de la fuente, bajados con su cabecera)", 30, { "pistas=${e.pistas} peticiones=${servidor.peticiones}" }) {
            e.pistas.any { it.tipo == "sub" && it.elegida && it.externa && it.idioma == "spa" }
        }
        assertTrue(servidor.peticiones.any { it.first == "/es.srt" && it.second })
        Reproductor.para()
        espera("se cierra", 15) { !Reproductor.estado.activo }
    }

    /** Un servidor de vídeos de este ordenador, con saltos (Range), que solo responde con la cabecera pedida (si no, 403). */
    class Servidor(private val dir: File, private val cabecera: Pair<String, String>) {
        private val ss = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val base = "http://127.0.0.1:${ss.localPort}"
        /** (ruta, ¿llevaba la cabecera?) de cada petición. */
        val peticiones = CopyOnWriteArrayList<Pair<String, Boolean>>()

        fun arranca() { Thread({ while (!ss.isClosed) runCatching { val s = ss.accept(); Thread { atiende(s) }.apply { isDaemon = true; start() } } }, "servidor-videos").apply { isDaemon = true; start() } }
        fun para() { runCatching { ss.close() } }

        private fun atiende(s: java.net.Socket) = s.use {
            val ent = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val primera = ent.readLine() ?: return@use
            val cab = HashMap<String, String>()
            while (true) { val l = ent.readLine() ?: break; if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) cab[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
            val ruta = primera.split(' ').getOrElse(1) { "/" }
            val ok = cab[cabecera.first.lowercase()] == cabecera.second
            peticiones += ruta to ok
            val sal = s.getOutputStream()
            val f = File(dir, ruta.trimStart('/').substringBefore('?'))
            if (!ok || !f.isFile) { sal.write("HTTP/1.1 ${if (!ok) 403 else 404} No\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return@use }
            val total = f.length()
            val rango = cab["range"]?.removePrefix("bytes=")?.split('-')
            val desde = rango?.getOrNull(0)?.toLongOrNull() ?: 0L
            val hasta = rango?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            val n = hasta - desde + 1
            val cabeza = if (rango != null) "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $desde-$hasta/$total\r\n" else "HTTP/1.1 200 OK\r\n"
            sal.write((cabeza + "Content-Type: video/x-matroska\r\nAccept-Ranges: bytes\r\nContent-Length: $n\r\nConnection: close\r\n\r\n").toByteArray())
            if (primera.startsWith("HEAD")) return@use
            java.io.RandomAccessFile(f, "r").use { r -> r.seek(desde); val buf = ByteArray(64 * 1024); var falta = n
                while (falta > 0) { val k = r.read(buf, 0, minOf(buf.size.toLong(), falta).toInt()); if (k < 0) break; runCatching { sal.write(buf, 0, k) }.onFailure { return@use }; falta -= k } }
        }
    }
}
