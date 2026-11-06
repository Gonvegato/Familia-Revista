package com.gonzalo.torrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.random.Random

/** Lo que sale mal, con una sesión de verdad (solo en este ordenador) y tiempos de espera cortos. */
class ErroresTest {
    @get:Rule val tmp = TemporaryFolder()
    @Before fun nativos() = hayLibtorrent()

    private val red = RedFija()
    private val tiempos = Tiempos(sinParesMs = 1_500, metadatosMs = 3_000, esperaPiezaMs = 5_000, apagadoMs = 300, borradoMs = 2_000)
    private val cache get() = File(tmp.root, "cache")
    private val soloLocal = listOf("dht:local")

    private fun motor(pares: List<Int> = emptyList(), ajustes: AjustesTorrent = AjustesTorrent(), t: Tiempos = tiempos, libre: (File) -> Long = { it.usableSpace }) =
        MotorTorrent(cache, ajustes, red, ConfigSesion(interfaces = "127.0.0.1:0", dht = false, lsd = false, pares = pares.map { "127.0.0.1" to it }), t, Ventanas(), libre)

    private fun hashAlAzar() = Random.nextBytes(20).joinToString("") { "%02x".format(it) }

    private fun falla(bloque: suspend () -> Unit): ErrorTorrent = runBlocking {
        try { bloque(); throw AssertionError("Tenía que fallar") } catch (e: ErrorTorrent) { e }
    }

    @Test fun hashQueNoLoEs() {
        val m = motor()
        try {
            val e = falla { m.prepara("hola", null, soloLocal, null) }
            assertEquals(ErrorTorrent.Motivo.HASH_NO_VALIDO, e.motivo)
            assertTrue(e.message!!.contains("«hola»"))
            assertNull(m.estado("hola"))
            m.para("hola")   // no pasa nada
        } finally { m.cierra() }
    }

    @Test fun sinRed() {
        red.tipo = TipoRed.NINGUNA
        val m = motor()
        try {
            assertEquals(ErrorTorrent.Motivo.SIN_RED, falla { m.prepara(hashAlAzar(), null, soloLocal, null) }.motivo)
            assertFalse(m.enMarcha())
        } finally { m.cierra() }
    }

    @Test fun conDatosMovilesSinSoloWifiSiVa() {
        red.tipo = TipoRed.MOVIL
        val m = motor()
        try {
            // No hay nadie, pero no es por la red: llega a buscar
            assertEquals(ErrorTorrent.Motivo.SIN_PARES, falla { m.prepara(hashAlAzar(), null, soloLocal, null) }.motivo)
        } finally { m.cierra() }
    }

    @Test fun nadieLoComparte() {
        val m = motor()
        val hash = hashAlAzar()
        try {
            val inicio = System.currentTimeMillis()
            val e = falla { m.prepara(hash, null, soloLocal, "Algo") }
            assertEquals(ErrorTorrent.Motivo.SIN_PARES, e.motivo)
            assertTrue(e.message!!, e.message!!.startsWith("Nadie comparte este torrent"))
            assertTrue(System.currentTimeMillis() - inicio < 6_000)
            // No queda nada: ni el torrent ni su carpeta, y la sesión se apaga
            assertNull(m.estado(hash))
            assertTrue(hastaQue { !File(cache, hash).exists() })
            assertTrue(hastaQue { !m.enMarcha() })
        } finally { m.cierra() }
    }

    @Test fun hayAlguienPeroNoDaLosMetadatos() {
        ParMudo().use { mudo ->
            // libtorrent prueba primero por uTP (UDP), y el mudo solo escucha por TCP: llega a él pasados unos 3 s
            val m = motor(listOf(mudo.puerto), t = tiempos.copy(metadatosMs = 9_000))
            val hash = hashAlAzar()
            try {
                val e = falla { m.prepara(hash, null, soloLocal, null) }
                assertEquals(ErrorTorrent.Motivo.SIN_METADATOS, e.motivo)
                assertTrue(e.message!!, e.message!!.contains("no ha llegado la lista de sus archivos"))
                assertTrue("El par mudo ha recibido la conexión", mudo.conexiones > 0)
                assertNull(m.estado(hash))
            } finally { m.cierra() }
        }
    }

