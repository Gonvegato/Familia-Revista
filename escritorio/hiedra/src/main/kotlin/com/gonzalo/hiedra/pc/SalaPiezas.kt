package com.gonzalo.hiedra.pc

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.Body
import com.gonzalo.familia.pc.revista.Display
import com.gonzalo.familia.pc.revista.Skin
import com.gonzalo.familia.pc.revista.Tones

/**
 * Los colores de la sala: los de tu apariencia de Hiedra, en su versión oscura (para la tele, de noche, mejor que un papel blanco; en
 * Ajustes se puede pedir que siga a la de la app). Son los de la revista: papel, tinta, el gris, las líneas y el acento.
 */
class TintaSala(t: Tones) {
    val fondo = t.cPaper; val tarjeta = t.cCrudo; val arena = t.cSand; val linea = t.cLine; val borde = t.cEdge
    val tinta = t.cInk; val gris = t.cMuted; val acento = Color(t.general)
    val sobreAcento = if (0.299f * acento.red + 0.587f * acento.green + 0.114f * acento.blue > 0.5f) Color(0xFF14151A) else Color.White
    val oscura = t.darkBg
}
val LocalTinta = staticCompositionLocalOf { TintaSala(Skin.tonosPara(true)) }

/** Las esquinas de las cosas de la sala (las de tu forma de la revista: rectas, redondas o biseladas). */
fun formaSala(r: Dp) = com.gonzalo.familia.pc.revista.esquina(r)

// ---------------------------------------------------------------- los botones del mando, dibujados

/** Lo que se enseña de un botón, según el mando (o el teclado) que estés usando. */
fun nombreBoton(a: Accion, t: Mando.Tipo, teclado: Boolean): String = if (teclado) when (a) {
    Accion.A -> "Intro"; Accion.B -> "Esc"; Accion.X -> "F"; Accion.Y -> "Espacio"; Accion.LB -> "Q"; Accion.RB -> "E"; Accion.LT -> "Inicio"; Accion.RT -> "Fin"
    Accion.MENU -> "S"; Accion.VISTA -> "F11"; Accion.ARRIBA -> "↑"; Accion.ABAJO -> "↓"; Accion.IZQUIERDA -> "←"; Accion.DERECHA -> "→"
} else when (t) {
    Mando.Tipo.PLAYSTATION -> when (a) { Accion.A -> "✕"; Accion.B -> "○"; Accion.X -> "□"; Accion.Y -> "△"; Accion.LB -> "L1"; Accion.RB -> "R1"; Accion.LT -> "L2"; Accion.RT -> "R2"; Accion.MENU -> "Options"; Accion.VISTA -> "Create"; else -> "✛" }
    Mando.Tipo.NINTENDO -> when (a) { Accion.A -> "B"; Accion.B -> "A"; Accion.X -> "Y"; Accion.Y -> "X"; Accion.LB -> "L"; Accion.RB -> "R"; Accion.LT -> "ZL"; Accion.RT -> "ZR"; Accion.MENU -> "+"; Accion.VISTA -> "−"; else -> "✛" }
    else -> when (a) { Accion.A -> "A"; Accion.B -> "B"; Accion.X -> "X"; Accion.Y -> "Y"; Accion.LB -> "LB"; Accion.RB -> "RB"; Accion.LT -> "LT"; Accion.RT -> "RT"; Accion.MENU -> "☰"; Accion.VISTA -> "⧉"; else -> "✛" }
}

/** El color de un botón (los de Xbox y PlayStation tienen el suyo; los de Nintendo y el teclado, la tinta). */
private fun colorBoton(a: Accion, t: Mando.Tipo): Color? = when (t) {
    Mando.Tipo.XBOX, Mando.Tipo.GENERICO -> when (a) { Accion.A -> Color(0xFF6CC24A); Accion.B -> Color(0xFFE5483B); Accion.X -> Color(0xFF3D8FE5); Accion.Y -> Color(0xFFF2C230); else -> null }
    Mando.Tipo.PLAYSTATION -> when (a) { Accion.A -> Color(0xFF8DB4F2); Accion.B -> Color(0xFFF07F80); Accion.X -> Color(0xFFE79CCD); Accion.Y -> Color(0xFF63CDB0); else -> null }
    else -> null
}

