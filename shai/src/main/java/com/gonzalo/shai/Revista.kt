package com.gonzalo.shai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip

/*
 * Las piezas de la interfaz, al estilo de una red social: una sola letra (Inter), líneas finas, pestañas con su raya azul, filas de
 * ajustes con su flecha y nada de mayúsculas espaciadas. (Los nombres de las funciones son los de antes: así las pantallas no cambian.)
 */

/** Una línea fina de separación. */
@Composable
fun Filete(alto: Dp = 1.dp, color: Color = Charcoal, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(if (alto > 1.dp) 1.dp else alto).background(if (color == Charcoal) Line else color))
}

/** La línea bajo la cabecera. */
@Composable
fun FileteCabecera(alto: Dp = 1.dp) = Filete(alto)

/** Título de sección («Tus rivales», «Lo gastado»…): en negrita; en acento, más pequeño y gris (lo que va encima de un título). */
@Composable
fun Antetitulo(texto: String, modifier: Modifier = Modifier, tam: TextUnit = 13.sp, color: Color = Charcoal, conCuadro: Boolean = true) {
    if (color == Acento) Text(texto, color = Muted, fontFamily = Body, fontSize = tam, fontWeight = FontWeight.Medium, lineHeight = (tam.value * 1.35f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    else Text(texto, color = color, fontFamily = Body, fontSize = (tam.value + 5f).sp, fontWeight = FontWeight.Bold, lineHeight = ((tam.value + 5f) * 1.3f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Un enlace de acción («Editar», «+ Añadir»…): en azul. */
@Composable
fun EnlaceRevista(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, onClick: () -> Unit) {
    Text(texto, color = Acento, fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.semantics { role = Role.Button }.tocable(onClick = onClick).padding(vertical = 10.dp))
}

/** Volver: la flecha. */
@Composable
fun VolverRevista(onClick: () -> Unit, texto: String = "Volver") {
    Box(Modifier.size(44.dp).clip(CircleShape).tocable(onClick = onClick).semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, texto, tint = Charcoal, modifier = Modifier.size(22.dp))
    }
}

/** Pestañas como las de una red social: el nombre, la elegida en negrita con su raya azul debajo; su número al lado. */
@Composable
fun PestanasRevista(nombres: List<String>, sel: Int, modifier: Modifier = Modifier, cuentas: List<Int?>? = null, onSel: (Int) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        val reparte = nombres.size <= 4
        Row(Modifier.fillMaxWidth().let { if (reparte) it else it.horizontalScroll(rememberScrollState()) }) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Box(Modifier.let { if (reparte) it.weight(1f) else it }.semantics { selected = on; role = Role.Tab }.tocable { onSel(i) }
                    .padding(horizontal = if (reparte) 4.dp else 14.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(n, color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
                            cuentas?.getOrNull(i)?.let { c -> Spacer(Modifier.width(6.dp)); Insignia(c) }
                        }
                        Box(Modifier.width(if (on) 44.dp else 0.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (on) Acento else Color.Transparent))
                    }
                }
            }
        }
        Filete(1.dp, Line)
    }
}

/** Filtros de una sola elección, en píldoras (en una línea o en varias). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun FiltrosRevista(opciones: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEachIndexed { i, o -> FiltroRevista(o, i == sel) { onSel(i) } }
    }
}

/** Un filtro suelto: una píldora (encendida, en tinta llena). */
@Composable
fun FiltroRevista(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(texto, color = if (on) Paper else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.semantics { selected = on }.clip(RoundedCornerShape(50)).background(if (on) Charcoal else Color.Transparent)
            .border(1.dp, if (on) Charcoal else Edge, RoundedCornerShape(50)).tocable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp))
}

