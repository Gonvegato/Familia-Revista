package com.gonzalo.torrent

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.concurrent.thread

/**
 * Lo de verdad: una sesión de libtorrent siembra un vídeo en 127.0.0.1 y el motor, en otra sesión, lo ve por su dirección local
 * mientras baja (sin internet, sin DHT: el único par es el sembrador). Se comprueba byte a byte lo que llega por HTTP.
 */
class MotorLocalTest {
    @get:Rule val tmp = TemporaryFolder()
    @Before fun nativos() = hayLibtorrent()

    private val red = RedFija()
    /** Ventanas pequeñas (el vídeo de prueba tiene 4 MB): así se nota que la descarga sigue al reproductor. */
    private val ventanas = Ventanas(
        criticaBytes = 128 * 1024, criticaMinPiezas = 2, criticaMaxPiezas = 8, lecturaBytes = 512 * 1024,
        cabezaBytes = 64 * 1024, colaBytes = 64 * 1024, ritmoBs = 512 * 1024,
    )
    private val tiempos = Tiempos(sinParesMs = 2_000, metadatosMs = 4_000, esperaPiezaMs = 20_000, apagadoMs = 500, borradoMs = 3_000)
    /** Sin trackers (nada sale de este ordenador); la DHT de la sesión está apagada. */
    private val soloLocal = listOf("dht:local")
    private val cache get() = File(tmp.root, "cache")

    private fun motor(pares: List<Int>, ajustes: AjustesTorrent = AjustesTorrent(), t: Tiempos = tiempos, libre: (File) -> Long = { it.usableSpace }) =
        MotorTorrent(cache, ajustes, red, ConfigSesion(interfaces = "127.0.0.1:0", dht = false, lsd = false, pares = pares.map { "127.0.0.1" to it }), t, ventanas, object : Disco { override fun libre(dir: File) = libre(dir) })

    /** Una película con su muestra y su .nfo, en una carpeta, con piezas de 32 KB. */
    private fun pelicula(nombre: String = "Peli (2024)", tamano: Int = 4 * 1024 * 1024 + 12_345, semilla: Int = 1): Triple<File, ByteArray, org.libtorrent4j.TorrentInfo> {
        val dir = File(tmp.root, "semilla/$nombre")
        val video = creaVideo(File(dir, "$nombre.1080p.mkv"), tamano, semilla)
        creaVideo(File(dir, "Sample/peli-sample.mkv"), 200_000, semilla + 100)
        File(dir, "$nombre.nfo").writeText("Una película de prueba")
        return Triple(dir, video, creaTorrent(dir, 32 * 1024, relleno = true))
    }

