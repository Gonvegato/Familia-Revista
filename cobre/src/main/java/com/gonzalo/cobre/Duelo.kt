package com.gonzalo.cobre

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.FichaPartido
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.familia.filaDe

/*
 * Nut 0.9.0: las piezas del partido rehecho. Una sola letra (la grotesca de las hermanas), escudos limpios a su tamaño (sin paspartú ni
 * círculo; sin escudo, una forma de escudo con sus colores), el marcador con los colores de los dos equipos partidos en diagonal bajo un velo
 * oscuro y «en directo» con un punto que late.
 */

/** Unos colores de club para los equipos sin escudo (o mientras llega): siempre el mismo para el mismo equipo. */
private val COLORES_CLUB = listOf(
    Color(0xFFB6202A), Color(0xFF123A7A), Color(0xFF0B6E3D), Color(0xFF6A1B6E), Color(0xFFD9822B), Color(0xFF1B6F9E),
    Color(0xFF7A1F1F), Color(0xFF2E4053), Color(0xFF8C6D1F), Color(0xFF1F5E57),
)

/** El color «de club» de un equipo cuando aún no se sabe el de su escudo. */
fun colorDe(e: Equipo): Color = COLORES_CLUB[Math.floorMod((e.id.ifBlank { e.nombre }).hashCode(), COLORES_CLUB.size)]

/** El color de un equipo para pintar su lado del marcador: el de su escudo (o el suyo de reserva), sin pasarse de claro. */
@Composable
fun colorDuelo(e: Equipo): Color {
    val c = rememberColorEquipo(e) ?: colorDe(e)
    // (un escudo casi blanco daría un lado blanco: se oscurece lo justo para que el velo y las letras sigan leyéndose)
    return if (luminancia(c) > 0.6f) lerpColor(c, Color.Black, 0.45f) else c
}

/** Una forma de escudo con los colores de un equipo (el que no tiene escudo, o mientras llega el suyo). */
@Composable
fun EscudoGenerico(e: Equipo, tam: Dp, modifier: Modifier = Modifier) {
    val a = colorDe(e)
    val b = lerpColor(a, Color.White, 0.55f)
    Canvas(modifier.size(tam)) {
        val w = size.width; val h = size.height
        // (la forma: de un escudo de 40×46, centrada en el cuadrado)
        val ew = w * 40f / 46f; val x0 = (w - ew) / 2f
        fun px(x: Float) = x0 + x / 40f * ew
        fun py(y: Float) = y / 46f * h
        val forma = Path().apply {
            moveTo(px(20f), py(2f)); lineTo(px(37f), py(7f)); lineTo(px(37f), py(22f))
            cubicTo(px(37f), py(33f), px(29f), py(41f), px(20f), py(44f))
            cubicTo(px(11f), py(41f), px(3f), py(33f), px(3f), py(22f)); lineTo(px(3f), py(7f)); close()
        }
        val mitad = Path().apply {
            moveTo(px(20f), py(2f)); lineTo(px(37f), py(7f)); lineTo(px(37f), py(22f))
            cubicTo(px(37f), py(33f), px(29f), py(41f), px(20f), py(44f)); close()
        }
        drawPath(forma, a)
        drawPath(mitad, b, alpha = 0.9f)
        drawLine(a, Offset(px(3f), py(18f)), Offset(px(37f), py(18f)), strokeWidth = w * 0.05f)
        drawPath(forma, lerpColor(a, Color.Black, 0.3f), style = Stroke(width = w * 0.05f))
    }
}

/**
 * El fondo del marcador: el color de un equipo a la izquierda y el del otro a la derecha, partidos en diagonal, con un velo oscuro encima
 * (más oscuro abajo) para que las letras blancas se lean siempre.
 */
@Composable
fun FondoDuelo(local: Equipo, visitante: Equipo, modifier: Modifier = Modifier) {
    val c1 = colorDuelo(local); val c2 = colorDuelo(visitante)
    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height
        val d = h * 0.23f
        drawRect(c2)
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(w / 2f + d, 0f); lineTo(w / 2f - d, h); lineTo(0f, h); close() }, c1)
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.66f))))
    }
}

/** Un punto que late (el de «en directo»). */
@Composable
fun PuntoVivo(color: Color = VIVO, tam: Dp = 7.dp) {
    val t = rememberInfiniteTransition(label = "vivo")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "latido")
    Box(Modifier.size(tam).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
}

/** El rojo de «en directo». */
val VIVO = Color(0xFFD6332A)

/**
 * La etiqueta del estado de un partido sobre el marcador: en juego, una pastilla blanca con el punto que late («EN DIRECTO · 67′»); si no,
 * el texto con un marco fino («TU PRÓXIMO PARTIDO · SÁB 21:00», «FINAL»).
 */
