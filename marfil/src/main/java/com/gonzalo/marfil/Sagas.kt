package com.gonzalo.marfil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Una saga: libros juntos bajo un nombre, en su orden. No mueve nada de su sitio (cada libro sigue en su estante); solo los junta en las listas.
 * Los libros se apuntan por su clave (la misma en todos tus dispositivos), así que la saga viaja con tu cuenta.
 */
data class Saga(val id: String, val nombre: String, val claves: List<String>, val mod: Long = 0L) {
    fun aJson(): JSONObject = JSONObject().put("id", id).put("n", nombre).put("mod", mod).put("k", JSONArray().also { a -> claves.forEach { a.put(it) } })
    companion object {
        fun deJson(o: JSONObject): Saga? {
            val id = o.optString("id").ifBlank { return null }
            val k = o.optJSONArray("k") ?: JSONArray()
            return Saga(id, o.optString("n"), (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() }.distinct(), o.optLong("mod"))
        }
    }
}

/** Una fila de una lista de libros: un libro suelto o una saga con sus libros (los que entran en esa lista, en el orden de la saga). */
sealed class FilaLista {
    class Suelto(val libro: Libro, val saga: Saga? = null, val numero: Int = 0) : FilaLista()
    class DeSaga(val saga: Saga, val miembros: List<Libro>) : FilaLista()
}

object Sagas {
    /** Las sagas de libros de la cuenta (la colección `sagas`, con `tipo` = «libros»): las mismas en todos tus dispositivos. */
    val lista = mutableStateListOf<Saga>()
    /** (Solo para leer lo de antes en la migración.) */
    val borradas = HashMap<String, Long>()
    var cargada by mutableStateOf(false)
    private var appCtx: Context? = null
    private val principal by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private fun col() = com.gonzalo.familia.LaFamilia.coleccion("sagas")

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        Cuenta.cargar(c)
        col().alCambiar { principal.post { refresca() } }
        refresca()
        cargada = true
    }

    fun refresca() {
        val nuevas = col().vivos().filter { it.texto("tipo") == "libros" }
            .map { Saga(it.ref, it.texto("n"), it.lista("claves").map { k -> k.toString() }, it.m.values.maxOrNull() ?: 0L) }
            .sortedBy { it.nombre.lowercase() }
        if (nuevas != lista.toList()) { lista.clear(); lista.addAll(nuevas) }
        appCtx?.let { Widgets.pedir(it) }
    }

    private fun guarda(s: Saga) { col().cambia(s.id, mapOf("n" to s.nombre, "tipo" to "libros", "claves" to s.claves, "b" to 0L)) }

    fun por(id: String): Saga? = lista.firstOrNull { it.id == id }

    /** La saga a la que pertenece un libro (por su clave), si tiene. */
    fun de(clave: String): Saga? = lista.firstOrNull { clave in it.claves }

    /** Guarda una saga. Un libro solo está en una: se quita de las demás. */
    fun poner(s: Saga) {
        val nueva = s.copy(claves = s.claves.distinct())
        // Los libros de esta saga salen de las otras
        lista.toList().forEach { o -> if (o.id != nueva.id && o.claves.any { it in nueva.claves }) {
            val resto = o.claves.filter { it !in nueva.claves }
            if (resto.isEmpty()) col().borra(o.id) else guarda(o.copy(claves = resto))
        } }
        guarda(nueva)
        refresca()
    }

    fun crear(nombre: String, claves: List<String> = emptyList()): Saga = Saga(UUID.randomUUID().toString().replace("-", "").take(10), nombre.trim(), claves).also { poner(it) }

    fun quitar(id: String) { col().borra(id); refresca() }

    /** Pone un libro en una saga (al final) o, con [saga] null, lo saca de la que estuviera. */
    fun asignar(clave: String, saga: Saga?) {
        val actual = de(clave)
        if (actual?.id == saga?.id) return
        if (actual != null) { val r = actual.claves - clave; if (r.isEmpty()) quitar(actual.id) else poner(actual.copy(claves = r)) }
        if (saga != null) por(saga.id)?.let { poner(it.copy(claves = it.claves + clave)) }
    }

    /** (La migración: pone las sagas de antes con su hora.) */
    fun migra(sagas: List<Saga>, borr: Map<String, Long>) {
        sagas.forEach { s -> col().pon(s.id, mapOf("n" to s.nombre, "tipo" to "libros", "claves" to s.claves.distinct()), s.mod.coerceAtLeast(1L)) }
        borr.forEach { (id, t) -> col().pon(id, mapOf("b" to t, "tipo" to "libros"), t.coerceAtLeast(1L)) }
        refresca()
    }

    /** Los libros de una saga, en su orden (los que ya no están en la biblioteca se saltan). */
    fun libros(s: Saga, todos: List<Libro> = Biblioteca.libros.toList()): List<Libro> {
        val porClave = todos.associateBy { it.clave }
        return s.claves.mapNotNull { porClave[it] }
    }

    /**
     * Agrupa una lista de libros (ya ordenada) por sagas: cada saga sale una vez, donde está su primer libro de la lista, con los de la lista en el
     * orden de la saga. Una saga con un solo libro en la lista sale como libro suelto (con su saga y número, para no perder el dato).
     */
    fun agrupar(libros: List<Libro>, sagas: List<Saga> = lista.toList()): List<FilaLista> {
        val deClave = HashMap<String, Saga>().also { m -> sagas.forEach { s -> s.claves.forEach { m[it] = s } } }
        val enLista = libros.map { it.clave }.toSet()
        val vistas = HashSet<String>()
        val out = ArrayList<FilaLista>()
        for (l in libros) {
            val s = deClave[l.clave]
            if (s == null) { out += FilaLista.Suelto(l); continue }
            if (!vistas.add(s.id)) continue
            val miembros = s.claves.filter { it in enLista }.mapNotNull { k -> libros.firstOrNull { it.clave == k } }
            if (miembros.size >= 2) out += FilaLista.DeSaga(s, miembros)
            else out += FilaLista.Suelto(miembros.first(), s, s.claves.indexOf(miembros.first().clave) + 1)
        }
        return out
    }
}
