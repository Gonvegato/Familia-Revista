package com.gonzalo.hiedra.pc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * El foco de la sala (como en una tele): lo enfocado se mueve con la cruceta al vecino más cercano en cada dirección (la búsqueda en dos
 * dimensiones de Compose, que también trae a la vista lo que está fuera de una fila), lleva un marco del color del acento que se desliza de
 * una portada a otra, y A hace lo suyo. Lo que se abre encima (buscar, opciones, el menú) es otra capa: mientras está, solo se enfoca lo suyo.
 * Cada vista recuerda lo último enfocado, y al volver a ella se vuelve ahí.
 */
class FocoSala {
    /** Las capas abiertas, la de arriba al final. */
    val capas = mutableStateListOf("base")
    val arriba: String get() = capas.last()
    /** Lo que se quiere enfocar (su id); cuando se enfoca, se borra. */
    var pedido by mutableStateOf<String?>(null)
    /** Lo enfocado ahora. */
    var actual by mutableStateOf<String?>(null)
        private set
    private var pulsar: (() -> Unit)? = null
    private var opciones: (() -> Unit)? = null
    /** Dónde está lo enfocado (en píxeles de la ventana) y la curva de sus esquinas, para el marco. */
    var marco by mutableStateOf(Rect.Zero)
        private set
    var radio by mutableStateOf(0f)
        private set
    /** El marco salta (sin deslizarse) a lo siguiente que se enfoque: al cambiar de pantalla. */
    var salta = true
    /** Lo último enfocado en cada vista. */
    val ultimo = HashMap<String, String>()

    val visible: Boolean get() = Entrada.con != ConQue.RATON

    fun abre(capa: String) { if (capa !in capas) capas += capa; salta = true }
    fun cierra(capa: String) { capas.remove(capa); salta = true }
    fun hay(capa: String) = capa in capas

    internal fun enfoca(id: String, alPulsar: () -> Unit, alOpciones: (() -> Unit)?) {
        actual = id; pulsar = alPulsar; opciones = alOpciones
        if (pedido == id) pedido = null
    }
    internal fun suelta(id: String) { if (actual == id) { actual = null; pulsar = null; opciones = null } }
    internal fun ponMarco(r: Rect, rad: Float) { marco = r; radio = rad }

    /** A: lo que hace lo enfocado. */
    fun pulsa(): Boolean { val p = pulsar ?: return false; p(); return true }
    /** Y: las opciones de lo enfocado (si tiene). */
    fun opcionesDe(): Boolean { val o = opciones ?: return false; o(); return true }
    fun tieneOpciones(): Boolean = opciones != null
}

val LocalFoco = staticCompositionLocalOf { FocoSala() }
/** La capa en que está algo (lo de debajo no se enfoca mientras hay otra encima). */
val LocalCapa = compositionLocalOf { "base" }

/**
 * Algo que se enfoca en la sala: con el mando o el teclado se llega a ello y A lo pulsa; con el ratón, se pulsa al hacer clic. Al enfocarse
 * crece un poco y lleva el marco. [id]: único en su pantalla (con él se recuerda y se vuelve a enfocar).
 */
