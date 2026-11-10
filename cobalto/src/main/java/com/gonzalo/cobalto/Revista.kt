package com.gonzalo.cobalto

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.withStyle
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

/*
 * Las piezas de la interfaz «Revista» de Atrio (RevistaUI.kt, Revista.kt, Ediciones.kt, Vitrina.kt), tal cual y con los mismos
 * nombres: papel, serif, filetes, antetítulos en versalitas con su cuadradito de acento, pestañas subrayadas, enlaces con flecha,
 * cifras enormes y puntos guía. Todas las pantallas de Thot usan estas, para que se vean como las de Atrio.
 */

/** Filete de la revista (horizontal). */
@Composable
fun Filete(alto: Dp = 2.dp, color: Color = Charcoal, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(alto).background(color))
}

/** El filete bajo la cabecera. */
@Composable
fun FileteCabecera(alto: Dp = 3.dp) = Filete(alto)

/** Antetítulo de casilla o de sección: cuadradito de acento y versalitas espaciadas. */
@Composable
fun Antetitulo(texto: String, modifier: Modifier = Modifier, tam: TextUnit = 13.sp, color: Color = Charcoal, conCuadro: Boolean = true) {
    // Dirección D: el de sección, un título en minúsculas normales; el de casilla, un rótulo apagado
    if (conCuadro) Text(texto, modifier, color = color, fontFamily = Grotesca, fontSize = (tam.value * 1.4f).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    else Text(texto, modifier, color = if (color == Charcoal) Muted else color, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Dentro de una barra de tinta (la cabecera del estudio): los enlaces se pintan del color del papel. */
val LocalSobreTinta = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Un enlace de acción en versalitas («BUSCAR», «EDITAR», «+ AÑADIR»…). */
@Composable
fun EnlaceRevista(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    Text(texto, color = color ?: if (acento) Acento else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.semantics { role = Role.Button }.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp))
}

/** «← VOLVER» (o «← LECTURA» en lo que cuelga de la portada). */
@Composable
fun VolverRevista(onClick: () -> Unit, texto: String = "Volver") = EnlaceRevista("← $texto", onClick = onClick)

/** Las pestañas de una página: versalitas; la elegida en tinta con el subrayado de acento; su número, en acento. */
@Composable
fun PestanasRevista(nombres: List<String>, sel: Int, modifier: Modifier = Modifier, cuentas: List<Int?>? = null, onSel: (Int) -> Unit) {
    // La fila se desplaza sola hasta la pestaña elegida si no se ve entera (al entrar en «Sagas» desde la portada, por ejemplo)
    val desplazamiento = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < desplazamiento.value || x + w > desplazamiento.value + anchoFila) desplazamiento.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, desplazamiento.maxValue))
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                // Un degradado en los bordes cuando hay más pestañas por un lado o por otro (para que no parezcan cortadas)
                drawContent()
                val f = 28.dp.toPx()
                if (desplazamiento.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (desplazamiento.value < desplazamiento.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(desplazamiento), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x).roundToInt() to c.size.width }
                    .semantics { selected = on; role = Role.Tab }.clickable { onSel(i) }
                    .padding(end = 6.dp).clip(RoundedCornerShape(50)).background(if (on) Charcoal else Sand)
                    .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.Bottom) {
                    // Dirección C: la elegida, en un bloque del acento
                    Text(n, color = if (on) Paper else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    cuentas?.getOrNull(i)?.let { c -> Text(" $c", color = if (on) Paper.copy(alpha = 0.7f) else Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
    }
}

/** Filtros u opciones de una sola elección, todas a la vista (en una línea o en varias): versalitas; el elegido en acento y subrayado. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun FiltrosRevista(opciones: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    // Todas a la vista: si no caben en una línea, siguen en la siguiente (una fila que se desliza dentro de una página que también
    // se desliza no se dejaba mover: el gesto se lo llevaba la página, y la última opción se veía cortada)
    FlowRow(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEachIndexed { i, o -> FiltroRevista(o, i == sel) { onSel(i) } }
    }
}

/** Un filtro suelto (que se enciende y se apaga): versalitas, encendido en acento y subrayado. */
@Composable
fun FiltroRevista(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Dirección D: una píldora; la encendida, rellena de tinta
    Text(texto, color = if (on) Paper else Charcoal, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.semantics { selected = on }.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
            .background(if (on) Charcoal else Sand).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Barra de arriba de las páginas: «← Volver» a la izquierda y los enlaces de la derecha. */
@Composable
fun RevistaTopBar(onBack: () -> Unit, texto: String = "Volver", actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        VolverRevista(onBack, texto)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Tamaño del titular de revista según lo largo que sea. */
fun headlineSize(title: String): Pair<TextUnit, TextUnit> {
    val longest = title.split(' ').maxOfOrNull { it.length } ?: 0
    return when {
        title.length <= 8 && longest <= 8 -> 86.sp to 84.sp
        title.length <= 16 && longest <= 10 -> 66.sp to 66.sp
        title.length <= 30 && longest <= 13 -> 50.sp to 52.sp
        else -> 38.sp to 41.sp
    }
}

/** Titular de ficha: antetítulo de acento y el nombre enorme, a todo lo ancho. */
@Composable
fun RevistaHeadline(title: String, overline: String?) {
    Column(Modifier.fillMaxWidth()) {
        if (overline != null) Text(overline, color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(title, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
    }
}

/** La cabecera de la revista (el nombre, enorme) en una sola línea. */
@Composable
fun Cabecera(tam: TextUnit, modifier: Modifier = Modifier, color: Color = Charcoal, texto: String = "Thot") {
    Box(modifier.fillMaxWidth()) {
        FitTitle(texto, tam, tam * 1.0f, color = color, maxLines = 1, modifier = Modifier.fillMaxWidth(), minSize = 12f, unaLinea = true, family = Grotesca, fontWeight = FontWeight.Bold)
    }
}
private val Float.em get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
private val Double.em get() = this.toFloat().em

/** Un enlace de texto (serif, con flecha): «Cambiar emulador →». */
@Composable
fun MagLink(text: String, sub: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text("$text →", fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, color = if (accent) Acento else Charcoal)
        if (sub != null) Text(sub, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** Elegir una de pocas opciones: mayúsculas pequeñas, la elegida en acento y subrayada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MagChoice(options: List<String>, selected: Int, label: String? = null, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (label != null) Text(label, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FiltroRevista(o, i == selected) { if (i != selected) onPick(i) } }
        }
    }
}

/** Una línea dato · valor con puntos guía (como el índice de una revista). */
@Composable
fun MagFact(label: String, value: String) {
    Layout(
        content = {
            Text(label, fontFamily = Body, fontSize = 14.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.drawBehind {
                drawLine(Muted.copy(alpha = 0.6f), Offset(0f, size.height - 2), Offset(size.width, size.height - 2), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f)))
            })
            Text(value, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Charcoal, maxLines = 1, softWrap = false)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) { ms, c ->
        val v = ms[2].measure(Constraints())
        val hueco = 6.dp.roundToPx()
        val ancho = if (c.hasBoundedWidth) c.maxWidth else null
        val anchoEtiqueta = ms[0].maxIntrinsicWidth(Constraints.Infinity)
        if (ancho == null || anchoEtiqueta + v.width + hueco * 2 <= ancho) {
            // Cabe en una línea: etiqueta, puntos y cifra
            val l = ms[0].measure(Constraints(maxWidth = ancho?.let { (it - v.width - hueco * 2).coerceAtLeast(0) } ?: Constraints.Infinity))
            val w = ancho ?: (l.width + v.width + hueco * 6)
            val puntos = (w - l.width - v.width - hueco * 2).coerceAtLeast(0)
            val h = maxOf(l.height, v.height)
            val d = ms[1].measure(Constraints.fixed(puntos, 12.dp.roundToPx().coerceAtMost(h)))
            layout(w, h) {
                l.place(0, h - l.height)
                d.place(l.width + hueco, h - d.height - 3.dp.roundToPx())
                v.place(w - v.width, h - v.height)
            }
        } else {
            // En una casilla estrecha no cabe todo: la etiqueta entera arriba (nada de «Termin…») y, debajo, los puntos y la cifra a la derecha
            val l = ms[0].measure(Constraints(maxWidth = ancho))
            val puntos = (ancho - v.width - hueco).coerceAtLeast(0)
            val d = ms[1].measure(Constraints.fixed(puntos, 12.dp.roundToPx().coerceAtMost(v.height)))
            val h = l.height + v.height
            layout(ancho, h) {
                l.place(0, 0)
                d.place(0, h - d.height - 3.dp.roundToPx())
                v.place(ancho - v.width, h - v.height)
            }
        }
    }
}

/** Final de la ficha: «Editar» grande, como pie de página. */
@Composable
fun MagFooter(text: String = "Editar", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("$text →", color = Acento, fontFamily = Body, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 16.dp))
    }
}

/** Una línea de la lista: título en serif, datos en versalitas y, a la derecha, lo que se pase. */
@Composable
fun FilaRevista(titulo: String, meta: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            FitTitle(titulo, 21.sp, 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, family = Grotesca)
            if (meta.isNotBlank()) Text(meta, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
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
    fontWeight: FontWeight = FontWeight.Bold, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    fontStyle: FontStyle? = null, minSize: Float = 14f,
    family: FontFamily = Display, align: TextAlign? = null,
    unaLinea: Boolean = false,
    /** (Como en Atrio: allí la revista reescribe los titulares según la edición; aquí no cambia nada.) */
    @Suppress("UNUSED_PARAMETER") conEdicion: Boolean = true,
) {
    val claveAncho = "$text|${fontSize.value}"
    var maxPx by remember { mutableStateOf(anchosFit[claveAncho] ?: 0) }
    // (sin caché: la medida de antes de que llegue la letra no debe volver a servir)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    // La letra puede ser de Google Fonts (la de tu apariencia) y llegar un momento después de abrir la app: medido con la de mientras, más
    // estrecha, el título salía cortado («Marfi» en vez de «Marfil») hasta volver a la pantalla. Cuando llega, se vuelve a medir.
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

/**
 * La cabecera plegada de la portada: al bajar, el nombre y las acciones de arriba (buscar, añadir) se quedan fijos en una barra fina, y el
 * resto de la cabecera (Nº, fecha, barras, menú) se va con la página. Igual en Hiedra, Marfil y Cobalto.
 */
@Composable
fun BarraPlegada(nombre: String, visible: Boolean, acciones: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NombreConPunto(nombre, 26.sp, Paper)
                Spacer(Modifier.weight(1f))
                acciones()
            }
        }
    }
}

/** «THOT.»: el nombre en grotesca con el punto del acento (la cabecera de la dirección C). */
@Composable
fun NombreConPunto(nombre: String, tam: TextUnit, color: Color = Charcoal, modifier: Modifier = Modifier) {
    Text(androidx.compose.ui.text.buildAnnotatedString {
        append(nombre)
        withStyle(androidx.compose.ui.text.SpanStyle(color = Acento)) { append(".") }
    }, modifier, color = color, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = tam, lineHeight = tam * 1.0f, maxLines = 1, softWrap = false,
        style = androidx.compose.ui.text.TextStyle(lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None)))
}

/** Un bloque de la rejilla de cifras: la cifra, su etiqueta y sus colores. */
class BloqueCifra(val cifra: String, val etiqueta: String, val fondo: Color, val letra: Color, val alHacer: (() -> Unit)? = null)

/** La rejilla dura de cifras de la dirección C: bloques de igual ancho con bordes de 2 px. */
@Composable
fun RejillaCifras(bloques: List<BloqueCifra>, modifier: Modifier = Modifier) {
    // Dirección D: tarjetas redondas, sin bordes; todas en superficie (la cifra, en tinta)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        bloques.forEach { b ->
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Crudo).let { m -> if (b.alHacer != null) m.clickable(onClick = b.alHacer) else m }.padding(horizontal = 14.dp, vertical = 14.dp)) {
                FitTitle(b.cifra, 28.sp, 30.sp, color = Charcoal, family = Grotesca, fontWeight = FontWeight.Bold, maxLines = 1, unaLinea = true, minSize = 14f, letterSpacing = 0.sp)
                Text(b.etiqueta, color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** El botón recto de ancho completo de la dirección C: texto a la izquierda y flecha a la derecha («SEGUIR LEYENDO →»). */
@Composable
fun BotonC(texto: String, modifier: Modifier = Modifier, fondo: Color = Charcoal, letra: Color = Paper, enabled: Boolean = true, onClick: () -> Unit) {
    // Dirección D: redondo; el de tinta de antes pasa a ser el del acento (el principal) y el de papel, el secundario (gris)
    val f = if (fondo == Charcoal || fondo == Acento) Acento else Sand
    val l = if (f == Acento) SobreAcento else Charcoal
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) f else f.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = l, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("→", color = l, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}
