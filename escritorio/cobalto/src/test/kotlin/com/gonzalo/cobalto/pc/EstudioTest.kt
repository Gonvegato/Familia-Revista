package com.gonzalo.cobalto.pc

import androidx.compose.ui.geometry.Offset
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Tipografia
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Lo del estudio que no es pantalla: el árbol (las medidas y «Ordenar»), la guía de la novela, los sinónimos y la tipografía
 * (con los casos compartidos de herramientas/casos, los mismos que prueban el móvil y Atrio) y el texto de las páginas de lectura.
 */
class EstudioTest {
    private fun raiz(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "herramientas/casos").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/casos")
        return dir
    }
    private fun ficha(id: String, tipo: String = "personaje", campos: JSONObject.() -> Unit = {}) = Doc(JSONObject().put("id", id).put("t", "f").put("pid", "p").put("tipo", tipo).apply(campos))

    // ---- El árbol

    @Test fun elNombreCabeEnElOvalo() {
        assertEquals(listOf("Marta"), ArbolEscribir.lineasNombre("Marta", 13))
        assertEquals(listOf("La casa del", "lago"), ArbolEscribir.lineasNombre("La casa del lago", 11))
        assertEquals(listOf("Sin nombre"), ArbolEscribir.lineasNombre("  ", 13))
        // Nunca más de dos líneas: la segunda acaba en «…»
        val l = ArbolEscribir.lineasNombre("El caballero de la triste figura y su escudero", 11)
        assertEquals(2, l.size); assertTrue(l[1].endsWith("…"))
        // Una palabra larguísima se corta
        assertTrue(ArbolEscribir.lineasNombre("Supercalifragilisticoespialidoso", 11)[0].endsWith("…"))
    }

    @Test fun elTamanoEsElElegidoOElDeSuTipo() {
        assertEquals(2, ArbolEscribir.tam(ficha("a", "personaje")))
        assertEquals(3, ArbolEscribir.tam(ficha("b", "lugar")))
        assertEquals(3, ArbolEscribir.tam(ficha("c", "mundo")))
        assertEquals(1, ArbolEscribir.tam(ficha("d", "objeto")))
        assertEquals(1, ArbolEscribir.tam(ficha("e", "lugar") { put("tam", 1) }))
        assertEquals(2, ArbolEscribir.tam(ficha("f", "personaje") { put("tam", 9) }))
        assertTrue(ArbolEscribir.dentro(Offset(70f, 0f), Offset.Zero, 2))
        assertFalse(ArbolEscribir.dentro(Offset(70f, 30f), Offset.Zero, 2))
    }

    @Test fun lasFichasSinSitioVanEnCirculoYLasDemasDondeEstan() {
        val p = ArbolEscribir.posiciones(listOf(ficha("a") { put("ax", 10); put("ay", -20) }, ficha("b"), ficha("c")))
        assertEquals(Offset(10f, -20f), p["a"])
        // Las dos sin sitio, en un círculo (achatado) de radio 140 + 2·28 alrededor del centro
        assertEquals(Offset(0f, -137f), p["b"])
        assertEquals(Offset(0f, 137f), p["c"])
        // Un «ax» que no es un número no cuenta como sitio
        assertNotEquals(Offset.Zero, ArbolEscribir.posiciones(listOf(ficha("x") { put("ax", "") }))["x"])
    }

    @Test fun ordenarAcercaLoUnidoYLoCentra() {
        val ids = listOf("a", "b", "c", "d")
        val ini = mapOf("a" to Offset(0f, 0f), "b" to Offset(800f, 0f), "c" to Offset(0f, 600f), "d" to Offset(810f, 590f))
        val r = ArbolEscribir.ordena(ids, listOf("a" to "b", "c" to "d"), ini)
        fun d(x: String, y: String) = hypot((r.getValue(x).x - r.getValue(y).x).toDouble(), (r.getValue(x).y - r.getValue(y).y).toDouble())
        assertTrue("lo unido, más cerca que lo no unido", d("a", "b") < d("a", "d") && d("c", "d") < d("b", "c"))
        // Centrado en el origen (con el redondeo)
        assertEquals(0.0, ids.map { r.getValue(it).x.toDouble() }.average(), 1.0)
        assertEquals(0.0, ids.map { r.getValue(it).y.toDouble() }.average(), 1.0)
        // Siempre igual con lo mismo (no hay azar)
        assertEquals(r, ArbolEscribir.ordena(ids, listOf("a" to "b", "c" to "d"), ini))
    }

    @Test fun laRuedaDelRatonAcercaYElTrackpadMueve() {
        assertTrue(ArbolEscribir.ruedaAcerca(0f, 1f, false))
        assertTrue(ArbolEscribir.ruedaAcerca(0f, -3f, false))
        assertFalse(ArbolEscribir.ruedaAcerca(0f, 0.37f, false))
        assertFalse(ArbolEscribir.ruedaAcerca(1.5f, 0f, false))
        assertTrue(ArbolEscribir.ruedaAcerca(0f, 0.37f, true))
    }

    // ---- La guía (herramientas/casos/guia.json)

    @Test fun laGuiaConLosCasosCompartidos() {
        val g = JSONObject(File(raiz(), "herramientas/casos/guia.json").readText())
        val titulos = g.getJSONArray("titulos")
        assertEquals(titulos.length(), GuiaNovela.PASOS.size)
        GuiaNovela.PASOS.forEachIndexed { i, p -> assertEquals(titulos.getString(i), p.titulo); assertEquals(i + 1, p.id) }
        val casos = g.getJSONArray("casos")
        for (i in 0 until casos.length()) {
            val c = casos.getJSONObject(i)
            val d = c.getJSONObject("datos")
            val est = GuiaNovela.estados(DatosGuia(d.optJSONObject("obra") ?: JSONObject(), d.optInt("personajes"), d.optInt("resumenes"), d.optInt("dias"), d.optInt("palabras"), d.optLong("ahora")))
            val esperado = c.getJSONArray("esperado").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals(c.getString("nombre"), esperado, est.filter { it.estado != "pendiente" }.map { "${it.id}:${it.estado}" })
            assertEquals(c.getString("nombre"), if (c.isNull("siguiente")) null else c.getInt("siguiente"), GuiaNovela.siguiente(est))
            if (c.has("diasReposo")) assertEquals(c.getInt("diasReposo"), est.first { it.id == 7 }.dias)
        }
        val esq = g.getJSONArray("esquema")
        for (i in 0 until esq.length()) { val c = esq.getJSONObject(i); assertEquals(c.getString("nombre"), c.getInt("esperado"), GuiaNovela.seccionesEsquema(c.getString("texto"))) }
    }

    // ---- Sinónimos y tipografía (herramientas/casos/sinonimos.json y tipografia.json)

    @Test fun sinonimosConLosCasosCompartidos() {
        // El diccionario va dentro de la app (se copia de los assets del móvil al compilar)
        Sinonimos.carga()
        assertTrue("el tesauro va en la app", Sinonimos.cargado)
        assertTrue(Sinonimos.cargaTexto(String(File(raiz(), "cobalto/src/main/assets/sinonimos/th_es_v2.dat").readBytes(), Charsets.ISO_8859_1)) > 20_000)
        val a = JSONObject(File(raiz(), "herramientas/casos/sinonimos.json").readText()).getJSONArray("casos")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val esperado = c.getJSONArray("grupos").let { g -> (0 until g.length()).map { j -> g.getJSONArray(j).let { x -> (0 until x.length()).map { x.getString(it) } } } }
            val r = Sinonimos.busca(c.getString("palabra"))
            assertEquals(c.getString("palabra"), esperado, r.take(esperado.size))
            if (esperado.isEmpty()) assertTrue(r.isEmpty())
        }
        assertEquals("miró", Sinonimos.palabraEn("Ella miró la casa.", 7, 7)?.let { "Ella miró la casa.".substring(it.first, it.last + 1) })
        assertEquals("casa", Sinonimos.palabraEn("Ella miró la casa.", 13, 17)?.let { "Ella miró la casa.".substring(it.first, it.last + 1) })
        assertNull(Sinonimos.palabraEn("Ella  miró", 5, 5))
    }

    @Test fun tipografiaConLosCasosCompartidos() {
        val a = JSONObject(File(raiz(), "herramientas/casos/tipografia.json").readText()).getJSONArray("casos")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val r = Tipografia.aplica(c.getString("texto"), c.getInt("cursor"), c.getString("ch")[0])
            if (c.isNull("esperado")) assertNull(c.getString("nombre"), r)
            else { assertEquals(c.getString("nombre"), c.getString("esperado"), r?.first); assertEquals(c.getString("nombre"), c.getInt("cursorEsperado"), r?.second) }
        }
    }

    // ---- El texto de las páginas de lectura

    @Test fun elTextoDeUnaFichaEnBloques() {
        val b = bloquesDe("# Su historia\n\nNació en el lago.\r\nCreció en Madrid.\n\n> Nadie vuelve igual.\n- Quiere: volver\n***\n### Final")
        assertEquals(listOf(Bloque.Titulo(1, "Su historia"), Bloque.Parrafo("Nació en el lago."), Bloque.Parrafo("Creció en Madrid."), Bloque.Cita("Nadie vuelve igual."),
            Bloque.Punto("Quiere: volver"), Bloque.Salto, Bloque.Titulo(3, "Final")), b)
        assertTrue(bloquesDe("  \n\n").isEmpty())
        val f = conFormato("Era **muy** alta y *nada* tímida.")
        assertEquals("Era muy alta y nada tímida.", f.text)
        assertEquals(2, f.spanStyles.size)
        assertEquals("sin cerrar *aquí", conFormato("sin cerrar *aquí").text)
    }

    @Test fun losColoresDeLosEscritos() {
        assertEquals(0xFFB0102F.toInt(), colorEscrito("#B0102F").value.shr(32).toInt())
        assertEquals(androidx.compose.ui.graphics.Color.Red, colorEscrito("", androidx.compose.ui.graphics.Color.Red))
        assertEquals(androidx.compose.ui.graphics.Color.Red, colorEscrito("#12", androidx.compose.ui.graphics.Color.Red))
        assertEquals(androidx.compose.ui.graphics.Color.Red, colorEscrito("#zzzzzz", androidx.compose.ui.graphics.Color.Red))
    }
}
