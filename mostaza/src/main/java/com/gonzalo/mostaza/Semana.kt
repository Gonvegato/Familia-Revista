package com.gonzalo.mostaza

import java.time.LocalDate

/**
 * «Tu semana» (el widget): lo que has visto estos siete días, hoy incluido. Sale del tiempo que ya apunta Hathor por día, título y
 * dispositivo (`tiempo/<ref>~<dispositivo>`, campos `d<día>`; ver [Biblioteca.sumaTiempo]): solo cuenta lo de tu biblioteca, que es lo
 * que se apunta (algo entra a los 10 minutos vistos de verdad).
 */
class Semana(
    /** Los siete días, del más antiguo a hoy, con lo visto (ms). */
    val dias: List<Pair<LocalDate, Long>>,
    /** Lo visto por título (ref, nombre, ms), lo que más primero. */
    val titulos: List<Triple<String, String, Long>>,
) {
    val total: Long get() = dias.sumOf { it.second }
    val maximo: Long get() = dias.maxOfOrNull { it.second } ?: 0L
    val vacia: Boolean get() = total < 60_000L

    companion object {
        /** Junta [ratos] (de todos tus dispositivos) en los siete días que acaban en [hoy]. [nombre]: el de tu biblioteca, si sigue. */
        fun de(ratos: List<Rato>, hoy: LocalDate = LocalDate.now(), nombre: (String) -> String? = { null }): Semana {
            val desde = hoy.minusDays(6)
            val dentro = ratos.filter { r -> runCatching { LocalDate.parse(r.dia) }.getOrNull()?.let { !it.isBefore(desde) && !it.isAfter(hoy) } == true }
            val porDia = dentro.groupBy { it.dia }.mapValues { (_, l) -> l.sumOf { it.ms } }
            val dias = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { it to (porDia[it.toString()] ?: 0L) }
            val titulos = dentro.groupBy { it.ref }.map { (ref, l) ->
                Triple(ref, nombre(ref)?.ifBlank { null } ?: l.maxByOrNull { it.dia }?.nombre.orEmpty(), l.sumOf { it.ms })
            }.filter { it.third >= 60_000L && it.second.isNotBlank() }.sortedByDescending { it.third }
            return Semana(dias, titulos)
        }

        /** «2 h 15 min», «45 min», «0 min». */
        fun horas(ms: Long): String = (ms / 60_000L).toInt().let { if (it <= 0) "0 min" else duracion(it) }

        /** «2,5 h» o «45 min»: lo de un día, corto (encima de su barra). */
        fun corto(ms: Long): String {
            val min = (ms / 60_000L).toInt()
            if (min < 60) return "$min min"
            val h = min / 60.0
            return if (h >= 10 || min % 60 == 0) "${Math.round(h)} h" else String.format(java.util.Locale("es", "ES"), "%.1f h", h)
        }
    }
}
