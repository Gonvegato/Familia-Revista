package com.gonzalo.cobre.pc

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.mov.filaDe
import com.gonzalo.familia.pc.rememberImagenWeb
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Estado
import com.gonzalo.iptv.futbol.FichaPartido
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.iptv.futbol.Orden
import com.gonzalo.iptv.futbol.Partido
import com.gonzalo.iptv.futbol.TipoEvento

/*
 * Las piezas del partido de Nut en el ordenador (las mismas que Nut 0.9 del móvil, Duelo.kt): una sola letra (la grotesca), escudos
 * limpios a su tamaño (sin escudo, una forma de escudo con sus colores), el marcador con los colores de los dos equipos partidos en
 * diagonal bajo un velo oscuro y «en directo» con un punto que late.
 */

/** El rojo de «en directo». */
val VIVO = Color(0xFFD6332A)

private val COLORES_CLUB = listOf(
    Color(0xFFB6202A), Color(0xFF123A7A), Color(0xFF0B6E3D), Color(0xFF6A1B6E), Color(0xFFD9822B), Color(0xFF1B6F9E),
    Color(0xFF7A1F1F), Color(0xFF2E4053), Color(0xFF8C6D1F), Color(0xFF1F5E57),
)

/** El color «de club» de un equipo cuando aún no se sabe el de su escudo: siempre el mismo para el mismo equipo. */
fun colorDe(e: Equipo): Color = COLORES_CLUB[Math.floorMod((e.id.ifBlank { e.nombre }).hashCode(), COLORES_CLUB.size)]

