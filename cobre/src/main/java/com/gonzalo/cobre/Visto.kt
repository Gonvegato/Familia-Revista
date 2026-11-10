package com.gonzalo.cobre

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.LaFamilia
import com.gonzalo.familia.nucleo.Coleccion
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Lo que ves en Nut, día a día (Nut 0.8.0): el reproductor suma el tiempo que de verdad está sonando con la imagen delante (canales,
 * partidos, películas y series), y se guarda en este dispositivo y en tu cuenta (`cobre_visto/<dispositivo>`, campos `d<día>` en ms),
 * como el «tiempo» de las hermanas. «Esta semana» de la portada suma todos tus dispositivos.
 */
object TiempoVisto {
    const val COLECCION = "cobre_visto"
    private const val PREFS = "cobre_visto"

    /** Para las pruebas y las capturas: los días fijos (día → ms), sin cuenta ni disco. */
    var fijos: Map<String, Long>? = null

    /** Cambia cuando se suma algo (para repintar la portada). */
    var version by androidx.compose.runtime.mutableIntStateOf(0)

    private fun col(): Coleccion? = runCatching { LaFamilia.coleccion(COLECCION) }.getOrNull()

    /** Suma [ms] vistos ahora mismo (al día de hoy) en este dispositivo y en tu cuenta. */
    fun suma(c: Context, ms: Long, dia: LocalDate = LocalDate.now()) {
        if (ms <= 0L) return
        val k = "d$dia"
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nuevo = maxOf(p.getLong(k, 0L), deCuenta()[k] ?: 0L) + ms
        p.edit().putLong(k, nuevo).apply()
        runCatching { if (Cuenta.dispositivo.isNotBlank()) col()?.cambia(Cuenta.dispositivo, mapOf(k to nuevo, "t" to System.currentTimeMillis())) }
        android.os.Handler(android.os.Looper.getMainLooper()).post { version++ }
    }

    /** Lo de este dispositivo que ya está en la cuenta (por si se borraron los datos de la app). */
    private fun deCuenta(): Map<String, Long> =
        runCatching { col()?.nodo(Cuenta.dispositivo)?.datos().orEmpty().filterKeys { DIA.matches(it) }.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L } }.getOrNull().orEmpty()

    private val DIA = Regex("d\\d{4}-\\d{2}-\\d{2}")

    /** Lo visto cada día (día ISO → ms), sumando tus dispositivos (lo de este, lo más alto entre el disco y la cuenta). */
    fun dias(c: Context): Map<String, Long> {
        fijos?.let { return it }
        val r = HashMap<String, Long>()
        val local = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.filterKeys { DIA.matches(it) }.mapValues { (_, v) -> (v as? Long) ?: 0L }
        val nodos = runCatching { col()?.vivos() }.getOrNull().orEmpty()
        var esteEnCuenta: Map<String, Long> = emptyMap()
        nodos.forEach { n ->
            val m = n.datos().filterKeys { DIA.matches(it) }.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L }
            if (n.ref == Cuenta.dispositivo) esteEnCuenta = m
            else m.forEach { (k, v) -> r[k.drop(1)] = (r[k.drop(1)] ?: 0L) + v }
        }
        (local.keys + esteEnCuenta.keys).forEach { k -> r[k.drop(1)] = (r[k.drop(1)] ?: 0L) + maxOf(local[k] ?: 0L, esteEnCuenta[k] ?: 0L) }
        return r
    }
}

/** La regla de los «días seguidos» de la familia: un día cuenta con un minuto; hasta hoy o, si hoy aún no, hasta ayer. */
object Racha {
    const val MINUTO_MS = 60_000L
    fun cuentaTiempo(ms: Long): Boolean = ms >= MINUTO_MS
    fun dias(hoy: Long, cuenta: (Long) -> Boolean): Int {
        var d = if (cuenta(hoy)) hoy else hoy - 1
        var n = 0
        while (cuenta(d)) { n++; d-- }
        return n
    }
}

/** «Esta semana» de la portada: lo visto hoy (minutos), los días seguidos, la semana (día → minutos) y el total (minutos). */
class SemanaVista(val hoyMin: Long, val racha: Int, val semana: List<Pair<LocalDate, Long>>) {
    val totalMin: Long get() = semana.sumOf { it.second }
    companion object {
        fun de(dias: Map<String, Long>, hoy: LocalDate = LocalDate.now()): SemanaVista = SemanaVista(
            (dias[hoy.toString()] ?: 0L) / 60_000L,
            Racha.dias(hoy.toEpochDay()) { Racha.cuentaTiempo(dias[LocalDate.ofEpochDay(it).toString()] ?: 0L) },
            (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toString()] ?: 0L) / 60_000L })
    }
}

/** Minutos en corto para las cifras: «0′», «52′», «3 H 01». */
fun minutosCorto(min: Long): String = if (min < 60) "$min′" else "${min / 60} H ${"%02d".format(min % 60)}"

/** Lo visto un día, para la etiqueta de su barra: «nada», «51 min», «2 h 10», «1 h». */
fun minutosDia(min: Long): String = when {
    min <= 0 -> "nada"
    min < 60 -> "$min min"
    min % 60 == 0L -> "${min / 60} h"
    else -> "${min / 60} h ${"%02d".format(min % 60)}"
}

/**
 * La semana en barras grandes y tocables (los siete últimos días; hoy, a la derecha y con su letra en el acento). Al tocar una, se marca
 * y sale encima lo que viste ese día («J · 2 h 10»); al tocarla otra vez, se quita. De serie, marcada hoy si has visto algo.
 */
@Composable
fun SemanaTocable(d: SemanaVista, modifier: Modifier = Modifier, alto: Int = 74) {
    val hoy = LocalDate.now()
    val es = Locale("es", "ES")
    var marcado by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(if (d.hoyMin >= 1) d.semana.lastIndex else -1) }
    val tope = maxOf(d.semana.maxOf { it.second }, 1L).toFloat()
    val escala = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(modifier.fillMaxWidth().padding(top = (40f * escala).dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        d.semana.forEachIndexed { i, (dia, m) ->
            val letra = dia.dayOfWeek.getDisplayName(TextStyle.NARROW, es).uppercase()
            val nombre = dia.dayOfWeek.getDisplayName(TextStyle.FULL, es)
            val sel = i == marcado
            Column(
                Modifier.weight(1f)
                    .tocable(fuera = 0.dp) { marcado = if (sel) -1 else i }
                    .semantics { contentDescription = "${if (dia == hoy) "Hoy, " else ""}$nombre: ${if (m >= 1) minutosDia(m) + " vistos" else "no viste nada"}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(alto.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(if (m >= 1) maxOf(4f, alto * m / tope).dp else 3.dp)
                        .background(if (sel) Acento else if (m >= 1) Charcoal else Line)
                        .then(if (sel) Modifier.border(2.dp, Charcoal) else Modifier))
                    if (sel) {
                        Text("$letra · ${minutosDia(m)}", color = Paper, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.align(when (i) { 0 -> Alignment.BottomStart; d.semana.lastIndex -> Alignment.BottomEnd; else -> Alignment.BottomCenter })
                                .offset(y = (-(alto + 6)).dp)
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