    @Test fun streamDeUnSembradorEnEsteOrdenador() = runBlocking {
        val (dir, video, ti) = pelicula()
        val hash = ti.hashV1
        Sembrador(subidaBs = 512 * 1024).use { s ->
            s.siembra(ti, dir.parentFile!!)
            val m = motor(listOf(s.puerto))
            try {
                val f = m.prepara(hash, null, soloLocal, null)
                // El vídeo grande (no la muestra ni el .nfo)
                assertEquals("Peli (2024).1080p.mkv", f.archivo)
                assertEquals(video.size.toLong(), f.tamano)
                assertEquals("video/x-matroska", f.mime)
                assertTrue(f.url, f.url.startsWith("http://127.0.0.1:") && f.url.endsWith("/Peli%20%282024%29.1080p.mkv"))

                // Un salto a la mitad mientras baja: llega enseguida (la descarga va a donde lee el reproductor)
                val medio = pide(f.url, "bytes=2000000-2099999")
                assertEquals(206, medio.codigo)
                assertEquals("bytes 2000000-2099999/${video.size}", medio.cabeceras["content-range"])
                assertEquals("bytes", medio.cabeceras["accept-ranges"])
                assertArrayEquals(video.copyOfRange(2_000_000, 2_100_000), medio.cuerpo)
                val e = m.estado(hash)!!
                assertTrue("Aún bajando: ${e.progreso}", e.progreso < 0.9f)
                assertTrue(e.pares >= 1)
                assertEquals(EstadoTorrent.Fase.DESCARGANDO, e.fase)
                assertEquals(f.archivo, e.archivo)

                // El final (los reproductores leen ahí el índice), de las dos formas
                assertArrayEquals(video.copyOfRange(video.size - 1000, video.size), pide(f.url, "bytes=-1000").cuerpo)
                val ultimo = pide(f.url, "bytes=${video.size - 1}-")
                assertEquals("bytes ${video.size - 1}-${video.size - 1}/${video.size}", ultimo.cabeceras["content-range"])
                assertArrayEquals(video.copyOfRange(video.size - 1, video.size), ultimo.cuerpo)
                // Otro salto, hacia atrás
                assertArrayEquals(video.copyOfRange(100_000, 150_001), pide(f.url, "bytes=100000-150000").cuerpo)
                assertEquals(416, pide(f.url, "bytes=${video.size}-").codigo)

                // Entero, byte a byte
                val todo = pide(f.url)
                assertEquals(200, todo.codigo)
                assertEquals("${video.size}", todo.cabeceras["content-length"])
                assertArrayEquals(video, todo.cuerpo)
                val head = pide(f.url, metodo = "HEAD")
                assertEquals(200, head.codigo)
                assertEquals("video/x-matroska", head.cabeceras["content-type"])

                assertTrue(hastaQue { m.estado(hash)?.fase == EstadoTorrent.Fase.COMPLETO })
                val fin = m.estado(hash)!!
                assertEquals(1f, fin.progreso)
                assertEquals(video.size.toLong(), fin.descargado)
                assertEquals(0L, fin.listoAdelante)   // el último lector acabó al final
                assertNull(fin.error)

                // Parar: deja de estar, la dirección ya no sirve y su carpeta se borra
                val carpeta = File(cache, hash)
                assertTrue(carpeta.isDirectory)
                m.para(hash)
                assertNull(m.estado(hash))
                val despues = runCatching { pide(f.url).codigo }
                assertTrue("${despues.getOrNull()}", despues.isFailure || despues.getOrNull() == 404)
                assertTrue("Se ha borrado la carpeta", hastaQue { !carpeta.exists() })
                // Sin nada en marcha, la sesión se apaga sola
                assertTrue(hastaQue { !m.enMarcha() })
                assertEquals(0, red.vigilando)
            } finally {
                m.cierra()
            }
        }
        assertTrue("Hilos que quedan: ${hilosDelMotor()}", hastaQue(5_000) { hilosDelMotor().isEmpty() })
    }

    @Test fun conFileIdxYPorNombre() = runBlocking {
        val (dir, _, ti) = pelicula()
        val fs = ti.files()
        val muestra = (0 until fs.numFiles()).first { fs.filePath(it).endsWith("peli-sample.mkv") }
        val esperado = File(dir, "Sample/peli-sample.mkv").readBytes()
        Sembrador(subidaBs = 4 * 1024 * 1024).use { s ->
            s.siembra(ti, dir.parentFile!!)
            val m = motor(listOf(s.puerto))
            try {
                // El addon pide la muestra por su índice: es la muestra lo que se ve
                val f = m.prepara(ti.hashV1, muestra, soloLocal, null)
                assertEquals("peli-sample.mkv", f.archivo)
                assertArrayEquals(esperado, pide(f.url).cuerpo)
                // El mismo torrent, otro archivo (por su nombre): se reutiliza el torrent
                val nfo = m.prepara(ti.hashV1, null, soloLocal, "Peli (2024).nfo")
                assertEquals("Peli (2024).nfo", nfo.archivo)
                assertEquals("application/octet-stream", nfo.mime)
                assertEquals("Una película de prueba", pide(nfo.url).cuerpo.toString(Charsets.UTF_8))
                // Y el primero sigue sirviendo
                assertArrayEquals(esperado.copyOfRange(1000, 2000), pide(f.url, "bytes=1000-1999").cuerpo)
            } finally {
                m.cierra()
            }
        }
    }

    @Test fun variosALaVez() = runBlocking {
        val (dirA, videoA, tiA) = pelicula("Una", 1_500_000, 3)
        val (dirB, videoB, tiB) = pelicula("Otra", 2_000_000, 4)
        Sembrador(subidaBs = 2 * 1024 * 1024).use { s ->
            s.siembra(tiA, dirA.parentFile!!)
            s.siembra(tiB, dirB.parentFile!!)
            val m = motor(listOf(s.puerto))
            try {
                val (fa, fb) = listOf(tiA, tiB).map { async { m.prepara(it.hashV1, null, soloLocal, null) } }.awaitAll()
                var ra: ByteArray? = null
                var rb: ByteArray? = null
                val ha = thread { ra = pide(fa.url).cuerpo }
                val hb = thread { rb = pide(fb.url).cuerpo }
                ha.join(60_000); hb.join(60_000)
                assertArrayEquals(videoA, ra)
                assertArrayEquals(videoB, rb)
                // Parar uno no toca al otro
                m.para(tiA.hashV1)
                assertNull(m.estado(tiA.hashV1))
                assertArrayEquals(videoB.copyOfRange(500_000, 600_000), pide(fb.url, "bytes=500000-599999").cuerpo)
                assertTrue(hastaQue { !File(cache, tiA.hashV1).exists() })
                assertTrue(File(cache, tiB.hashV1).exists())
            } finally {
                m.cierra()
            }
        }
    }