private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
private fun luz(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

/** El color medio de lo que se ve de una imagen (sin lo transparente: el de un escudo es el del club). */
private fun colorMedio(img: ImageBitmap): Color? {
    val p = img.toPixelMap()
    var r = 0f; var g = 0f; var b = 0f; var n = 0
    val pasoX = maxOf(1, p.width / 24); val pasoY = maxOf(1, p.height / 24)
    for (y in 0 until p.height step pasoY) for (x in 0 until p.width step pasoX) { val c = p[x, y]; if (c.alpha < 0.5f) continue; r += c.red; g += c.green; b += c.blue; n++ }
    return if (n == 0) null else Color(r / n, g / n, b / n)
}

/** La dirección del escudo de ESPN para fondos oscuros, si la hay. */
private fun oscuroEspn(url: String): String? = url.takeIf { "/teamlogos/" in it && "/500/" in it }?.replace("/500/", "/500-dark/")

/** El color de un equipo para pintar su lado del marcador: el de su escudo (o el suyo de reserva), sin pasarse de claro. */
@Composable
fun colorDuelo(e: Equipo): Color {
    val img = if (e.escudo.isNotBlank()) rememberImagenWeb(e.escudo) else null
    val c = remember(img) { img?.let { runCatching { colorMedio(it) }.getOrNull() } } ?: colorDe(e)
    return if (luz(c) > 0.6f) lerp(c, Color.Black, 0.45f) else c
}

/** Una forma de escudo con los colores de un equipo (el que no tiene escudo, o mientras llega el suyo). */
@Composable
fun EscudoGenerico(e: Equipo, tam: Dp, modifier: Modifier = Modifier) {
    val a = colorDe(e); val b = lerp(a, Color.White, 0.55f)
    Canvas(modifier.size(tam)) {
        val w = size.width; val h = size.height
        val ew = w * 40f / 46f; val x0 = (w - ew) / 2f
        fun px(x: Float) = x0 + x / 40f * ew
        fun py(y: Float) = y / 46f * h
        val forma = Path().apply {
            moveTo(px(20f), py(2f)); lineTo(px(37f), py(7f)); lineTo(px(37f), py(22f))
            cubicTo(px(37f), py(33f), px(29f), py(41f), px(20f), py(44f))
            cubicTo(px(11f), py(41f), px(3f), py(33f), px(3f), py(22f)); lineTo(px(3f), py(7f)); close()
        }
        val mitad = Path().apply { moveTo(px(20f), py(2f)); lineTo(px(37f), py(7f)); lineTo(px(37f), py(22f)); cubicTo(px(37f), py(33f), px(29f), py(41f), px(20f), py(44f)); close() }
        drawPath(forma, a); drawPath(mitad, b, alpha = 0.9f)
        drawLine(a, Offset(px(3f), py(18f)), Offset(px(37f), py(18f)), strokeWidth = w * 0.05f)
        drawPath(forma, lerp(a, Color.Black, 0.3f), style = Stroke(width = w * 0.05f))
    }
}

/** El escudo de un equipo, limpio y a su tamaño; [oscuro]: va sobre el marcador (la variante oscura de ESPN, si la hay). */
@Composable
fun Escudo(e: Equipo, tam: Dp, modifier: Modifier = Modifier, oscuro: Boolean = false) {
    val url = if (e.escudo.isBlank()) "" else (if (oscuro) oscuroEspn(e.escudo) else null) ?: e.escudo
    val img = if (url.isNotBlank()) rememberImagenWeb(url) else null
    val normal = if (img == null && oscuro && e.escudo.isNotBlank() && url != e.escudo) rememberImagenWeb(e.escudo) else null
    Box(modifier.size(tam), contentAlignment = Alignment.Center) {
        val i = img ?: normal
        if (i != null) Image(i, e.nombre, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else EscudoGenerico(e, tam)
    }
}

/** El fondo del marcador: el color de un equipo a la izquierda y el del otro a la derecha, partidos en diagonal, con un velo oscuro. */
@Composable
fun FondoDuelo(local: Equipo, visitante: Equipo, modifier: Modifier = Modifier) {
    val c1 = colorDuelo(local); val c20 = colorDuelo(visitante)
    // (si los dos salen casi del mismo color, el de fuera toma otro: si no, la diagonal no se ve)
    val parecidos = kotlin.math.abs(c1.red - c20.red) + kotlin.math.abs(c1.green - c20.green) + kotlin.math.abs(c1.blue - c20.blue) < 0.18f
    val c2 = if (!parecidos) c20 else COLORES_CLUB.firstOrNull { o -> kotlin.math.abs(c1.red - o.red) + kotlin.math.abs(c1.green - o.green) + kotlin.math.abs(c1.blue - o.blue) > 0.5f } ?: c20
    Canvas(modifier.clipToBounds()) {
        val w = size.width; val h = size.height; val d = h * 0.23f
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

/** «PRÓXIMO · SÁB 14 NOV · 21:00», «EN DIRECTO · 67'», «FINAL». */
fun etiquetaPartido(p: Partido, ahora: Long): String = when {
    p.estado.enDirecto -> "En directo · " + p.cuando(ahora)
    p.estado == Estado.POR_JUGAR -> "Próximo · " + Hora.diaYHora(p.inicio, ahora)
    else -> p.estado.texto
}.uppercase()

/** El estado sobre el marcador: en juego, una pastilla blanca con el punto que late; si no, el texto con un marco fino. */
@Composable
fun EtiquetaEstado(p: Partido, ahora: Long, siNoVivo: String? = null, modifier: Modifier = Modifier) {
    if (p.estado.enDirecto) {
        Row(modifier.background(Color.White).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            PuntoVivo(); Spacer(Modifier.width(7.dp))
            Text(("En directo · " + p.cuando(ahora)).uppercase(), color = Color(0xFF0B0A09), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1, softWrap = false)
        }
    } else Text((siNoVivo ?: etiquetaPartido(p, ahora)).uppercase(), color = Color.White, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 2,
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.7f)).padding(horizontal = 9.dp, vertical = 4.dp))
}

/** Los dos equipos enfrentados (letras blancas, sobre [FondoDuelo]): escudo y nombre de cada uno y el marcador (o la hora) en medio. */
@Composable
fun Duelo(p: Partido, tamEscudo: Dp, alEquipo: ((Equipo) -> Unit)? = null, modifier: Modifier = Modifier) {
    val gl = p.golesLocal; val gv = p.golesVisitante
    val conGoles = gl != null && gv != null
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Lado(e: Equipo) = Column(Modifier.weight(1f).filaDe("e:" + e.id).let { if (alEquipo != null) it.levanta(alto = 2.dp) { alEquipo(e) } else it },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Escudo(e, tamEscudo, oscuro = true)
            Text(e.nombre, color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
        Lado(p.local)
        Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (conGoles) "$gl–$gv" else Hora.reloj(p.inicio), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = if (conGoles) 64.sp else 46.sp, letterSpacing = (-1).sp, maxLines = 1, softWrap = false)
            if (p.penaltis.isNotBlank()) Text("Penaltis ${p.penaltis}", color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Lado(p.visitante)
    }
}

/** Quién marcó, debajo del marcador: «Raphinha 8′, 41′ · Lamine Yamal 64′ — Julián Álvarez 52′» (los de casa a la izquierda). */
fun goleadores(f: FichaPartido): String {
    val p = f.partido
    fun de(id: String) = f.eventos.filter { it.tipo.esGol && it.equipo == id }
        .groupBy { e -> e.jugador.ifBlank { "Gol" } + if (e.tipo == TipoEvento.GOL_PROPIA) " (p. p.)" else "" }
        .entries.joinToString(" · ") { (quien, l) -> "$quien " + l.joinToString(", ") { it.minuto } }
    val a = de(p.local.id); val b = de(p.visitante.id)
    return when { a.isBlank() && b.isBlank() -> ""; a.isBlank() -> "— $b"; b.isBlank() -> "$a —"; else -> "$a — $b" }
}

/** El antetítulo de un grupo de partidos: «LALIGA» en el acento y una raya de tinta hasta el borde. */
@Composable
fun AntetituloRaya(t: String, modifier: Modifier = Modifier, color: Color = Acento) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(t.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(2.dp).background(Charcoal))
    }
}

/** Un equipo y sus goles, en una línea; el que gana, en negrita; un rival, en cursiva. */
@Composable
fun LineaEquipo(e: Equipo, goles: Int?, tam: Dp, letra: Int, ganador: Boolean, mio: Boolean, rival: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Escudo(e, tam)
        Spacer(Modifier.width(10.dp))
        Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontStyle = if (rival) FontStyle.Italic else null, fontWeight = if (ganador) FontWeight.ExtraBold else if (mio) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = letra.sp, lineHeight = (letra * 1.18f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (goles != null) Text("$goles", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = (letra + 1).sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
    }
}

/**
 * Un partido en una fila: a la izquierda la hora (o el minuto, en rojo, o «FINAL»); los dos equipos con sus escudos y sus goles. Una raya
 * fina debajo; ninguna entre los dos equipos. [debajo]: lo que va dentro del partido (los canales de los tuyos).
 */
@Composable
fun FilaPartido(p: Partido, mios: Set<String>, rivales: Set<String>, conDia: Boolean = false, debajo: @Composable () -> Unit = {}, alAbrir: () -> Unit) {
    val vivo = p.estado.enDirecto
    val ahora = System.currentTimeMillis()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().filaDe("p:" + p.id).levanta(alto = 1.dp, alPulsar = alAbrir).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(if (conDia) 96.dp else 64.dp)) {
                if (conDia) Text((if (vivo) "En directo" else Hora.diaRelativo(p.inicio, ahora)).uppercase(), color = if (vivo) VIVO else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 0.8.sp, maxLines = 1)
                Text(com.gonzalo.iptv.futbol.Dia.cuando(p).uppercase(), color = if (vivo) VIVO else if (conDia && p.estado == Estado.POR_JUGAR) Charcoal else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1, softWrap = false)
                if (conDia) Text(p.competicion.corto.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, letterSpacing = 0.8.sp, maxLines = 1)
                val marca = when {
                    Orden.contraRival(p, mios, rivales) -> "Tu rival"
                    rivales.any { p.juega(it) } && mios.none { p.juega(it) } -> "Rival"
                    else -> null
                }
                if (marca != null) Text(marca.uppercase(), color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp).border(1.dp, Charcoal).padding(horizontal = 5.dp, vertical = 1.dp))
            }
            Column(Modifier.weight(1f)) {
                val gl = p.golesLocal; val gv = p.golesVisitante
                val fin = p.estado.terminado || vivo
                LineaEquipo(p.local, gl, 24.dp, 16, gl != null && gv != null && gl > gv && fin, p.local.id in mios, p.local.id in rivales)
                LineaEquipo(p.visitante, gv, 24.dp, 16, gl != null && gv != null && gv > gl && fin, p.visitante.id in mios, p.visitante.id in rivales)
            }
        }
        debajo()
        Filete(1.dp, Line)
    }
}

