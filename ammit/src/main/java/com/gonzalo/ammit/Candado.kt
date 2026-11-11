package com.gonzalo.ammit

import android.content.Context
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Nodo
import org.json.JSONObject
import java.time.LocalDate

/**
 * El candado de Ammit: una vez encendida, solo se suelta cuando, desde que la encendiste, has hecho una de estas dos cosas (en cualquier
 * dispositivo, porque se mira en la cuenta de la familia):
 *  - escrito **300 palabras** en Thot, o
 *  - leído **un capítulo** en Seshat.
 *
 * Al encender se apunta cómo estaba todo ([marcar]); luego se compara con la cuenta:
 *  - Palabras: Thot sube, por dispositivo, las palabras de cada día (`palabras/<dispositivo>`, campos `dAAAA-MM-DD`). Lo escrito desde que
 *    la encendiste = lo de los días desde ese día, en todos los dispositivos, menos lo que ya había ese día al encenderla.
 *  - Capítulo: Seshat guarda por libro hasta dónde has llegado de verdad (`lectura/<libro>`: `bestLabel`, el capítulo, y `bestFrac`, el
 *    punto; solo avanza leyendo, no saltando con la barra) y el tiempo leído por día (`tiempo/<libro>~<dispositivo>`). Cuenta como capítulo
 *    leído pasar a otro capítulo más adelante en un libro habiendo leído en él al menos [MINUTOS] minutos desde que la encendiste; y,
 *    claro, terminar un libro (`libros/<libro>.fin`).
 *
 * Sin la cuenta no se puede comprobar, así que no se suelta (la cuenta se pone en los ajustes de Ammit).
 */
object Candado {
    const val PALABRAS = 300
    const val MINUTOS = 5

    /** Cómo vas. [listo]: ya hay con qué comparar (si no, todavía se está mirando la cuenta). */
    data class Progreso(
        val conCuenta: Boolean,
        val listo: Boolean,
        val palabras: Int,
        val capitulo: Boolean,
        /** El libro que más has leído desde que la encendiste y los minutos. */
        val libro: String = "",
        val minutos: Int = 0,
    ) {
        val puede: Boolean get() = conCuenta && listo && (palabras >= PALABRAS || capitulo)
    }

    private fun prefs(c: Context) = c.getSharedPreferences("ammit_candado", Context.MODE_PRIVATE)

    private val COLECCIONES = listOf("palabras", "tiempo", "lectura", "libros")

    private fun dentro(): Boolean = runCatching { LaFamilia.familia.sesion.dentro }.getOrDefault(false)

    /** Abre las colecciones que mira el candado (para que se bajen y se mantengan al día). */
    fun abrir() { if (dentro()) COLECCIONES.forEach { runCatching { LaFamilia.coleccion(it) } } }

    /** Al encender: apunta el momento y, en cuanto la cuenta esté al día, cómo estaba todo. */
    fun marcar(c: Context) {
        val ahora = System.currentTimeMillis()
        prefs(c).edit().clear().putLong("desde", ahora).putString("dia", LocalDate.now().toString()).apply()
        Thread { runCatching { sincronizar(); tomarBase(c) } }.start()
    }

    /** Baja lo último de la cuenta (bloquea: fuera del hilo principal). */
    fun sincronizar(): Boolean {
        if (!dentro()) return false
        abrir()
        return runCatching { LaFamilia.sincronizaYa().ok }.getOrDefault(false)
    }

    /** Apunta cómo estaba todo al encender (una vez). */
    @Synchronized fun tomarBase(c: Context) {
        val p = prefs(c)
        if (p.contains("base") || !dentro()) return
        if (!p.contains("desde")) p.edit().putLong("desde", System.currentTimeMillis()).putString("dia", LocalDate.now().toString()).apply()
        val dia = p.getString("dia", null) ?: LocalDate.now().toString()
        abrir()
        p.edit().putString("base", base(dia, col("palabras"), col("tiempo"), col("lectura")).toString()).apply()
    }

    /** Cómo estaba todo (lo que se compara luego). */
    internal fun base(dia: String, palabrasN: List<Nodo>, tiempoN: List<Nodo>, lecturaN: List<Nodo>): JSONObject {
        val lect = JSONObject()
        lecturaN.forEach { n -> lect.put(n.ref, JSONObject().put("l", n.texto("bestLabel")).put("f", n.decimal("bestFrac"))) }
        return JSONObject()
            .put("palabras", palabrasDesde(palabrasN, dia))
            .put("tiempo", JSONObject(tiempoDesde(tiempoN, lecturaN, dia).mapValues { it.value }))
            .put("lectura", lect)
    }

    /** Las palabras escritas en Thot desde [dia] (incluido), sumando todos los dispositivos. */
    private fun palabrasDesde(dia: String): Long = palabrasDesde(col("palabras"), dia)
    internal fun palabrasDesde(palabras: List<Nodo>, dia: String): Long = palabras.sumOf { n -> sumaDias(n, dia) }

    private fun col(nombre: String): List<Nodo> = runCatching { LaFamilia.coleccion(nombre).vivos() }.getOrDefault(emptyList())

