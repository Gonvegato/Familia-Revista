package com.gonzalo.cobalto

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Las copias de seguridad en el dispositivo (EscribirCopias.kt: el mismo archivo en el móvil, comprimidas, y en el ordenador, en JSON; esta
 * prueba la pasan los dos): solo si algo ha cambiado, las últimas N y no más de lo que se deja, y sacar una obra como obra nueva.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscribirCopiasTest {
    @get:Rule val tmp = TemporaryFolder()
    private var ahora = 1_790_000_000_000L

    private fun copias(guarda: Int = 40, comprimidas: Boolean = true, tope: Long = Long.MAX_VALUE) = CopiasEscritos({ File(tmp.root, "copias") }, guarda, comprimidas, tope)
    private fun haz(c: CopiasEscritos, e: Escritos, forzar: Boolean = false): File? { ahora += 1000; return c.haz(e, forzar, ahora) }

    @Test fun soloHaceUnaSiAlgoHaCambiadoAunqueSeCierreLaApp() {
        val e = Escritos(File(tmp.root, "e"))
        val c = copias()
        assertNull("sin nada escrito, nada que copiar", haz(c, e))
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago") }
        val cap = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", cap.id); put("texto", "Llovía sobre el lago.") }
        val f = haz(c, e)!!
        assertTrue(f.name.matches(Regex("copia-\\d{4}-\\d{2}-\\d{2}_\\d{6}-1obras-4pal\\.json\\.gz")))
        // Sin cambios no hay otra, tampoco con la app vuelta a abrir (otro CopiasEscritos, la misma carpeta)
        assertNull(haz(c, e)); assertNull(haz(copias(), e))
        // A mano, sí
        assertNotNull(haz(c, e, forzar = true))
        // Al cambiar algo, otra; también al borrar
        e.edita(o.id) { put("sinopsis", "Vuelve a la casa.") }
        assertNotNull(haz(c, e))
        e.borra(cap.id)
        assertNotNull(haz(c, e))
        val l = c.lista()
        assertEquals(4, l.size)
        assertTrue("la más nueva primero", l.zipWithNext().all { (a, b) -> a.hecha >= b.hecha })
        assertEquals(1, l[0].obras); assertEquals(0, l[0].palabras); assertEquals(4, l.last().palabras)
        // Lleva todos los documentos, también los de la papelera, y se lee (comprimida)
        val docs = c.lee(l[0].archivo)
        assertEquals(e.volcado().length(), docs.size)
        assertTrue(docs.any { it.optString("id") == cap.id && it.optInt("d") == 1 })
        // Y sin comprimir es el JSON de las del ordenador y Atrio: {app, hecha, docs}
        val j = JSONObject(String(CopiasEscritos.json(l[0].archivo)))
        assertEquals("cobalto", j.getString("app")); assertEquals(docs.size, j.getJSONArray("docs").length())
        // (lo que quedó a medias nunca pasa por una copia)
        assertTrue(c.dir().listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun seQuedanLasUltimasYNoMasDeLoQueSeDeja() {
        val e = Escritos(File(tmp.root, "e"))
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Obra") }
        val c = copias(guarda = 5, comprimidas = false)
        repeat(8) { i -> e.edita(o.id) { put("sinopsis", "Versión $i") }; assertNotNull(haz(c, e)) }
        assertEquals(5, c.lista().size)
        assertTrue(c.lista().first().archivo.name.endsWith(".json"))
        // Con un tope de bytes, se van las más viejas, pero siempre quedan las tres últimas
        val chica = copias(guarda = 40, comprimidas = false, tope = 1)
        e.edita(o.id) { put("sinopsis", "Otra") }; haz(chica, e, forzar = true)
        assertEquals(3, chica.lista().size)
    }

    @Test fun sacarUnaObraDeUnaCopiaLaPoneComoObraNuevaConTodoLoSuyo() {
        val e = Escritos(File(tmp.root, "e"))
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La casa del lago"); put("autor", "Gonzalo"); put("meta", 80000); put("gOn", 1); put("g1", 1) }
        val parte = e.nuevo(Escritos.T_PARTE, o.id) { put("titulo", "Invierno") }
        val cap = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "La llegada"); put("aid", parte.id) }
        val marta = e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Marta"); put("vin", JSONArray().put(cap.id)) }
        val casa = e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "lugar"); put("nombre", "La casa") }
        val esc = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", cap.id); put("texto", "Llovía sobre el lago."); put("pov", marta.id); put("lugar", casa.id) }
        val otra = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", cap.id); put("texto", "Esta se borró antes de la copia.") }
        e.nuevo(Escritos.T_REL, o.id) { put("a", marta.id); put("b", casa.id); put("titulo", "hogar") }
        e.nuevo(Escritos.T_NOTA, o.id) { put("titulo", "Idea"); put("vin", JSONArray().put(parte.id).put(cap.id)) }
        val rama = e.nuevo(Escritos.T_RAMA, o.id) { put("titulo", "Lo que hizo Lucas") }
        e.nuevo(Escritos.T_HITO, o.id) { put("titulo", "Llega"); put("eid", esc.id); put("rama", rama.id) }
        e.nuevo(Escritos.T_BORRADOR, o.id) { put("eid", esc.id); put("nombre", "Otra versión"); put("texto", "Llovía.") }
        e.escribe(esc.id, "Llovía sobre el lago, mucho.")
        e.borra(otra.id)
        val c = copias()
        val docs = c.lee(haz(c, e)!!)
        val antes = e.volcado().toString()
        // Ya en la copia: la obra, con sus 4 palabras… (las del día no se sacan: ya están contadas)
        assertEquals(listOf("La casa del lago"), CopiasEscritos.obras(docs).map { it.s("titulo") })
        assertEquals(5 to 1, CopiasEscritos.resumen(docs, o.id))
        val n = CopiasEscritos.recupera(e, docs, o.id, ahora = ahora)!!
        assertEquals("La casa del lago (copia)", n.s("titulo")); assertEquals("Gonzalo", n.s("autor")); assertEquals(80000, n.i("meta")); assertEquals(1, n.i("g1"))
        assertTrue(n.id != o.id)
        // Lo de antes, intacto
        val ahoraTodo = e.volcado().let { a -> (0 until a.length()).associate { a.getJSONObject(it).getString("id") to a.getJSONObject(it).toString() } }
        JSONArray(antes).let { a -> (0 until a.length()).forEach { i -> val d = a.getJSONObject(i); assertEquals(d.toString(), ahoraTodo[d.getString("id")]) } }
        assertEquals("La casa del lago", e.get(o.id)!!.s("titulo"))
        // Lo nuevo: lo mismo que había (sin lo borrado ni las palabras del día), todo con ids nuevos y enlazado entre sí
        val nuevos = e.todos().filter { it.pid == n.id }
        assertEquals(listOf("a", "b", "c", "e", "f", "f", "h", "n", "p", "r", "v"), nuevos.map { it.t }.sorted())
        val idsNuevos = nuevos.map { it.id }.toSet()
        assertTrue(idsNuevos.none { e.todos().any { v -> v.pid == o.id && v.id == it } })
        val c2 = nuevos.single { it.t == "c" }; val p2 = nuevos.single { it.t == "a" }; val e2 = nuevos.single { it.t == "e" }
        val m2 = nuevos.single { it.t == "f" && it.s("nombre") == "Marta" }; val l2 = nuevos.single { it.t == "f" && it.s("nombre") == "La casa" }
        assertEquals(p2.id, c2.s("aid")); assertEquals(c2.id, e2.s("cid")); assertEquals(m2.id, e2.s("pov")); assertEquals(l2.id, e2.s("lugar"))
        assertEquals("Llovía sobre el lago, mucho.", e2.s("texto"))
        assertEquals(listOf(c2.id), m2.lista("vin"))
        assertEquals(listOf(p2.id, c2.id), nuevos.single { it.t == "n" }.lista("vin"))
        nuevos.single { it.t == "v" }.let { assertEquals(m2.id, it.s("a")); assertEquals(l2.id, it.s("b")) }
        nuevos.single { it.t == "h" }.let { assertEquals(e2.id, it.s("eid")); assertEquals(nuevos.single { r -> r.t == "r" }.id, it.s("rama")) }
        assertEquals(e2.id, nuevos.single { it.t == "b" }.s("eid"))
        assertEquals(5, e.palabrasObra(n.id))
        // Ninguno apunta a lo de la obra de antes
        val viejos = JSONArray(antes).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }
        for (d in nuevos) for (k in listOf("aid", "cid", "pov", "lugar", "a", "b", "eid", "rama")) assertFalse("$k de ${d.t}", d.s(k) in viejos)
        // Y se sube como todo (está pendiente, parte de cero)
        assertTrue(nuevos.all { d -> e.pendientes().any { it.id == d.id } && d.o.optLong("pu") == 0L })
    }

    @Test fun deUnaObraQueEstabaEnLaPapeleraSaleConLoQueSeFueConElla() {
        val e = Escritos(File(tmp.root, "e"))
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Cuentos") }
        val cap = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "El farero") }
        val antes = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", cap.id); put("texto", "Borrada antes, a mano.") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", cap.id); put("texto", "Se fue con la obra.") }
        e.borra(antes.id)
        e.borra(o.id)
        val c = copias()
        val docs = c.lee(haz(c, e)!!)
        assertTrue(CopiasEscritos.obras(docs).single().borrado)
        val n = CopiasEscritos.recupera(e, docs, o.id)!!
        assertEquals(listOf("Se fue con la obra."), e.escenasDe(n.id).map { it.s("texto") })
        assertNull("la de antes sigue en la papelera", e.get(o.id))
    }
}
