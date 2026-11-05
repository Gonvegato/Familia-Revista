package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/*
 * «Sesiones»: cómo has jugado o leído una cosa, día a día (lo que cuentan Hiedra y Marfil en `tiempo/<ref>~<fuente>`, sumando los
 * dispositivos). Se resume en barras —por día si fue cosa de pocas semanas, por semana o por mes si fue más larga—, los días que le
 * dedicaste, la sesión más larga y la última. Es lo mismo que enseña Azabache en el ordenador (allí, `Sesiones.kt`, con las mismas pruebas).
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

/** Lo de cada día de una ficha (jugado en Hiedra o leído en Marfil), de lo último que llegó de la cuenta. */
fun tiempoPorDiaDe(c: android.content.Context, kind: Kind, id: Long): Map<Long, Long> = when {
    kind.isGame -> Hermanas.jugadoPorDia(c).mapNotNull { (dia, m) -> m.entries.filter { it.key.sg == id }.sumOf { it.value }.takeIf { it > 0 }?.let { dia to it } }.toMap()
    kind.isBook -> Hermanas.leidoPorDia(c).mapNotNull { (dia, m) -> m[id]?.takeIf { it > 0 }?.let { dia to it } }.toMap()
    else -> emptyMap()
}

/** Las sesiones de una ficha (null si Hiedra o Marfil aún no han contado nada de ella). */
@Composable
fun rememberSesiones(kind: Kind, id: Long, fin: Long): Sesiones? {
    val ctx = LocalContext.current
    if (!kind.isGame && !kind.isBook) return null
    return remember(kind.id, id, fin, Hermanas.version) { runCatching { sesionesDe(tiempoPorDiaDe(ctx, kind, id), today(), fin) }.getOrNull() }
}

/**
 * La casilla «Sesiones»: las barras (en tinta; en el acento, la de ahora si sigue en curso, o la de más tiempo si ya acabó), y debajo los
 * días, la sesión más larga y la última, con puntos guía. Se mide por intrínsecos (va en las filas de dos casillas).
 */
@Composable
fun MagSesiones(s: Sesiones, kind: Kind, terminado: Boolean) {
    val hoy = today()
    val tope = maxOf(s.barras.maxOrNull() ?: 1L, 1L).toFloat()
    val acento = if (!terminado && s.hasta >= hoy) s.barras.lastIndex else s.barras.indexOf(s.barras.maxOrNull() ?: 0L)
    val alto = 58f
    Row(Modifier.fillMaxWidth().height(alto.dp), horizontalArrangement = Arrangement.spacedBy(if (s.barras.size > 16) 2.dp else 4.dp), verticalAlignment = Alignment.Bottom) {
        s.barras.forEachIndexed { i, v ->
            Box(Modifier.weight(1f).height(if (v > 0) maxOf(3f, alto * v / tope).dp else 1.dp).background(if (v <= 0) Line else if (i == acento) ColGeneral else Charcoal))
        }
    }
    val que = when (s.unidad) { 1 -> "por día"; 7 -> "por semana"; else -> "por mes" }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Text(Formato.fechaSinAno(LocalDate.ofEpochDay(s.desde), LocalDate.ofEpochDay(hoy)).uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(que.uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text((if (s.hasta >= hoy) "hoy" else Formato.fechaSinAno(LocalDate.ofEpochDay(s.hasta), LocalDate.ofEpochDay(hoy))).uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
    }
    Spacer(Modifier.height(8.dp))
    MagFact(if (kind.isBook) "Días leyendo" else "Días jugando", Formato.cifra(s.dias))
    // (en corto: en una casilla estrecha, «2 h 20 min» no cabía al lado de su etiqueta)
    val larga = (s.masLargaMs / 60_000L).toInt()
    MagFact("La más larga", if (larga < 60) "$larga min" else shortHours(larga))
    if (terminado && s.hasta >= s.primera) MagFact("De principio a fin", "${s.hasta - s.primera + 1} días")
    else MagFact("La última", haceDias(s.ultima, hoy))
}

/** Una barra de avance fina (lo jugado frente a lo que suele durar, lo leído de un libro): tinta sobre una raya. */
@Composable
fun BarraAvance(fraccion: Float, modifier: Modifier = Modifier) {
    val f = fraccion.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(6.dp).background(Line)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(if (f >= 1f) ColGeneral else Charcoal))
    }
}
