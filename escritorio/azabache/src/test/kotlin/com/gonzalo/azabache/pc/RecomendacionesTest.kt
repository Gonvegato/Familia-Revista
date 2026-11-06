package com.gonzalo.azabache.pc

import com.gonzalo.familia.nucleo.Emulador
import com.gonzalo.familia.nucleo.Familia
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.LlaveroMemoria
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * Las recomendaciones a tu pareja: lo que se escribe aquí es la `Rec` del móvil (`recToJson`, con el org.json de Android) en tu lista `recs`
 * (`azabache/recs~<id>`), un elemento nuevo cada vez y sin tocar los de antes; lo que te recomienda tu pareja se lee de su cuenta; «A mi
 * lista» lo pone en tus pendientes, y lo hecho se recuerda en este ordenador (como `recsdone` en cada móvil), no en la cuenta.
 */
class RecomendacionesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var otra: Familia
    private lateinit var aqui: Familia
    private lateinit var lucia: Familia
    private lateinit var ella: String

    // El código del móvil (Data.kt de Android), tal cual
    private data class Rec0(val id: Long, val kind: Int, val itemId: Long, val name: String, val cover: String, val creator: String, val note: String, val time: Long)
    private fun recToJson(r: Rec0): JSONObject = JSONObject().put("id", r.id).put("kind", r.kind).put("item", r.itemId)
        .put("name", r.name).put("cover", r.cover).put("creator", r.creator).put("note", r.note).put("time", r.time)
    private fun recFromJson(o: JSONObject) = Rec0(o.getLong("id"), o.optInt("kind", 0), o.optLong("item", 0L), o.optString("name"), o.optString("cover"),
        o.optString("creator"), o.optString("note"), o.optLong("time", 0L))

    @Before fun prepara() {
        Emulador.limpia()
        val yo = Emulador.cuenta("gonzalo")
        ella = Emulador.cuenta("lucia")
        Emulador.escribe("/users/$yo/partner", ella); Emulador.escribe("/users/$ella/partner", yo); Emulador.escribe("/users/$ella/name", "Lucía")
        otra = Emulador.dispositivo("gonzalo")
        lucia = Emulador.dispositivo("lucia")
        LaFamilia.reiniciaParaPruebas()
        LaFamilia.alMomento = true
        LaFamilia.configPruebas = Emulador.cfg
        LaFamilia.dirPruebas = tmp.root
        LaFamilia.llaveroPruebas = LlaveroMemoria()
        Local.olvidaParaPruebas()
        aqui = LaFamilia.inicia("azabache").also { it.automatico = false }
        LaFamilia.entrar("gonzalo", "secreto1")
        Cuenta.olvidaParaPruebas()
        listOf("juegos", "libros", "tiempo", "azabache").forEach { otra.coleccion(it); LaFamilia.coleccion(it); lucia.coleccion(it) }
        sincroniza()
    }

    @After fun fuera() { LaFamilia.reiniciaParaPruebas(); Cuenta.olvidaParaPruebas(); Local.olvidaParaPruebas() }

    private fun sincroniza() { assertTrue(otra.sincroniza().ok); assertTrue(lucia.sincroniza().ok); assertTrue(aqui.sincroniza().ok); assertTrue(otra.sincroniza().ok) }

    @Test fun laRecomendacionEsLaDelMovil() {
        val r = Rec(1730000000000L, 1, 13L, "Rayuela", "https://covers.openlibrary.org/b/id/1-L.jpg", "Julio Cortázar", "Te va a encantar", 1730000000000L)
        // Lo mismo que escribe el código del móvil (por lo que dice) y en su orden (el de los «put» del móvil)
        val nuestro = com.gonzalo.familia.nucleo.Json.escribe(recAMapa(r))
        assertEquals(canon(com.gonzalo.familia.nucleo.Json.lee(recToJson(Rec0(r.id, 1, 13L, "Rayuela", r.portada, "Julio Cortázar", "Te va a encantar", r.hora)).toString())), canon(com.gonzalo.familia.nucleo.Json.lee(nuestro)))
        assertEquals(listOf("id", "kind", "item", "name", "cover", "creator", "note", "time"), Regex("\"(\\w+)\":").findAll(nuestro).map { it.groupValues[1] }.toList())
        val leida = recFromJson(JSONObject(com.gonzalo.familia.nucleo.Json.escribe(recAMapa(r))))
        assertEquals(Rec0(r.id, 1, 13L, "Rayuela", r.portada, "Julio Cortázar", "Te va a encantar", r.hora), leida)
        assertEquals(r, recDeMapa(objDe(recToJson(Rec0(r.id, 1, 13L, "Rayuela", r.portada, "Julio Cortázar", "Te va a encantar", r.hora)).toString())))
    }

    @Test fun recomendarAquiLoVeTuParejaComoDelMovil() {
        val kid = Cuenta.kidDe(Cuenta.claveLibro("Rayuela", "Julio Cortázar"))
        otra.coleccion("libros").pon(kid, mapOf("clave" to Cuenta.claveLibro("Rayuela", "Julio Cortázar"), "t" to "Rayuela", "a" to "Julio Cortázar", "estado" to "terminado", "nota" to 92L, "azid" to 13L, "pu" to "https://covers.openlibrary.org/b/id/1-L.jpg"))
        sincroniza()
        val f = Cuenta.ficha(BOOKS.id, 13L)!!
        val r1 = Cuenta.recomienda(f, "  Te va a encantar ")
        val r2 = Cuenta.recomienda(f, "")
        sincroniza()
        assertNotEquals(r1.id, r2.id)
        val az = otra.coleccion("azabache")
        val o = JSONObject(az.nodo("recs~${r1.id}")!!.texto("o"))
        assertEquals(0L, az.nodo("recs~${r1.id}")!!.entero("b"))
        val leida = recFromJson(o)
        assertEquals(1, leida.kind); assertEquals(13L, leida.itemId); assertEquals("Rayuela", leida.name); assertEquals("Julio Cortázar", leida.creator)
        assertEquals("Te va a encantar", leida.note); assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", leida.cover); assertEquals(r1.id, leida.id)
        // Las claves del móvil, ni una más
        assertEquals(setOf("id", "kind", "item", "name", "cover", "creator", "note", "time"), o.keys().asSequence().toSet())
        // Lo de antes no se toca: las dos están
        assertEquals(2, az.vivos().count { it.ref.startsWith("recs~") })
        assertEquals(listOf(r2.id, r1.id), Cuenta.misRecs().map { it.id })
    }

    @Test fun loQueTeRecomiendaTuParejaYAMiLista() {
        // Lucía, desde su móvil, te recomienda dos libros
        val dune = JSONObject().put("id", 5L).put("kind", 1).put("item", 112L).put("name", "La sombra del viento").put("cover", "").put("creator", "Carlos Ruiz Zafón").put("note", "Barcelona").put("time", 1000L)
        val otro = JSONObject().put("id", 6L).put("kind", 0).put("item", 103L).put("name", "Animal Crossing").put("cover", "").put("creator", "").put("note", "").put("time", 2000L)
        lucia.coleccion("azabache").pon("recs~5", mapOf("o" to dune.toString(), "b" to 0L))
        lucia.coleccion("azabache").pon("recs~6", mapOf("o" to otro.toString(), "b" to 0L))
        sincroniza()
        assertEquals(ella, Cuenta.uidPareja())
        LaFamilia.ajena(ella, "azabache")
        sincroniza()
        val suyas = Cuenta.recsDe(ella)
        assertEquals(listOf(6L, 5L), suyas.map { it.id })
        assertEquals(listOf(6L, 5L), recsPendientes(suyas, emptySet()).map { it.id })
        // «A mi lista»: a tus pendientes de libros, con el número de su ficha (como recAdd del móvil)
        val (f, ya) = anadePendiente(BOOKS, 112L, "La sombra del viento", "Carlos Ruiz Zafón", "")
        assertFalse(ya); assertNotNull(f)
        Local.recHecha(5L)
        sincroniza()
        val n = otra.coleccion("libros").nodo(Cuenta.kidDe(Cuenta.claveLibro("La sombra del viento", "Carlos Ruiz Zafón")))!!
        assertEquals("pendiente", n.texto("estado")); assertEquals(112L, n.entero("azid")); assertEquals("Carlos Ruiz Zafón", n.texto("a"))
        assertNull("sin nota", n["nota"])
        // Lo hecho no vuelve a salir, y se recuerda en este ordenador (no en la cuenta)
        assertEquals(listOf(6L), recsPendientes(Cuenta.recsDe(ella), Local.recsHechas).map { it.id })
        Local.olvidaParaPruebas()
        assertEquals(setOf(5L), Local.recsHechas)
        assertTrue(otra.coleccion("azabache").vivos().none { it.ref.contains("recsdone") || it.ref.contains("local") })
        // Si ya lo tienes (por su número o por su nombre), no se duplica
        assertTrue(anadePendiente(BOOKS, 999L, "la sombra del viento", "", "").second)
        assertEquals(1, Cuenta.fichas(BOOKS.id).count { normName(it.nombre) == normName("La sombra del viento") })
        assertEquals(LocalDate.now().toEpochDay(), diaDeMs(System.currentTimeMillis()))
    }
}
