package com.gonzalo.mostaza

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/** ¿Pantalla ancha? (la tablet, tumbada o de pie) */
@Composable
fun esAncha(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** El margen de las páginas: en la tablet, más (y el contenido no pasa de un ancho de revista). */
@Composable
fun margen(): Dp = if (LocalConfiguration.current.screenWidthDp >= 900) 48.dp else if (esAncha()) 36.dp else 22.dp

/**
 * El póster de una película o una serie, en 2:3. Sin imagen (o mientras llega), una tapa de papel con el título en serif, como las
 * tapas de tela de Marfil.
 */
@Composable
fun Poster(imagen: String, nombre: String, modifier: Modifier = Modifier) {
    val forma = esquina(4.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(forma).background(Sand)) {
        @Composable fun Tapa() = Box(Modifier.fillMaxSize().background(Sand).border(1.dp, Line, forma).padding(10.dp), contentAlignment = Alignment.Center) {
            Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        if (imagen.isBlank()) Tapa()
        else SubcomposeAsyncImage(model = imagen, contentDescription = nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            loading = { Tapa() }, error = { Tapa() })
    }
}

/** Una tarjeta de póster con su nombre debajo (siempre dos líneas reservadas: la fila no salta) y, si se pasa, lo que llevas visto. */
@Composable
fun TarjetaPoster(imagen: String, nombre: String, ancho: Dp = Dp.Unspecified, sub: String = "", progreso: Float? = null, alAbrir: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val foco by src.collectIsFocusedAsState()
    val escala by animateFloatAsState(if (foco) 1.06f else 1f, label = "poster")
    Column((if (ancho != Dp.Unspecified) Modifier.width(ancho) else Modifier.fillMaxWidth()).tocableCon(src) { alAbrir() }) {
        Box(Modifier.scale(escala)) {
            Poster(imagen, nombre, Modifier.fillMaxWidth())
            if (progreso != null && progreso > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).fillMaxHeight().background(Acento))
            }
        }
        Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Como [tocable], con la fuente de interacción de fuera (para saber si tiene el foco). */
fun Modifier.tocableCon(src: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.marcoFoco(src, 4.dp).clickable(interactionSource = src, indication = null, onClick = onClick)

/** Una fila de pósteres que se desliza, con su antetítulo y, si se pasa, «Ver todo». */
@Composable
fun FilaPosters(titulo: String, cosas: List<MetaBreve>, alAbrir: (MetaBreve) -> Unit, verTodo: (() -> Unit)? = null, sub: (MetaBreve) -> String = { it.ano }, progreso: (MetaBreve) -> Float? = { null }, imagen: (MetaBreve) -> String = { it.poster }) {
    val ancho = if (esAncha()) 140.dp else 108.dp
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Antetitulo(titulo, Modifier.weight(1f))
            if (verTodo != null) EnlaceRevista("Ver todo →", acento = true) { verTodo() }
        }
        Filete(1.dp)
        Spacer(Modifier.height(12.dp))
        LazyRow(Modifier.fillMaxWidth().sangrado(8.dp), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cosas, key = { it.tipo + it.id }) { m -> TarjetaPoster(imagen(m), m.nombre, ancho, sub(m), progreso(m)) { alAbrir(m) } }
        }
    }
}

/** Un texto de espera o de aviso, en cursiva (como en la revista). */
@Composable
fun Nota(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, color = color, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier)

/** «2 h 22 min» o «48 min». */
fun duracion(min: Int): String = if (min <= 0) "" else if (min >= 60) "${min / 60} h" + (if (min % 60 > 0) " ${min % 60} min" else "") else "$min min"

/** «Película», «Serie»… */
fun tipoNombre(t: String): String = when (t) { "movie" -> "Película"; "series" -> "Serie"; "channel" -> "Canal"; "tv" -> "Tele"; else -> t.replaceFirstChar { it.uppercase() } }
fun tiposNombre(t: String): String = when (t) { "movie" -> "Películas"; "series" -> "Series"; "channel" -> "Canales"; "tv" -> "Tele"; else -> t.replaceFirstChar { it.uppercase() } }

/** Una barra fina de lo visto. */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}
