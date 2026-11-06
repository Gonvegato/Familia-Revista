package com.gonzalo.azabache.pc

import java.time.LocalDate

/*
 * «Sesiones»: cómo has jugado o leído una cosa, día a día (lo que cuentan Hiedra y Marfil en `tiempo/<ref>~<fuente>`, sumando los
 * dispositivos). Se resume en barras —por día si fue cosa de pocas semanas, por semana o por mes si fue más larga—, los días que le
 * dedicaste, la sesión más larga y la última. Es lo mismo que en el Azabache del móvil (`Sesiones.kt`, con las mismas pruebas).
 */

/**
 * [barras]: lo de cada día, semana o mes ([unidad]: 1, 7 o 30), del más antiguo al más nuevo; [desde] es el primer día que cubre la primera
 * barra y [hasta] el último día que cuenta. Los días son días desde 1970; el tiempo, en milisegundos.
 */
class Sesiones(
    val dias: Int, val totalMs: Long, val masLargaMs: Long, val diaMasLarga: Long, val primera: Long, val ultima: Long,
    val barras: List<Long>, val unidad: Int, val desde: Long, val hasta: Long
)

/** El lunes de la semana de un día (el 1 de enero de 1970 fue jueves). */
private fun lunesDe(dia: Long): Long = dia - Math.floorMod(dia + 3, 7L)

/**
 * Las sesiones de una cosa a partir de lo de cada día ([porDia]: día → ms; un día cuenta desde un minuto). [fin]: el día en que la
 * terminaste (0 si sigue en curso: entonces las barras llegan hasta hoy). Null si no hay ningún día con tiempo.
 */
fun sesionesDe(porDia: Map<Long, Long>, hoy: Long, fin: Long = 0L): Sesiones? {
    val d = porDia.filterValues { it >= 60_000L }
    if (d.isEmpty()) return null
    val primera = d.keys.min()
    val ultima = d.keys.max()
    val tope = maxOf(ultima, if (fin > 0L) fin else hoy)
    // La más larga: la de más tiempo (con empate, la más antigua)
    val mas = d.entries.sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key }).first()
    val tramo = tope - primera + 1
    val barras: List<Long>; val unidad: Int; val desde: Long
    when {
        tramo <= 21 -> {
            // Por día: al menos dos semanas, para que se vea el ritmo
            val n = maxOf(14L, tramo)
            desde = tope - n + 1; unidad = 1
            barras = (0 until n).map { d[desde + it] ?: 0L }
        }
        tramo <= 20 * 7 -> {
            // Por semana (de lunes a domingo): al menos ocho
            val ultimoLunes = lunesDe(tope)
            desde = minOf(lunesDe(primera), ultimoLunes - 7 * 7); unidad = 7
            val n = ((ultimoLunes - desde) / 7 + 1).toInt()
            barras = (0 until n).map { w -> (0 until 7).sumOf { d[desde + w * 7 + it] ?: 0L } }
        }
        else -> {
            // Por mes: como mucho los dos últimos años
            val ultimoMes = LocalDate.ofEpochDay(tope).withDayOfMonth(1)
            var ini = LocalDate.ofEpochDay(primera).withDayOfMonth(1)
            if (ini.isBefore(ultimoMes.minusMonths(23))) ini = ultimoMes.minusMonths(23)
            if (ini.isAfter(ultimoMes.minusMonths(5))) ini = ultimoMes.minusMonths(5)
            desde = ini.toEpochDay(); unidad = 30
            val meses = generateSequence(ini) { it.plusMonths(1) }.takeWhile { !it.isAfter(ultimoMes) }.toList()
            barras = meses.map { m -> val a = m.toEpochDay(); val b = m.plusMonths(1).toEpochDay(); d.entries.sumOf { (k, v) -> if (k in a until b) v else 0L } }
        }
    }
    return Sesiones(d.size, d.values.sum(), mas.value, mas.key, primera, ultima, barras, unidad, desde, tope)
}

/** «hoy», «ayer», «hace 3 días» o la fecha («2 nov»). */
fun haceDias(dia: Long, hoy: Long): String = when (val n = hoy - dia) {
    0L -> "hoy"
    1L -> "ayer"
    in 2L..6L -> "hace $n días"
    else -> Formato.fechaSinAno(LocalDate.ofEpochDay(dia), LocalDate.ofEpochDay(hoy))
}
