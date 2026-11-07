package com.gonzalo.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

/** Lo de cada lista en el dispositivo: bajar sus canales, su videoclub y su guía a trozos, guardarlos y leerlos; y que un fallo no lo estropee. */
class AlmacenTest {
    @get:Rule val tmp = TemporaryFolder()
    private var ahora = java.time.LocalDateTime.of(2026, 11, 7, 14, 20).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L

    /** Un cifrado de mentira (en Android es AES): para comprobar que lo que lleva secretos no queda en claro en el disco. */
    object Volteo : Envoltorio {
        override fun escribe(out: OutputStream): OutputStream = object : FilterOutputStream(out) { override fun write(b: Int) = out.write(b xor 0x5A) }
        override fun lee(inp: InputStream): InputStream = object : FilterInputStream(inp) {
            override fun read(): Int { val b = super.read(); return if (b < 0) b else b xor 0x5A }
            override fun read(b: ByteArray, off: Int, len: Int): Int { val n = super.read(b, off, len); for (i in off until off + maxOf(n, 0)) b[i] = (b[i].toInt() xor 0x5A).toByte(); return n }
        }
    }

    private fun gz(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    private val m3u = Lista("lm3u000001", "Lista M3U", TipoLista.M3U, "http://lista.ejemplo.test/get.m3u?token=t0k3nSecreto")
    private val redM3u = RedDeMentira { u ->
        when {
            u.startsWith("http://lista.ejemplo.test/get.m3u") -> recurso("lista.m3u")
            u == "http://epg.ejemplo.test/guia.xml.gz" -> gz(recurso("guia.xml"))
            u == "http://epg2.ejemplo.test/otra.xml" -> 500
            else -> null
        }
    }

    @Test fun losCanalesDeUnaListaM3u() {
        val a = Almacen(tmp.root, redM3u, Volteo) { ahora }
        assertEquals(11, a.actualizaCanales(m3u))
        val (grupos, canales) = a.canales(m3u)
        assertTrue(grupos.isEmpty())
        assertEquals(M3u.leeTexto(recursoTexto("lista.m3u")).second, canales)
        assertEquals(listOf("http://epg.ejemplo.test/guia.xml.gz", "http://epg2.ejemplo.test/otra.xml"), a.guiasDeLaLista(m3u))
        assertEquals(ahora, a.estado(m3u, "canales").cuando)
        // En el disco, nada en claro (las direcciones llevan la contraseña)
        val todo = tmp.root.walkTopDown().filter { it.isFile }.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse(todo.contains("s3cr3t0")); assertFalse(todo.contains("tv.ejemplo.test"))
    }

    @Test fun unFalloNoBorraLoDeAntesYNoDiceSecretos() {
        val a = Almacen(tmp.root, redM3u) { ahora }
        a.actualizaCanales(m3u)
        val rota = Almacen(tmp.root, RedDeMentira { 403 }) { ahora }
        val e = runCatching { rota.actualizaCanales(m3u) }.exceptionOrNull() as IptvError
        assertTrue(e.message!!.contains("403")); assertFalse(e.message!!.contains("t0k3nSecreto"))
        assertEquals("se quedan los de antes", 11, rota.canales(m3u).second.size)
        assertTrue(rota.estado(m3u, "canales").error.contains("403"))
        // Una lista que no es una lista
        val nada = Almacen(tmp.root, RedDeMentira { "<html>no</html>" }) { ahora }
        assertTrue(runCatching { nada.actualizaCanales(m3u) }.exceptionOrNull()!!.message!!.contains("ningún canal"))
        assertEquals(11, nada.canales(m3u).second.size)
        // Un error del servidor que lleve la dirección dentro: se tapa
        val conUrl = Almacen(tmp.root, RedDeMentira { throw IptvError("algo pasó en http://lista.ejemplo.test/get.m3u?token=t0k3nSecreto") }) { ahora }
        assertFalse(runCatching { conUrl.actualizaCanales(m3u) }.exceptionOrNull()!!.message!!.contains("t0k3nSecreto"))
    }

    @Test fun unaListaDeArchivo() {
        val l = Lista("larchivo01", "En el móvil", TipoLista.ARCHIVO, dispositivo = "cobre-1")
        val a = Almacen(tmp.root, RedDeMentira { null }, Volteo) { ahora }
        assertTrue(runCatching { a.actualizaCanales(l) }.exceptionOrNull()!!.message!!.contains("otro dispositivo"))
        a.guardaArchivo(l, recurso("lista.m3u").inputStream())
        assertEquals(11, a.actualizaCanales(l))
        assertFalse(String(File(File(tmp.root, l.id), "lista.m3u").readBytes(), Charsets.ISO_8859_1).contains("s3cr3t0"))
    }

    @Test fun xtreamConSuVideoclub() {
        val a = Almacen(tmp.root, servidorXtream()) { ahora }
        val l = LISTA_XTREAM
        assertEquals(4, a.actualizaCanales(l))
        val (grupos, canales) = a.canales(l)
        assertEquals("los grupos en el orden del servidor", listOf("ES | Nacionales", "ES | Deportes", "Radio"), grupos)
        assertEquals("x101", canales[0].clave)
        assertEquals(3, a.actualizaPeliculas(l)); assertEquals(2, a.actualizaSeries(l))
        assertEquals(listOf("Estrenos", "Clásicos"), a.categorias(l, "peliculas").map { it.nombre })
        assertEquals(listOf(5002L, 5003L), a.peliculas(l, "11").map { it.id })
        assertEquals(1, a.peliculas(l, tope = 1).size)
        assertEquals(listOf(5001L), a.buscaPeliculas(l, "dune").map { it.id })
        assertEquals("sin acentos", listOf(3001L), a.buscaSeries(l, "JUEGO DE TRONOS").map { it.id })
        assertEquals("Cadena perpetua", a.pelicula(l, 5002)!!.nombre); assertEquals("The Bear", a.serie(l, 3002)!!.nombre)
        // Las fichas se guardan: la segunda vez no se piden
        val red = servidorXtream(); val b = Almacen(tmp.root, red) { ahora }
        assertEquals(9960L, b.fichaPelicula(l, a.pelicula(l, 5001)!!).duracionSeg)
        val antes = red.pedidas.size
        b.fichaPelicula(l, a.pelicula(l, 5001)!!)
        assertEquals(antes, red.pedidas.size)
        assertEquals(3, b.fichaSerie(l, a.serie(l, 3001)!!).temporadas.size)
        // En el videoclub no hay ni direcciones ni contraseñas
        val todo = File(tmp.root, l.id).walkTopDown().filter { it.isFile && !it.name.startsWith("ficha") }.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse(todo.contains("s3cr3t0"))
    }

    @Test fun laGuiaDeUnaListaM3uConVariasFuentes() {
        val a = Almacen(tmp.root, redM3u) { ahora }
        a.actualizaCanales(m3u)
        assertTrue(a.guiaCaducada(m3u))
        val l = m3u.copy(epg = "http://mi.epg.test/propia.xml")
        // De tres fuentes, una da error y otra no existe: vale con la que responde
        val n = a.actualizaGuia(l)
        assertTrue(n > 0)
        val g = a.guia(l)!!
        // Solo los canales que tienes (BBC One no está en tu lista)
        assertFalse(g.tiene("Ajeno.uk"))
        assertTrue(g.tiene("La1.es")); assertTrue("Antena 3 por su tvg-id", g.tiene("Antena3.es"))
        assertTrue("Canal Sur, sin tvg-id: por su nombre contra el id de la guía (CanalSur.es)", g.tiene("CanalSur.es"))
        assertTrue(a.estado(l, "guia").error.isNotBlank())
        // Si fallan todas: error, y se queda la de antes
        val rota = Almacen(tmp.root, RedDeMentira { u -> if (u.contains("lista.ejemplo")) recurso("lista.m3u") else 500 }) { ahora }
        assertTrue(runCatching { rota.actualizaGuia(l) }.isFailure)
        assertNotNull(rota.guia(l)); assertTrue(rota.guia(l)!!.tiene("La1.es"))
    }

    @Test fun laGuiaDeXtreamYCuandoCaduca() {
        val a = Almacen(tmp.root, servidorXtream()) { ahora }
        val l = LISTA_XTREAM
        a.actualizaCanales(l)
        assertEquals(listOf(Xtream(l, servidorXtream()).urlGuia()), a.fuentesGuia(l))
        assertTrue(a.actualizaGuia(l) > 0)
        val g = a.guia(l)!!
        assertEquals("Telediario 1", g.ahoraYDespues("La1.es", ahora).first!!.titulo)
        // La guía de la prueba acaba hoy: aunque se acaba de bajar, ya toca otra (se acaba en menos de un día)
        assertTrue(a.guiaCaducada(l))
        // Una lista sin guía lo dice
        val sin = Lista("lsinguia01", "Sin guía", TipoLista.M3U, "http://otra.test/l.m3u")
        val b = Almacen(tmp.root, RedDeMentira { "#EXTM3U\n#EXTINF:-1,Uno\nhttp://x/1\n" }) { ahora }
        b.actualizaCanales(sin)
        assertEquals(0, b.actualizaGuia(sin)); assertTrue(b.estado(sin, "guia").error.contains("XMLTV"))
        a.olvida(l); assertFalse(File(tmp.root, l.id).exists())
    }
}