    @Test fun seguirCompartiendoYReanudarSinPares() = runBlocking {
        val (dir, video, ti) = pelicula()
        val hash = ti.hashV1
        val carpeta = File(cache, hash)
        Sembrador(subidaBs = 8 * 1024 * 1024).use { s ->
            s.siembra(ti, dir.parentFile!!)
            val m = motor(listOf(s.puerto), AjustesTorrent(seguirCompartiendoAlParar = true))
            try {
                val f = m.prepara(hash, null, soloLocal, null)
                assertArrayEquals(video, pide(f.url).cuerpo)
                m.para(hash)
                m.esperaSerie()
                assertNull(m.estado(hash))
                // Se queda compartiendo: no se borra
                delay(1_000)
                assertTrue(carpeta.isDirectory)
                assertTrue(m.enMarcha())
                m.paraTodo()
                m.esperaSerie()
                assertTrue("Guardado para reanudar", File(carpeta, MotorTorrent.REANUDAR).isFile)
            } finally {
                m.cierra()
            }
        }
        // Otro día, sin nadie compartiéndolo: está entero en la caché, y se ve sin esperar a nadie
        val m = motor(emptyList(), AjustesTorrent(seguirCompartiendoAlParar = false))
        try {
            val f = m.prepara(hash, null, soloLocal, null)
            assertArrayEquals(video, pide(f.url).cuerpo)
            assertEquals(EstadoTorrent.Fase.COMPLETO, m.estado(hash)!!.fase)
            assertFalse(File(carpeta, MotorTorrent.REANUDAR).exists())
            m.para(hash)
            assertTrue(hastaQue { !carpeta.exists() })
        } finally {
            m.cierra()
        }
    }

    @Test fun soloWifiEnMarcha() = runBlocking {
        val (dir, video, ti) = pelicula()
        val hash = ti.hashV1
        Sembrador(subidaBs = 256 * 1024).use { s ->
            s.siembra(ti, dir.parentFile!!)
            red.tipo = TipoRed.MOVIL
            val m = motor(listOf(s.puerto), AjustesTorrent(soloWifi = true))
            try {
                // Con datos móviles y «Solo con wifi», ni empieza
                val e = runCatching { m.prepara(hash, null, soloLocal, null) }.exceptionOrNull() as ErrorTorrent
                assertEquals(ErrorTorrent.Motivo.SIN_WIFI, e.motivo)
                assertTrue(e.message!!.contains("datos móviles"))
                assertFalse(m.enMarcha())
                // Se quita «Solo con wifi»: ya va
                m.ajusta(AjustesTorrent(soloWifi = false))
                val f = m.prepara(hash, null, soloLocal, null)
                assertArrayEquals(video.copyOfRange(0, 1000), pide(f.url, "bytes=0-999").cuerpo)
                // Se vuelve a poner, en marcha y con datos móviles: todo en pausa, y lo que falta no llega
                m.ajusta(AjustesTorrent(soloWifi = true))
                m.esperaSerie()
                val pausa = m.estado(hash)!!
                assertEquals(EstadoTorrent.Fase.EN_PAUSA, pausa.fase)
                assertTrue(pausa.error!!.contains("Solo con wifi"))
                val corta = runCatching { pide(f.url, "bytes=3000000-") }
                assertTrue(corta.isFailure || corta.getOrThrow().cuerpo.size < video.size - 3_000_000)
                // Llega la wifi: sigue
                red.cambia(TipoRed.WIFI)
                m.esperaSerie()
                assertNull(m.estado(hash)!!.error)
                assertArrayEquals(video.copyOfRange(3_000_000, video.size), pide(f.url, "bytes=3000000-").cuerpo)
            } finally {
                m.cierra()
            }
        }
    }
}