    @Test fun archivoQueNoEstaYSinSitio() = runBlocking {
        val dir = File(tmp.root, "semilla/Corto")
        creaVideo(File(dir, "corto.mp4"), 300_000)
        val ti = creaTorrent(dir, 16 * 1024)
        Sembrador(subidaBs = 4 * 1024 * 1024).use { s ->
            s.siembra(ti, dir.parentFile!!)
            val m = motor(listOf(s.puerto), libre = { 10L * 1024 * 1024 })
            try {
                val e = falla { m.prepara(ti.hashV1, 7, soloLocal, null) }
                assertEquals(ErrorTorrent.Motivo.ARCHIVO_NO_ENCONTRADO, e.motivo)
                assertEquals("El archivo n.º 7 no está en este torrent (tiene 1). Prueba con otra fuente.", e.message)
                assertNull(m.estado(ti.hashV1))
                // Con 10 MB libres no cabe (se pide un margen de 64 MB además del vídeo)
                val sitio = falla { m.prepara(ti.hashV1, null, soloLocal, null) }
                assertEquals(ErrorTorrent.Motivo.SIN_ESPACIO, sitio.motivo)
                assertTrue(sitio.message!!, sitio.message!!.startsWith("No hay sitio para «corto.mp4»"))
                assertTrue(hastaQue { !File(cache, ti.hashV1).exists() })
            } finally { m.cierra() }
        }
    }

    @Test fun cancelarPreparaLoQuitaTodo() = runBlocking {
        val m = motor(t = tiempos.copy(sinParesMs = 60_000, metadatosMs = 60_000))
        val hash = hashAlAzar()
        try {
            val trabajo = async(Dispatchers.IO) { m.prepara(hash, null, soloLocal, null) }
            assertTrue(hastaQue { m.estado(hash) != null })
            assertEquals(EstadoTorrent.Fase.BUSCANDO, m.estado(hash)!!.fase)
            assertTrue(File(cache, hash).isDirectory)
            val inicio = System.currentTimeMillis()
            trabajo.cancel()
            runCatching { trabajo.await() }
            assertTrue(trabajo.isCancelled)
            assertTrue(System.currentTimeMillis() - inicio < 2_000)
            m.esperaSerie()
            assertNull(m.estado(hash))
            assertTrue(hastaQue { !File(cache, hash).exists() })
            assertTrue(hastaQue { !m.enMarcha() })
        } finally { m.cierra() }
        assertTrue("Hilos que quedan: ${hilosDelMotor()}", hastaQue(5_000) { hilosDelMotor().isEmpty() })
    }

    @Test fun pararMientrasPreparaYDosPreparaDelMismo() = runBlocking {
        val m = motor(t = tiempos.copy(sinParesMs = 60_000, metadatosMs = 60_000))
        val hash = hashAlAzar()
        try {
            val uno = async(Dispatchers.IO) { runCatching { m.prepara(hash, null, soloLocal, null) } }
            val dos = async(Dispatchers.IO) { runCatching { m.prepara(hash.uppercase(), null, soloLocal, null) } }
            assertTrue(hastaQue { m.estado(hash) != null })
            delay(300)
            m.para(hash)
            val e1 = uno.await().exceptionOrNull() as ErrorTorrent
            val e2 = dos.await().exceptionOrNull() as ErrorTorrent
            assertEquals(ErrorTorrent.Motivo.PARADO, e1.motivo)
            assertEquals(ErrorTorrent.Motivo.PARADO, e2.motivo)
            assertTrue(hastaQue { !File(cache, hash).exists() })
        } finally { m.cierra() }
    }

    @Test fun losRestosDeOtraVezSeBorran() {
        // Una carpeta a medias sin datos para reanudar (la app se cerró de golpe) no sirve: fuera al crear el motor
        val resto = File(cache, hashAlAzar()).also { it.mkdirs(); File(it, "algo.mkv").writeBytes(ByteArray(1000)) }
        val guardado = File(cache, hashAlAzar()).also { it.mkdirs(); File(it, MotorTorrent.REANUDAR).writeBytes(ByteArray(10)) }
        val m = motor()
        try {
            runBlocking { m.esperaSerie() }
            assertFalse(resto.exists())
            assertTrue(guardado.exists())
        } finally { m.cierra() }
    }
}

/** Un «par» que contesta al saludo de BitTorrent pero no sabe dar metadatos (sin extensiones) y no manda nada más. */
internal class ParMudo : AutoCloseable {
    private val ss = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    val puerto = ss.localPort
    @Volatile var conexiones = 0

    init {
        thread(isDaemon = true, name = "par-mudo") {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                conexiones++
                thread(isDaemon = true) {
                    runCatching {
                        s.use {
                            it.soTimeout = 30_000
                            val i = it.getInputStream()
                            val hs = ByteArray(68)
                            var n = 0
                            while (n < 68) { val r = i.read(hs, n, 68 - n); if (r < 0) return@use; n += r }
                            // Un saludo cifrado (lo intenta primero) no es esto: se cuelga y libtorrent prueba sin cifrar
                            if (hs[0].toInt() != 19) return@use
                            val resp = hs.copyOf()
                            for (k in 20 until 28) resp[k] = 0
                            "-XX0001-123456789012".toByteArray().copyInto(resp, 48)
                            it.getOutputStream().write(resp)
                            it.getOutputStream().flush()
                            val buf = ByteArray(1024)
                            while (i.read(buf) >= 0) { /* nada */ }
                        }
                    }
                }
            }
        }
    }

    override fun close() = ss.close()
}
