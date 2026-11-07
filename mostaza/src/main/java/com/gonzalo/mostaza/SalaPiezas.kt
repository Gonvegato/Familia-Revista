package com.gonzalo.mostaza

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * Los colores de la sala: los de tu apariencia de Mostaza, en su versión oscura (para la tele, de noche y con las luces bajas, mejor que un
 * papel blanco; como la sala de Hiedra). Son los de la revista: papel, tinta, el gris, las líneas y el acento.
 */
class TintaSala(t: Tones) {
    val fondo = t.cPaper; val tarjeta = t.cCrudo; val arena = t.cSand; val linea = t.cLine; val borde = t.cEdge
    val tinta = t.cInk; val gris = t.cMuted; val acento = Color(t.general)
    val sobreAcento = if (0.299f * acento.red + 0.587f * acento.green + 0.114f * acento.blue > 0.5f) Color(0xFF14151A) else Color.White
    val oscura = t.darkBg
}
val LocalTinta = staticCompositionLocalOf { TintaSala(Skin.tonosPara(true)) }

/** Las esquinas de las cosas de la sala (las de tu forma de la revista: rectas, redondas o biseladas). */
fun formaSala(r: Dp) = esquina(r)

/** Los márgenes de la sala, a salvo del borde de cualquier tele (el 5 % que pide Google para el «overscan»: 48 × 27 dp de 960 × 540). */
val MARGEN_H = 96.dp
val MARGEN_V = 54.dp

// ---------------------------------------------------------------- pósteres y fondos

/** Un póster de la sala, en 2:3; sin imagen (o mientras llega), una tapa con el título en grande, del color que sale del título. */
@Composable
fun PosterSala(imagen: String, nombre: String, modifier: Modifier = Modifier, radio: Dp = 10.dp, sombra: Dp = 0.dp, progreso: Float? = null) {
    val tinta = LocalTinta.current
    val forma = formaSala(radio)
    Box(modifier.aspectRatio(2f / 3f).let { if (sombra > 0.dp) it.shadow(sombra, forma, clip = false) else it }.clip(forma).background(tinta.arena)) {
        if (imagen.isBlank()) TapaSala(nombre)
        else SubcomposeAsyncImage(model = imagen, contentDescription = nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            loading = { TapaSala(nombre) }, error = { TapaSala(nombre) })
        if (progreso != null && progreso > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(7.dp).background(Color(0x99000000))) {
            Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().background(tinta.acento))
        }
    }
}

/** La tapa de letra (sin imagen): el color sale del título (siempre el mismo), la inicial grande y el nombre en la letra de los títulos. */
@Composable
private fun TapaSala(nombre: String) {
    val tinta = LocalTinta.current
    val h = (nombre.lowercase().hashCode() and 0x7fffffff) % 360
    val base = Color.hsv(h.toFloat(), 0.38f, if (tinta.oscura) 0.36f else 0.78f)
    val claro = lerp(base, tinta.tinta, 0.12f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(claro, base)))) {
        Text(nombre.trim().take(1).uppercase(), Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 2.dp), color = Color.White.copy(alpha = 0.16f),
            fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 120.sp, lineHeight = 120.sp)
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(26.dp).height(3.dp).background(tinta.acento))
            Text(nombre, color = Color.White, fontFamily = Display, fontSize = 22.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * El fondo de una película o una serie: su imagen grande o, si no tiene, su póster ampliado y desenfocado; con un velo del color del papel
 * que lo funde con la página por abajo y por la izquierda (donde va el texto). Se funde al cambiar de una a otra.
 */
@Composable
fun FondoSala(fondo: String, poster: String, clave: String?, modifier: Modifier = Modifier, velo: Float = 1f) {
    val tinta = LocalTinta.current
    Box(modifier.clipToBounds().background(tinta.fondo)) {
        Crossfade(Triple(clave, fondo, poster), animationSpec = tween(520), label = "fondo") { (k, f, p) ->
            if (k != null) Box(Modifier.fillMaxSize()) {
                when {
                    f.isNotBlank() -> SubcomposeAsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    p.isNotBlank() -> SubcomposeAsyncImage(p, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }.blur(38.dp), contentScale = ContentScale.Crop, alpha = 0.6f)
                }
            }
        }
        // El velo: de izquierda a derecha y de abajo arriba
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to tinta.fondo.copy(alpha = 0.96f * velo), 0.45f to tinta.fondo.copy(alpha = 0.74f * velo), 1f to tinta.fondo.copy(alpha = 0.12f * velo))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to tinta.fondo.copy(alpha = 0.3f * velo), 1f to tinta.fondo)))
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

