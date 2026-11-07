package com.gonzalo.iptv

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream

/** La red de verdad contra un servidor de este ordenador: redirecciones, gzip, el user-agent y errores sin la dirección. */
class RedJavaTest {
    private lateinit var s: HttpServer
    private val base get() = "http://127.0.0.1:${s.address.port}"
    private var agenteVisto = ""

    @Before fun arranca() {
        s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/lista.m3u") { x ->
            agenteVisto = x.requestHeaders.getFirst("User-Agent").orEmpty()
            val cuerpo = recurso("lista.m3u")
            if (x.requestHeaders.getFirst("Accept-Encoding").orEmpty().contains("gzip")) {
                val z = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(cuerpo) } }.toByteArray()
                x.responseHeaders.add("Content-Encoding", "gzip"); x.sendResponseHeaders(200, z.size.toLong()); x.responseBody.use { it.write(z) }
            } else { x.sendResponseHeaders(200, cuerpo.size.toLong()); x.responseBody.use { it.write(cuerpo) } }
        }
        s.createContext("/salta") { x -> x.responseHeaders.add("Location", "/lista.m3u"); x.sendResponseHeaders(302, -1); x.close() }
        s.createContext("/prohibido") { x -> x.sendResponseHeaders(403, -1); x.close() }
        s.start()
    }
    @After fun para() = s.stop(0)

    @Test fun redireccionesYGzip() {
        val r = RedJava("Cobre/0.1.0").abre("$base/salta?token=x", emptyMap())
        assertTrue(r.ok)
        val (_, canales) = r.use { M3u.lee(it.cuerpo) {} }
        assertEquals(11, canales)
        assertEquals("Cobre/0.1.0", agenteVisto)
        // El user-agent de la lista manda
        RedJava("Cobre").abre("$base/lista.m3u", mapOf("User-Agent" to "Otro/2")).close()
        assertEquals("Otro/2", agenteVisto)
        RedJava().abre("$base/prohibido", emptyMap()).use { assertEquals(403, it.codigo) }
    }

    @Test fun losErroresNoLlevanLaDireccion() {
        for (u in listOf("http://127.0.0.1:1/live/pepe/s3cr3t0/1.ts", "http://no-existe.invalid/get.php?username=pepe&password=s3cr3t0", "esto no es una dirección s3cr3t0")) {
            val e = runCatching { RedJava(conectar = 2_000).abre(u, emptyMap()) }.exceptionOrNull()
            assertTrue(e is IptvError)
            assertFalse(e!!.message, e.message!!.contains("s3cr3t0") || e.message!!.contains("pepe"))
        }
    }
}
