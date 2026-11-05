package com.gonzalo.marfil.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Marfil del ordenador: leer un EPUB, las posiciones como las del móvil (EPUB CFI de foliate) y lo que se apunta en la cuenta. */
class MarfilTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Un EPUB de prueba: dos capítulos, índice EPUB 3, portada. */
    private fun epub(): File {
        val f = File(tmp.root, "dune.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            pon("mimetype", "application/epub+zip")
            pon("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            pon("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Dune</dc:title><dc:creator>Frank Herbert</dc:creator></metadata>
<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="texto/c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="texto/c2.xhtml" media-type="application/xhtml+xml"/></manifest>
<spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            pon("OEBPS/nav.xhtml", """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Índice</title></head><body><nav epub:type="toc"><ol><li><a href="texto/c1.xhtml">Arrakis</a></li><li><a href="texto/c2.xhtml#sueno">El sueño</a></li></ol></nav></body></html>""")
            pon("OEBPS/texto/c1.xhtml", """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>1</title></head><body><h1>Arrakis</h1>
<p>Al principio es el <em>tiempo</em> de dar precisión a las cosas.</p>
<div><p>Una semana antes de partir hacia Arrakis…</p><p>La vieja vino a ver a la madre del muchacho.</p></div></body></html>""")
            pon("OEBPS/texto/c2.xhtml", """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>2</title></head><body><section><h2 id="sueno">El sueño</h2>
<p>Soñó con <strong>agua</strong>.</p><hr/><blockquote><p>El miedo mata la mente.</p></blockquote></section></body></html>""")
        }
        return f
    }

    @Test fun unEpubSeLeeConSusCapitulosYSuIndice() {
        Epub(epub()).use { e ->
            assertEquals("Dune", e.titulo); assertEquals("Frank Herbert", e.autor)
            assertEquals(listOf("OEBPS/texto/c1.xhtml", "OEBPS/texto/c2.xhtml"), e.secciones.map { it.ruta })
            assertEquals(6, e.pasoSpine)
            assertEquals(listOf(2, 4), e.secciones.map { it.pasoCfi })
            assertEquals(listOf("Arrakis", "El sueño"), e.indice.map { it.titulo })
            val b1 = e.bloques(0)
            assertEquals(listOf(Bloque.Tipo.TITULO1, Bloque.Tipo.PARRAFO, Bloque.Tipo.PARRAFO, Bloque.Tipo.PARRAFO), b1.map { it.tipo })
            assertEquals("Al principio es el tiempo de dar precisión a las cosas.", b1[1].texto.text)
            // Los sitios, como los cuenta foliate: <body> es /4 (después de <head>), el <div> el 3.er elemento (/6) y su 2.º párrafo /4
            assertEquals(listOf(4, 2), b1[0].camino)
            assertEquals(listOf(4, 6, 4), b1[3].camino)
            val b2 = e.bloques(1)
            assertEquals(listOf(Bloque.Tipo.TITULO2, Bloque.Tipo.PARRAFO, Bloque.Tipo.SEPARADOR, Bloque.Tipo.CITA), b2.map { it.tipo })
            assertTrue("sueno" in b2[0].ids)
        }
    }

    @Test fun laPosicionDelMovilAbreAquiEnElMismoSitioYLaDeAquiLaEntiendeElMovil() {
        Epub(epub()).use { e ->
            val todos = e.secciones.indices.flatMap { e.bloques(it) }
            // Una posición de foliate en el móvil: 2.º capítulo, su <section>, el párrafo, el texto en la letra 5
            val movil = Cfi.lee("epubcfi(/6/4[c2]!/4/2/4/1:5)")!!
            assertEquals(1 to 1, movil.bloque(e))
            // Un rango (subrayado) también vale: su principio
            assertEquals(0 to 3, Cfi.lee("epubcfi(/6/2[c1]!/4/6,/4/1:0,/4/1:12)")!!.bloque(e))
            // La de aquí: el bloque con su camino, y la vuelve a encontrar igual
            val b = todos.first { it.texto.text.startsWith("La vieja") }
            val cfi = Cfi.de(e, b)
            assertEquals("epubcfi(/6/2[c1]!/4/6/4/1:0)", cfi)
            assertEquals(0 to 3, Cfi.lee(cfi)!!.bloque(e))
            // El porcentaje crece con el libro
            val fr = todos.indices.map { fraccionDe(e, todos, it) }
            assertEquals(fr.sorted(), fr)
            assertTrue(fr.last() < 1.0 && fr.first() == 0.0)
            assertEquals(todos.indexOf(b), indicePorFraccion(e, todos, fraccionDe(e, todos, todos.indexOf(b))))
            assertEquals("Arrakis", etiqueta(e, todos, 2)); assertEquals("El sueño", etiqueta(e, todos, todos.size - 1))
        }
        assertEquals(3, PosicionPaginas.pagina(PosicionPaginas.cfi(3)))
    }

    private lateinit var otra: Familia
    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Libros.dirPruebas = null }

    private fun cuenta() {
        Emulador.limpia(); Emulador.cuenta("gonzalo")
        otra = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp.root; LaFamilia.llaveroPruebas = LlaveroMemoria()
        LaFamilia.inicia("marfil").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Libros.dirPruebas = File(tmp.root, "marfil"); Libros.dispositivo = { "marfil-pc1" }
        listOf("libros", "lectura", "tiempo", "ajustes").forEach { LaFamilia.coleccion(it); otra.coleccion(it) }
    }
    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(otra.sincroniza().ok) }

    @Test fun unLibroQueAnadesAquiEsElMismoQueEnElMovilYLoLeidoLlega() {
        cuenta()
        val id = anadeArchivo(epub())
        // El mismo nodo que crearía Marfil del móvil (por su clave)
        assertEquals(Libros.kid("libro:dune:frank herbert"), id)
        sincroniza()
        val n = otra.coleccion("libros").nodo(id)!!
        assertEquals("Dune", n.texto("t")); assertEquals("libro:dune:frank herbert", n.texto("clave")); assertEquals("pendiente", n.texto("estado"))
        assertTrue(Libros.archivo(id)!!.isFile)
        // Lees un poco aquí: el móvil abre por ahí, y el libro pasa a «Leyendo»
        Libros.leido(id, "epubcfi(/6/2[c1]!/4/6/4/1:0)", 0.31, "Arrakis")
        Libros.sumaTiempo(id, 90_000)
        sincroniza()
        val lec = otra.coleccion("lectura").nodo(id)!!
        assertEquals("epubcfi(/6/2[c1]!/4/6/4/1:0)", lec.texto("cfi")); assertEquals(0.31, lec.decimal("fraction"), 1e-9); assertEquals("Arrakis", lec.texto("label"))
        assertEquals(0.31, lec.decimal("bestFrac"), 1e-9)
        assertEquals("en_curso", otra.coleccion("libros").nodo(id)!!.texto("estado"))
        assertEquals(0.31, otra.coleccion("libros").nodo(id)!!.decimal("fr"), 1e-9)
        val t = otra.coleccion("tiempo").nodo("$id~marfil-pc1")!!
        assertEquals(90_000L, t.entero("total")); assertEquals(id, t.texto("ref")); assertEquals(90_000L, t.entero("d" + java.time.LocalDate.now()))
        // Llegas al final: «Leído», con la fecha
        Libros.leido(id, "epubcfi(/6/4[c2]!/4/2/8/1:0)", 0.99, "El sueño")
        sincroniza()
        val l = otra.coleccion("libros").nodo(id)!!
        assertEquals("terminado", l.texto("estado")); assertEquals(1, l.lista("fin").size)
        // Y lo que lees en el móvil se ve aquí (su posición)
        otra.coleccion("lectura").cambia(id, mapOf("cfi" to "epubcfi(/6/4[c2]!/4/2/4/1:5)", "fraction" to 0.8, "label" to "El sueño", "read" to System.currentTimeMillis()))
        assertTrue(otra.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok)
        assertEquals("epubcfi(/6/4[c2]!/4/2/4/1:5)", Libros.posicion(id)!!.cfi)
    }
}
