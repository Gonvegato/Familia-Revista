package com.gonzalo.marfil.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Los subrayados, las notas, los marcadores y los ajustes del lector, de ida y vuelta por la cuenta (el emulador de Firebase con las reglas
 * de verdad): lo que hace el ordenador queda exactamente como lo escribe y lo lee el lector del móvil (`LecturaNube` y `MezclaLectura`), y
 * lo que hace el móvil (aquí [movil]: el núcleo escribiendo con el formato de Android) se ve aquí. Con `-Dcasos.escribe=<carpeta>` deja en
 * `marfil-marcas.json` los nodos tal cual, para la prueba del móvil (`MarcasEscritorioTest`), que los lee con el código de Android.
 */
class MarcasTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var movil: Familia
    private var uid = ""

    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Libros.dirPruebas = null }

    private fun cuenta() {
        Emulador.limpia(); uid = Emulador.cuenta("gonzalo")
        movil = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas(); LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg; LaFamilia.dirPruebas = tmp.root; LaFamilia.llaveroPruebas = LlaveroMemoria()
        LaFamilia.inicia("marfil").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Libros.dirPruebas = File(tmp.root, "marfil"); Libros.dispositivo = { "marfil-pc1" }
        listOf("libros", "lectura", "tiempo", "ajustes", "marcas").forEach { LaFamilia.coleccion(it); movil.coleccion(it) }
    }
    private fun sincroniza() { assertTrue(LaFamilia.sincronizaYa().ok); assertTrue(movil.sincroniza().ok); assertTrue(LaFamilia.sincronizaYa().ok) }

    private fun epub(): File {
        val f = File(tmp.root, "dune.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            pon("META-INF/container.xml", """<?xml version="1.0"?><container><rootfiles><rootfile full-path="c.opf"/></rootfiles></container>""")
            pon("c.opf", """<?xml version="1.0"?><package><metadata><title>Dune</title><creator>Frank Herbert</creator></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            pon("c1.xhtml", """<?xml version="1.0"?><html><head><title>1</title></head><body><h1>Arrakis</h1><p>El miedo mata la mente. El miedo es la pequeña muerte.</p></body></html>""")
            pon("c2.xhtml", """<?xml version="1.0"?><html><head><title>2</title></head><body><p>Quien controla la especia controla el universo.</p></body></html>""")
        }
        return f
    }

    /** El id de una marca como lo calcula el móvil (`LecturaNube.idMarca`: el SHA-1 de «sitio|creada»). */
    private fun idMovil(kid: String, valor: String, creada: Long) = kid + "~" + MessageDigest.getInstance("SHA-1").digest("$valor|$creada".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    @Test fun losSubrayadosYLasNotasVanYVienenComoLosDelMovil() {
        cuenta()
        val id = anadeArchivo(epub())
        Epub(Libros.archivo(id)!!).use { e ->
            val b = e.bloques(0)[1]
            val i = b.texto.text.indexOf("El miedo mata la mente.")
            val cfi = e.cfiTramo(b, i, b, i + "El miedo mata la mente.".length)!!
            assertEquals("epubcfi(/6/2!/4/4,/1:0,/1:23)", cfi)
            // Subrayas aquí, y luego le pones una nota
            val m = Marcas.subraya(id, cfi, "El miedo mata la mente.", 0, "Arrakis", 0.12, ahora = 1_700_000_000_000L)
            assertEquals(idMovil(id, cfi, 1_700_000_000_000L), m.ref)
            val conNota = Marcas.ponNota(m, "  La letanía contra el miedo.  ", ahora = 1_700_000_100_000L)
            sincroniza()
            val n = movil.coleccion("marcas").nodo(m.ref)!!
            assertEquals(id, n.texto("ref")); assertEquals("notes", n.texto("tipo")); assertFalse(n.borrado)
            val o = n.mapa("n")
            assertEquals(cfi, o["value"]); assertEquals("subrayado", o["color"]); assertEquals("El miedo mata la mente.", o["text"]); assertEquals("La letanía contra el miedo.", o["note"])
            assertEquals(1_700_000_000_000L, (o["created"] as Number).toLong()); assertEquals(0L, (o["index"] as Number).toLong()); assertEquals("Arrakis", o["label"])
            assertEquals(0.12, (o["frac"] as Number).toDouble(), 1e-9)
            // La hora de la última edición, en `m/n` y en `mod` (lo que mira MezclaLectura para que gane la más reciente)
            assertEquals(1_700_000_100_000L, n.m["n"]); assertEquals(1_700_000_100_000L, (o["mod"] as Number).toLong())
            // Un subrayado del móvil (tal cual lo sube LecturaNube.sube) se ve aquí y se pinta donde dice
            val cfiMovil = "epubcfi(/6/4!/4/4,/1:0,/1:30)"
            val creadaMovil = 1_700_000_200_000L
            movil.coleccion("marcas").pon(idMovil(id, cfiMovil, creadaMovil), mapOf("ref" to id, "tipo" to "notes", "n" to mapOf("value" to cfiMovil, "color" to "subrayado",
                "text" to "Quien controla la especia controla", "note" to "", "created" to creadaMovil, "index" to 1L, "label" to "", "frac" to 0.8, "mod" to creadaMovil), "b" to 0L), creadaMovil)
            sincroniza()
            val aqui = Marcas.de(id, Marcas.SUBRAYADOS)
            assertEquals(2, aqui.size)
            val delMovil = aqui.first { it.creada == creadaMovil }
            val u = e.ubica(delMovil.valor)!!
            assertEquals(1, u.seccion)
            assertEquals("Quien controla la especia cont", e.bloques(1)[u.desde!!.first].texto.text.substring(u.desde!!.second, u.hasta!!.second))
            // Lo quitas aquí: en el móvil queda borrado con una hora posterior a su última edición (así MezclaLectura lo quita también allí)
            Marcas.quita(conNota, ahora = 1_700_000_300_000L)
            sincroniza()
            val q = movil.coleccion("marcas").nodo(m.ref)!!
            assertTrue(q.borrado); assertEquals(1_700_000_300_000L, q.entero("b")); assertTrue(q.entero("b") > (q.mapa("n")["mod"] as Number).toLong())
            assertEquals(1, Marcas.de(id, Marcas.SUBRAYADOS).size)
            // «Deshacer»: vuelve, más nuevo que el borrado
            Marcas.recupera(conNota, ahora = 1_700_000_400_000L)
            sincroniza()
            val r = movil.coleccion("marcas").nodo(m.ref)!!
            assertFalse(r.borrado); assertEquals(1_700_000_400_000L, (r.mapa("n")["mod"] as Number).toLong()); assertEquals("La letanía contra el miedo.", r.mapa("n")["note"])
            // Un marcador de esta página
            val pagina = e.cfiDe(e.bloques(1)[0], 0)
            val mk = Marcas.marca(id, pagina, "Capítulo 2", 0.75, 1, ahora = 1_700_000_500_000L)
            // Y uno que quitas (para que la prueba del móvil vea también un borrado)
            val quitado = Marcas.subraya(id, e.cfiTramo(b, 0, b, 8)!!, "El miedo", 0, "Arrakis", 0.1, ahora = 1_700_000_600_000L)
            Marcas.quita(quitado, ahora = 1_700_000_700_000L)
            sincroniza()
            // Y unos ajustes del lector elegidos aquí (para la prueba del móvil)
            AjustesLectura.guarda(id, AjustesLectura.de(id).con("theme", "sepia").con("font", "garamond"))
            sincroniza()
            val k = movil.coleccion("marcas").nodo(mk.ref)!!.mapa("n")
            assertEquals("marks", movil.coleccion("marcas").nodo(mk.ref)!!.texto("tipo"))
            assertEquals(pagina, k["value"]); assertEquals("Capítulo 2", k["label"]); assertEquals(0.75, (k["fraction"] as Number).toDouble(), 1e-9); assertEquals(1L, (k["index"] as Number).toLong())
            System.getProperty("casos.escribe")?.let { dir ->
                val nodos = Emulador.lee("/users/$uid/familia/marcas").comoMapa().filterKeys { it != idMovil(id, cfiMovil, creadaMovil) }
                File(dir, "marfil-marcas.json").writeText(Json.escribe(linkedMapOf(
                    "nota" to "Los subrayados, notas y marcadores que escribe Marfil del ordenador (MarcasTest de escritorio/marfil, con -Dcasos.escribe), tal cual quedan en la cuenta (familia/marcas). Los lee MarcasEscritorioTest de Marfil de Android con su propio código.",
                    "kid" to id, "titulo" to "Dune", "autor" to "Frank Herbert", "nodos" to nodos,
                    "lectura" to Emulador.lee("/users/$uid/familia/lectura/$id"), "ajustes" to Emulador.lee("/users/$uid/familia/ajustes/marfil"))) + "\n")
            }
        }
    }

    @Test fun losAjustesDelLectorSonLosDelMovil() {
        cuenta()
        val id = anadeArchivo(epub())
        // Lo que eligió en el móvil: de partida para todos (con el brillo, que es de cada dispositivo) y, para este libro, otra letra
        movil.coleccion("ajustes").pon("marfil", mapOf("lector" to Json.escribe(mapOf("at" to 1_700_000_000_000L, "s" to mapOf("theme" to "sepia", "size" to 120L, "font" to "literata", "spacing" to 1.7, "brightness" to 0.4, "tapZones" to "adelante")))))
        movil.coleccion("lectura").pon(id, mapOf("ref" to id, "settings" to mapOf("font" to "garamond", "margin" to "l", "columns" to true), "settingsAt" to 1_700_000_100_000L))
        sincroniza()
        val a = AjustesLectura.de(id)
        assertEquals("sepia", a.tema); assertEquals(120, a.tamano); assertEquals("garamond", a.letra); assertEquals(1.7, a.interlineado, 1e-9); assertEquals("l", a.margen); assertTrue(a.columnas)
        assertEquals("lo de cada dispositivo no viaja", null, a.m["brightness"])
        // Lo cambias aquí: va al libro (todos sus ajustes, con su hora) y de partida para los demás (sin lo de cada dispositivo); lo que este no usa, se conserva
        val nuevo = a.con("theme", "noche").con("paraSpace", 2L)
        AjustesLectura.guarda(id, nuevo)
        sincroniza()
        val lec = movil.coleccion("lectura").nodo(id)!!
        assertEquals("noche", lec.mapa("settings")["theme"]); assertEquals("garamond", lec.mapa("settings")["font"]); assertEquals(2L, (lec.mapa("settings")["paraSpace"] as Number).toLong())
        assertTrue(lec.entero("settingsAt") > 1_700_000_100_000L)
        val (at, s) = AjustesLectura.dePartidaEnLaCuenta(movil.coleccion("ajustes").nodo("marfil")!!.texto("lector"))!!
        assertTrue(at > 1_700_000_000_000L)
        assertEquals("noche", s["theme"]); assertEquals("adelante", s["tapZones"]); assertFalse(s.containsKey("brightness"))
        // Un libro sin ajustes propios abre con los de partida
        val otro = Libros.anade("Solaris", "Stanisław Lem", "solaris.epub")
        assertEquals("noche", AjustesLectura.de(otro).tema)
        assertEquals("app", AjustesLectura.mezcla(null, null).tema)
    }
}
