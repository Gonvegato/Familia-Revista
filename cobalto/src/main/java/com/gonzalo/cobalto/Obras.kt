package com.gonzalo.cobalto

import android.content.Context

/**
 * Lo de Cobalto sobre las obras de Escribir (el formato es el de Atrio, docs/escribir.md): en qué menú va cada una y lo que la
 * portada y los widgets necesitan saber. En la obra (`p`) Cobalto añade `est` («pausa» o «fin»; sin él, se está escribiendo);
 * `arch` = 1 (archivada) ya existía. Atrio del PC no mira `est`: no le molesta.
 */
enum class EstadoObra(val nombre: String, val plural: String) {
    ESCRIBIENDO("Escribiendo", "Escribiendo"), PAUSA("En pausa", "En pausa"), TERMINADA("Terminada", "Terminadas"), ARCHIVO("Archivada", "Archivo")
}

fun estadoDe(o: Doc): EstadoObra = when {
    o.b("arch") -> EstadoObra.ARCHIVO
    o.s("est") == "pausa" -> EstadoObra.PAUSA
    o.s("est") == "fin" -> EstadoObra.TERMINADA
    else -> EstadoObra.ESCRIBIENDO
}

/** Cambia el menú de una obra. */
fun Escritos.ponEstado(id: String, e: EstadoObra) {
    edita(id) {
        remove("arch"); remove("est")
        when (e) {
            EstadoObra.ARCHIVO -> put("arch", 1)
            EstadoObra.PAUSA -> put("est", "pausa")
            EstadoObra.TERMINADA -> put("est", "fin")
            EstadoObra.ESCRIBIENDO -> {}
        }
    }
}

/** Las obras de un menú, la última que se tocó primero. */
fun Escritos.obrasDe(e: EstadoObra): List<Doc> = obras().filter { estadoDe(it) == e }.sortedByDescending { ultimaEdicion(it.id) }

/** Cuándo se tocó por última vez una obra (su escena más reciente, o ella misma). */
fun Escritos.ultimaEdicion(pid: String): Long = (escenasDe(pid).maxOfOrNull { it.u } ?: 0L).coerceAtLeast(get(pid)?.u ?: 0L)

/** La obra en la que estás: la de la última escena que tocaste (de las que estás escribiendo), o la última editada. */
fun Escritos.obraActual(): Doc? {
    val e = ultimaEscena()?.let { get(it.pid) }?.takeIf { estadoDe(it) == EstadoObra.ESCRIBIENDO }
    return e ?: obrasDe(EstadoObra.ESCRIBIENDO).firstOrNull()
}

/** La última escena que tocaste de esa obra. */
fun Escritos.ultimaEscenaDe(pid: String): Doc? = escenasDe(pid).maxByOrNull { it.u }

/** «3 capítulos · 12 escenas». */
fun Escritos.cuentaObra(pid: String): String {
    val c = capitulos(pid).size; val s = escenasDe(pid).size
    return "$c ${if (c == 1) "capítulo" else "capítulos"} · $s ${if (s == 1) "escena" else "escenas"}"
}

/** Lo que dice debajo del título de una obra en una lista. */
fun metaObra(e: Escritos, o: Doc, conEstado: Boolean = false): String {
    val p = e.palabrasObra(o.id); val meta = o.i("meta")
    return listOfNotNull(
        o.s("genero").ifBlank { null },
        cifraE(p) + (if (meta > 0) " de ${cifraE(meta)} palabras" else " palabras"),
        if (conEstado) estadoDe(o).nombre else null
    ).joinToString(" · ")
}

/** Un párrafo de tu manuscrito que se lee bien suelto (para el widget de la frase): de qué escena es y el texto. */
class Frase(val escena: Doc, val obra: Doc, val texto: String)

/** Los párrafos de tus obras en marcha que se leen bien sueltos (ni una línea ni media página), sin títulos ni citas. */
fun Escritos.frases(): List<Frase> = obras().filter { estadoDe(it) != EstadoObra.ARCHIVO }.flatMap { o ->
    escenasDe(o.id).flatMap { s ->
        s.s("texto").split('\n').map { it.trim() }.filter { it.length in 60..320 && !it.startsWith("#") && !it.startsWith(">") && it != "***" }
            .map { Frase(s, o, it.replace("**", "").replace("*", "")) }
    }
}

/** Las palabras escritas cada día de los últimos [n] (el último, hoy) y el objetivo diario (la suma de las obras en marcha). */
class ResumenEscritura(val hoy: Int, val racha: Int, val diaria: Int, val dias: List<Int>, val total: Int)

fun Escritos.resumen(dias: Int = 14): ResumenEscritura {
    val activas = obrasDe(EstadoObra.ESCRIBIENDO)
    return ResumenEscritura(escritas(Escritos.hoy()), racha(), activas.sumOf { it.i("diaria") }, ultimosDias(dias).map { it.second }, activas.sumOf { palabrasObra(it.id) })
}

/** Lo de Cobalto que se guarda en el móvil y no viaja: por ahora, nada más que el último estado del estudio. */
object AjustesCobalto {
    private fun p(c: Context) = c.getSharedPreferences("cobalto_ajustes", Context.MODE_PRIVATE)
    /** La última obra abierta (para «Sigue escribiendo» cuando no hay escenas). */
    fun ultimaObra(c: Context): String = p(c).getString("ultimaObra", "").orEmpty()
    fun ponUltimaObra(c: Context, id: String) = p(c).edit().putString("ultimaObra", id).apply()
}
