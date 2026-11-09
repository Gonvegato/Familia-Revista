package com.gonzalo.shai

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fechas y cifras, iguales en Hiedra, Mostaza, Cobalto, Azabache y FcFeed:
 *  - en fichas y textos corridos: «16 oct 2026» ([fecha]);
 *  - en tablas de datos: «16/10/2026» ([fechaTabla]);
 *  - las cifras, siempre con punto de millar: «4.010 palabras», «80.000» ([cifra]).
 * Los meses van escritos aquí (no los de Android, que según la versión les pone punto: «oct.»).
 */
object Formato {
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

    fun fecha(d: LocalDate): String = "${d.dayOfMonth} ${MESES[d.monthValue - 1]} ${d.year}"
    fun fecha(ms: Long): String = fecha(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())
    fun fechaDia(epochDay: Long): String = fecha(LocalDate.ofEpochDay(epochDay))
    /** «16 oct» (sin el año, si es de este año). */
    fun fechaSinAno(d: LocalDate, hoy: LocalDate = LocalDate.now()): String = if (d.year == hoy.year) "${d.dayOfMonth} ${MESES[d.monthValue - 1]}" else fecha(d)

    fun fechaTabla(d: LocalDate): String = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
    fun fechaTablaDia(epochDay: Long): String = fechaTabla(LocalDate.ofEpochDay(epochDay))

    fun cifra(n: Long): String {
        val s = kotlin.math.abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, ch -> if (i > 0 && (s.length - i) % 3 == 0) out.append('.'); out.append(ch) }
        return (if (n < 0) "-" else "") + out
    }
    fun cifra(n: Int): String = cifra(n.toLong())

    /** «7,5» (con una cifra decimal, y sin ella si es entera: «8»). */
    fun decimal(d: Double): String { val r = Math.round(d * 10) / 10.0; return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',') }

    /** «1.250.000 €»; y en corto, para las cifras grandes: «1,3 M€», «850 mil €». */
    fun euros(n: Long): String = cifra(n) + " €"
    fun eurosCorto(n: Long): String {
        val a = kotlin.math.abs(n)
        val s = when {
            a >= 1_000_000 -> decimal(a / 1_000_000.0) + " M€"
            a >= 10_000 -> cifra(Math.round(a / 1000.0)) + " mil €"
            else -> cifra(a) + " €"
        }
        return (if (n < 0) "−" else "") + s
    }

    /** Un precio en céntimos, con sus dos decimales: «18,40 €». */
    fun centimos(c: Long): String = cifra(c / 100) + "," + "%02d".format(kotlin.math.abs(c % 100)) + " €"

    /** Dólares de la API: «0,42 $» (con dos decimales; «0,004 $» si es menos de un céntimo). */
    fun dolares(d: Double): String {
        val t = if (d != 0.0 && kotlin.math.abs(d) < 0.01) "%.3f".format(java.util.Locale.ROOT, d) else "%.2f".format(java.util.Locale.ROOT, d)
        return t.replace('.', ',') + "\u00A0$"
    }
}
