package com.gonzalo.familia.pc.revista

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.VentanaFamilia
import com.gonzalo.familia.pc.mov.Atajos
import com.gonzalo.familia.pc.mov.PestanasPager
import com.gonzalo.familia.pc.mov.Teclas
import com.gonzalo.familia.pc.mov.apareceSegun
import com.gonzalo.familia.pc.mov.pasoDePagina
import com.gonzalo.familia.pc.mov.pulsable
import kotlinx.coroutines.launch

/*
 * La cara C de la familia en el ordenador (la de las apps del móvil desde Seshat 0.20): una cabecera de tinta con el nombre en grotesca
 * enorme, una fila de pestañas con el bloque del acento y las páginas que se pasan de lado. Lo comparten las seis apps del Mac.
 */

/** La grotesca de las hermanas (la misma letra que en el móvil). */
val Grotesca: FontFamily get() = VitrinaDisplay

/** El azul de los bloques de cifras (el mismo que en el móvil). */
val Bloque2: Color get() = Color(0xFF1F3A5F)

/** El nombre de la app en grotesca con su punto del acento: «SESHAT.». */
@Composable
fun NombreConPunto(nombre: String, tam: TextUnit, color: Color = Charcoal, modifier: Modifier = Modifier, punto: Color = Acento) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(nombre.uppercase(), color = color, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = tam, lineHeight = tam, letterSpacing = (-tam.value * 0.035f).sp, maxLines = 1, softWrap = false)
        Text(".", color = punto, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = tam, lineHeight = tam, maxLines = 1, softWrap = false)
    }
}

/**
 * Al pasar el ratón, la fila (o la tarjeta) se levanta un poco y el puntero es la mano; al pulsar, se hunde con el muelle (lo del móvil)
 * y hace [alPulsar].
 */
fun Modifier.levanta(enabled: Boolean = true, hundido: Float = 0.975f, alto: Dp = 3.dp, alPulsar: () -> Unit): Modifier = composed {
    val h = remember { MutableInteractionSource() }
    val encima by h.collectIsHoveredAsState()
    val alza by animateFloatAsState(if (encima && enabled) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "alza")
    this.hoverable(h).pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .graphicsLayer { translationY = -alto.toPx() * alza; shadowElevation = 10.dp.toPx() * alza }
        .pulsable(enabled = enabled, hundido = hundido, onClick = alPulsar)
}

/** Un enlace de la cabecera de tinta: «BUSCAR», «+ AÑADIR»… (con su atajo en gris al lado, si lo tiene). */
@Composable
fun EnlaceCabecera(texto: String, color: Color, atajo: String? = null, modifier: Modifier = Modifier, alPulsar: () -> Unit) {
    Row(modifier.levanta(alto = 0.dp, hundido = 0.94f, alPulsar = alPulsar).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto.uppercase(), color = color, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.6.sp, maxLines = 1, softWrap = false)
        if (atajo != null) Text(atajo, color = color.copy(alpha = 0.55f), fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
    }
}

/** «⌘» en el Mac y «Ctrl+» en Linux, para enseñar los atajos. */
val TeclaMando: String get() = if (System.getProperty("os.name").orEmpty().lowercase().contains("mac")) "⌘" else "Ctrl+"