@Composable
fun EtiquetaEstado(p: Partido, ahora: Long, siNoVivo: String? = null, modifier: Modifier = Modifier) {
    if (p.estado.enDirecto) {
        Row(modifier.background(Color.White).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            PuntoVivo(); Spacer(Modifier.width(6.dp))
            Text(("En directo · " + p.cuando(ahora)).uppercase(), color = Color(0xFF0B0A09), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.4.sp, maxLines = 1, softWrap = false)
        }
    } else {
        Text((siNoVivo ?: etiquetaPartido(p, ahora)).uppercase(), color = Color.White, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.4.sp, maxLines = 2,
            modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/**
 * Los dos equipos enfrentados (letras blancas, sobre [FondoDuelo]): cada uno, su escudo y su nombre debajo; en medio, el marcador (o la
 * hora) en grande. [alEquipo]: tocar un equipo abre su página.
 */
@Composable
fun Duelo(p: Partido, tamEscudo: Dp, alEquipo: ((Equipo) -> Unit)? = null, modifier: Modifier = Modifier) {
    val gl = p.golesLocal; val gv = p.golesVisitante
    val conGoles = gl != null && gv != null
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Lado(e: Equipo) = Column(Modifier.weight(1f).let { if (alEquipo != null) it.tocable { alEquipo(e) } else it }.filaDe("e:" + e.id),
            horizontalAlignment = Alignment.CenterHorizontally) {
            EscudoEquipo(e, tamEscudo, oscuro = true)
            Text(e.nombre, color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        Lado(p.local)
        Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FitTitle(if (conGoles) "$gl–$gv" else Hora.reloj(p.inicio), if (conGoles) 58.sp else 42.sp, if (conGoles) 58.sp else 44.sp, color = Color.White, family = Grotesca,
                fontWeight = FontWeight.Black, maxLines = 1, unaLinea = true, minSize = 20f, letterSpacing = (-1).sp, align = TextAlign.Center)
            if (p.penaltis.isNotBlank()) Text("Penaltis ${p.penaltis}", color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        Lado(p.visitante)
    }
}

/** Quién marcó, debajo del marcador: «Julián Álvarez 52′ — Raphinha 41′ · Lamine Yamal 64′» (los de casa a la izquierda). */
fun goleadores(f: FichaPartido): String {
    val p = f.partido
    // (los goles de un mismo jugador, juntos: «Raphinha 8′, 41′»)
    fun de(id: String) = f.eventos.filter { it.tipo.esGol && it.equipo == id }
        .groupBy { e -> e.jugador.ifBlank { "Gol" } + if (e.tipo == com.gonzalo.iptv.futbol.TipoEvento.GOL_PROPIA) " (p. p.)" else "" }
        .entries.joinToString(" · ") { (quien, l) -> "$quien " + l.joinToString(", ") { it.minuto } }
    val a = de(p.local.id); val b = de(p.visitante.id)
    return when { a.isBlank() && b.isBlank() -> ""; a.isBlank() -> "— $b"; b.isBlank() -> "$a —"; else -> "$a — $b" }
}

/** Un día de la fila de días: el elegido, en un bloque de tinta; los demás, en gris, sin marco. */
@Composable
fun PildoraDia(texto: String, on: Boolean, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Text(texto.uppercase(), color = if (on) Paper else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.6.sp, maxLines = 1, softWrap = false,
        modifier = modifier.tocable(fuera = 0.dp, onClick = alTocar).background(if (on) Charcoal else Color.Transparent).padding(horizontal = if (texto.length <= 1) 6.dp else 8.dp, vertical = 8.dp))
}

/** Una cifra del partido para los dos: sus números a los lados y una barra con el color de cada equipo; debajo, su nombre. */
@Composable
fun BarraCifra(nombre: String, a: String, b: String, ca: Color, cb: Color) {
    val x = a.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    val y = b.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(a, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 40.dp))
            Row(Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp).background(Line)) {
                if (x + y > 0f) {
                    Box(Modifier.weight(maxOf(x, 0.001f)).fillMaxHeight().background(ca))
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.weight(maxOf(y, 0.001f)).fillMaxHeight().background(cb))
                }
            }
            Text(b, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 40.dp))
        }
        Text(nombre.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.3.sp, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** El título de un bloque (portada, ficha): en grotesca, sin raya debajo; a la derecha, su cifra o su enlace en el acento. */
@Composable
fun TituloSeccion(t: String, modifier: Modifier = Modifier, derecha: String? = null, alDerecha: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp, maxLines = 2, modifier = Modifier.weight(1f))
        derecha?.let { d -> Text(d.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.4.sp, maxLines = 1,
            modifier = Modifier.padding(start = 10.dp, bottom = 3.dp).let { if (alDerecha != null) it.tocable(onClick = alDerecha) else it }) }
    }
}

/** El antetítulo de un grupo de partidos: «LALIGA» en el acento y una raya de tinta hasta el borde. */
@Composable
fun AntetituloRaya(t: String, modifier: Modifier = Modifier, color: Color = Acento) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(t.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(2.dp).background(Charcoal.copy(alpha = 0.9f)))
    }
}

/** El partido está terminado y [e] lo ganó. */
fun gano(p: Partido, e: Equipo): Boolean {
    val gl = p.golesLocal ?: return false; val gv = p.golesVisitante ?: return false
    if (!p.estado.terminado && !p.estado.enDirecto) return false
    return if (e.id == p.local.id) gl > gv else gv > gl
}
