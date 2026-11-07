package com.gonzalo.cobre

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.gonzalo.iptv.Programa
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ¿Pantalla ancha? (la tablet, tumbada o de pie, y la tele) */
@Composable
fun esAncha(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** ¿Es una tele (Android TV, Google TV)? Allí todo va con el mando: el foco se pone solo en lo primero de cada página. */
fun esTele(c: Context): Boolean =
    (c.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        c.packageManager.hasSystemFeature("android.software.leanback") && !c.packageManager.hasSystemFeature("android.hardware.touchscreen")

@Composable
fun enTele(): Boolean { val c = LocalContext.current; return remember { esTele(c) } }

/** El margen de las páginas: en la tablet y la tele, más (en la tele, también por la zona segura de los bordes). */
@Composable
fun margen(): Dp = if (LocalConfiguration.current.screenWidthDp >= 900) 48.dp else if (esAncha()) 36.dp else 22.dp

/**
 * En la tele (con el mando), lo que lleva esto recibe el foco al abrir la página: con el mando no se puede «tocar» la pantalla para
 * empezar, así que algo tiene que tener el foco desde el principio. Con el dedo no hace nada.
 */
fun Modifier.focoInicial(clave: Any? = Unit): Modifier = composed {
    if (!enTele()) return@composed this
    val f = remember { FocusRequester() }
    LaunchedEffect(clave) { runCatching { kotlinx.coroutines.delay(60); f.requestFocus() } }
    this.focusRequester(f)
}

/** Como [tocable], con la fuente de interacción de fuera (para saber si tiene el foco). */
fun Modifier.tocableCon(src: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.marcoFoco(src, 4.dp).clickable(interactionSource = src, indication = null, onClick = onClick)

/** El logo de un canal; sin logo (o mientras llega), sus iniciales sobre papel, como las tapas de Marfil. */
@Composable
fun Logo(url: String, nombre: String, modifier: Modifier = Modifier, tam: Dp = 44.dp) {
    val forma = esquina(4.dp)
    Box(modifier.size(tam).clip(forma).background(Sand), contentAlignment = Alignment.Center) {
        @Composable fun Iniciales() = Text(iniciales(nombre), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (tam.value * 0.36f).sp, maxLines = 1)
        if (url.isBlank()) Iniciales()
        else SubcomposeAsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(tam * 0.08f),
            loading = { Iniciales() }, error = { Iniciales() })
    }
}

/** «La 1 HD» → «L1»; «Antena 3» → «A3». */
fun iniciales(n: String): String {
    val limpio = com.gonzalo.iptv.Emparejador.normaliza(n).ifBlank { n.lowercase() }
    val palabras = n.replace(Regex("^[A-Za-z]{2,3}\\s*[:|]\\s*"), "").split(Regex("[\\s\\-_.]+")).filter { it.isNotBlank() && it.lowercase() !in setOf("hd", "fhd", "uhd", "4k", "sd") }
    val s = if (palabras.size >= 2) palabras.take(2).joinToString("") { it.take(1) } else limpio.take(2)
    return s.uppercase()
}

/**
 * El póster de una película o una serie, en 2:3. Sin imagen (o mientras llega), una tapa de papel con el título en serif, como las
 * tapas de tela de Marfil (y los pósteres de Mostaza).
 */
@Composable
fun Poster(imagen: String, nombre: String, modifier: Modifier = Modifier) {
    val forma = esquina(4.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(forma).background(Sand)) {
        @Composable fun Tapa() = Box(Modifier.fillMaxSize().background(Sand).border(1.dp, Line, forma).padding(10.dp), contentAlignment = Alignment.Center) {
            Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        if (imagen.isBlank()) Tapa()
        else SubcomposeAsyncImage(model = imagen, contentDescription = nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), loading = { Tapa() }, error = { Tapa() })
    }
}

/** Una tarjeta de póster con su nombre debajo (siempre dos líneas: la fila no salta) y, si se pasa, lo que llevas visto. */
@Composable
fun TarjetaPoster(imagen: String, nombre: String, ancho: Dp = Dp.Unspecified, sub: String = "", progreso: Float? = null, modifier: Modifier = Modifier, alAbrir: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val foco by src.collectIsFocusedAsState()
    val escala by animateFloatAsState(if (foco) 1.06f else 1f, label = "poster")
    Column((if (ancho != Dp.Unspecified) modifier.width(ancho) else modifier.fillMaxWidth()).tocableCon(src) { alAbrir() }) {
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

/** Un texto de espera o de aviso, en cursiva (como en la revista). */
@Composable
fun Nota(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, color = color, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier)

/** Una barra fina de lo que va (de un programa o de lo visto). */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier, fondo: Color = Line) {
    Box(modifier.fillMaxWidth().height(3.dp).background(fondo)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** «21:30». */
fun hora(ms: Long): String = SimpleDateFormat("HH:mm", Locale("es", "ES")).format(Date(ms))

/** «21:30–22:15 · Telediario». */
fun franja(p: Programa) = "${hora(p.inicio)}–${hora(p.fin)}"

/** «2 h 22 min» o «48 min». */
fun duracion(min: Int): String = if (min <= 0) "" else if (min >= 60) "${min / 60} h" + (if (min % 60 > 0) " ${min % 60} min" else "") else "$min min"

/** «1:23:45» o «23:45». */
fun tiempo(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}