/**
 * Las páginas de una app (dirección C, como en el móvil): la cabecera de tinta (la fila pequeña con [enlaces] a la izquierda, la fecha y ⚙
 * a la derecha, y el nombre enorme, que se recoge al bajar en una página), la fila de pestañas y [pagina] para cada una, que se pasan
 * tocando la pestaña, con ⌘1…⌘9 o deslizando dos dedos en el trackpad. En el Mac, la barra de la ventana toma el color de la tinta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaginasC(
    nombre: String, nombres: List<String>, cuentas: List<Int?>, pager: PagerState,
    alAjustes: (() -> Unit)?,
    enlaces: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    pagina: @Composable (Int) -> Unit,
) {
    val ambito = rememberCoroutineScope()
    // La barra de la ventana del Mac, del color de la tinta mientras se ven las páginas
    val tinta = Charcoal
    DisposableEffect(tinta) { VentanaFamilia.barraDeTinta = tinta; onDispose { VentanaFamilia.barraDeTinta = null } }
    // ⌘1…⌘9: a esa página
    Teclas { k ->
        val n = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine).indexOf(k.key)
        if (Atajos.mando(k) && n in nombres.indices) { ambito.launch { pager.animateScrollToPage(n) }; true } else false
    }
    // El nombre grande se recoge al bajar (y vuelve al subir): lo que la página aún no ha movido, lo mueve la cabecera
    var altoNombre by remember { mutableIntStateOf(0) }
    var recogido by remember { mutableFloatStateOf(0f) }
    val conexion = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || altoNombre == 0) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, -(recogido - antes))
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, antes - recogido)
            }
        }
    }
    val plegado = if (altoNombre > 0) ((recogido / altoNombre - 0.6f) / 0.4f).coerceIn(0f, 1f) else 0f
    val margen = 48.dp
    Column(modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        Column(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal = margen)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp * plegado), verticalAlignment = Alignment.CenterVertically) {
                if (plegado > 0f) NombreConPunto(nombre, 24.sp, Paper, Modifier.apareceSegun(plegado).padding(end = 18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) { enlaces() }
                Spacer(Modifier.weight(1f))
                if (plegado < 1f) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = Paper.copy(alpha = 0.7f), fontFamily = Body,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1, modifier = Modifier.apareceSegun(1f - plegado))
                if (alAjustes != null) Text("⚙", color = Paper.copy(alpha = 0.7f), fontSize = 18.sp, modifier = Modifier.padding(start = 14.dp).levanta(alto = 0.dp, hundido = 0.9f, alPulsar = alAjustes).padding(6.dp))
            }
            // El nombre: su alto se mide una vez y se va quitando según se recoge; encoge con su hueco desde la esquina de arriba a la izquierda
            Box(Modifier.fillMaxWidth().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                if (altoNombre != p.height) altoNombre = p.height
                val h = (p.height - recogido.toInt()).coerceAtLeast(0)
                val escala = if (p.height > 0) h.toFloat() / p.height else 1f
                layout(p.width, h) { if (h > 0) p.placeWithLayer(0, 0) { scaleX = escala; scaleY = escala; transformOrigin = TransformOrigin(0f, 0f) } }
            }) {
                NombreConPunto(nombre, 96.sp, Paper, Modifier.padding(top = 2.dp, bottom = 16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().background(Paper), verticalAlignment = Alignment.Bottom) {
            PestanasPager(nombres, pager, cuentas, Paper, Charcoal, Acento, SobreAcento, Body, Grotesca, Modifier.weight(1f).padding(start = margen - 12.dp)) { i ->
                ambito.launch { pager.animateScrollToPage(i) }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), flingBehavior = pasoDePagina(pager), beyondViewportPageCount = 0) { i -> pagina(i) }
    }
}

/** Un bloque de cifras de una página (como en el móvil: el primero en el acento, el tercero en azul). */
class BloqueCifra(val valor: String, val etiqueta: String)

@Composable
fun BloquesCifras(cifras: List<BloqueCifra>, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min).border(3.dp, Charcoal)) {
        cifras.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.width(3.dp).fillMaxHeight().background(Charcoal))
            val (fondo, letra) = when (i) { 0 -> Acento to SobreAcento; 2 -> Bloque2 to Color.White; else -> Paper to Charcoal }
            Column(Modifier.weight(1f).background(fondo).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(c.valor.uppercase(), color = letra, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.etiqueta.uppercase(), color = letra, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Un filtro de una página (dirección C): un recuadro de tinta; el elegido, lleno. */
@Composable
fun FiltroC(texto: String, on: Boolean, modifier: Modifier = Modifier, alPulsar: () -> Unit) {
    Text(texto.uppercase(), color = if (on) Paper else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp, maxLines = 1, softWrap = false,
        modifier = modifier.levanta(alto = 1.dp, hundido = 0.95f, alPulsar = alPulsar).background(if (on) Charcoal else Color.Transparent).border(2.dp, Charcoal).padding(horizontal = 14.dp, vertical = 10.dp))
}

/** El título de un bloque de una página: en grotesca, sin raya debajo; a la derecha, su cifra o su enlace en el acento. */
@Composable
fun TituloC(t: String, modifier: Modifier = Modifier, derecha: String? = null, alDerecha: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, maxLines = 2, modifier = Modifier.weight(1f))
        derecha?.let { d -> Text(d.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.4.sp, maxLines = 1,
            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp).let { if (alDerecha != null) it.levanta(alto = 0.dp, alPulsar = alDerecha) else it }) }
    }
}

/** El botón grande de la acción principal (dirección C): una banda del acento con la flecha a la derecha. */
@Composable
fun BotonC(texto: String, modifier: Modifier = Modifier, fondo: Color = Acento, letra: Color = SobreAcento, alPulsar: () -> Unit) {
    Row(modifier.fillMaxWidth().levanta(alto = 0.dp, hundido = 0.985f, alPulsar = alPulsar).background(fondo).padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto.uppercase(), color = letra, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.4.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text("→", color = letra, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}
