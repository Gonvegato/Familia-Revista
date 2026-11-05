package com.gonzalo.hiedra

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
 * Una saga de juegos: juegos juntos bajo un nombre, en su orden. No mueve nada de su sitio (cada juego sigue en su sección); solo los junta en las listas.
 * Los juegos se apuntan por su id (el mismo en todos tus dispositivos), así que la saga viaja con tu cuenta.
 */
data class Saga(val id: String, val nombre: String, val juegos: List<String>, val mod: Long = 0L) {
    fun aJson(): JSONObject = JSONObject().put("id", id).put("n", nombre).put("mod", mod).put("j", JSONArray().also { a -> juegos.forEach { a.put(it) } })
    companion object {
        fun deJson(o: JSONObject): Saga? {
            val id = o.optString("id").ifBlank { return null }
            val k = o.optJSONArray("j") ?: JSONArray()
            return Saga(id, o.optString("n"), (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() }.distinct(), o.optLong("mod"))
        }
    }
}

/** Una fila de una lista de juegos: un juego suelto o una saga con sus juegos (los que entran en esa lista, en el orden de la saga). */
sealed class FilaLista {
    class Suelto(val juego: Juego, val saga: Saga? = null, val numero: Int = 0) : FilaLista()
    class DeSaga(val saga: Saga, val miembros: List<Juego>) : FilaLista()
}

object Sagas {
    /** Las sagas de juegos de la cuenta (la colección `sagas`, con `tipo` = «juegos»): las mismas en todos tus dispositivos. */
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
        val nuevas = col().vivos().filter { it.texto("tipo") == "juegos" }
            .map { Saga(it.ref, it.texto("n"), it.lista("refs").map { k -> k.toString() }, it.m.values.maxOrNull() ?: 0L) }
            .sortedBy { it.nombre.lowercase() }
        if (nuevas != lista.toList()) { lista.clear(); lista.addAll(nuevas) }
        appCtx?.let { Widgets.pedir(it) }
    }

    private fun guarda(s: Saga) { col().cambia(s.id, mapOf("n" to s.nombre, "tipo" to "juegos", "refs" to s.juegos, "b" to 0L)) }

    fun por(id: String): Saga? = lista.firstOrNull { it.id == id }

    /** La saga a la que pertenece un juego (por su id), si tiene. */
    fun de(id: String): Saga? = lista.firstOrNull { id in it.juegos }
    fun de(j: Juego): Saga? = de(j.id)

    /** Guarda una saga. Un juego solo está en una: se quita de las demás. */
    fun poner(s: Saga) {
        val nueva = s.copy(juegos = s.juegos.distinct())
        lista.toList().forEach { o -> if (o.id != nueva.id && o.juegos.any { it in nueva.juegos }) {
            val resto = o.juegos.filter { it !in nueva.juegos }
            if (resto.isEmpty()) col().borra(o.id) else guarda(o.copy(juegos = resto))
        } }
        guarda(nueva)
        refresca()
    }

    fun crear(nombre: String, juegos: List<String> = emptyList()): Saga = Saga(UUID.randomUUID().toString().replace("-", "").take(10), nombre.trim(), juegos).also { poner(it) }

    fun quitar(id: String) { col().borra(id); refresca() }

    /** Pone un juego en una saga (al final) o, con [saga] null, lo saca de la que estuviera. */
    fun asignar(juegoId: String, saga: Saga?) {
        val actual = de(juegoId)
        if (actual?.id == saga?.id) return
        if (actual != null) { val r = actual.juegos - juegoId; if (r.isEmpty()) quitar(actual.id) else poner(actual.copy(juegos = r)) }
        if (saga != null) por(saga.id)?.let { poner(it.copy(juegos = it.juegos + juegoId)) }
    }

    /** (La migración: pone las sagas de antes con su hora.) */
    fun migra(sagas: List<Saga>, borr: Map<String, Long>) {
        sagas.forEach { s -> col().pon(s.id, mapOf("n" to s.nombre, "tipo" to "juegos", "refs" to s.juegos.distinct()), s.mod.coerceAtLeast(1L)) }
        borr.forEach { (id, t) -> col().pon(id, mapOf("b" to t, "tipo" to "juegos"), t.coerceAtLeast(1L)) }
        refresca()
    }

    /** Los juegos de una saga, en su orden (los que ya no están en la biblioteca se saltan). */
    fun juegos(s: Saga, todos: List<Juego> = Biblioteca.juegos.toList()): List<Juego> {
        val porId = todos.associateBy { it.id }
        return s.juegos.mapNotNull { porId[it] }
    }

    /**
     * Agrupa una lista de juegos (ya ordenada) por sagas: cada saga sale una vez, donde está su primer juego de la lista, con los de la lista en el
     * orden de la saga. Una saga con un solo juego en la lista sale como juego suelto (con su saga y número, para no perder el dato).
     */
    fun agrupar(juegos: List<Juego>, sagas: List<Saga> = lista.toList()): List<FilaLista> {
        val deId = HashMap<String, Saga>().also { m -> sagas.forEach { s -> s.juegos.forEach { m[it] = s } } }
        val vistas = HashSet<String>()
        val out = ArrayList<FilaLista>()
        for (j in juegos) {
            val s = deId[j.id]
            if (s == null) { out += FilaLista.Suelto(j); continue }
            if (!vistas.add(s.id)) continue
            val miembros = s.juegos.mapNotNull { id -> juegos.firstOrNull { it.id == id } }
            if (miembros.size >= 2) out += FilaLista.DeSaga(s, miembros)
            else out += FilaLista.Suelto(miembros.first(), s, s.juegos.indexOf(miembros.first().id) + 1)
        }
        return out
    }
}
