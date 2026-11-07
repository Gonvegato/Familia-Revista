package com.gonzalo.mostaza

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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/*
 * El foco de la sala (la de la tele, como la sala de Hiedra en el ordenador): la cruceta del mando a distancia mueve lo enfocado al vecino
 * más cercano en cada dirección (la búsqueda en dos dimensiones de Compose, que también trae a la vista lo que está fuera de una fila), un
 * marco del color del acento se desliza de una portada a otra y OK (o A en un mando de juegos) hace lo suyo. Lo que se abre encima (la
 * ficha, las fuentes, el menú, una pregunta) es otra capa: mientras está, solo se enfoca lo suyo, y lo de debajo sigue ahí para volver
 * donde estabas. Cada vista recuerda lo último enfocado, y al volver a ella se vuelve ahí.
 *
 * No se usa Compose for TV (androidx.tv): sus filas «lazy» se retiraron en favor de las normales de Compose, que ya saben moverse con la
 * cruceta, y sus tarjetas y botones son los de Material, no los de la revista. Con lo de siempre de Compose basta, y es lo mismo que la
 * sala de Hiedra.
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
    /** Dónde está lo enfocado (en píxeles de la ventana) y la curva de sus esquinas, para el marco. */
    var marco by mutableStateOf(Rect.Zero)
        private set
    var radio by mutableStateOf(0f)
        private set
    /** El marco salta (sin deslizarse) a lo siguiente que se enfoque: al cambiar de pantalla. */
    var salta = true
    /** Lo último enfocado en cada vista. */
    val ultimo = HashMap<String, String>()
    /**
     * ¿Se ve el marco? En la tele, siempre; en una tablet en la sala, mientras usas el mando o un teclado (con el dedo no hace falta: en
     * modo táctil nada tiene el foco a la vista).
     */
    var visible by mutableStateOf(true)

    fun abre(capa: String) { if (capa !in capas) capas += capa; salta = true }
    fun cierra(capa: String) { capas.remove(capa); salta = true }
    fun hay(capa: String) = capa in capas

    internal fun enfoca(id: String) { actual = id; if (pedido == id) pedido = null }
    internal fun suelta(id: String) { if (actual == id) actual = null }
    internal fun ponMarco(r: Rect, rad: Float) { marco = r; radio = rad }
}

val LocalFoco = staticCompositionLocalOf { FocoSala() }
/** La capa en que está algo (lo de debajo no se enfoca mientras hay otra encima). */
val LocalCapa = compositionLocalOf { "base" }

/** Las teclas que pulsan lo enfocado: OK del mando a distancia, Intro de un teclado y A de un mando de juegos. */
private val TECLAS_PULSAR = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/**
 * Algo que se enfoca en la sala: con el mando a distancia (o un mando de juegos, o un teclado) se llega a ello y OK lo pulsa; con el dedo
 * (en una tablet), se pulsa al tocarlo. Al enfocarse crece un poco y lleva el marco. [id]: único en su vista (con él se recuerda y se
 * vuelve a enfocar). Hace lo suyo al bajar la tecla (no al soltarla): así el «soltar» que llega a lo que se acaba de abrir no lo pulsa
 * también; ese «soltar» se come aquí.
 */
@Composable
fun Modifier.enfocable(id: String, radio: Dp = 10.dp, crece: Float = 1.06f, alEnfocar: (() -> Unit)? = null, alPulsar: () -> Unit): Modifier {
    val foco = LocalFoco.current
    val capa = LocalCapa.current
    val fr = remember { FocusRequester() }
    var enfocado by remember { mutableStateOf(false) }
    val activa = foco.arriba == capa
    val pulsar by rememberUpdatedState(alPulsar)
    val enfocar by rememberUpdatedState(alEnfocar)
    LaunchedEffect(foco.pedido, activa) { if (activa && foco.pedido == id) runCatching { fr.requestFocus() } }
    val escala by animateFloatAsState(if (enfocado && foco.visible) crece else 1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "escala")
    val radioPx = with(LocalDensity.current) { radio.toPx() }
    // Dónde está (sin contar lo que crece al enfocarse): el marco va a ese sitio, ya crecido
    val sitio = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun marca() { sitio[0]?.takeIf { it.isAttached }?.let { c -> foco.ponMarco(crecido(c.boundsInRoot(), crece), radioPx * crece) } }
    DisposableEffect(id) { onDispose { foco.suelta(id) } }
    return this
        .zIndex(if (enfocado) 2f else 0f)
        .semantics(mergeDescendants = true) { role = Role.Button; onClick { if (activa) pulsar(); activa } }
        .onKeyEvent { e ->
            if (!activa || e.key !in TECLAS_PULSAR) return@onKeyEvent false
            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) pulsar()
            true
        }
        .focusRequester(fr)
        .focusProperties { canFocus = activa }
        .onFocusChanged { f ->
            enfocado = f.isFocused
            if (f.isFocused) { foco.enfoca(id); marca(); enfocar?.invoke() } else foco.suelta(id)
        }
        .focusable(activa)
        .onGloballyPositioned { c -> sitio[0] = c; if (enfocado) marca() }
        .graphicsLayer { scaleX = escala; scaleY = escala }
        .pointerInput(activa) { if (activa) detectTapGestures(onTap = { runCatching { fr.requestFocus() }; pulsar() }) }
}

/** Un rectángulo crecido [k] veces desde su centro (lo que ocupa algo enfocado). */
fun crecido(r: Rect, k: Float): Rect {
    val dx = r.width * (k - 1f) / 2f; val dy = r.height * (k - 1f) / 2f
    return Rect(r.left - dx, r.top - dy, r.right + dx, r.bottom + dy)
}

/** El marco del foco: se desliza (con un poco de muelle) de lo enfocado a lo siguiente. */
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
