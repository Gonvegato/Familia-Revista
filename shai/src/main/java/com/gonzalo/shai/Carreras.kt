package com.gonzalo.shai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.nucleo.comoTexto
import java.io.File

/** Dónde se guarda lo de FcFeed: en un archivo de este móvil ([AlmacenArchivo]) o, en las pruebas, en memoria. */
interface Almacen {
    /** Los nodos vivos (sin los borrados): ref → campos. */
    fun nodos(): Map<String, Map<String, Any?>>
    fun nodo(ref: String): Map<String, Any?>? = nodos()[ref]
    fun pon(ref: String, campos: Map<String, Any?>)
    fun borra(ref: String)
}

class AlmacenMemoria : Almacen {
    val datos = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any?>>()
    override fun nodos(): Map<String, Map<String, Any?>> = HashMap(datos)
    override fun pon(ref: String, campos: Map<String, Any?>) { datos[ref] = (datos[ref].orEmpty() + campos).filterValues { it != null && it != "" } }
    override fun borra(ref: String) { datos.remove(ref) }
}

/**
 * Un [Almacen] en un archivo de este móvil: tus carreras ([Carreras.real], `carreras.json`) y las del modo prueba (`prueba.json`).
 * Se escribe entero en cada cambio, primero a un archivo aparte y luego se cambia de nombre (si se corta a medias, queda el de antes).
 */
class AlmacenArchivo(private val archivo: File) : Almacen {
    private val datos = HashMap<String, Map<String, Any?>>()
    init {
        runCatching { Json.lee(archivo.readText()).comoMapa().forEach { (k, v) -> datos[k] = v.comoMapa() } }
    }
    @Synchronized override fun nodos(): Map<String, Map<String, Any?>> = HashMap(datos)
    @Synchronized override fun nodo(ref: String): Map<String, Any?>? = datos[ref]
    @Synchronized override fun pon(ref: String, campos: Map<String, Any?>) { datos[ref] = (datos[ref].orEmpty() + campos).filterValues { it != null && it != "" }; guarda() }
    @Synchronized override fun borra(ref: String) { datos.remove(ref); guarda() }
    @Synchronized fun vacia() { datos.clear(); guarda() }
    private fun guarda() {
        runCatching {
            archivo.parentFile?.mkdirs()
            val tmp = File(archivo.path + ".nuevo")
            tmp.writeText(Json.escribe(datos))
            if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
        }
    }
}

/**
 * Tus carreras y lo que pasa en ellas, en este móvil (`carreras.json`):
 *  - `u<10 hex>`: una carrera (`o` = la carrera en JSON, [Universo.aJson]).
 *  - `e<10 hex>`: algo que pasa en una (`un` = la carrera, `o` = [Entrada.aJson]).
 *  - `gasto` y `ajustes`: lo gastado en la API y el tope ([Gasto]).
 * Tu clave de la API no va aquí: va cifrada aparte ([ClaveApi]).
 */
object Carreras {
    /** El nombre de la colección de la cuenta de antes (solo para traerla una vez: [Migracion]). */
    const val COLECCION = "shai"

    @Volatile var almacen: Almacen = AlmacenMemoria()
    /** Las de verdad (aunque esté puesto el modo prueba, que cambia [almacen]). */
    @Volatile var real: Almacen = almacen

    /** Al arrancar: las carreras de este móvil. */
    fun abre(c: android.content.Context) {
        if (real is AlmacenArchivo) return
        val a = AlmacenArchivo(File(c.filesDir, "carreras.json"))
        real = a
        if (!Prueba.activo) almacen = a
    }

    /** Sube con cada cambio de aquí (las pantallas lo leen para volver a pintarse). */
    var version by mutableIntStateOf(0)
        private set

    fun avisa() { version++ }

    private fun lee() { version }

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
        almacen.borra("k$id")
        almacen.borra(id)
        avisa()
    }

    /** Lo que pasa en una carrera, de lo más nuevo a lo más viejo. Sin [todas], solo lo que ya ha salido. */
    fun entradasDe(universo: String, todas: Boolean = false, ahora: Long = System.currentTimeMillis()): List<Entrada> {
        lee()
        return almacen.nodos().filter { (k, v) -> k.startsWith("e") && v["un"].comoTexto() == universo }.values
            .mapNotNull { Entrada.deJson(it["o"].comoTexto()) }.filter { todas || it.visible(ahora) }
            .sortedWith(ORDEN.reversed())
    }

    /**
     * El orden de lo que pasa (de lo más viejo a lo más nuevo): por jornada y, dentro de cada una, por el momento en que salió (el partido,
     * y luego lo de entre partidos de esa jornada, aunque llegara más tarde por lotes).
     */
    val ORDEN: Comparator<Entrada> = compareBy<Entrada> { it.paso }.thenBy { maxOf(it.visibleDesde, it.creado) }.thenBy { it.id }

    fun entrada(id: String): Entrada? { lee(); return Entrada.deJson(almacen.nodo(id)?.get("o").comoTexto()) }

    fun guarda(e0: Entrada): Entrada {
        // Cada mensaje, con el id de la persona de su gente que lo escribe (si se sabe): así «mamá» y «Marisa» van al mismo chat
        val e = if (e0.dms.any { !it.mio && it.pid.isBlank() }) de(e0.universo)?.let { u ->
            e0.copy(dms = e0.dms.map { d -> if (d.mio || d.pid.isNotBlank()) d else Gente.quien(Gente.todos(u), d.de)?.let { p -> d.copy(pid = p.id) } ?: d })
        } ?: e0 else e0
        almacen.pon(e.id, mapOf("un" to e.universo, "o" to e.aJson()))
        avisa()
        return e
    }

    /** Las plantillas del modo prueba que ya han salido en una carrera (para no repetirlas): en su nodo `k<id>`. */
    fun usadas(universo: String): Set<String> = almacen.nodo("k$universo")?.get("us").comoTexto().split('|').filter { it.isNotBlank() }.toSet()
    fun apunta(universo: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        almacen.pon("k$universo", mapOf("us" to (usadas(universo) + ids).joinToString("|")))
    }
    fun olvida(universo: String, ids: Collection<String>) {
        almacen.pon("k$universo", mapOf("us" to (usadas(universo) - ids.toSet()).joinToString("|").ifEmpty { "|" }))
    }

    /** Los partidos de una carrera, del primero al último. */
    fun partidos(universo: String): List<Pair<Entrada, Partido>> =
        entradasDe(universo, todas = true).mapNotNull { e -> e.partido?.let { e to it } }.sortedBy { it.first.paso }
}
