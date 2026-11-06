package com.gonzalo.torrent

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.random.Random

/** Un archivo que «se descarga»: solo se puede leer hasta [disponible]; lo que pasa de ahí espera (como una pieza que no ha llegado). */
private class Falso(val datos: ByteArray, @Volatile var disponible: Long = datos.size.toLong(), val esperaMaxMs: Long = 5_000) : Contenido {
    val aperturas = CopyOnWriteArrayList<Long>()
    @Volatile var abiertos = 0
    private val cerrojo = ReentrantLock()
    private val llega = cerrojo.newCondition()
    @Volatile var fallo: ErrorTorrent? = null

    override val tamano: Long get() = datos.size.toLong()
    override val mime = "video/x-matroska"

    fun llegaHasta(n: Long) = cerrojo.withLock { disponible = n; llega.signalAll() }
    fun falla(e: ErrorTorrent) = cerrojo.withLock { fallo = e; llega.signalAll() }

    override fun abre(desde: Long): Lectura {
        aperturas += desde
        abiertos++
        return object : Lectura {
            override fun lee(pos: Long, buf: ByteArray, max: Int): Int {
                cerrojo.withLock {
                    val limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(esperaMaxMs)
                    while (pos >= disponible) {
                        fallo?.let { throw it }
                        if (System.nanoTime() > limite) throw ErrorTorrent(ErrorTorrent.Motivo.SIN_DATOS, "no llega")
                        llega.await(50, TimeUnit.MILLISECONDS)
                    }
                    val n = minOf(max.toLong(), disponible - pos, 1000L).toInt()  // de 1000 en 1000, como si fueran piezas pequeñas
                    System.arraycopy(datos, pos.toInt(), buf, 0, n)
                    return n
                }
            }
            override fun close() { abiertos-- }
        }
    }
}

class ServidorLocalTest {
    private val datos = Random(7).nextBytes(100_000)
    private val falso = Falso(datos)
    private val srv = ServidorLocal({ ruta -> if (ruta == "/clave/peli.mkv") falso else null }, inactividadMs = 5_000)
    private val url = "http://127.0.0.1:${srv.puerto}/clave/peli.mkv"

    @After fun cierra() = srv.close()

    private class Respuesta(val codigo: Int, val cabeceras: Map<String, String>, val cuerpo: ByteArray)