/** El resultado de un partido terminado para [equipo]: 'V', 'E' o 'D' (null si no lo jugó o no ha terminado). */
fun resultadoDe(p: Partido, equipo: String): Char? {
    if (!p.estado.terminado || !p.juega(equipo)) return null
    val gl = p.golesLocal ?: return null; val gv = p.golesVisitante ?: return null
    val (a, b) = if (p.local.id == equipo) gl to gv else gv to gl
    return when { a == b -> 'E'; a > b -> 'V'; else -> 'D' }
}

/** Los cinco últimos resultados de un equipo, del más antiguo al último. */
fun formaDe(partidos: Collection<Partido>, equipo: String, n: Int = 5): List<Char> = Orden.jugados(partidos, equipo).mapNotNull { resultadoDe(it, equipo) }.take(n).reversed()

/** Los cinco últimos en cuadritos de color con su letra: G, E, P. */
@Composable
fun BloquesForma(forma: List<Char>, modifier: Modifier = Modifier, lado: Dp = 18.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        forma.forEach { r ->
            val c = when (r) { 'V' -> Color(0xFF2E7D4F); 'D' -> VIVO; else -> Color(0xFF9A948A) }
            Box(Modifier.size(lado).background(c), contentAlignment = Alignment.Center) {
                Text(when (r) { 'V' -> "G"; 'D' -> "P"; else -> "E" }, color = Color.White, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = (lado.value * 0.55f).sp, maxLines = 1)
            }
        }
        repeat(5 - forma.size) { Box(Modifier.size(lado).background(Line)) }
    }
}

