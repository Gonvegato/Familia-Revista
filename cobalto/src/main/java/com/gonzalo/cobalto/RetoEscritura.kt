package com.gonzalo.cobalto

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.LaFamilia
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * La meta de palabras al día: una para todas tus obras, la misma en todos tus dispositivos (va en la cuenta de la familia,
 * `ajustes/cobalto_meta`, campo `diaria`; sin cuenta, se queda en este). De serie, [DE_SERIE].
 */
object MetaDiaria {
    const val DE_SERIE = 500
    const val NODO = "cobalto_meta"
    /** Sube al cambiarla (aquí o en otro dispositivo): la portada la vuelve a leer. */
    var version by mutableIntStateOf(0)
        private set

    private fun p(c: Context) = c.getSharedPreferences("cobalto_ajustes", Context.MODE_PRIVATE)

    private fun deCuenta(): Int? = if (!Cuenta.vinculada) null else runCatching { LaFamilia.coleccion("ajustes").nodo(NODO)?.entero("diaria")?.toInt() }.getOrNull()?.takeIf { it > 0 }

    /** La meta de hoy: la de la cuenta si la hay; si no, la de aquí; si no, [DE_SERIE]. */
    fun de(c: Context): Int {
        val cuenta = deCuenta()
        if (cuenta != null) { if (p(c).getInt("metaDiaria", 0) != cuenta) p(c).edit().putInt("metaDiaria", cuenta).apply(); return cuenta }
        return p(c).getInt("metaDiaria", 0).takeIf { it > 0 } ?: DE_SERIE
    }

    /** Cambia la meta (de 50 a 20 000 palabras): aquí y en la cuenta. */
    fun pon(c: Context, n: Int) {
        val m = n.coerceIn(50, 20_000)
        p(c).edit().putInt("metaDiaria", m).apply()
        if (Cuenta.vinculada) runCatching { LaFamilia.coleccion("ajustes").cambia(NODO, mapOf("diaria" to m.toLong())) }
        version++
        Widgets.pedir(c)
    }

    /** Ha llegado algo de la cuenta. */
    fun llega() { version++ }
}

/**
 * Lo de la escritura para la portada: las palabras de hoy, la meta del día, cuántos de los últimos 30 días la cumpliste, los días seguidos
 * y la semana (los siete últimos días; el último, hoy).
 */
class DatosEscritura(val hoy: Int, val meta: Int, val cumplidos30: Int, val racha: Int, val semana: List<Pair<LocalDate, Int>>) {
    val cumplida: Boolean get() = meta in 1..hoy
    val semanaTotal: Int get() = semana.sumOf { it.second }
}

/** Días de los últimos [n] (con hoy) en los que llegaste a [meta] palabras. */
fun Escritos.cumplidos(meta: Int, n: Int = 30): Int = if (meta <= 0) 0 else ultimosDias(n).count { it.second >= meta }

fun datosEscritura(e: Escritos, meta: Int, hoy: LocalDate = LocalDate.now()): DatosEscritura {
    val dias = e.ultimosDias(7)
    return DatosEscritura(e.escritas(Escritos.hoy()), meta, e.cumplidos(meta), e.racha(), dias.mapIndexed { i, (_, n) -> hoy.minusDays((dias.size - 1 - i).toLong()) to n })
}

/** «312 / 500 · TE FALTAN 188», «¡META CUMPLIDA! Y 120 DE PROPINA». */
fun textoMeta(d: DatosEscritura): String = when {
    d.meta <= 0 -> ""
    d.hoy >= d.meta -> if (d.hoy > d.meta) "¡META CUMPLIDA! Y ${cifraE(d.hoy - d.meta)} DE PROPINA" else "¡META CUMPLIDA!"
    else -> "TE FALTAN ${cifraE(d.meta - d.hoy)}"
}

/** «CUMPLIDA 12 DE LOS ÚLTIMOS 30 DÍAS». */
fun textoCumplidos(n: Int): String = if (n == 1) "CUMPLIDA 1 DE LOS ÚLTIMOS 30 DÍAS" else "CUMPLIDA $n DE LOS ÚLTIMOS 30 DÍAS"