@Composable
fun Modifier.enfocable(id: String, radio: Dp = 10.dp, crece: Float = 1.06f, alOpciones: (() -> Unit)? = null, alEnfocar: (() -> Unit)? = null, alPulsar: () -> Unit): Modifier {
    val foco = LocalFoco.current
    val capa = LocalCapa.current
    val fr = remember { FocusRequester() }
    var enfocado by remember { mutableStateOf(false) }
    val activa = foco.arriba == capa
    val pulsar by rememberUpdatedState(alPulsar)
    val opciones by rememberUpdatedState(alOpciones)
    val enfocar by rememberUpdatedState(alEnfocar)
    LaunchedEffect(foco.pedido, activa) { if (activa && foco.pedido == id) runCatching { fr.requestFocus() } }
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    val escala by animateFloatAsState(if (enfocado && foco.visible) crece else if (encima && !foco.visible) 1f + (crece - 1f) / 2 else 1f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "escala")
    val radioPx = with(LocalDensity.current) { radio.toPx() }
    // Dónde está (sin contar lo que crece al enfocarse): el marco va a ese sitio, ya crecido
    val sitio = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    fun marca() { sitio[0]?.takeIf { it.isAttached }?.let { c -> foco.ponMarco(crecido(c.boundsInRoot(), crece), radioPx * crece) } }
    DisposableEffect(id) { onDispose { foco.suelta(id) } }
    return this
        .zIndex(if (enfocado) 2f else if (encima) 1f else 0f)
        .focusRequester(fr)
        .focusProperties { canFocus = activa }
        .onFocusChanged { f ->
            enfocado = f.isFocused
            if (f.isFocused) { foco.enfoca(id, { pulsar() }, opciones?.let { { it() } }); marca(); enfocar?.invoke() } else foco.suelta(id)
        }
        .focusable(activa)
        .onGloballyPositioned { c -> sitio[0] = c; if (enfocado) marca() }
        .graphicsLayer { scaleX = escala; scaleY = escala }
        .hoverable(hover)
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(activa) { if (activa) detectTapGestures(onTap = { runCatching { fr.requestFocus() }; pulsar() }) }
}

/** Un rectángulo crecido [k] veces desde su centro (lo que ocupa algo enfocado). */
fun crecido(r: Rect, k: Float): Rect {
    val dx = r.width * (k - 1f) / 2f; val dy = r.height * (k - 1f) / 2f
    return Rect(r.left - dx, r.top - dy, r.right + dx, r.bottom + dy)
}

/** El marco del foco: se desliza (con un poco de muelle) de lo enfocado a lo siguiente y desaparece al usar el ratón. */
@Composable
fun MarcoFoco(foco: FocoSala, color: Color, grosor: Dp = 4.dp) {
    val anim = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val r = foco.marco
    LaunchedEffect(r) {
        if (r == Rect.Zero) return@LaunchedEffect
        if (foco.salta || anim.value == Rect.Zero || (anim.value.center - r.center).getDistance() > 1400f) { foco.salta = false; anim.snapTo(r) }
        else anim.animateTo(r, spring(dampingRatio = 0.86f, stiffness = 650f))
    }
    val alfa by animateFloatAsState(if (foco.visible && foco.actual != null && r != Rect.Zero) 1f else 0f, tween(180), label = "marco")
    val g = with(LocalDensity.current) { grosor.toPx() }
    val fuera = with(LocalDensity.current) { 7.dp.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        if (alfa <= 0.01f) return@Canvas
        val b = anim.value
        val tl = Offset(b.left - fuera, b.top - fuera)
        val sz = Size(b.width + fuera * 2, b.height + fuera * 2)
        val rad = CornerRadius(foco.radio + fuera)
        // Un halo suave y, encima, el marco
        for (i in 3 downTo 1) drawRoundRect(color.copy(alpha = 0.10f * alfa), Offset(tl.x - g * i, tl.y - g * i), Size(sz.width + g * i * 2, sz.height + g * i * 2), CornerRadius(rad.x + g * i), style = Stroke(g * 1.6f))
        drawRoundRect(color.copy(alpha = alfa), tl, sz, rad, style = Stroke(g))
    }
}

/**
 * Cómo se trae a la vista lo enfocado: con aire alrededor (se ve lo que viene). [inicio]: en vertical, lo enfocado se queda a esa distancia
 * del borde de arriba (la fila enfocada sube a su sitio, como en una tele).
 */
@OptIn(ExperimentalFoundationApi::class)
class TraerAVista(private val margen: Float, private val inicio: Float? = null) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (inicio != null && size < containerSize - inicio) return offset - inicio
        val m = minOf(margen, ((containerSize - size) / 2f).coerceAtLeast(0f))
        val fin = offset + size
        return when {
            offset < m -> offset - m
            fin > containerSize - m -> fin - (containerSize - m)
            else -> 0f
        }
    }
}