    /** El tiempo leído en Seshat desde [dia] (incluido), por libro (ms), sumando dispositivos. */
    private fun tiempoDesde(dia: String): Map<String, Long> = tiempoDesde(col("tiempo"), col("lectura"), dia)
    internal fun tiempoDesde(tiempo: List<Nodo>, lectura: List<Nodo>, dia: String): Map<String, Long> {
        val r = HashMap<String, Long>()
        // (en «tiempo» también van las horas de juegos de Bastet y lo visto en Hathor: solo cuenta lo de los libros de Seshat)
        val libros = lectura.map { it.ref }.toSet()
        tiempo.forEach { n ->
            val libro = n.texto("ref").ifBlank { n.ref.substringBefore('~') }
            if (libro !in libros && !libro.startsWith("escrito_")) return@forEach
            r[libro] = (r[libro] ?: 0L) + sumaDias(n, dia)
        }
        return r
    }

    /** Lo de los campos `dAAAA-MM-DD` de [n] desde [dia] (incluido). */
    private fun sumaDias(n: Nodo, dia: String): Long = n.campos.entries.sumOf { (k, _) ->
        if (k.length == 11 && k[0] == 'd' && k.substring(1) >= dia) n.entero(k) else 0L
    }

    /** Cómo vas ahora, con lo que hay ya bajado de la cuenta (no bloquea: no va a la red). */
    fun progreso(c: Context): Progreso {
        if (!dentro()) return Progreso(conCuenta = false, listo = false, palabras = 0, capitulo = false)
        val p = prefs(c)
        // (encendida antes de existir el candado: se empieza a contar desde ahora)
        if (!p.contains("desde")) p.edit().putLong("desde", System.currentTimeMillis()).putString("dia", LocalDate.now().toString()).apply()
        val baseTxt = p.getString("base", null) ?: run { pideBase(c); return Progreso(true, false, 0, false) }
        val base = runCatching { JSONObject(baseTxt) }.getOrNull() ?: return Progreso(true, false, 0, false)
        val dia = p.getString("dia", null) ?: LocalDate.now().toString()
        val desde = p.getLong("desde", 0L)
        abrir()
        return calcula(base, dia, desde, col("palabras"), col("tiempo"), col("lectura"), col("libros"))
    }

    /** La cuenta en sí, con las colecciones ya leídas (para las pruebas). */
    internal fun calcula(base: JSONObject, dia: String, desde: Long, palabrasN: List<Nodo>, tiempoN: List<Nodo>, lecturaN: List<Nodo>, librosN: List<Nodo>): Progreso {
        val palabras = (palabrasDesde(palabrasN, dia) - base.optLong("palabras")).coerceAtLeast(0L).toInt()
        // Lo leído desde que la encendiste, por libro
        val bt = base.optJSONObject("tiempo") ?: JSONObject()
        val leido = tiempoDesde(tiempoN, lecturaN, dia).mapValues { (k, v) -> (v - bt.optLong(k)).coerceAtLeast(0L) }
        val (masLibro, masMs) = leido.maxByOrNull { it.value }?.toPair() ?: ("" to 0L)
        // Un capítulo: en algún libro, el sitio de verdad ha pasado a otro capítulo más adelante, con tiempo leído en él
        val bl = base.optJSONObject("lectura") ?: JSONObject()
        val capituloNuevo = lecturaN.any { n ->
            val antes = bl.optJSONObject(n.ref)
            val label = n.texto("bestLabel"); val frac = n.decimal("bestFrac")
            val otro = label.isNotBlank() && label != (antes?.optString("l") ?: "")
            val adelante = frac > (antes?.optDouble("f", 0.0) ?: 0.0) + 0.0005
            otro && adelante && (leido[n.ref] ?: 0L) >= MINUTOS * 60_000L
        }
        // …o un libro terminado
        val libroTerminado = librosN.any { n -> n.lista("fin").any { (it as? Number)?.toLong()?.let { t -> t > desde } == true } }
        val titulo = if (masLibro.isBlank()) "" else (librosN.firstOrNull { it.ref == masLibro }?.texto("t").orEmpty()
            .ifBlank { tiempoN.firstOrNull { it.ref.startsWith("$masLibro~") }?.texto("t").orEmpty() })
        return Progreso(true, true, palabras, capituloNuevo || libroTerminado, titulo, (masMs / 60_000L).toInt())
    }

    private val pidiendo = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Sin marca todavía (la cuenta no estaba al encender, o se encendió antes de existir el candado): se toma en cuanto se pueda. */
    private fun pideBase(c: Context) {
        if (!pidiendo.compareAndSet(false, true)) return
        Thread { try { runCatching { sincronizar(); tomarBase(c) } } finally { pidiendo.set(false) } }.start()
    }

    /** ¿Se puede soltar ya? (con lo que hay bajado; para comprobarlo de verdad, [sincronizar] antes). */
    fun puede(c: Context): Boolean = progreso(c).puede

    /** Al apagar: se olvida la marca. */
    fun olvidar(c: Context) { prefs(c).edit().clear().apply() }
}
