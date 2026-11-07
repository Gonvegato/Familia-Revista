package com.gonzalo.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream

/** Las guías XMLTV (`resources/guia.xml`, con la forma de `xmltv.dtd`): las horas con su zona, el texto, y las guías enormes a trozos. */
class XmltvTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun utc(a: Int, m: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(a, m, d, h, mi).toEpochSecond(ZoneOffset.UTC) * 1000L

    private fun lee(bytes: ByteArray, op: Xmltv.Opciones = Xmltv.Opciones()): Pair<List<CanalGuia>, List<Programa>> {
        val c = ArrayList<CanalGuia>(); val p = ArrayList<Programa>()
        Xmltv.lee(ByteArrayInputStream(bytes), op, { c += it }, { p += it })
        return c to p
    }

    @Test fun lasHorasConSuZona() {
        val catorce = utc(2026, 11, 7, 14)
        assertEquals(catorce, Xmltv.hora("20261107150000 +0100"))
        assertEquals(catorce, Xmltv.hora("20261107080000 -0600"))
        assertEquals("sin zona: UTC (lo dice el DTD)", catorce, Xmltv.hora("20261107140000"))
        assertEquals(catorce, Xmltv.hora("202611071500 BST"))
        assertEquals(catorce, Xmltv.hora("20261107140000 UTC"))
        assertEquals(catorce, Xmltv.hora("20261107153000 +0130"))
        assertEquals(catorce, Xmltv.hora("20261107193000 +05:30"))
        assertEquals("solo el día", utc(2026, 11, 7, 0), Xmltv.hora("20261107"))
        assertEquals(0L, Xmltv.hora("")); assertEquals(0L, Xmltv.hora("mañana"))
    }

    @Test fun losCanalesYLosProgramas() {
        val (c, p) = lee(recurso("guia.xml"))
        assertEquals(listOf("La1.es", "La2.es", "Antena3.es", "CanalSur.es", "Ajeno.uk"), c.map { it.id })
        assertEquals(listOf("La 1", "TVE La 1 HD"), c[0].nombres); assertEquals("http://logos.ejemplo.test/la1.png", c[0].icono)
        assertEquals(8, p.size)
        val td = p.first { it.titulo == "Telediario 1" }
        assertEquals(utc(2026, 11, 7, 14), td.inicio); assertEquals(utc(2026, 11, 7, 14, 50), td.fin)
        assertEquals("Edición de mediodía", td.subtitulo)
        assertEquals("las entidades", "Las noticias del día & el tiempo, con \"Ana Blanco\" — en directo.", td.descripcion)
        assertEquals("Noticias", td.categoria); assertEquals("http://img.ejemplo.test/td.jpg", td.icono); assertEquals("T1 E120", td.episodio)
        assertEquals("el CDATA, tal cual", "Saber y ganar: <el concurso>", p.first { it.inicio == utc(2026, 11, 7, 14, 50) && it.canal == "La1.es" }.titulo)
        assertEquals("Documentales árabes", p.first { it.canal == "La2.es" }.titulo)
        // Todas a la misma hora de verdad aunque cada una venga en su zona
        assertTrue(listOf("La2.es", "Antena3.es", "CanalSur.es").all { id -> p.first { it.canal == id }.inicio == utc(2026, 11, 7, 14) })
    }

    @Test fun soloLoQueSeQuiereYEnLaVentana() {
        val op = Xmltv.Opciones(desde = utc(2026, 11, 7, 14, 30), hasta = utc(2026, 11, 7, 23), quiero = { it != "Ajeno.uk" })
        val (_, p) = lee(recurso("guia.xml"), op)
        assertFalse(p.any { it.canal == "Ajeno.uk" })
        // El que acabó antes de la ventana, fuera; el que se está emitiendo al empezar la ventana, dentro
        assertFalse(p.any { it.fin <= utc(2026, 11, 7, 14, 30) })
        assertTrue(p.any { it.titulo == "Telediario 1" })
    }

    @Test fun comprimidaYEnOtraCodificacion() {
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(recurso("guia.xml")) } }.toByteArray()
        assertEquals("comprimida con gzip, se nota sola", 8, lee(gz).second.size)
        val latin = """<?xml version="1.0" encoding="ISO-8859-1"?><tv><programme start="20261107140000 +0000" stop="20261107150000 +0000" channel="x"><title>Pequeña España</title></programme></tv>"""
            .toByteArray(Charsets.ISO_8859_1)
        assertEquals("Pequeña España", lee(latin).second.single().titulo)
    }

    @Test fun laGuiaEnElDispositivo() {
        val (c, p) = lee(recurso("guia.xml"))
        val f = File(tmp.root, "guia.bin")
        Guia.Escritor(f).use { e -> c.forEach(e::canal); p.forEach(e::programa); e.cierra(123L) }
        assertFalse("no queda nada de paso", File(tmp.root, "guia.bin.paso").exists())
        val g = Guia.abre(f)!!
        assertEquals(123L, g.hecha); assertEquals(5, g.canales.size)
        val la1 = g.programas("La1.es")
        assertEquals("en orden y sin el repetido (de dos a la misma hora, el primero)", listOf("Telediario 1", "Saber y ganar: <el concurso>"), la1.map { it.titulo }.drop(1))
        assertEquals("El tiempo", la1.first().titulo)
        val (ahora, luego) = g.ahoraYDespues("La1.es", utc(2026, 11, 7, 14, 20))
        assertEquals("Telediario 1", ahora!!.titulo); assertEquals("Saber y ganar: <el concurso>", luego!!.titulo)
        assertEquals(0.4f, ahora.avance(utc(2026, 11, 7, 14, 20)), 0.01f)
        val (nada, primero) = g.ahoraYDespues("La1.es", utc(2026, 11, 7, 10))
        assertNull(nada); assertEquals("El tiempo", primero!!.titulo)
        assertTrue(g.programas("NoExiste").isEmpty())
        assertEquals(2, g.entre("La1.es", utc(2026, 11, 7, 14, 40), utc(2026, 11, 7, 15)).size)
        assertEquals(utc(2026, 11, 7, 16), g.hastaCuando)
        // Una guía nueva en el mismo sitio: la abierta deja de valer (no lee lo que no es)
        Thread.sleep(1100)
        Guia.Escritor(f).use { e -> e.programa(Programa("La1.es", 1, 2, "x")); e.cierra() }
        assertFalse(g.vigente)
        assertNotNull(Guia.abre(f))
        assertNull(Guia.abre(File(tmp.root, "no.bin")))
    }

    @Test fun unaGuiaEnormeSinTenerlaEnMemoria() {
        // 2 000 canales × 7 días × 48 programas = 672 000 programas: más de 200 MB de XML (que no existen: se inventan según se leen).
        // Con 256 MB de memoria para todo, se guardan solo los 300 canales que tienes y 4 días.
        val canales = 2_000; val dias = 7; val porDia = 48
        val inicio = utc(2026, 11, 7, 0)
        fun hora(ms: Long) = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.Instant.ofEpochMilli(ms).atOffset(ZoneOffset.ofHours(1))) + " +0100"
        val xml = sequenceOf("""<?xml version="1.0" encoding="UTF-8"?>""", "<tv>") +
            (1..canales).asSequence().map { """<channel id="c$it.es"><display-name>Canal $it</display-name></channel>""" } +
            (1..canales).asSequence().flatMap { c -> (0 until dias * porDia).asSequence().map { k ->
                val a = inicio + k * 30 * 60_000L
                """<programme start="${hora(a)}" stop="${hora(a + 30 * 60_000L)}" channel="c$c.es"><title lang="es">Programa $k del canal $c</title><desc lang="es">Una descripción de las de verdad, con su texto y sus cosas para que la guía pese lo que pesa una guía de verdad de un proveedor grande.</desc><category>Varios</category></programme>"""
            } } + sequenceOf("</tv>")
        val crudo = BytesQueSeInventan(xml)
        val tuyos = (1..300).map { Canal("x$it", "Canal $it", tvgId = "c$it.es") }
        val f = File(tmp.root, "enorme.bin")
        val t0 = System.nanoTime()
        val em = ArrayList<CanalGuia>()
        var quiero: Set<String>? = null
        val e = Guia.Escritor(f)
        val n = Xmltv.lee(crudo, Xmltv.Opciones(inicio, inicio + 4 * 86_400_000L, quiero = { id ->
            (quiero ?: Emparejador(em).let { x -> tuyos.mapNotNull { x.busca(it) }.toSet() }.also { quiero = it }).contains(id)
        }), { em += it; e.canal(it) }, { e.programa(it) })
        e.cierra()
        val s = (System.nanoTime() - t0) / 1_000_000_000.0
        assertTrue("más de 200 MB de guía: ${crudo.leidos}", crudo.leidos > 200_000_000)
        assertEquals("solo tus 300 canales, 4 días", 300 * 4 * porDia, n)
        val g = Guia.abre(f)!!
        assertEquals(4 * porDia, g.programas("c300.es").size)
        assertTrue(g.programas("c301.es").isEmpty())
        assertTrue("en un tiempo razonable: $s s", s < 120)
    }
}
