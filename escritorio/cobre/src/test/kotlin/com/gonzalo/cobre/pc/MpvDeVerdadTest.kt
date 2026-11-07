package com.gonzalo.cobre.pc

import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Episodio
import com.gonzalo.iptv.FichaSerie
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie
import com.gonzalo.iptv.Temporada
import com.gonzalo.iptv.TipoLista
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
 * El reproductor de verdad: mpv abierto por Cobre (sin ventana ni sonido: `--vo=null --ao=null`), con canales y vídeos hechos aquí con
 * ffmpeg y servidos por un servidor de este ordenador con las direcciones de un servidor de Xtream (que llevan el usuario y la contraseña), y
 * la cuenta de verdad (el emulador). Como en Mostaza del ordenador (`MpvDeVerdadTest`), se comprueba lo que hace el reproductor del móvil: la
 * dirección y la contraseña no salen en la línea de órdenes de mpv, las cabeceras del canal y el user-agent de la lista llegan, se cambia de
 * canal con las teclas de la ventana de mpv (las flechas, los números, la lista de canales con Intro), si se corta vuelve a conectar solo,
 * una película sigue donde la dejaste en otro dispositivo y queda vista al 90 % en la cuenta, y el episodio siguiente empieza solo. Sin mpv o
 * sin ffmpeg, se salta.
 */
class MpvDeVerdadTest : ConCuenta() {
    private lateinit var servidor: Servidor
    private val videos = File(System.getProperty("java.io.tmpdir"), "cobre-videos-prueba").also { it.mkdirs() }

