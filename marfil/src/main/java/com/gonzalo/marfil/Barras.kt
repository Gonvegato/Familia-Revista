package com.gonzalo.marfil

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
import java.time.format.TextStyle
import java.util.Locale

/** Lo leído cada día (milisegundos), sumando todos los libros: el tiempo que guarda el lector en cada uno. */
fun diasLeidos(ctx: android.content.Context, libros: List<Libro>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (l in libros) {
        val dias = EstadoLectura.estado(ctx, l.id).optJSONObject("stats")?.optJSONObject("days") ?: continue
        for (k in dias.keys()) out[k] = (out[k] ?: 0L) + dias.optLong(k)
    }
    return out
}

/** Días seguidos leyendo, con la regla común de la familia ([Racha]: al menos un minuto; hasta hoy o hasta ayer). */
fun rachaDeLectura(dias: Map<String, Long>, hoy: LocalDate = LocalDate.now()): Int =
    Racha.dias(hoy.toEpochDay()) { Racha.cuentaTiempo(dias[LocalDate.ofEpochDay(it).toString()] ?: 0L) }

/**
 * Entre la fecha y el menú, como en la portada de Atrio: lo leído hoy (y tu objetivo, si tienes), los días seguidos
 * y la semana en barras (cada día, lo que leíste; la raya fina es tu objetivo). Medidas y letras, las de Atrio.
 */
@Composable
fun BarrasLectura(libros: List<Libro>) {
    val ctx = LocalContext.current
    val dias = remember(libros.map { it.id to it.msPropios to it.ultima }) { diasLeidos(ctx, libros) }
    val meta = remember(libros) { Ajustes.metaLectura(ctx) }
    val hoy = LocalDate.now()
    val hoyMin = (dias[hoy.toString()] ?: 0L) / 60_000L
    val racha = rachaDeLectura(dias)
    val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toString()] ?: 0L) / 60_000L }
    val algo = semana.any { it.second >= 1 }
    if (!algo && meta <= 0 && racha < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Acento)); Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(
                    "LECTURA",
                    when {
                        meta > 0 && hoyMin >= meta -> "OBJETIVO CUMPLIDO · ${readTime(hoyMin).uppercase()} HOY"
                        meta > 0 -> "$hoyMin DE $meta MIN HOY"
                        hoyMin >= 1 -> "${readTime(hoyMin).uppercase()} HOY"
                        else -> null
                    },
                    if (racha >= 2) "$racha DÍAS SEGUIDOS" else null
                ).joinToString(" · "),
                fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                color = if (meta > 0 && hoyMin >= meta) Acento else Charcoal
            )
        }
        if (algo) {
            val tope = maxOf(semana.maxOf { it.second }, meta.toLong(), 1L).toFloat()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                semana.forEach { (d, m) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth(0.62f).height(30.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(if (m >= 1) maxOf(3f, 30f * m / tope).dp else 1.dp)
                                .background(if (m >= 1) (if (meta > 0 && m >= meta) Acento else Charcoal) else Line))
                        }
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")).uppercase(), fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (d == hoy) Acento else Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Lo de la lectura para la portada de la dirección C: lo leído hoy, la racha, la semana (día → minutos) y tu objetivo diario. */
class DatosLectura(val hoyMin: Long, val racha: Int, val semana: List<Pair<LocalDate, Long>>, val meta: Int)

fun datosLectura(ctx: android.content.Context, libros: List<Libro>): DatosLectura {
    val dias = diasLeidos(ctx, libros)
    val hoy = LocalDate.now()
    return DatosLectura((dias[hoy.toString()] ?: 0L) / 60_000L, rachaDeLectura(dias), (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toString()] ?: 0L) / 60_000L },
        Ajustes.metaLectura(ctx))
}

/** La semana en barras planas de la dirección C: cada día, lo que leíste; hoy, en el acento. */
@Composable
fun BarrasSemanaC(d: DatosLectura, modifier: Modifier = Modifier) {
    val hoy = LocalDate.now()
    val tope = maxOf(d.semana.maxOf { it.second }, d.meta.toLong(), 1L).toFloat()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            d.semana.forEach { (dia, m) ->
                Box(Modifier.weight(1f).height(if (m >= 1) maxOf(4f, 58f * m / tope).dp else 3.dp).background(if (dia == hoy) Acento else Charcoal))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.semana.forEach { (dia, _) ->
                Text(dia.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")).uppercase(), fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    color = if (dia == hoy) Acento else Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
