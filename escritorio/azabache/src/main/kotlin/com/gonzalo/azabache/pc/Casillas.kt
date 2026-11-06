package com.gonzalo.azabache.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate

/*
 * Las casillas de la ficha de revista, las mismas que en el Azabache del móvil (Revista.kt, Maqueta.kt, Sesiones.kt): la página en filas
 * con filetes, una o dos casillas por fila separadas por un filete vertical, cada casilla con su antetítulo en versalitas; la nota en
 * cifras enormes con los criterios en puntos guía, la opinión con capitular, las horas, las sesiones en barras… Con los mismos nombres.
 */

/** Qué parte del ancho de la fila ocupa la casilla (las cifras grandes encogen en una casilla estrecha). */
val LocalCellFraction = compositionLocalOf { 1f }

/** El antetítulo de una casilla, con una acción a la derecha (opcional). */
@Composable
fun MagLabel(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Antetitulo(text, tam = 12.sp, conCuadro = false) }
        if (action != null && onAction != null) EnlaceRevista(action, acento = true) { onAction() }
    }
}

/** Una casilla: su antetítulo (null: lo que va dentro ya trae el suyo) y lo que pinta. */
class Casilla(val label: String?, val action: Pair<String, () -> Unit>? = null, val content: @Composable ColumnScope.() -> Unit)

/**
 * Una fila de la página: una casilla a todo lo ancho o dos, separadas por un filete vertical que llega de arriba abajo; [split] es lo que
 * ocupa la de la izquierda (0–1) y [alto], el alto fijo de la fila en dp (0: el que pida lo de dentro). Con un filete grueso arriba.
 */
@Composable
fun FilaDeCasillas(casillas: List<Casilla>, split: Float = 0.5f, alto: Float = 0f) {
    Column(Modifier.fillMaxWidth()) {
        Filete(2.dp)
        Row(Modifier.fillMaxWidth().then(if (alto > 0f) Modifier.height(alto.dp) else Modifier.height(IntrinsicSize.Min))) {
            casillas.forEachIndexed { n, c ->
                val w = if (casillas.size == 2) (if (n == 0) split.coerceIn(0.2f, 0.8f) else 1f - split.coerceIn(0.2f, 0.8f)) else 1f
                if (n == 1) Box(Modifier.fillMaxHeight().width(1.5.dp).background(Charcoal))
                Column(Modifier.weight(w).fillMaxHeight().clipToBounds()
                    .padding(top = 12.dp, bottom = 18.dp, start = if (n == 1) 14.dp else 0.dp, end = if (n == 0 && casillas.size == 2) 14.dp else 0.dp)) {
                    CompositionLocalProvider(LocalCellFraction provides w) {
                        if (c.label != null) MagLabel(c.label, c.action?.first, c.action?.second)
                        c.content(this)
                    }
                }
            }
        }
    }
}

/**
 * Dos columnas, como una revista abierta sobre la mesa: lo de dentro (las filas de la ficha, los bloques de Cifras…) va en su orden,
 * primero la columna de la izquierda y luego la de la derecha, partido donde las dos quedan más iguales, con un filete fino entre ellas.
 * Es la misma pieza que en la tableta (`ColumnasRevista`). Cada pieza se mide una vez, con el ancho de su columna.
 */
@Composable
fun ColumnasRevista(columnas: Int, modifier: Modifier = Modifier, hueco: Dp = 44.dp, content: @Composable () -> Unit) {
    if (columnas <= 1) { Column(modifier.fillMaxWidth()) { content() }; return }
    val filete = Line
    Layout(content, modifier.fillMaxWidth().drawBehind {
        if (size.height > 0f) drawRect(filete, Offset(size.width / 2f - 0.5.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
    }) { ms, c ->
        val ancho = if (c.hasBoundedWidth) c.maxWidth else 0
        val g = hueco.roundToPx()
        val w = ((ancho - g) / 2).coerceAtLeast(0)
        val ps = ms.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
        val total = ps.sumOf { it.height }
        // El corte que deja las dos columnas más iguales (sin cambiar el orden)
        var corte = ps.size; var peor = Int.MAX_VALUE; var acc = 0
        for (k in 0..ps.size) {
            val alto = maxOf(acc, total - acc)
            if (alto <= peor) { peor = alto; corte = k }
            if (k < ps.size) acc += ps[k].height
        }
        layout(ancho, peor.coerceAtLeast(0)) {
            var y = 0
            ps.forEachIndexed { i, p ->
                if (i == corte) y = 0
                p.place(if (i < corte) 0 else w + g, y); y += p.height
            }
        }
    }
}

/** La portada de la ficha: ocupa el ancho de su casilla (como mucho 240 dp), en 2:3. */
@Composable
fun MagPortada(f: Ficha, k: Kind, deUid: String? = null) {
    Box(Modifier.widthIn(max = 240.dp).fillMaxWidth()) { PortadaCosa(f.portada, f.portadaWeb, f.nombre, Modifier.fillMaxWidth(), deUid = deUid, pie = k.unit) }
}

/** La nota en cifras enormes y, debajo, los criterios con puntos guía. */
@Composable
fun MagScore(general: Int, scores: List<Int>, kind: Kind) {
    // En una casilla estrecha, la cifra encoge (nunca se parte en dos líneas)
    val k = (LocalCellFraction.current / 0.5f).coerceIn(0.55f, 1f)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$general", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (96 * k).sp, lineHeight = (90 * k).sp, letterSpacing = (-3 * k).sp, maxLines = 1, softWrap = false)
        Text("/100", color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
    }
    if (kind.crit > 1) {
        Spacer(Modifier.height(6.dp))
        for (i in 1..kind.crit) MagFact(kind.labels.getOrElse(i) { "" }, "${scores.getOrElse(i) { 0 }}")
    }
}

/** El texto de la opinión con capitular (la primera letra grande y en el acento). */
@Composable
fun MagBody(text: String) {
    val t = text.trim()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Acento, fontFamily = Display)) { append(t.take(1)) }
            append(t.drop(1))
        },
        color = Charcoal, fontFamily = Display, fontSize = 20.sp, lineHeight = 31.sp
    )
}

