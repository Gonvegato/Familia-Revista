package com.gonzalo.marfil

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Los subrayados del Kobo por cable (SesKobo): con la base de datos de verdad de un Clara BW 4.46 (la de NickelInkSync, anonimizada:
 * herramientas/casos/kosync), se leen todos y se guardan en la cuenta con lo que necesita el lector para ponerlos en su sitio (lo prueba
 * herramientas/marfil/lector-prueba/subrayados-kobo.js con los mismos subrayados).
 */
@RunWith(RobolectricTestRunner::class)
class SubrayadosKoboTest {
    private fun base(): File {
        val sql = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "herramientas/casos/kosync/kobo-clarabw-4.46-probe-books.sql") }.first { it.isFile }
        val f = File.createTempFile("kobo", ".sqlite"); f.delete()
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            for (s in sql.readText().split(Regex(";\\s*\\n"))) { val q = s.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").trim(); if (q.isNotEmpty()) db.execSQL(q) }
        }
        return f
    }

    @Test fun seLeenYSeGuardan() {
        val f = base()
        val libros = KoboCable.leeBase(f)
        val subs = KoboCable.leeSubrayados(f)
        assertEquals(32, subs.size)
        assertTrue(subs.all { it.texto.isNotBlank() && it.id.isNotBlank() })
        val p = Kobo.planeaSubrayados(subs, libros, emptyMap(), yaEstan = { false })
        assertEquals("todos tienen su libro", 32, p.size)
        assertEquals(32, p.map { it.ref }.toSet().size)
        for (c in p) {
            assertEquals("marcas", c.coleccion); assertEquals("notes", c.campos["tipo"])
            @Suppress("UNCHECKED_CAST") val n = c.campos["n"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST") val k = n["kobo"] as Map<String, Any?>
            assertTrue(n["value"].toString().startsWith("kobo:"))
            assertEquals("OEBPS/text/probe.xhtml", k["cap"])
            assertTrue(c.ref.startsWith(c.campos["ref"].toString() + "~"))
        }
        // En kepub, la frase y las letras; en EPUB, el punto de Adobe
        val kepub = subs.first { it.volumen.endsWith(".kepub.epub") }
        assertTrue(kepub.ini.startsWith("span#kobo") && kepub.ioff > 0)
        val epub = subs.first { !it.volumen.endsWith(".kepub.epub") }
        assertTrue(epub.ini.contains("#point("))
        // La segunda vez (o si ya se puso en su sitio o se quitó), no se vuelven a guardar
        val ya = p.map { it.ref }.toSet()
        assertEquals(0, Kobo.planeaSubrayados(subs, libros, emptyMap(), yaEstan = { it in ya }).size)
        f.delete()
    }

    @Test fun capitulo() {
        fun s(c: String, ini: String = "") = Kobo.Subrayado("1", "v", c, ini, 0, ini, 0, "t", "", 1, 1)
        assertEquals("OEBPS/text/probe.xhtml", Kobo.capituloDe(s("/mnt/onboard/Books/book-p.kepub.epub!OEBPS!text/probe.xhtml")))
        assertEquals("OEBPS/text/probe.xhtml", Kobo.capituloDe(s("file:///mnt/onboard/Books/book-p.epub#(1)OEBPS/text/probe.xhtml")))
        assertEquals("OPS/c.html", Kobo.capituloDe(s("raro", "OPS/c.html#point(/1/4/2:0)")))
    }
}
