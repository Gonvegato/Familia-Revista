package com.gonzalo.shai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.comoTexto

/** Dónde se guarda lo de Shai: en la cuenta (la colección `shai`) o, en las pruebas, en memoria. */
interface Almacen {
    /** Los nodos vivos (sin los borrados): ref → campos. */
    fun nodos(): Map<String, Map<String, Any?>>
    fun nodo(ref: String): Map<String, Any?>? = nodos()[ref]
    fun pon(ref: String, campos: Map<String, Any?>)
    fun borra(ref: String)
}

/** La colección `shai` de tu cuenta (`/users/<uid>/familia/shai/<ref>`): se guarda aquí al momento y sube sola en cuanto hay red. */
class AlmacenCuenta : Almacen {
    private fun col() = LaFamilia.coleccion(Carreras.COLECCION)
    override fun nodos(): Map<String, Map<String, Any?>> = runCatching { col().vivos().associate { it.ref to it.campos } }.getOrDefault(emptyMap())
    override fun nodo(ref: String): Map<String, Any?>? = runCatching { col().nodo(ref)?.takeIf { !it.borrado }?.campos }.getOrNull()
    override fun pon(ref: String, campos: Map<String, Any?>) = col().cambia(ref, campos)
    override fun borra(ref: String) = col().borra(ref)
}

class AlmacenMemoria : Almacen {
    val datos = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any?>>()
    override fun nodos(): Map<String, Map<String, Any?>> = HashMap(datos)
    override fun pon(ref: String, campos: Map<String, Any?>) { datos[ref] = (datos[ref].orEmpty() + campos).filterValues { it != null && it != "" } }
    override fun borra(ref: String) { datos.remove(ref) }
}

/**
 * Tus carreras y lo que pasa en ellas, en tu cuenta (`nube/ESQUEMA.md`, colección `shai`):
 *  - `u<10 hex>`: una carrera (`o` = la carrera en JSON, [Universo.aJson]).
 *  - `e<10 hex>`: algo que pasa en una (`un` = la carrera, `o` = [Entrada.aJson]).
 *  - `gasto` y `ajustes`: lo gastado en la API y el tope ([Gasto]).
 * De cada cosa gana el último cambio, como en todo lo de la familia. Tu clave de la API no va aquí: va en `/claves` ([ClaveApi]).
 */
object Carreras {
    const val COLECCION = "shai"

    @Volatile var almacen: Almacen = AlmacenMemoria()

    /** Sube con cada cambio de aquí (las pantallas lo leen para volver a pintarse). */
    var version by mutableIntStateOf(0)
        private set

    fun avisa() { version++ }

    private fun lee() { version; runCatching { LaFamilia.cambios } }

    fun todas(): List<Universo> {
        lee()
        return almacen.nodos().filterKeys { it.startsWith("u") }.values.mapNotNull { Universo.deJson(it["o"].comoTexto()) }.sortedByDescending { it.cambiado }
    }

    fun de(id: String): Universo? { lee(); return Universo.deJson(almacen.nodo(id)?.get("o").comoTexto()) }

    fun guarda(u: Universo): Universo {
        val n = u.copy(cambiado = System.currentTimeMillis(), creado = u.creado.takeIf { it > 0 } ?: System.currentTimeMillis())
        almacen.pon(n.id, mapOf("o" to n.aJson()))
        avisa()
        return n
    }

    /** Borra una carrera y todo lo que pasó en ella. */
    fun borra(id: String) {
        entradasDe(id, todas = true).forEach { almacen.borra(it.id) }
        almacen.borra(id)
        avisa()
    }

    /** Lo que pasa en una carrera, de lo más nuevo a lo más viejo. Sin [todas], solo lo que ya ha salido. */
    fun entradasDe(universo: String, todas: Boolean = false, ahora: Long = System.currentTimeMillis()): List<Entrada> {
        lee()
        return almacen.nodos().filter { (k, v) -> k.startsWith("e") && v["un"].comoTexto() == universo }.values
            .mapNotNull { Entrada.deJson(it["o"].comoTexto()) }.filter { todas || it.visible(ahora) }
            .sortedWith(compareByDescending<Entrada> { maxOf(it.visibleDesde, it.creado) }.thenByDescending { it.paso })
    }

    fun entrada(id: String): Entrada? { lee(); return Entrada.deJson(almacen.nodo(id)?.get("o").comoTexto()) }

    fun guarda(e: Entrada): Entrada {
        almacen.pon(e.id, mapOf("un" to e.universo, "o" to e.aJson()))
        avisa()
        return e
    }

    /** Los partidos de una carrera, del primero al último. */
    fun partidos(universo: String): List<Pair<Entrada, Partido>> =
        entradasDe(universo, todas = true).mapNotNull { e -> e.partido?.let { e to it } }.sortedBy { it.first.paso }
}
