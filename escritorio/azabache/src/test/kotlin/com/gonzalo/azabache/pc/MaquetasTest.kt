package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Las maquetas que se diseñan aquí son las del móvil: el mismo JSON (lo escribe aquí el código de `MagLayouts.toJson` de Android, con el
 * org.json de Android, y se compara), el mismo número (`nubeId`) y el mismo elemento en la cuenta (`azabache/maquetas~<id>`, como
 * `Store.saveMaqueta`). Y las herramientas del editor hacen lo mismo que las del móvil.
 */
class MaquetasTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var otra: Familia
    private lateinit var aqui: Familia

    // ---- El código del móvil, tal cual (Maqueta.kt y Nube.kt de Android) ----
    private data class MagCell(val type: String, val title: String = "", val text: String = "", val image: String = "", val fit: Boolean = false,
        val zoom: Float = 1f, val ox: Float = 0f, val oy: Float = 0f, val shape: String = "", val asp: Float = 0f)
    private data class MagRowSpec(val cells: List<MagCell>, val split: Float = 0.5f, val height: Float = 0f)
    private fun toJson(rows: List<MagRowSpec>) = JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject().put("s", r.split.toDouble()).put("a", r.height.toDouble()).put("c", JSONArray().apply {
                r.cells.forEach { c ->
                    put(JSONObject().put("t", c.type).put("h", c.title).put("x", c.text).put("i", c.image).put("f", c.fit)
                        .put("z", c.zoom.toDouble()).put("ox", c.ox.toDouble()).put("oy", c.oy.toDouble()).put("sh", c.shape).put("as", c.asp.toDouble()))
                }
            }))
        }
    }.toString()
    private fun fromJson(s: String): List<MagRowSpec> { val a = JSONArray(s); return (0 until a.length()).map { i -> val o = a.getJSONObject(i); val cs = o.getJSONArray("c")
        MagRowSpec((0 until cs.length()).map { j -> val c = cs.getJSONObject(j)
            MagCell(c.optString("t", "vacio"), c.optString("h"), c.optString("x"), c.optString("i"), c.optBoolean("f"), c.optDouble("z", 1.0).toFloat(),
                c.optDouble("ox", 0.0).toFloat(), c.optDouble("oy", 0.0).toFloat(), c.optString("sh"), c.optDouble("as", 0.0).toFloat()) }.take(2),
            o.optDouble("s", 0.5).toFloat(), o.optDouble("a", 0.0).toFloat()) } }
    private fun nubeId(k: String): Long { var h = -0x340d631b7bdddcdbL; for (b in k.toByteArray()) { h = h xor (b.toLong() and 0xff); h *= 0x100000001b3L }; return (h and 0x1FFFFFFFFFFFFFL).coerceAtLeast(1L) }

    private fun aMovil(f: List<FilaMaqueta>) = f.map { r -> MagRowSpec(r.casillas.map { c -> MagCell(c.tipo, c.titulo, c.texto, c.imagen, c.entera, c.zoom, c.ox, c.oy, c.forma, c.proporcion) }, r.split, r.alto) }

    private val ejemplo = listOf(
        FilaMaqueta(listOf(CasillaMaqueta("texto", "Mi veredicto", "El mejor juego de rol que he jugado."), CasillaMaqueta("nota")), split = 0.6f),
        FilaMaqueta(listOf(CasillaMaqueta("imagen", imagen = "https://cdn2.steamgriddb.com/hero/abc.png", zoom = 1.35f, ox = -0.12f, oy = 0.05f, forma = "llenar", proporcion = 1.42f)), alto = 320f),
        FilaMaqueta(listOf(CasillaMaqueta("datos"), CasillaMaqueta("enlaces"))),
    )

    @Test fun elJsonEsElMismoQueEscribeElMovil() {
        // Lo mismo que escribe el código del móvil (comparado por lo que dice: la copia de org.json de las pruebas, la de 2013, no guarda el
        // orden de los campos), y los campos en el orden en que los pone el móvil (su org.json de ahora sí lo guarda)
        assertEquals(canon(Json.lee(toJson(aMovil(ejemplo)))), canon(Json.lee(Maquetas.aJson(ejemplo))))
        assertEquals(listOf("s", "a", "c", "t", "h", "x", "i", "f", "z", "ox", "oy", "sh", "as"), Regex("\"(\\w+)\":").findAll(Maquetas.aJson(ejemplo.take(1))).map { it.groupValues[1] }.toList().distinct())
        // Y lo que escribe el móvil se lee aquí igual (y al revés)
        assertEquals(ejemplo, Maquetas.lee(toJson(aMovil(ejemplo))))
        assertEquals(aMovil(ejemplo), fromJson(Maquetas.aJson(ejemplo)))
    }

    @Test fun elNumeroDeCadaMaquetaEsElDelMovil() {
        for (k in listOf("v:0:6", "p:1:12345678901", "tpl:v:2", "tpl:p:0", "v:3:603")) assertEquals(nubeId(k), Maquetas.idDe(k))
        assertTrue(Maquetas.claveValida("tpl:p:3")); assertTrue(Maquetas.claveValida("v:0:6")); assertFalse(Maquetas.claveValida("x:0:6")); assertFalse(Maquetas.claveValida("v:a:6"))
    }

    @Test fun lasHerramientasDelEditorHacenLoDelMovil() {
        val f = Maquetas.deSiempre(GAMES, enCurso = false)
        assertEquals(listOf("nota", "portada"), f[0].casillas.map { it.tipo })
        // Subir, bajar (sin salirse)
        assertEquals(f[1], Maquetas.sube(f, 1)[0]); assertEquals(f, Maquetas.sube(f, 0)); assertEquals(f, Maquetas.baja(f, f.lastIndex))
        // Dividir añade una vacía a medias; unir se queda con la primera que no está vacía
        val d = Maquetas.divide(f, 1)
        assertEquals(listOf("opinion", "vacio"), d[1].casillas.map { it.tipo }); assertEquals(0.5f, d[1].split)
        assertEquals(listOf("opinion"), Maquetas.une(d, 1)[1].casillas.map { it.tipo })
        assertEquals(listOf("portada"), Maquetas.une(Maquetas.ponCasilla(f, 0, 0, CasillaMaqueta("vacio")), 0)[0].casillas.map { it.tipo })
        // La línea, entre el 30 y el 70 %; el alto, entre 56 y 1400 dp (0: el que pida)
        assertEquals(0.3f, Maquetas.ponSplit(f, 0, 0.05f)[0].split); assertEquals(0.7f, Maquetas.ponSplit(f, 0, 0.95f)[0].split)
        assertEquals(56f, Maquetas.ponAlto(f, 0, 10f)[0].alto); assertEquals(1400f, Maquetas.ponAlto(f, 0, 5000f)[0].alto); assertEquals(0f, Maquetas.ponAlto(f, 0, 0f)[0].alto)
        assertEquals(f.size + 1, Maquetas.anade(f).size); assertEquals("vacio", Maquetas.anade(f).last().casillas.single().tipo)
        assertEquals(f.size - 1, Maquetas.quita(f, 2).size)
    }

    @Test fun laPortadaVaPequenaComoEnElMovil() {
        // Una fila entera para la portada (la de Azabache 1.2): se quita y la portada pasa a la primera fila de una sola casilla
        val antes = listOf(FilaMaqueta(listOf(CasillaMaqueta("portada"))), FilaMaqueta(listOf(CasillaMaqueta("nota"))), FilaMaqueta(listOf(CasillaMaqueta("opinion"))))
        assertEquals(listOf(listOf("nota", "portada"), listOf("opinion")), Maquetas.portadaPequena(antes).map { r -> r.casillas.map { it.tipo } })
        // Sin portada y sin filas de una casilla: arriba, sola
        val sin = listOf(FilaMaqueta(listOf(CasillaMaqueta("nota"), CasillaMaqueta("datos"))))
        assertEquals(listOf(listOf("portada"), listOf("nota", "datos")), Maquetas.portadaPequena(sin).map { r -> r.casillas.map { it.tipo } })
        // Con la portada ya junto a otra, no se toca
        assertEquals(Maquetas.deSiempre(BOOKS, false), Maquetas.portadaPequena(Maquetas.deSiempre(BOOKS, false)))
    }

    @Test fun lasCasillasQueSeOfrecenSonLasDelMovil() {
        assertEquals(listOf("portada", "nota", "opinion", "horas", "sesiones", "diario", "enlaces", "datos"), Maquetas.modulos(GAMES, false).map { it.tipo })
        assertEquals(listOf("portada", "estado", "jugar", "apuntes", "horas", "sesiones", "diario", "enlaces"), Maquetas.modulos(GAMES, true).map { it.tipo })
        val pelis = customKind(2, "Películas", listOf("Guion"), "Viendo", 0, SRC_MOVIE)
        // Lo que aquí no se pinta (la crítica, dónde verla) se puede poner: se ve en el móvil. El progreso de una serie se pinta aquí
        // desde la 1.2.1 (lo visto en Mostaza)
        assertEquals(setOf("critica", "donde"), Maquetas.modulos(pelis, false).filter { !it.aqui }.map { it.tipo }.toSet())
        assertEquals(setOf("critica", "donde"), Maquetas.modulos(pelis, true).filter { !it.aqui }.map { it.tipo }.toSet())
        assertTrue(Maquetas.modulos(pelis, true).any { it.tipo == "progreso" && it.aqui })
    }

    // ---------------------------------------------------------------- en la cuenta

    @Before fun prepara() {
        Emulador.limpia()
        Emulador.cuenta("gonzalo")
        otra = Emulador.dispositivo("gonzalo")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = tmp.root
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        aqui = LaFamilia.inicia("azabache").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuenta.olvidaParaPruebas()
        listOf("juegos", "libros", "tiempo", "azabache").forEach { otra.coleccion(it); LaFamilia.coleccion(it) }
        sincroniza()
    }

    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Cuenta.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(otra.sincroniza().ok); assertTrue(aqui.sincroniza().ok); assertTrue(otra.sincroniza().ok) }

    @Test fun loQueDisenasAquiLoLeeElMovilComoSuyo() {
        Maquetas.guarda("v:0:6", ejemplo)
        sincroniza()
        val n = otra.coleccion("azabache").nodo("maquetas~${nubeId("v:0:6")}")!!
        assertEquals(0L, n.entero("b"))
        // El elemento como lo deja Store.saveMaqueta: id, k, j y updated
        val o = JSONObject(n.texto("o"))
        assertEquals(nubeId("v:0:6"), o.getLong("id")); assertEquals("v:0:6", o.getString("k"))
        assertTrue(o.getLong("updated") > 1_600_000_000_000L); assertFalse(o.has("sv"))
        assertEquals(aMovil(ejemplo), fromJson(o.getString("j")))
        // Cambiarla reescribe el mismo elemento (con su versión de sincronización, como al reescribir en el móvil)
        Maquetas.guarda("v:0:6", Maquetas.quita(ejemplo, 2))
        sincroniza()
        val o2 = JSONObject(otra.coleccion("azabache").nodo("maquetas~${nubeId("v:0:6")}")!!.texto("o"))
        assertEquals(2, JSONArray(o2.getString("j")).length()); assertTrue(o2.has("sv"))
        assertEquals(1, otra.coleccion("azabache").vivos().count { it.ref.startsWith("maquetas~") })
    }

    @Test fun laDeTodaLaCategoriaVaSinTusTextosNiImagenesYRestablecerLasQuita() {
        Maquetas.guarda("v:0:6", ejemplo)
        Maquetas.guardaPlantilla("tpl:v:0", ejemplo)
        sincroniza()
        val tpl = fromJson(JSONObject(otra.coleccion("azabache").nodo("maquetas~${nubeId("tpl:v:0")}")!!.texto("o")).getString("j"))
        assertEquals(listOf("vacio", "nota"), tpl[0].cells.map { it.type }); assertEquals("vacio", tpl[1].cells.single().type); assertEquals("", tpl[0].cells[0].text)
        assertEquals(0.6f, tpl[0].split, 0.0001f)
        // Una ficha sin la suya ve la de la categoría; la que tiene la suya, la suya
        assertEquals(listOf("vacio", "nota"), Maquetas.de(0, 99L, enCurso = false)!![0].casillas.map { it.tipo })
        assertEquals("Mi veredicto", Maquetas.de(0, 6L, enCurso = false)!![0].casillas[0].titulo)
        assertNull(Maquetas.de(0, 6L, enCurso = true))
        // Restablecer: las dos con su marca de borrado (nada se borra de verdad), y la página vuelve a la de siempre
        Maquetas.restablece("v:0:6", "tpl:v:0")
        sincroniza()
        val az = otra.coleccion("azabache")
        assertTrue(az.todos()["maquetas~${nubeId("v:0:6")}"]!!.borrado); assertTrue(az.todos()["maquetas~${nubeId("tpl:v:0")}"]!!.borrado)
        assertEquals(Maquetas.deSiempre(GAMES, false), Maquetas.paraVer(GAMES, 6L, false))
    }

    @Test fun unaMaquetaDelMovilSeEditaAquiEnSuMismoElemento() {
        val delMovil = toJson(listOf(MagRowSpec(listOf(MagCell("nota"), MagCell("portada"))), MagRowSpec(listOf(MagCell("opinion")))))
        otra.coleccion("azabache").pon("maquetas~${nubeId("p:1:11")}", mapOf("o" to JSONObject().put("id", nubeId("p:1:11")).put("k", "p:1:11").put("j", delMovil).put("updated", 5L).toString(), "b" to 0L))
        sincroniza()
        val vista = Maquetas.paraVer(BOOKS, 11L, enCurso = true)
        assertEquals(listOf(listOf("nota", "portada"), listOf("opinion")), vista.map { r -> r.casillas.map { it.tipo } })
        Maquetas.guarda("p:1:11", Maquetas.divide(vista, 1))
        sincroniza()
        val vivos = otra.coleccion("azabache").vivos().filter { it.ref.startsWith("maquetas~") }
        assertEquals(1, vivos.size)
        assertEquals(listOf("opinion", "vacio"), fromJson(Json.lee(vivos.single().texto("o")).comoMapa()["j"] as String)[1].cells.map { it.type })
    }
}