/** Un día de la fila de días: el elegido, en un bloque de tinta; los demás, en gris, sin marco. */
@Composable
fun PildoraDia(texto: String, on: Boolean, modifier: Modifier = Modifier, alPulsar: () -> Unit) {
    Text(texto.uppercase(), color = if (on) Paper else Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false,
        modifier = modifier.levanta(alto = 0.dp, hundido = 0.94f, alPulsar = alPulsar).background(if (on) Charcoal else Color.Transparent).padding(horizontal = 12.dp, vertical = 9.dp))
}

/** Una cifra del partido para los dos: sus números a los lados y una barra con el color de cada equipo; debajo, su nombre. */
@Composable
fun BarraCifra(nombre: String, a: String, b: String, ca: Color, cb: Color) {
    val x = a.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    val y = b.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(a, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 48.dp))
            Row(Modifier.weight(1f).padding(horizontal = 12.dp).height(7.dp).background(Line)) {
                if (x + y > 0f) {
                    Box(Modifier.weight(maxOf(x, 0.001f)).fillMaxHeight().background(ca))
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.weight(maxOf(y, 0.001f)).fillMaxHeight().background(cb))
                }
            }
            Text(b, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 48.dp))
        }
        Text(nombre.uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.3.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** El pie de lo que sale de ESPN. */
@Composable
fun PieFuente(modifier: Modifier = Modifier) = Text(Futbol.FUENTE, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = modifier.padding(top = 18.dp))
