package com.gonzalo.hiedra

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Lo jugado cada día (ms), sumando todos los juegos y todos tus dispositivos. */
fun diasJugados(juegos: List<Juego>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (j in juegos) for ((k, v) in j.dias()) out[k] = (out[k] ?: 0L) + v
    return out
}

/** Días seguidos jugando, con la regla común de la familia ([Racha]: al menos un minuto; hasta hoy o hasta ayer). */
fun rachaDeJuego(dias: Map<String, Long>, hoy: LocalDate = LocalDate.now()): Int =
    Racha.dias(hoy.toEpochDay()) { Racha.cuentaTiempo(dias[LocalDate.ofEpochDay(it).toString()] ?: 0L) }

/**
 * Entre la fecha y el menú, como en la portada de Atrio: lo jugado hoy, los días seguidos y la semana en barras (cada día, lo que jugaste).
 * Las medidas y las letras son las de Atrio.
 */
@Composable
fun BarrasJuego(juegos: List<Juego>) {
    val dias = remember(juegos.map { it.id to it.msJugados to it.ultima }) { diasJugados(juegos) }
    val hoy = LocalDate.now()
    val hoyMin = (dias[hoy.toString()] ?: 0L) / 60_000L
    val racha = rachaDeJuego(dias)
    val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toString()] ?: 0L) / 60_000L }
    val algo = semana.any { it.second >= 1 }
    if (!algo && racha < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Acento)); Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull("JUEGO", if (hoyMin >= 1) "${tiempoDe(hoyMin * 60_000L).uppercase()} HOY" else null, if (racha >= 2) "$racha DÍAS SEGUIDOS" else null).joinToString(" · "),
                fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Charcoal
            )
        }
        if (algo) {
            val tope = maxOf(semana.maxOf { it.second }, 1L).toFloat()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                semana.forEach { (d, m) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth(0.62f).height(30.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(if (m >= 1) maxOf(3f, 30f * m / tope).dp else 1.dp).background(if (m >= 1) Charcoal else Line))
                        }
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")), fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (d == hoy) Acento else Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Lo de la semana para la portada: lo jugado hoy (minutos), la racha y la semana (día → minutos). */
class SemanaJuego(val hoyMin: Long, val racha: Int, val semana: List<Pair<LocalDate, Long>>)

fun datosJuego(juegos: List<Juego>, hoy: LocalDate = LocalDate.now()): SemanaJuego {
    val dias = diasJugados(juegos)
    return SemanaJuego((dias[hoy.toString()] ?: 0L) / 60_000L, rachaDeJuego(dias, hoy), (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toString()] ?: 0L) / 60_000L })
}

/** Minutos en corto para las cifras: «0′», «52′», «3 H 01». */
fun minutosCorto(min: Long): String = if (min < 60) "$min′" else "${min / 60} H ${"%02d".format(min % 60)}"

/** Lo jugado un día, para la etiqueta de su barra: «nada», «51 min», «2 h 10», «1 h». */
fun minutosDia(min: Long): String = when {
    min <= 0 -> "nada"
    min < 60 -> "$min min"
    min % 60 == 0L -> "${min / 60} h"
    else -> "${min / 60} h ${"%02d".format(min % 60)}"
}

/**
 * La semana en barras grandes y tocables (los siete últimos días; hoy, a la derecha y con su letra en el acento). Al tocar una, se marca
 * y sale encima lo que jugaste ese día («J · 2 h 10»); al tocarla otra vez, se quita. De serie, marcada hoy si has jugado.
 */
@Composable
fun SemanaTocable(d: SemanaJuego, modifier: Modifier = Modifier, alto: Int = 74) {
    val hoy = LocalDate.now()
    val es = Locale("es", "ES")
    var marcado by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(if (d.hoyMin >= 1) d.semana.lastIndex else -1) }
    val tope = maxOf(d.semana.maxOf { it.second }, 1L).toFloat()
    // (arriba, sitio para la etiqueta del día marcado aunque sea el más alto y con la letra grande)
    val escala = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(modifier.fillMaxWidth().padding(top = (40f * escala).dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        d.semana.forEachIndexed { i, (dia, m) ->
            val letra = dia.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase()
            val nombre = dia.dayOfWeek.getDisplayName(TextStyle.FULL, es)
            val sel = i == marcado
            Column(
                Modifier.weight(1f)
                    .tocable(fuera = 0.dp) { marcado = if (sel) -1 else i }
                    .semantics { contentDescription = "${if (dia == hoy) "Hoy, " else ""}$nombre: ${if (m >= 1) minutosDia(m) + " jugados" else "no jugaste"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(alto.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(if (m >= 1) maxOf(4f, alto * m / tope).dp else 3.dp)
                        .background(if (sel) Acento else if (m >= 1) Charcoal else Line)
                        .then(if (sel) Modifier.border(2.dp, Charcoal) else Modifier))
                    if (sel) {
                        // La etiqueta, encima de la barra (puede salirse de su columna; no se recorta)
                        val arriba = if (m >= 1) maxOf(4f, alto * m / tope) else 3f
                        Text("$letra · ${minutosDia(m)}", color = Paper, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.align(when (i) { 0 -> Alignment.BottomStart; d.semana.lastIndex -> Alignment.BottomEnd; else -> Alignment.BottomCenter })
                                .offset(y = (-(arriba + 6)).dp)
                                // (en los extremos se pega a su lado, para no salirse de la pantalla)
                                .wrapContentWidth(align = when (i) { 0 -> Alignment.Start; d.semana.lastIndex -> Alignment.End; else -> Alignment.CenterHorizontally }, unbounded = true)
                                .background(Charcoal).padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
                Text(letra, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (dia == hoy) Acento else Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