/** Las palabras en corto para las cifras de la portada: «312», «1,2K», «12K». */
fun palabrasCorto(n: Int): String = when {
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(Locale("es", "ES"), n / 1000.0)
    else -> "$n"
}

/** Lo escrito un día, para la etiqueta de su barra: «nada», «312 palabras». */
fun palabrasDia(n: Int): String = when { n <= 0 -> "nada"; n == 1 -> "1 palabra"; else -> "${cifraE(n)} palabras" }

/** El reto del día: «Meta del día · 312 / 500» (lo de todas tus obras), su barra y cuántos días la cumpliste. [alPoner] lleva a cambiar la meta (Ajustes). */
@Composable
fun BloqueReto(d: DatosEscritura, alPoner: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = alPoner).padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Meta del día", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(cifraE(d.hoy), color = if (d.cumplida) Acento else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1)
            Text(" / ${cifraE(d.meta)}", color = Muted, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(9.dp).background(Line)) {
            Box(Modifier.fillMaxWidth((d.hoy.toFloat() / d.meta.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().background(Acento))
        }
        Text("${textoMeta(d)} · ${textoCumplidos(d.cumplidos30)}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.sp, lineHeight = 14.sp,
            modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * La semana en barras grandes y tocables (los siete últimos días; hoy, a la derecha y con su letra en el acento). La meta del día va como una
 * raya fina a su altura. Al tocar una barra, se marca y sale encima lo que escribiste ese día («J · 812 palabras»); al tocarla otra vez, se
 * quita. De serie, marcado hoy si has escrito.
 */
@Composable
fun SemanaTocable(d: DatosEscritura, modifier: Modifier = Modifier, alto: Int = 74) {
    val hoy = LocalDate.now()
    val es = Locale("es", "ES")
    var marcado by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(if (d.hoy > 0) d.semana.lastIndex else -1) }
    val tope = maxOf(d.semana.maxOf { it.second }, d.meta, 1).toFloat()
    val escala = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.fillMaxWidth().padding(top = (40f * escala).dp)) {
        // La meta: una raya del acento a su altura, detrás de las barras (la que la pasa, la cruza)
        if (d.meta > 0) Box(Modifier.fillMaxWidth().offset(y = (alto - alto * d.meta / tope - 1f).dp).height(2.dp).background(Acento))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.semana.forEachIndexed { i, (dia, n) ->
                val letra = dia.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase()
                val nombre = dia.dayOfWeek.getDisplayName(TextStyle.FULL, es)
                val sel = i == marcado
                val cumple = d.meta > 0 && n >= d.meta
                Column(
                    Modifier.weight(1f)
                        .clickable { marcado = if (sel) -1 else i }
                        .semantics { contentDescription = "${if (dia == hoy) "Hoy, " else ""}$nombre: ${if (n > 0) palabrasDia(n) + if (cumple) ", meta cumplida" else "" else "no escribiste"}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(alto.dp), contentAlignment = Alignment.BottomCenter) {
                        val h = if (n > 0) maxOf(4f, alto * n / tope) else 3f
                        Box(Modifier.fillMaxWidth().height(h.dp)
                            .background(if (sel) Acento else if (n > 0) (if (cumple) Charcoal else Charcoal.copy(alpha = 0.55f)) else Line)
                            .then(if (sel) Modifier.border(2.dp, Charcoal) else Modifier))
                        if (sel) {
                            Text("$letra · ${palabrasDia(n)}", color = Paper, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, maxLines = 1, softWrap = false,
                                modifier = Modifier.align(when (i) { 0 -> Alignment.BottomStart; d.semana.lastIndex -> Alignment.BottomEnd; else -> Alignment.BottomCenter })
                                    .offset(y = (-(h + 6)).dp)
                                    .wrapContentWidth(align = when (i) { 0 -> Alignment.Start; d.semana.lastIndex -> Alignment.End; else -> Alignment.CenterHorizontally }, unbounded = true)
                                    .background(Charcoal).padding(horizontal = 7.dp, vertical = 4.dp))
                        }
                    }
                    Text(letra, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (dia == hoy) Acento else Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