/** La barra de arriba de una página: la flecha de volver, el título y lo de la derecha. */
@Composable
fun RevistaTopBar(onBack: () -> Unit, texto: String = "Volver", actions: @Composable RowScope.() -> Unit = {}, titulo: String? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VolverRevista(onBack, texto)
        if (titulo != null) Text(titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
        else Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Tamaño de un título grande según lo largo que sea. */
fun headlineSize(title: String): Pair<TextUnit, TextUnit> {
    val longest = title.split(' ').maxOfOrNull { it.length } ?: 0
    return when {
        title.length <= 8 && longest <= 8 -> 34.sp to 40.sp
        title.length <= 16 && longest <= 10 -> 30.sp to 36.sp
        title.length <= 30 && longest <= 13 -> 26.sp to 32.sp
        else -> 22.sp to 28.sp
    }
}

/** Título de una ficha: lo de encima, en gris, y el nombre grande. */
@Composable
fun RevistaHeadline(title: String, overline: String?) {
    val (fs, lh) = headlineSize(title)
    Column(Modifier.fillMaxWidth()) {
        if (overline != null) Text(overline, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FitTitle(title, fs, lh)
    }
}

/** El nombre de la app, como logotipo. */
@Composable
fun Cabecera(tam: TextUnit, modifier: Modifier = Modifier, color: Color = Charcoal, texto: String = "FcFeed") {
    Text(texto, color = color, fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = minOf(tam.value, 30f).sp, letterSpacing = (-0.5).sp, maxLines = 1, modifier = modifier)
}

/** Una fila de ajuste o de acción: el texto, lo de debajo en gris y la flecha. */
@Composable
fun MagLink(text: String, sub: String? = null, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().tocable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontFamily = Body, fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp, color = if (accent) Acento else Charcoal)
            if (sub != null) Text(sub, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.padding(start = 8.dp).size(22.dp))
    }
}

/** Elegir una de pocas opciones: píldoras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MagChoice(options: List<String>, selected: Int, label: String? = null, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (label != null) Text(label, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FiltroRevista(o, i == selected) { if (i != selected) onPick(i) } }
        }
    }
}

/** Una línea dato · valor: el dato en gris a la izquierda y el valor en negrita a la derecha. */
@Composable
fun MagFact(label: String, value: String) {
    Layout(
        content = {
            Text(label, fontFamily = Body, fontSize = 15.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier)
            Text(value, fontFamily = Body, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Charcoal, maxLines = 1, softWrap = false)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) { ms, c ->
        val v = ms[2].measure(Constraints())
        val hueco = 8.dp.roundToPx()
        val ancho = if (c.hasBoundedWidth) c.maxWidth else null
        val anchoEtiqueta = ms[0].maxIntrinsicWidth(Constraints.Infinity)
        ms[1].measure(Constraints.fixed(0, 0))
        if (ancho == null || anchoEtiqueta + v.width + hueco <= ancho) {
            val l = ms[0].measure(Constraints(maxWidth = ancho?.let { (it - v.width - hueco).coerceAtLeast(0) } ?: Constraints.Infinity))
            val w = ancho ?: (l.width + v.width + hueco * 4)
            val h = maxOf(l.height, v.height)
            layout(w, h) { l.place(0, h - l.height); v.place(w - v.width, h - v.height) }
        } else {
            // Si no cabe en una línea: el dato entero arriba y el valor debajo, a la derecha
            val l = ms[0].measure(Constraints(maxWidth = ancho))
            val h = l.height + v.height
            layout(ancho, h) { l.place(0, 0); v.place(ancho - v.width, h - v.height) }
        }
    }
}

/** Al final de una ficha: una acción grande. */
@Composable
fun MagFooter(text: String = "Editar", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Filete(1.dp, Line)
        Text(text, color = Acento, fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.tocable(onClick = onClick).padding(vertical = 16.dp))
    }
}

/** Una línea de una lista: el título en negrita, los datos en gris y, a la derecha, lo que se pase. */
@Composable
fun FilaRevista(titulo: String, meta: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().tocable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Charcoal, fontFamily = Body, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, color = Muted, fontFamily = Body, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        }
        derecha()
    }
    Filete(1.dp, Line)
}

/**
 * Titular que nunca parte una palabra: si la palabra más larga no cabe a lo ancho, baja el tamaño de la letra
 * (y el interlineado en proporción) hasta que quepa. Es el de Atrio.
 */