/** Un botón de la sala: el principal relleno del acento («REPRODUCIR»), los demás con borde; se enfoca como todo lo demás. */
@Composable
fun BotonSala(texto: String, id: String, modifier: Modifier = Modifier, principal: Boolean = false, icono: String? = null, activo: Boolean = true, alto: Dp = 68.dp, alPulsar: () -> Unit) {
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

/** Una pastilla de elegir (una sección, un filtro, una temporada): la elegida, rellena del acento. */
@Composable
fun ChipSala(texto: String, id: String, on: Boolean, modifier: Modifier = Modifier, alPulsar: () -> Unit) {
    val tinta = LocalTinta.current
    Text(texto.uppercase(), color = if (on) tinta.sobreAcento else tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.8.sp, maxLines = 1,
        modifier = modifier.enfocable(id, radio = 10.dp, crece = 1.05f, alPulsar = alPulsar).clip(formaSala(10.dp))
            .background(if (on) tinta.acento else tinta.tarjeta.copy(alpha = 0.7f)).padding(horizontal = 22.dp, vertical = 12.dp))
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
        Text(valor, color = tinta.tinta, fontFamily = Body, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 520.dp))
    }
}

/** Una nota de la sala (de espera o de aviso), en cursiva. */
@Composable
fun NotaSala(t: String, modifier: Modifier = Modifier, tam: TextUnit = 24.sp) {
    val tinta = LocalTinta.current
    Text(t, color = tinta.gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam, lineHeight = (tam.value * 1.35f).sp, modifier = modifier)
}

/** Una opción de un panel: una línea grande que se enfoca (con la marca a la izquierda si es la elegida). */
@Composable
fun OpcionSala(texto: String, id: String, sub: String = "", marcada: Boolean = false, peligro: Boolean = false, alPulsar: () -> Unit) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val on = foco.actual == id
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).enfocable(id, radio = 10.dp, crece = 1.02f, alPulsar = alPulsar).clip(formaSala(10.dp))
        .background(if (on && foco.visible) tinta.tarjeta else Color.Transparent).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(26.dp).background(if (marcada) tinta.acento else Color.Transparent))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(texto, color = if (peligro) tinta.acento else tinta.tinta, fontFamily = Display, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = if (marcada) FontWeight.Bold else FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = tinta.gris, fontFamily = Body, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (marcada) Text("✓", color = tinta.acento, fontSize = 26.sp, fontFamily = Body, fontWeight = FontWeight.Bold)
    }
}

/** Un panel a la derecha, encima de todo (el fondo se oscurece): el menú, las fuentes. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Lateral(titulo: String, ante: String, ancho: Dp = 680.dp, alCerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    val tinta = LocalTinta.current
    var dentro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dentro = true }
    val x by animateFloatAsState(if (dentro) 0f else 1f, spring(dampingRatio = 0.86f, stiffness = 420f), label = "panel")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * (1f - x))).pointerInput(Unit) { detectTapGestures(onTap = { alCerrar() }) }) {
        Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(ancho).graphicsLayer { translationX = x * size.width * 1.05f }.background(tinta.fondo)
            .pointerInput(Unit) { detectTapGestures {} }.padding(start = 56.dp, end = MARGEN_H, top = MARGEN_V + 10.dp, bottom = MARGEN_V)) {
            Text(ante.uppercase(), color = tinta.acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(titulo, color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(tinta.tinta))
            Spacer(Modifier.height(18.dp))
            CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides remember { TraerAVista(160f) }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = contenido)
            }
        }
    }
}

/** Una tecla del mando a distancia dibujada («OK», «ATRÁS», «☰»), para la barra de abajo. */
@Composable
fun TeclaMando(t: String, tam: Dp = 30.dp) {
    val tinta = LocalTinta.current
    Box(Modifier.heightIn(min = tam).widthIn(min = tam).clip(RoundedCornerShape(tam * 0.5f)).background(tinta.tinta.copy(alpha = 0.9f)).padding(horizontal = (tam.value * 0.32f).dp),
        contentAlignment = Alignment.Center) {
        Text(t.uppercase(), color = tinta.fondo, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = (tam.value * if (t.length > 3) 0.36f else 0.44f).sp,
            letterSpacing = 0.5.sp, maxLines = 1, lineHeight = (tam.value * 0.5f).sp)
    }
}

/** Una tecla y lo que hace («OK Abrir»), para la barra de abajo. */
@Composable
fun PistaSala(tecla: String, texto: String) {
    val tinta = LocalTinta.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeclaMando(tecla)
        Spacer(Modifier.width(10.dp))
        Text(texto.uppercase(), color = tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.6.sp, maxLines = 1)
    }
}