    private fun hay(p: String) = runCatching { ProcessBuilder(p, "-version").redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() } == 0 }.getOrDefault(false)

    /** Un vídeo de [segundos], pequeño (64×48, 2 imágenes por segundo), con un audio en castellano; [ts]: en MPEG-TS, como un canal. */
    private fun video(nombre: String, segundos: Int, ts: Boolean = false): File {
        val f = File(videos, nombre)
        if (f.isFile && f.length() > 0) return f
        val o = mutableListOf("ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=orange:size=64x48:rate=2", "-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono", "-t", "$segundos",
            "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "ultrafast", "-g", "4", "-c:a", "aac", "-b:a", "8k", "-metadata:s:a:0", "language=spa")
        if (ts) o += listOf("-f", "mpegts")
        o += f.absolutePath
        val p = ProcessBuilder(o).redirectErrorStream(true).start(); p.inputStream.readBytes(); check(p.waitFor() == 0) { "ffmpeg no ha podido hacer $nombre" }
        return f
    }

    private val clave = "s3cr3t0"
    private lateinit var xtream: Lista
    private lateinit var m3u: Lista

    @Before fun prepara() {
        assumeTrue("Sin mpv en este ordenador", Mpv.candidatos().any { it.isFile && it.canExecute() })
        assumeTrue("Sin ffmpeg (para hacer los vídeos de prueba)", hay("ffmpeg"))
        preparaCuenta()
        val canal = video("canal.ts", 4, ts = true)
        val peli = video("pelicula.mkv", 200)
        val ep = video("episodio.mkv", 5)
        servidor = Servidor(mapOf(
            "/live/pepe/$clave/1.ts" to canal, "/live/pepe/$clave/2.ts" to canal, "/live/pepe/$clave/7.ts" to canal,
            "/movie/pepe/$clave/5001.mkv" to peli, "/series/pepe/$clave/9001.mkv" to ep, "/series/pepe/$clave/9002.mkv" to ep,
        )).also { it.arranca() }
        xtream = Lista("lxtream0001", "Mi servidor", TipoLista.XTREAM, servidor.base, "pepe", clave, agente = "AgenteDeLaLista/2.0")
        m3u = Lista("lm3u0000001", "Mi lista", TipoLista.M3U, "${servidor.base}/get.php?username=pepe&password=$clave", agente = "AgenteDeLaLista/2.0")
        Mpv.extra = listOf("--vo=null", "--ao=null")
        PantallaCompleta.pon(false)
        Reproductor.pulsoMs = 100; Reproductor.esperaReintento = 200
    }

    @After fun fin() {
        runCatching { Reproductor.alCerrarLaApp() }
        Reproductor.pulsoMs = 1_000L; Reproductor.esperaReintento = 1_000L; Mpv.extra = emptyList()
        if (::servidor.isInitialized) servidor.para()
        suelta()
    }

    private fun canal(n: Int, numero: Int) = Canal("c$n", "Canal $n", "Pruebas", numero = numero, url = "${servidor.base}/live/pepe/$clave/$n.ts", cabeceras = mapOf("Referer" to "http://ejemplo.test/"))

    /** La línea de órdenes de mpv, como la ve cualquier programa del ordenador: ni la dirección ni la contraseña. */
    private fun lineaSinSecretos() {
        espera("mpv se abre", 15) { Reproductor.pidParaPruebas() != null }
        val linea = File("/proc/${Reproductor.pidParaPruebas()}/cmdline").takeIf { it.isFile }?.readText()?.replace('\u0000', ' ') ?: return
        assertFalse(linea, linea.contains("127.0.0.1")); assertFalse(linea, linea.contains(clave)); assertFalse(linea, linea.contains("pepe"))
        assertTrue(linea.contains("--input-ipc-server=")); assertTrue(linea.contains("--input-conf="))
    }

    private fun pedidas(ruta: String) = servidor.peticiones.count { it.first == ruta }
    private fun indice() = (Reproductor.encargoParaPruebas() as? Encargo.Directo)?.indice

    @Test fun enDirectoSeCambiaDeCanalDesdeLaVentanaDeMpvYSeVuelveAConectar() {
        val canales = listOf(canal(1, 1), canal(2, 2), canal(7, 7))
        assertEquals(null, Reproductor.canal(m3u, canales, canales[0]))
        lineaSinSecretos()
        val e = Reproductor.estado
        fun como() = "índice=${indice()} aviso=${e.aviso} mensaje=${e.mensaje} peticiones=${servidor.peticiones.map { it.first }}"
        espera("pone el canal 1", 20, ::como) { pedidas("/live/pepe/$clave/1.ts") > 0 && e.activo && e.directo && e.numero == 1 }
        val p = servidor.peticiones.first { it.first == "/live/pepe/$clave/1.ts" }.second
        assertEquals("el user-agent de la lista", "AgenteDeLaLista/2.0", p["user-agent"]); assertEquals("las cabeceras del canal", "http://ejemplo.test/", p["referer"])
        // ↑ en la ventana de mpv: el siguiente; ↓: el anterior (lo cuenta mpv por su socket)
        assertTrue(Reproductor.teclaParaPruebas("UP"))
        espera("↑: el canal 2", 20, ::como) { indice() == 1 && pedidas("/live/pepe/$clave/2.ts") > 0 }
        assertTrue(Reproductor.teclaParaPruebas("DOWN"))
        espera("↓: otra vez el 1", 20, ::como) { indice() == 0 }
        // Los números: el 7 es el tercero de la lista (por su número)
        assertTrue(Reproductor.teclaParaPruebas("7"))
        espera("«7»: el canal 7", 20, ::como) { indice() == 2 && pedidas("/live/pepe/$clave/7.ts") > 0 && e.tecleado.isEmpty() }
        // Intro: la lista de canales encima, con el de ahora marcado; ↓ y Intro, el siguiente de la lista (el primero, dando la vuelta)
        assertTrue(Reproductor.teclaParaPruebas("ENTER"))
        espera("la lista encima", 10, ::como) { e.lista?.size == 3 && e.enLista == 2 }
        assertTrue(Reproductor.teclaParaPruebas("DOWN"))
        espera("baja en la lista (sin cambiar de canal)", 10, ::como) { e.enLista == 0 && indice() == 2 }
        val antes = pedidas("/live/pepe/$clave/1.ts")
        assertTrue(Reproductor.teclaParaPruebas("ENTER"))
        espera("Intro en la lista: ese canal", 20, ::como) { indice() == 0 && e.lista == null && pedidas("/live/pepe/$clave/1.ts") > antes }
        // El canal «se corta» (el vídeo de prueba dura 4 segundos): se vuelve a conectar solo, al mismo canal
        val ya = pedidas("/live/pepe/$clave/1.ts")
        espera("vuelve a conectar", 30, ::como) { pedidas("/live/pepe/$clave/1.ts") >= ya + 2 && indice() == 0 }
        assertTrue("todas las peticiones con su user-agent", servidor.peticiones.all { it.second["user-agent"] == "AgenteDeLaLista/2.0" })
        Reproductor.para()
        espera("se cierra", 15) { !Reproductor.estado.activo }
        sincroniza()
        val u = otro.coleccion(DatosCuenta.ESTADO).nodo("ultimo")
        assertNotNull(u); assertEquals("c1", u!!.texto("canal")); assertEquals(m3u.id, u.texto("lista"))
    }

    @Test fun unaPeliculaSigueDondeLaDejasteYQuedaVista() {
        // En el móvil la dejaste en el minuto 2
        val p = Pelicula(5001, "Una de prueba", extension = "mkv")
        otro.coleccion(DatosCuenta.PROGRESO).pon(DatosCuenta.refProgreso(xtream.id, "p5001"), mapOf("lista" to xtream.id, "v" to "p5001", "tipo" to "pelicula", "id" to 5001L, "ext" to "mkv",
            "t" to "Una de prueba", "pos" to 120_000L, "dur" to 200_000L, "visto" to false, "ul" to System.currentTimeMillis()))
        sincroniza()
        espera("llega lo del móvil") { MiCuenta.progreso(xtream.id, "p5001")?.pos == 120_000L }
        assertEquals(null, Reproductor.pon(Encargo.DePelicula(xtream, p, 200)))
        lineaSinSecretos()
        val e = Reproductor.estado
        espera("sigue por el minuto 2", 30, { "pos=${e.pos} aviso=${e.aviso}" }) { e.reproduciendo && e.pos >= 119_000L }
        assertTrue(pedidas("/movie/pepe/$clave/5001.mkv") > 0)
        Reproductor.salta(65)
        espera("más allá del 90 %", 20, { "pos=${e.pos}" }) { e.pos >= 184_000L }
        Reproductor.para()
        espera("se cierra", 15) { !Reproductor.estado.activo }
        sincroniza()
        val n = otro.coleccion(DatosCuenta.PROGRESO).nodo(DatosCuenta.refProgreso(xtream.id, "p5001"))!!
        assertTrue("vista al 90 %", n.si("visto")); assertTrue("la posición viaja: ${n.entero("pos")}", n.entero("pos") >= 180_000L)
        assertTrue("lo que dura: ${n.entero("dur")}", kotlin.math.abs(n.entero("dur") - 200_000L) < 2_000L)
        assertEquals("pelicula", n.texto("tipo")); assertEquals("mkv", n.texto("ext"))
    }

    @Test fun elEpisodioSiguienteEmpiezaSolo() {
        val s = Serie(3001, "Serie de prueba")
        val e1 = Episodio(9001, 1, 1, "Serie de prueba - S01E01 - El primero", "mkv"); val e2 = Episodio(9002, 1, 2, "El segundo", "mkv")
        val ficha = FichaSerie(s, listOf(Temporada(1, "Temporada 1")), mapOf(1 to listOf(e1, e2)))
        assertEquals(null, Reproductor.pon(Encargo.DeEpisodio(xtream, s, ficha, e1)))
        val e = Reproductor.estado
        fun como() = "subtítulo=${e.subtitulo} siguiente=${e.siguiente} cuenta=${e.cuentaAtras} aviso=${e.aviso} peticiones=${servidor.peticiones.map { it.first }}"
        espera("el primero", 20, ::como) { e.subtitulo == "T1 · E1 · El primero" && pedidas("/series/pepe/$clave/9001.mkv") > 0 }
        espera("al acabar, el segundo solo (en 8 s)", 40, ::como) { pedidas("/series/pepe/$clave/9002.mkv") > 0 && e.subtitulo == "T1 · E2 · El segundo" }
        Reproductor.para()
        espera("se cierra", 15) { !Reproductor.estado.activo }
        sincroniza()
        val n = otro.coleccion(DatosCuenta.PROGRESO).nodo(DatosCuenta.refProgreso(xtream.id, "e9001"))!!
        assertTrue("el primero, visto", n.si("visto")); assertEquals("episodio", n.texto("tipo")); assertEquals(3001L, n.entero("serie")); assertEquals("El primero", n.texto("t"))
        assertEquals(1L, n.entero("temp")); assertEquals(1L, n.entero("ep"))
    }

    /** Un servidor de vídeos de este ordenador, con saltos (Range), que apunta cada petición con sus cabeceras. */
    class Servidor(private val rutas: Map<String, File>) {
        private val ss = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val base = "http://127.0.0.1:${ss.localPort}"
        /** (ruta, cabeceras en minúsculas) de cada petición. */
        val peticiones = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()

        fun arranca() { Thread({ while (!ss.isClosed) runCatching { val s = ss.accept(); Thread { atiende(s) }.apply { isDaemon = true; start() } } }, "servidor-videos").apply { isDaemon = true; start() } }
        fun para() { runCatching { ss.close() } }

        private fun atiende(s: java.net.Socket) = s.use {
            val ent = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val primera = ent.readLine() ?: return@use
            val cab = HashMap<String, String>()
            while (true) { val l = ent.readLine() ?: break; if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) cab[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
            val ruta = primera.split(' ').getOrElse(1) { "/" }.substringBefore('?')
            peticiones += ruta to cab
            val sal = s.getOutputStream()
            val f = rutas[ruta]
            if (f == null || !f.isFile) { sal.write("HTTP/1.1 404 No\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return@use }
            val total = f.length()
            val rango = cab["range"]?.removePrefix("bytes=")?.split('-')
            val desde = rango?.getOrNull(0)?.toLongOrNull() ?: 0L
            val hasta = rango?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            val n = hasta - desde + 1
            val cabeza = if (rango != null) "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $desde-$hasta/$total\r\n" else "HTTP/1.1 200 OK\r\n"
            val tipo = if (ruta.endsWith(".ts")) "video/mp2t" else "video/x-matroska"
            sal.write((cabeza + "Content-Type: $tipo\r\nAccept-Ranges: bytes\r\nContent-Length: $n\r\nConnection: close\r\n\r\n").toByteArray())
            if (primera.startsWith("HEAD")) return@use
            java.io.RandomAccessFile(f, "r").use { r -> r.seek(desde); val buf = ByteArray(64 * 1024); var falta = n
                while (falta > 0) { val k = r.read(buf, 0, minOf(buf.size.toLong(), falta).toInt()); if (k < 0) break; runCatching { sal.write(buf, 0, k) }.onFailure { return@use }; falta -= k } }
        }
    }
}