@Composable
fun FitTitle(
    text: String, fontSize: TextUnit, lineHeight: TextUnit,
    modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.ExtraBold, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    fontStyle: FontStyle? = null, minSize: Float = 14f,
    family: FontFamily = Display, align: TextAlign? = null,
    unaLinea: Boolean = false,
) {
    // (los tamaños que piden las pantallas son los de unos titulares de revista: aquí, más contenidos)
    val fontSize = if (fontSize.value > 30f) (fontSize.value * 0.58f).coerceAtLeast(26f).sp else fontSize
    val lineHeight = if (lineHeight.isSp && lineHeight.value > 34f) (lineHeight.value * 0.62f).coerceAtLeast(30f).sp else lineHeight
    val letterSpacing = if (letterSpacing.isSp && letterSpacing.value < -0.5f) (-0.5).sp else letterSpacing
    val claveAncho = "$text|${fontSize.value}"
    var maxPx by remember { mutableStateOf(anchosFit[claveAncho] ?: 0) }
    // (sin caché: la medida de antes de que llegue la letra no debe volver a servir)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // La letra puede ser de Google Fonts (la de tu apariencia) y llegar un momento después de abrir la app: medido con la de mientras, más
    // estrecha, el título salía cortado («Marfi» en vez de «FcFeed») hasta volver a la pantalla. Cuando llega, se vuelve a medir.
    val resolutor = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val letra = remember(resolutor, family, fontWeight, fontStyle) { resolutor.resolve(family, fontWeight, fontStyle ?: FontStyle.Normal) }
    fun estilo(s: Float) = TextStyle(fontFamily = family, fontWeight = fontWeight, fontSize = s.sp, letterSpacing = letterSpacing, fontStyle = fontStyle)
    fun ajusta(ancho: Int): Float {
        if (ancho <= 0 || ancho == Constraints.Infinity) return fontSize.value
        val words = if (unaLinea) listOf(text) else text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return fontSize.value
        var s = fontSize.value
        while (s > minSize) {
            val widest = words.maxOf { measurer.measure(it, estilo(s), softWrap = false, maxLines = 1).size.width }
            if (widest <= ancho * 0.97f) break
            s -= 1f
        }
        return s
    }
    val size = remember(text, fontSize, maxPx, family, fontWeight, letterSpacing, unaLinea, letra.value) { ajusta(maxPx) }
    fun alto(s: Float) = if (lineHeight.isSp) (lineHeight.value * s / fontSize.value).sp else lineHeight
    Text(
        text, color = color, fontFamily = family, fontWeight = fontWeight, fontStyle = fontStyle, fontSize = size.sp,
        lineHeight = alto(size), letterSpacing = letterSpacing, maxLines = maxLines, overflow = overflow, textAlign = align,
        modifier = modifier.then(AnchoDelTitulo { w ->
            if (w != maxPx) { maxPx = w; if (w > 0 && w != Constraints.Infinity) anchosFit[claveAncho] = w }
        })
    )
}
private val anchosFit = ConcurrentHashMap<String, Int>()

/**
 * Apunta el ancho que tiene el título (FitTitle) solo cuando se mide de verdad. Las preguntas de alto de una fila de alto intrínseco
 * (IntrinsicSize.Min, las casillas de la ficha) pasan de largo sin tocar el estado: si lo escribieran, el ancho de la pregunta y el de la
 * medida (que pueden diferir en un píxel por redondeo) se pisarían en cada fotograma y la pantalla no dejaría de recomponerse.
 */
private class AnchoDelTitulo(private val alMedir: (Int) -> Unit) : androidx.compose.ui.layout.LayoutModifier {
    override fun androidx.compose.ui.layout.MeasureScope.measure(measurable: androidx.compose.ui.layout.Measurable, constraints: Constraints): androidx.compose.ui.layout.MeasureResult {
        alMedir(constraints.maxWidth)
        val pl = measurable.measure(constraints)
        return layout(pl.width, pl.height) { pl.place(0, 0) }
    }
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(measurable: androidx.compose.ui.layout.IntrinsicMeasurable, width: Int) = measurable.minIntrinsicHeight(width)
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(measurable: androidx.compose.ui.layout.IntrinsicMeasurable, width: Int) = measurable.maxIntrinsicHeight(width)
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(measurable: androidx.compose.ui.layout.IntrinsicMeasurable, height: Int) = measurable.minIntrinsicWidth(height)
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(measurable: androidx.compose.ui.layout.IntrinsicMeasurable, height: Int) = measurable.maxIntrinsicWidth(height)
}

/** La barra fina que queda arriba al bajar en una lista: el nombre y las acciones. */
@Composable
fun BarraPlegada(nombre: String, visible: Boolean, acciones: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Column(Modifier.fillMaxWidth().background(Paper).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(nombre, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                acciones()
            }
            Filete(1.dp)
        }
    }
}
