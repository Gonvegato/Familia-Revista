package com.gonzalo.familia

import com.gonzalo.familia.nucleo.Nodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Los mapas de ATLAS tal como los escribe ATLAS (`familia/atlas/<mapa>`, ver nube/ESQUEMA.md). */
@RunWith(RobolectricTestRunner::class)
class MapasAtlasTest {
    private fun nodo(ref: String, vararg campos: Pair<String, Any?>, m: Map<String, Long> = emptyMap()) =
        Nodo(ref, linkedMapOf<String, Any?>(*campos).also { if (m.isNotEmpty()) it[Nodo.M] = m })

    private val lug = """[{"n":"Aldreth","k":"capital","x":0.5321,"y":0.41},{"n":"Aldea del Vado","k":"aldea","x":0.2,"y":0.6},{"n":"Vado","k":"puerto","x":0.9,"y":0.9},{"n":"Ur","k":"ruinas","x":0.1,"y":0.1},{"k":"sin nombre","x":0.3,"y":0.3}]"""

    private val ithria = nodo("m_ithria", "t" to "Ithria", "k" to "continente", "obra" to "o_cristal", "obras" to "o_cristal,o_cosmos", "w" to 8192L, "h" to 6144L,
        "portada" to "abc", "lam" to mapOf("gd" to "GD1", "n" to "m_ithria.lamina.webp", "s" to 1234L, "w" to 3072L, "h" to 2304L), "lug" to lug, "ul" to 50L,
        m = mapOf("lam" to 40L, "t" to 40L))
    private val varenne = nodo("m_varenne", "t" to "Varenne", "obra" to "o_cristal", "w" to 4096L, "h" to 4096L, "ul" to 60L)
    private val otra = nodo("m_otra", "t" to "Otra", "obra" to "o_otra", "obras" to "o_otra")
    private val borrado = nodo("m_borrado", "t" to "Borrado", "obra" to "o_cristal", "b" to 99L)

    @Test fun filtraPorObraEnObraYEnObrasSinLosBorrados() {
        val todos = listOf(ithria, varenne, otra, borrado)
        assertEquals(listOf("Ithria", "Varenne"), MapasAtlas.deLaObra(todos, "o_cristal").map { it.nombre })
        // o_cosmos solo está en «obras»
        assertEquals(listOf("Ithria"), MapasAtlas.deLaObra(todos, "o_cosmos").map { it.nombre })
        // Sin «obras», vale «obra»
        assertEquals(listOf("o_cristal"), MapasAtlas.deNodo(varenne)!!.obras)
        assertEquals(emptyList<MapaAtlas>(), MapasAtlas.deLaObra(todos, ""))
        assertNull(MapasAtlas.deNodo(borrado))
    }

    @Test fun leeLaLaminaYLosLugares() {
        val m = MapasAtlas.deNodo(ithria)!!
        assertEquals(LaminaAtlas("GD1", "m_ithria.lamina.webp", 1234L, 3072, 2304), m.lamina)
        assertEquals("GD1@40", m.versionLamina)
        assertEquals("continente", m.tipo)
        assertEquals("UN CONTINENTE · 4 LUGARES", VisorMapas.resumen(m))
        assertEquals("", VisorMapas.resumen(MapasAtlas.deNodo(varenne)!!))
        assertEquals(4, m.lugares.size)   // el que no tiene nombre se salta
        assertEquals(LugarMapa("Aldreth", "capital", 0.5321f, 0.41f), m.lugares[0])
    }

    @Test fun sinLaminaSeVeLaPortadaYNoHayVersion() {
        // Un mapa subido con una versión de ATLAS anterior a la 0.14.0: sin «lam»
        val m = MapasAtlas.deNodo(varenne)!!
        assertNull(m.lamina)
        assertEquals("", m.versionLamina)
        assertTrue(m.lugares.isEmpty())
        assertEquals(emptyList<LugarMapa>(), MapasAtlas.lugares("esto no es JSON"))
    }

    @Test fun encuentraLosLugaresEnElTextoComoPalabrasEnterasYGanaElMasLargo() {
        val mapas = listOf(MapasAtlas.deNodo(ithria)!!)
        val texto = "Salieron de Aldea del Vado al alba. Tres jornadas hasta ALDRÉTH; el vado estaba crecido. Aldrethenses no cuenta. Ur tampoco."
        val ms = MapasAtlas.menciones(texto, mapas)
        assertEquals(listOf("Aldea del Vado", "ALDRÉTH", "vado"), ms.map { texto.substring(it.inicio, it.fin) })
        assertEquals(listOf("Aldea del Vado", "Aldreth", "Vado"), ms.map { it.lugar.nombre })
        assertTrue(ms.all { it.mapa.id == "m_ithria" })
    }
}
