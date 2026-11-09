package com.gonzalo.hiedra

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * El reto del año de Bastet: cuántos juegos quieres TERMINAR (pasar) este año. Va en tu cuenta (`ajustes/juego_reto`: `ano`, el reto, y
 * `anio`, el año en que se puso), así sale igual en tus otros Bastet; sin cuenta, en este móvil. Manda el último cambio (por campo, como todo).
 */
object RetoJuegos {
    private const val NODO = "juego_reto"
    private fun p(c: Context) = c.getSharedPreferences("hiedra_reto", Context.MODE_PRIVATE)
    private fun nodo() = runCatching { com.gonzalo.familia.LaFamilia.coleccion("ajustes").nodo(NODO) }.getOrNull()

    /** El reto (0 = ninguno). */
    fun meta(c: Context): Int = nodo()?.takeIf { !it.borrado && it.campos.isNotEmpty() }?.entero("ano")?.toInt()?.coerceIn(0, 999)
        ?: p(c).getInt("ano", 0)

    /** Cambias el reto aquí: se guarda en el móvil y va a la cuenta. */
    fun poner(c: Context, n: Int) {
        val v = n.coerceIn(0, 999)
        p(c).edit().putInt("ano", v).apply()
        runCatching { com.gonzalo.familia.LaFamilia.coleccion("ajustes").cambia(NODO, mapOf("ano" to v.toLong(), "anio" to LocalDate.now().year.toLong())) }
    }
}

/** El año de una fecha (ms), con la hora de este móvil. */
fun anoDe(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

/** Los juegos que has terminado (pasado) en [ano]: los que están en Pasados con la fecha de ese año. */
fun terminadosEn(juegos: List<Juego>, ano: Int): List<Juego> =
    juegos.filter { it.estado == Estado.PASADO && it.finalizado > 0 && anoDe(it.finalizado) == ano }.sortedByDescending { it.finalizado }

/**
 * Cómo vas con el reto del año: [diferencia] es lo terminado menos lo que tocaría a estas alturas (el reto repartido por igual entre los
 * días del año, contando hoy y redondeando hacia abajo); [quedan], los que faltan; [dias], los días que quedan (con hoy). Como en Seshat.
 */
class RitmoReto(val hechos: Int, val meta: Int, val diferencia: Int, val quedan: Int, val dias: Int) {
    val cumplido: Boolean get() = meta in 1..hechos
    /** «VAS 2 POR DETRÁS · TE QUEDAN 17 EN 52 DÍAS», «VAS AL DÍA · …», «¡RETO CUMPLIDO!». */
    fun texto(): String {
        if (cumplido) return if (hechos > meta) "¡RETO CUMPLIDO! Y ${hechos - meta} DE PROPINA" else "¡RETO CUMPLIDO!"
        val vas = when {
            diferencia > 0 -> "VAS $diferencia POR DELANTE"
            diferencia < 0 -> "VAS ${-diferencia} POR DETRÁS"
            else -> "VAS AL DÍA"
        }
        return "$vas · ${if (quedan == 1) "TE QUEDA 1" else "TE QUEDAN $quedan"} EN $dias ${if (dias == 1) "DÍA" else "DÍAS"}"
    }
}

fun ritmoReto(meta: Int, hechos: Int, hoy: LocalDate = LocalDate.now()): RitmoReto {
    val diasAno = hoy.lengthOfYear()
    val esperado = if (meta <= 0) 0 else (meta.toLong() * hoy.dayOfYear / diasAno).toInt()
    return RitmoReto(hechos, meta, hechos - esperado, (meta - hechos).coerceAtLeast(0), diasAno - hoy.dayOfYear + 1)
}