    private fun pide(u: String = url, rango: String? = null, metodo: String = "GET"): Respuesta {
        val c = URL(u).openConnection() as HttpURLConnection
        c.requestMethod = metodo
        c.readTimeout = 20_000
        rango?.let { c.setRequestProperty("Range", it) }
        val codigo = c.responseCode
        val cab = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") }
        val cuerpo = (if (codigo >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Respuesta(codigo, cab, cuerpo)
    }

    @Test fun enteroConSuTipoYSuTamano() {
        val r = pide()
        assertEquals(200, r.codigo)
        assertEquals("video/x-matroska", r.cabeceras["content-type"])
        assertEquals("100000", r.cabeceras["content-length"])
        assertEquals("bytes", r.cabeceras["accept-ranges"])
        assertArrayEquals(datos, r.cuerpo)
    }

    @Test fun rangos() {
        val r = pide(rango = "bytes=1000-1999")
        assertEquals(206, r.codigo)
        assertEquals("bytes 1000-1999/100000", r.cabeceras["content-range"])
        assertEquals("1000", r.cabeceras["content-length"])
        assertArrayEquals(datos.copyOfRange(1000, 2000), r.cuerpo)
        // El final del archivo, de las dos formas
        assertArrayEquals(datos.copyOfRange(99_000, 100_000), pide(rango = "bytes=-1000").cuerpo)
        val fin = pide(rango = "bytes=99999-")
        assertEquals("bytes 99999-99999/100000", fin.cabeceras["content-range"])
        assertArrayEquals(datos.copyOfRange(99_999, 100_000), fin.cuerpo)
        // Cada respuesta abre un lector donde empieza (es lo que mueve la descarga)
        assertEquals(listOf(1000L, 99_000L, 99_999L), falso.aperturas.toList())
    }

    @Test fun fueraDelArchivo416() {
        val r = pide(rango = "bytes=100000-")
        assertEquals(416, r.codigo)
        assertEquals("bytes */100000", r.cabeceras["content-range"])
    }

    @Test fun headNoLeeNada() {
        val r = pide(metodo = "HEAD")
        assertEquals(200, r.codigo)
        assertEquals("100000", r.cabeceras["content-length"])
        assertEquals(0, falso.aperturas.size)
    }

    @Test fun otraRuta404YOtroMetodo405() {
        assertEquals(404, pide("http://127.0.0.1:${srv.puerto}/otra/peli.mkv").codigo)
        assertEquals(404, pide("http://127.0.0.1:${srv.puerto}/").codigo)
        assertEquals(405, crudo("POST /clave/peli.mkv HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").substringBefore("\r\n").substringAfter(' ').substringBefore(' ').toInt())
    }

    @Test fun soloEn127() {
        // Escucha solo en el propio móvil: desde otra dirección de la máquina no se llega
        val otras = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
        for (a in otras) {
            val conecta = runCatching { Socket().use { s -> s.connect(InetSocketAddress(a, srv.puerto), 500) } }
            assertTrue("Se ha podido conectar desde $a", conecta.isFailure)
        }
    }

    @Test fun conexionReutilizada() {
        Socket("127.0.0.1", srv.puerto).use { s ->
            s.soTimeout = 10_000
            val o = s.getOutputStream()
            val i = s.getInputStream()
            o.write("GET /clave/peli.mkv HTTP/1.1\r\nHost: x\r\nRange: bytes=0-9\r\n\r\n".toByteArray())
            assertArrayEquals(datos.copyOfRange(0, 10), leeRespuesta(i))
            o.write("GET /clave/peli.mkv HTTP/1.1\r\nHost: x\r\nRange: bytes=50-59\r\n\r\n".toByteArray())
            assertArrayEquals(datos.copyOfRange(50, 60), leeRespuesta(i))
        }
    }

    @Test fun loQueNoHaLlegadoEsperaYLuegoSale() {
        falso.llegaHasta(10_000)
        val inicio = System.currentTimeMillis()
        thread { Thread.sleep(600); falso.llegaHasta(50_000); Thread.sleep(400); falso.llegaHasta(100_000) }
        val r = pide(rango = "bytes=40000-60000")
        val tardo = System.currentTimeMillis() - inicio
        assertEquals(206, r.codigo)
        assertArrayEquals(datos.copyOfRange(40_000, 60_001), r.cuerpo)
        assertTrue("Ha esperado a los datos ($tardo ms)", tardo >= 900)
    }

    @Test fun siNoLleganSeCortaLaRespuesta() {
        falso.llegaHasta(5_000)
        val c = URL(url).openConnection() as HttpURLConnection
        c.readTimeout = 20_000
        assertEquals(200, c.responseCode)
        thread { Thread.sleep(300); falso.falla(ErrorTorrent(ErrorTorrent.Motivo.PARADO, "parado")) }
        val leido = ByteArrayOutputStream()
        val r = runCatching { c.inputStream.use { it.copyTo(leido) } }
        // El reproductor recibe menos de lo prometido (y lo pedirá otra vez): la conexión no se queda colgada
        assertTrue(r.isFailure || leido.size() < datos.size)
        assertArrayEquals(datos.copyOfRange(0, 5_000), leido.toByteArray().copyOfRange(0, 5_000))
        // El lector se cierra
        val hasta = System.currentTimeMillis() + 3_000
        while (falso.abiertos > 0 && System.currentTimeMillis() < hasta) Thread.sleep(20)
        assertEquals(0, falso.abiertos)
    }

    @Test fun alCerrarNoQuedanHilos() {
        assertArrayEquals(datos, pide().cuerpo)
        srv.close()
        val hasta = System.currentTimeMillis() + 3_000
        fun vivos() = Thread.getAllStackTraces().keys.filter { it.isAlive && it.name.startsWith("torrent-http") }
        while (vivos().isNotEmpty() && System.currentTimeMillis() < hasta) Thread.sleep(20)
        assertEquals(emptyList<Thread>(), vivos())
        assertTrue(runCatching { Socket("127.0.0.1", srv.puerto).close() }.isFailure)
    }

    private fun crudo(peticion: String): String = Socket("127.0.0.1", srv.puerto).use { s ->
        s.soTimeout = 5_000
        s.getOutputStream().write(peticion.toByteArray())
        s.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
    }

    /** Lee una respuesta (cabeceras y cuerpo según Content-Length) sin cerrar la conexión. */
    private fun leeRespuesta(i: InputStream): ByteArray {
        val cab = StringBuilder()
        while (!cab.endsWith("\r\n\r\n")) cab.append(i.read().toChar())
        val largo = Regex("Content-Length: (\\d+)").find(cab)!!.groupValues[1].toInt()
        return ByteArray(largo).also { b -> var n = 0; while (n < largo) n += i.read(b, n, largo - n) }
    }
}
