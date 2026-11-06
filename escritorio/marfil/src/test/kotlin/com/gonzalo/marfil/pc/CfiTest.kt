package com.gonzalo.marfil.pc

import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoLista
import com.gonzalo.familia.nucleo.comoLong
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Las posiciones del ordenador son las del móvil, letra a letra: con el caso `herramientas/casos/marfil-cfi.json` (un EPUB con capítulos
 * que no se leen seguido, ids, comentarios, cursivas, espacios duros, `<br>`, un itemref con id…) y lo que calcula de verdad el lector
 * del móvil en Chromium (foliate-js, `herramientas/marfil/lector-prueba/cfi.js`):
 *  - el subrayado de cada frase hecho aquí es el mismo CFI que haría el móvil (el mismo texto, carácter a carácter);
 *  - el subrayado del móvil se pinta aquí sobre las mismas letras;
 *  - la primera página de cada capítulo en el móvil abre aquí en su primer bloque;
 *  - y lo que escribe el ordenador como «por dónde vas», foliate lo lee en el mismo sitio.
 */
class CfiTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun raiz(): File { var d: File? = File("").absoluteFile; while (d != null && !File(d, "herramientas/casos/marfil-cfi.json").exists()) d = d.parentFile; return d ?: error("no encuentro herramientas/casos") }
    private val caso: Map<String, Any?> by lazy { Json.lee(File(raiz(), "herramientas/casos/marfil-cfi.json").readText()).comoMapa() }

    private fun epub(): Epub {
        val f = File(tmp.root, "caso.epub")
        ZipOutputStream(f.outputStream()).use { z -> caso["archivos"].comoMapa().forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.comoTexto().toByteArray()); z.closeEntry() } }
        return Epub(f)
    }

    private fun normal(s: String) = s.replace(Regex("\\s+"), " ").trim()

    @Test fun elSubrayadoDeAquiEsElMismoQueElDelMovil() {
        epub().use { e ->
            val frases = caso["foliate"].comoMapa()["frases"].comoLista().map { it.comoMapa() }
            assertTrue(frases.size >= 10)
            for (f in frases) {
                val s = f["seccion"].comoLong().toInt(); val texto = f["texto"].comoTexto(); val cfi = f["cfi"].comoTexto()
                val bs = e.bloques(s)
                val b = bs.firstOrNull { texto in it.texto.text } ?: error("«$texto» no está en la sección $s")
                val i = b.texto.text.indexOf(texto)
                assertEquals("«$texto»", cfi, e.cfiTramo(b, i, b, i + texto.length))
            }
        }
    }

    @Test fun elSubrayadoDelMovilSePintaAquiSobreLasMismasLetras() {
        epub().use { e ->
            for (f in caso["foliate"].comoMapa()["frases"].comoLista().map { it.comoMapa() }) {
                val texto = f["texto"].comoTexto()
                val l = e.ubica(f["cfi"].comoTexto()) ?: error("no se ubica ${f["cfi"]}")
                assertEquals(f["seccion"].comoLong().toInt(), l.seccion)
                val (b1, l1) = l.desde!!; val (b2, l2) = l.hasta!!
                assertEquals("«$texto» en un solo bloque", b1, b2)
                assertEquals(texto, e.bloques(l.seccion)[b1].texto.text.substring(l1, l2))
            }
        }
    }

    @Test fun laPrimeraPaginaDeCadaCapituloEnElMovilAbreAquiEnSuPrincipio() {
        epub().use { e ->
            caso["foliate"].comoMapa()["inicios"].comoMapa().forEach { (s, v) ->
                val l = e.ubica(v.comoMapa()["cfi"].comoTexto())!!
                assertEquals(s.toInt(), l.seccion)
                assertEquals("capítulo $s", 0 to 0, l.desde)
            }
        }
    }

    @Test fun porDondeVasEnElOrdenadorLoLeeElMovilEnElMismoSitio() {
        epub().use { e ->
            // Lo que escribe el ordenador al principio de cada bloque (con su letra 0) y, en el caso, lo que foliate lee ahí
            val aqui = e.secciones.indices.flatMap { s -> e.bloques(s).map { e.cfiDe(it, 0) } }
            val esperados = caso["escritorio"].comoLista().map { it.comoTexto() }
            System.getProperty("casos.escribe")?.let { File(it, "marfil-cfi-escritorio.json").writeText(Json.escribe(aqui)) }
            assertEquals("si cambian, hay que volver a pasar cfi.js (ver la nota del caso)", esperados, aqui)
            val leido = caso["foliate"].comoMapa()["escritorio"].comoLista().map { it.comoMapa() }
            assertEquals(esperados.size, leido.size)
            leido.forEach { r ->
                val cfi = r["cfi"].comoTexto()
                val l = e.ubica(cfi)!!
                val (b, letra) = l.desde!!
                assertEquals(cfi, r["indice"].comoLong().toInt(), l.seccion)
                // (un separador o una imagen no tienen texto: foliate lee lo que va detrás)
                if (e.bloques(l.seccion)[b].piezas.isEmpty()) return@forEach
                val desde = normal(e.bloques(l.seccion)[b].texto.text.substring(letra))
                val foliate = r["texto"].comoTexto()
                // (sin los blancos: cfi.js junta los textos sin el salto de un <br>)
                val x = desde.replace(Regex("\\s"), ""); val y = foliate.replace(Regex("\\s"), "")
                assertTrue("$cfi: aquí «$desde», en el móvil «$foliate»", x.startsWith(y) || y.startsWith(x))
            }
            // Una posición a mitad de un párrafo, también
            val b = e.bloques(1).first { it.texto.text.startsWith("La vieja") }
            assertEquals("epubcfi(/6/4!/4/6/4[p-vieja]/1:10)", e.cfiDe(b, b.texto.text.indexOf("miró")))
        }
    }

    @Test fun leerYEscribirCfiComoFoliate() {
        val c = EpubCfi.lee("epubcfi(/6/10[ref-c3]!/4/6,/1:9,/1:20)")!!
        assertTrue(c.esRango)
        assertEquals("epubcfi(/6/10[ref-c3]!/4/6,/1:9,/1:20)", c.toString())
        assertEquals(10, EpubCfi.pasoSeccion(c))
        assertEquals("epubcfi(/6/4!/4/2[a^,b])", EpubCfi.Leido(listOf(listOf(EpubCfi.Paso(6), EpubCfi.Paso(4)), listOf(EpubCfi.Paso(4), EpubCfi.Paso(2, "a,b")))).toString())
        assertEquals("a,b", EpubCfi.lee("epubcfi(/6/4!/4/2[a^,b])")!!.padre[1][1].id)
        // En el orden del libro
        val a = EpubCfi.lee("epubcfi(/6/4!/4/4,/1:3,/1:18)")!!; val b = EpubCfi.lee("epubcfi(/6/4!/4/6/2,/1:4,/1:16)")!!; val d = EpubCfi.lee("epubcfi(/6/6!/4/2)")!!
        assertEquals(listOf(a, b, d), listOf(d, b, a).sortedWith { x, y -> EpubCfi.compara(x, y) })
        assertNull(EpubCfi.lee(""))
    }
}
