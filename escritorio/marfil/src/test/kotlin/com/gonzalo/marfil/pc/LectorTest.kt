package com.gonzalo.marfil.pc

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** El lector del ordenador sin pantalla: notas al pie, buscar, lo que te queda, las marcas de capítulo, los ajustes y las páginas. */
class LectorTest {
    @get:Rule val tmp = TemporaryFolder()

    /** El EPUB del caso de las posiciones (`herramientas/casos/marfil-cfi.json`). */
    private fun caso(): Epub {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "herramientas/casos/marfil-cfi.json").exists()) d = d.parentFile
        val c = Json.lee(File(d!!, "herramientas/casos/marfil-cfi.json").readText()).comoMapa()
        val f = File(tmp.root, "caso.epub")
        ZipOutputStream(f.outputStream()).use { z -> c["archivos"].comoMapa().forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.comoTexto().toByteArray()); z.closeEntry() } }
        return Epub(f)
    }

    @Test fun lasNotasAlPieSeAbrenComoEnElMovil() {
        caso().use { e ->
            val bs = e.bloques(1)
            // La nota no sale en el texto (como en el móvil, que la esconde): se ve al tocar su llamada
            assertTrue(bs.none { it.texto.text.contains("La nota al pie del primer capítulo") })
            val b = bs.first { it.texto.text.startsWith("Texto con nota") }
            val i = b.texto.text.indexOf("1")
            val a = b.texto.getStringAnnotations(ETIQUETA_ENLACE, i, i + 1).single()
            val en = e.enlace(a.item, 1)!!
            assertTrue(en.nota); assertEquals(1, en.seccion); assertEquals("n1", en.ancla)
            val (nombre, textos) = e.textoDeNota(en)!!
            assertEquals("Nota al pie", nombre)
            assertEquals(listOf("La nota al pie del primer capítulo."), textos.map { it.text })
            // Un enlace del índice o de fuera no es una nota
            assertEquals("https://example.com/x", e.enlace("|https://example.com/x", 1)!!.externo)
            assertNull(e.textoDeNota(Epub.Enlace(2, "")))
        }
    }

    @Test fun buscarSinMirarTildesNiMayusculas() {
        caso().use { e ->
            val l = LibroAbierto(e)
            val r = l.busca("SUENO")
            assertEquals(1, r.size)
            assertEquals(Sitio(2, 0, 3), r[0].desde); assertEquals("sueño", r[0].frase); assertEquals("El sueño", r[0].capitulo)
            val m = l.busca("miedo mata")
            assertEquals(listOf("miedo mata"), m.map { it.frase })
            assertEquals(2, m[0].desde.seccion)
            assertTrue(l.busca("   ").isEmpty())
            assertEquals(3, l.busca("a", max = 3).size)
            // Un texto con letras compuestas (e + tilde aparte) se marca en su sitio
            val (p, mapa) = LibroAbierto.pliega("café solo")
            assertEquals("cafe solo", p); assertEquals(5, mapa[4])
        }
    }

    @Test fun loQueTeQuedaYElPorcentajeComoFoliate() {
        caso().use { e ->
            val l = LibroAbierto(e)
            // Lo que no se lee seguido no cuenta para el porcentaje (la portada y las notas del final)
            assertEquals(0L, e.tamanos[0]); assertEquals(0L, e.tamanos[3])
            assertEquals(0.0, l.fraccion(1, 0.0), 1e-9)
            assertEquals(1.0, l.fraccion(4, 1.0), 1e-9)
            val (s, f) = l.deFraccion(0.5)
            assertEquals(0.5, l.fraccion(s, f), 1e-9)
            // Lo que queda, a la velocidad de foliate (1600 bytes por minuto) o a la tuya
            val (cap, libro) = l.quedan(1, 0.5, 0)
            assertEquals(e.secciones[1].tamano * 0.5 / 1600, cap, 1e-9)
            assertEquals((e.total - e.tamanos[1] * 0.5) / 1600, libro, 1e-9)
            assertEquals(cap / 2, l.quedan(1, 0.5, 3200).first, 1e-9)
            assertEquals("menos de 1 min", LibroAbierto.duracion(0.4)); assertEquals("12 min", LibroAbierto.duracion(12.2)); assertEquals("2 h", LibroAbierto.duracion(119.7))
            assertEquals("1 h 5 min", LibroAbierto.duracion(65.0))
            // Las marcas de la barra: donde empieza cada capítulo del índice
            assertEquals(listOf("Arrakis", "El sueño", "El desierto"), l.marcasCapitulos.map { it.second })
            assertEquals(l.marcasCapitulos.map { it.first }.sorted(), l.marcasCapitulos.map { it.first })
            assertEquals("El sueño", l.etiqueta(Sitio(2, 3)))
        }
    }

    @Test fun elDiccionarioSacaLasAcepcionesComoElDelMovil() {
        val ex = "== Español ==\n=== Etimología 1 ===\nDel latín mare.\n==== Sustantivo ambiguo ====\n1 \nMasa de agua salada que cubre la mayor parte de la superficie terrestre.\n" +
            "2 Cada una de las partes en que se divide esa masa de agua.\nSinónimos: océano.\n== Inglés ==\n1 Something else entirely, in English."
        assertEquals(listOf("Masa de agua salada que cubre la mayor parte de la superficie terrestre.", "Cada una de las partes en que se divide esa masa de agua."), Diccionario.parse(ex))
        assertEquals(listOf("Something else entirely, in English."), Diccionario.parse(ex, "Inglés"))
        assertEquals("falda", Diccionario.lemmaDe("Forma del plural de falda."))
        assertTrue("acordar" in Diccionario.candidates("acordarme")); assertTrue("casa" in Diccionario.candidates("casas"))
        assertEquals("Inglés", Diccionario.lengua("en")); assertEquals("Español", Diccionario.lengua("es-ES")); assertEquals("Español", Diccionario.lengua(""))
    }

    @Test fun lasPaginasDeUnCapituloNoSeSaltanNiRepitenNada() {
        caso().use { e ->
            // Un capítulo largo: el texto del caso, muchas veces
            val bs = e.bloques(1)
            val muchos = (0 until 30).flatMap { bs }
            val d = Density(1f)
            val medidor = TextMeasurer(createFontFamilyResolver(), d, LayoutDirection.Ltr, 0)
            val a = AjustesLectura.mezcla(null, null)
            val pg = Paginador(medidor, { b, ant -> estiloDe(a, d, b, ant) }, { IntSize(600, 900) }).pagina(1, muchos, 620, 560)
            assertTrue("varias páginas", pg.total > 3)
            // Cada letra de cada bloque está en una página, en orden, y ningún trozo se sale de la página
            var anterior = -1 to -1
            for ((i, p) in pg.paginas.withIndex()) for (t in p.trozos) {
                assertTrue(t.y >= 0f && t.y + t.alto <= 560f + 0.5f)
                val aqui = t.bloque to t.letraDesde
                assertTrue("$aqui después de $anterior", aqui.first > anterior.first || (aqui.first == anterior.first && aqui.second > anterior.second))
                anterior = aqui
                assertEquals(i, pg.paginaDe(t.bloque, t.letraDesde))
            }
            for (k in muchos.indices) assertTrue("el bloque $k está", pg.paginas.any { p -> p.trozos.any { it.bloque == k } })
            // Un título nunca se queda solo al pie de una página
            for (p in pg.paginas) p.trozos.lastOrNull()?.let { t -> assertFalse(muchos[t.bloque].esTitulo && p != pg.paginas.last()) }
        }
    }
}
