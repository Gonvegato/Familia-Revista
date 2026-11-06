package com.gonzalo.cobalto

import androidx.compose.ui.geometry.Offset
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.hypot

/**
 * El árbol (las medidas y «Ordenar», EscribirArbol.kt) y la guía de la novela (EscribirGuia.kt, con los casos de herramientas/casos/guia.json,
 * los mismos que prueba Atrio): los mismos archivos en el móvil y en el ordenador, y esta prueba la pasan los dos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscribirArbolGuiaTest {
    @get:Rule val tmp = TemporaryFolder()
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

    @Test fun laGuiaMiraLoQueHayEnLaObra() {
        val e = Escritos(tmp.root)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra"); put("idea", "Una frase."); put("meta", 10) }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno"); put("resumen", "Pasa algo.") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Una dos tres cuatro cinco seis siete ocho nueve diez once."); put("resumen", "Y algo más.") }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Marta") }
        e.nuevo(Escritos.T_NOTA, o.id) { put("titulo", GuiaNovela.TITULO_ESQUEMA); put("texto", "Planteamiento: llega.\n\nNudo:\n\nDesenlace: se va.") }
        val d = datosGuia(e, o.id)
        assertEquals(1, d.personajes); assertEquals(2 + 2, d.resumenes); assertEquals(11, d.palabras)
        val est = GuiaNovela.estados(d)
        assertEquals(listOf(1, 2, 3, 6), est.filter { it.estado == "hecho" }.map { it.id })
        assertEquals(4, GuiaNovela.siguiente(est))
    }
}