/** Un texto en cursiva y gris para lo que aún no hay («Aún no has escrito nada»). */
@Composable
fun MagVacio(t: String, tam: Int = 16) = Text(t, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam * 1.4f).sp)

/** Una barra de avance fina (lo jugado frente a lo que suele durar, lo leído de un libro): tinta sobre una raya. */
@Composable
fun BarraAvance(fraccion: Float, modifier: Modifier = Modifier) {
    val f = fraccion.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(6.dp).background(Line)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(if (f >= 1f) Acento else Charcoal))
    }
}

/** Una etiqueta pequeña en versalitas (bajo las cifras grandes). */
@Composable
fun Pie(t: String, acento: Boolean = false, modifier: Modifier = Modifier) =
    Text(t.uppercase(), modifier, color = if (acento) Acento else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 13.sp)

/** «24 h», «24,5 h» (sin minutos, para que nunca se parta en una casilla estrecha). */
fun horasCortas(min: Int): String {
    val r = Math.round(min / 60.0 * 2) / 2.0
    return if (r % 1.0 == 0.0) "${r.toInt()} h" else String.format(java.util.Locale("es", "ES"), "%.1f h", r)
}

/** Horas: las tuyas en grande y las habituales (historia, extras, completo) con puntos guía; jugando, lo que llevas de la historia. */
@Composable
fun MagHours(mine: Int, main: Int, extra: Int, full: Int, enCurso: Boolean) {
    if (mine > 0) {
        val k = (LocalCellFraction.current / 0.5f).coerceIn(0.6f, 1f)
        Text(horasCortas(mine), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (44 * k).sp, lineHeight = (46 * k).sp, maxLines = 1, softWrap = false)
        Pie("Tus horas")
        if (enCurso && main > 0) {
            val pct = mine * 100 / main
            BarraAvance(mine.toFloat() / main, Modifier.padding(top = 10.dp))
            Pie(if (pct < 100) "Vas por el $pct % de la historia" else "Ya más de lo que suele durar", acento = pct >= 100, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
    if (main > 0) MagFact("Historia", horasCortas(main))
    if (extra > 0) MagFact("+ Extras", horasCortas(extra))
    if (full > 0) MagFact("Completo", horasCortas(full))
    if (mine <= 0 && main <= 0 && extra <= 0 && full <= 0) MagVacio("Sin horas todavía.")
}

/**
 * La casilla «Sesiones»: las barras (en tinta; en el acento, la de ahora si sigue en curso, o la de más tiempo si ya acabó), y debajo los
 * días, la sesión más larga y la última, con puntos guía.
 */
@Composable
fun MagSesiones(s: Sesiones, kind: Kind, terminado: Boolean) {
    val hoy = LocalDate.now().toEpochDay()
    val tope = maxOf(s.barras.maxOrNull() ?: 1L, 1L).toFloat()
    val acento = if (!terminado && s.hasta >= hoy) s.barras.lastIndex else s.barras.indexOf(s.barras.maxOrNull() ?: 0L)
    val alto = 64f
    Row(Modifier.fillMaxWidth().height(alto.dp), horizontalArrangement = Arrangement.spacedBy(if (s.barras.size > 16) 3.dp else 5.dp), verticalAlignment = Alignment.Bottom) {
        s.barras.forEachIndexed { i, v ->
            Box(Modifier.weight(1f).height(if (v > 0) maxOf(3f, alto * v / tope).dp else 1.dp).background(if (v <= 0) Line else if (i == acento) Acento else Charcoal))
        }
    }
    val que = when (s.unidad) { 1 -> "por día"; 7 -> "por semana"; else -> "por mes" }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Pie(Formato.fechaSinAno(LocalDate.ofEpochDay(s.desde), LocalDate.ofEpochDay(hoy)))
        Spacer(Modifier.weight(1f)); Pie(que); Spacer(Modifier.weight(1f))
        Pie(if (s.hasta >= hoy) "hoy" else Formato.fechaSinAno(LocalDate.ofEpochDay(s.hasta), LocalDate.ofEpochDay(hoy)))
    }
    Spacer(Modifier.height(8.dp))
    MagFact(if (kind.isBook) "Días leyendo" else "Días jugando", Formato.cifra(s.dias))
    val larga = (s.masLargaMs / 60_000L).toInt()
    MagFact("La más larga", if (larga < 60) "$larga min" else horasCortas(larga))
    if (terminado && s.hasta >= s.primera) MagFact("De principio a fin", "${s.hasta - s.primera + 1} días")
    else MagFact("La última", haceDias(s.ultima, hoy))
}

/** Un enlace de la casilla «Enlaces» que lleva a algún sitio de la app. */
@Composable
fun EnlaceFicha(t: String, sub: String? = null, accent: Boolean = false, onClick: () -> Unit) = MagLink(t, sub, accent, onClick)

/** Un texto pequeño de ayuda en una casilla. */
@Composable
fun NotaCasilla(t: String) = Text(t, color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))

/** Algo que se toca en la casilla («Escribir en el diario →»), en una línea. */
@Composable
fun AccionCasilla(t: String, onClick: () -> Unit) =
    Text("$t →", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp))