/** ¿Se enseñan las teclas en vez de los botones? (si estás con el teclado, o con el ratón y sin mando) */
@Composable
fun conTeclado(): Boolean = Entrada.con == ConQue.TECLADO || (Entrada.con == ConQue.RATON && Mando.mandos.isEmpty())

/** Un botón del mando dibujado (o la tecla, con el teclado). */
@Composable
fun Glifo(a: Accion, tam: Dp = 30.dp) {
    val tinta = LocalTinta.current
    val teclado = conTeclado()
    val t = Mando.tipo
    val texto = nombreBoton(a, t, teclado)
    val redondo = !teclado && a in setOf(Accion.A, Accion.B, Accion.X, Accion.Y)
    if (redondo) {
        val c = colorBoton(a, t) ?: tinta.tinta
        Box(Modifier.size(tam).clip(CircleShape).background(tinta.tinta.copy(alpha = if (tinta.oscura) 0.92f else 0.9f)).border(1.5.dp, c.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
            if (t == Mando.Tipo.PLAYSTATION) FormaPlay(a, c, tam * 0.46f)
            else Text(texto, color = c, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = (tam.value * 0.5f).sp, lineHeight = (tam.value * 0.5f).sp)
        }
    } else {
        Box(Modifier.heightIn(min = tam).widthIn(min = tam).clip(RoundedCornerShape(tam * 0.3f)).background(tinta.tinta.copy(alpha = 0.9f)).padding(horizontal = (tam.value * 0.28f).dp),
            contentAlignment = Alignment.Center) {
            Text(texto.uppercase(), color = tinta.fondo, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = (tam.value * if (texto.length > 3) 0.34f else 0.42f).sp,
                letterSpacing = 0.5.sp, maxLines = 1, lineHeight = (tam.value * 0.5f).sp)
        }
    }
}

/** Las figuras de los botones de PlayStation, a trazo (mejor que con una letra). */
@Composable
private fun FormaPlay(a: Accion, c: Color, tam: Dp) {
    Canvas(Modifier.size(tam)) {
        val g = size.minDimension * 0.16f
        val s = Stroke(g)
        val w = size.width; val h = size.height
        when (a) {
            Accion.A -> { drawLine(c, Offset(0f, 0f), Offset(w, h), g); drawLine(c, Offset(w, 0f), Offset(0f, h), g) }
            Accion.B -> drawCircle(c, w / 2 - g / 2, style = s)
            Accion.X -> drawRect(c, Offset(g / 2, g / 2), androidx.compose.ui.geometry.Size(w - g, h - g), style = s)
            else -> drawPath(Path().apply { moveTo(w / 2, g / 2); lineTo(w - g / 2, h - g / 2); lineTo(g / 2, h - g / 2); close() }, c, style = s)
        }
    }
}

/** Un botón y lo que hace («A Jugar»), para la barra de abajo. */
@Composable
fun Pista(a: Accion, texto: String) {
    val tinta = LocalTinta.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glifo(a, 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(texto.uppercase(), color = tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.6.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- portadas

/** Una portada de la sala: la tuya de la cuenta, la de internet o la de este ordenador; si no hay, su título compuesto como una tapa. */
@Composable
fun PortadaSala(f: Ficha, modifier: Modifier = Modifier, radio: Dp = 10.dp, sombra: Dp = 0.dp) {
    val tinta = LocalTinta.current
    val img = rememberImagen(f.portada, f.portadaUrl)
    val forma = formaSala(radio)
    Box(modifier.aspectRatio(2f / 3f).let { if (sombra > 0.dp) it.shadow(sombra, forma, clip = false) else it }.clip(forma).background(tinta.arena)) {
        Crossfade(img, animationSpec = tween(260), label = "portada") { i ->
            if (i != null) Image(i, f.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Tapa(f)
        }
        if (!f.jugable) Box(Modifier.fillMaxSize().background(tinta.fondo.copy(alpha = 0.18f)))
    }
}

/** La tapa de letra (sin imagen): el color sale del título (siempre el mismo), la inicial grande y el nombre en la letra de los títulos. */
@Composable
fun Tapa(f: Ficha) {
    val tinta = LocalTinta.current
    val h = (Juegos.norm(f.titulo).hashCode() and 0x7fffffff) % 360
    val base = Color.hsv(h.toFloat(), 0.38f, if (tinta.oscura) 0.36f else 0.78f)
    val claro = lerp(base, tinta.tinta, 0.12f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(claro, base)))) {
        Text(f.titulo.trim().take(1).uppercase(), Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 2.dp), color = Color.White.copy(alpha = 0.16f),
            fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 120.sp, lineHeight = 120.sp)
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(26.dp).height(3.dp).background(tinta.acento))
            Text(f.titulo, color = Color.White, fontFamily = Display, fontSize = 22.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text(f.etiqueta.uppercase(), color = Color.White.copy(alpha = 0.7f), fontFamily = Body, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * El fondo de un juego: su imagen grande (la de Steam o la de su tienda) o, si no tiene, su portada ampliada y desenfocada; con un velo del
 * color del papel que la funde con la página por abajo y por la izquierda (donde va el texto).
 */
@Composable
fun FondoJuego(f: Ficha?, modifier: Modifier = Modifier, velo: Float = 1f) {
    val tinta = LocalTinta.current
    Box(modifier.background(tinta.fondo)) {
        Crossfade(f?.clave, animationSpec = tween(520), label = "fondo") { clave ->
            val ff = f?.takeIf { it.clave == clave }
            if (ff != null) {
                val grande = rememberImagen(ff.fondo, fondo = true)
                val portada = rememberImagen(ff.portada, ff.portadaUrl)
                Box(Modifier.fillMaxSize()) {
                    when {
                        grande != null -> Image(grande, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                        portada != null -> Image(portada, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }.blur(38.dp), contentScale = ContentScale.Crop, alpha = 0.75f)
                    }
                }
            }
        }
        // El velo: de izquierda a derecha y de abajo arriba
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to tinta.fondo.copy(alpha = 0.96f * velo), 0.42f to tinta.fondo.copy(alpha = 0.72f * velo), 1f to tinta.fondo.copy(alpha = 0.10f * velo))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to tinta.fondo.copy(alpha = 0.25f * velo), 1f to tinta.fondo)))
    }
}

// ---------------------------------------------------------------- textos y botones

/** Un antetítulo de la sala: el cuadradito de acento y las versalitas espaciadas (el de la revista, más grande). */
@Composable
fun AnteSala(texto: String, modifier: Modifier = Modifier, tam: TextUnit = 17.sp, color: Color = LocalTinta.current.tinta) {
    val tinta = LocalTinta.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size((tam.value * 0.72f).dp).background(tinta.acento))
        Spacer(Modifier.width(12.dp))
        Text(texto.uppercase(), color = color, fontFamily = Body, fontSize = tam, fontWeight = FontWeight.Black, letterSpacing = (tam.value * 0.16f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Un botón de la sala: el principal relleno del acento («JUGAR»), los demás con borde; se enfoca como todo lo demás. */
@Composable
fun BotonSala(texto: String, id: String, modifier: Modifier = Modifier, principal: Boolean = false, icono: String? = null, activo: Boolean = true, alto: Dp = 64.dp, alPulsar: () -> Unit) {
    val tinta = LocalTinta.current
    val forma = formaSala(12.dp)
    val fondo = if (principal) tinta.acento else tinta.tarjeta.copy(alpha = 0.85f)
    val letra = if (principal) tinta.sobreAcento else tinta.tinta
    Row(modifier.heightIn(min = alto).enfocable(id, radio = 12.dp, crece = 1.04f) { if (activo) alPulsar() }.clip(forma).background(if (activo) fondo else fondo.copy(alpha = 0.4f))
        .let { if (principal) it else it.border(1.5.dp, tinta.borde.copy(alpha = 0.6f), forma) }.padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icono != null) { Text(icono, color = letra, fontSize = 22.sp, fontFamily = Body); Spacer(Modifier.width(12.dp)) }
        Text(texto.uppercase(), color = letra.copy(alpha = if (activo) 1f else 0.5f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Una línea de datos (etiqueta · puntos · valor), la de la revista en grande. */
@Composable
fun DatoSala(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    val tinta = LocalTinta.current
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Bottom) {
        Text(etiqueta, color = tinta.gris, fontFamily = Body, fontSize = 19.sp, maxLines = 1)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(14.dp).drawWithContent {
            val y = size.height - 3f
            var x = 0f
            while (x < size.width) { drawCircle(tinta.gris.copy(alpha = 0.55f), 1.4f, Offset(x, y)); x += 8f }
        })
        Text(valor, color = tinta.tinta, fontFamily = Body, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
