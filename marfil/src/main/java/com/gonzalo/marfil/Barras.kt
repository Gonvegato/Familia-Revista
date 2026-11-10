package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")), fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold,
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
                Text(dia.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")), fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (dia == hoy) Acento else Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Cómo vas con el reto del año: [diferencia] es lo terminado menos lo que tocaría a estas alturas (lo esperado: el reto repartido por
 * igual entre los días del año, contando hoy y redondeando hacia abajo); [quedan], los que faltan; [dias], los días que quedan (con hoy).
 */
class RitmoReto(val hechos: Int, val meta: Int, val diferencia: Int, val quedan: Int, val dias: Int) {
    val cumplido: Boolean get() = meta in 1..hechos
    /** «VAS 2 POR DETRÁS · TE QUEDAN 17 EN 52 DÍAS», «VAS AL DÍA · …», «¡RETO CUMPLIDO!». */
    fun texto(): String {
        if (cumplido) return if (hechos > meta) "¡Reto cumplido! Y ${hechos - meta} de propina" else "¡Reto cumplido!"
        val vas = when {
            diferencia > 0 -> "Vas $diferencia por delante"
            diferencia < 0 -> "Vas ${-diferencia} por detrás"
            else -> "Vas al día"
        }
        return "$vas · ${if (quedan == 1) "te queda 1" else "te quedan $quedan"} en $dias ${if (dias == 1) "día" else "días"}"
    }
}

fun ritmoReto(meta: Int, hechos: Int, hoy: LocalDate = LocalDate.now()): RitmoReto {
    val diasAno = hoy.lengthOfYear()
    val esperado = if (meta <= 0) 0 else (meta.toLong() * hoy.dayOfYear / diasAno).toInt()
    return RitmoReto(hechos, meta, hechos - esperado, (meta - hechos).coerceAtLeast(0), diasAno - hoy.dayOfYear + 1)
}

/** Minutos en corto para las cifras: «0′», «52′», «3 H 01». */
fun minutosCorto(min: Long): String = if (min < 60) "$min′" else "${min / 60} H ${"%02d".format(min % 60)}"

/** Lo leído un día, para la etiqueta de su barra: «nada», «51 min», «2 h 10», «1 h». */
fun minutosDia(min: Long): String = when {
    min <= 0 -> "nada"
    min < 60 -> "$min min"
    min % 60 == 0L -> "${min / 60} h"
    else -> "${min / 60} h ${"%02d".format(min % 60)}"
}

/**
 * La semana en barras grandes y tocables (los siete últimos días; hoy, a la derecha y con su letra en el acento). Al tocar una, se marca
 * y sale encima lo que leíste ese día («J · 2 h 10»); al tocarla otra vez, se quita. De serie, marcada hoy si has leído.
 */
@Composable
fun SemanaTocable(d: DatosLectura, modifier: Modifier = Modifier, alto: Int = 74) {
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
                    .clickable { marcado = if (sel) -1 else i }
                    .semantics { contentDescription = "${if (dia == hoy) "Hoy, " else ""}$nombre: ${if (m >= 1) minutosDia(m) + " leídos" else "no leíste"}" },
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
