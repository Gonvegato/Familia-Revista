// Copiado de familia/ (Android) por herramientas: lo mismo en el Mac. Lo de Android (vibrar, el gesto de atrás, las preferencias) cambia por lo del ordenador.
package com.gonzalo.familia.pc.mov

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/*
 * Las piezas del movimiento de la familia que van igual en todas las apps (lo hecho primero en Seshat): la capa que nace de la fila
 * tocada (la ficha de un libro, de un juego, de una obra, de un partido…), las marcas de las filas y de las tapas, y las pestañas con el
 * bloque del acento pegado a la página.
 */

/** Dónde está cada tapa (la última vez que se colocó en pantalla), para que lo que se abre desde una fila sepa de qué tapa sale. */
object Tapas {
    internal val sitios = HashMap<String, LayoutCoordinates>()
    fun de(clave: String): LayoutCoordinates? = sitios[clave]?.takeIf { it.isAttached }
}

/** Dónde está cada fila (la última vez que se colocó en pantalla): lo que se abre sin haber apuntado su origen nace de ahí. */
object Filas {
    internal val sitios = HashMap<String, LayoutCoordinates>()
    fun de(clave: String): LayoutCoordinates? = sitios[clave]?.takeIf { it.isAttached }
}

/** La fila (o tarjeta) de [clave]: su ficha nace de aquí aunque se toque con el toque propio de la app (el del mando, por ejemplo). */
fun Modifier.filaDe(clave: String): Modifier = this.onGloballyPositioned { Filas.sitios[clave] = it }

/** La tapa (portada, carátula, escudo…) de [clave] en una lista: se apunta dónde está y se esconde mientras vuela hacia su ficha. */
fun Modifier.tapaDe(clave: String): Modifier = this
    .onGloballyPositioned { Tapas.sitios[clave] = it }
    .graphicsLayer { alpha = Vuelo.alfaOrigen(clave) }

/** La tapa de destino (la de la ficha abierta): adonde vuela la de la fila, y de donde sale al cerrar. */
fun Modifier.tapaDestino(clave: String): Modifier = this
    .onGloballyPositioned { c -> Vuelo.destino = c.boundsInRoot(); Vuelo.coords = c }
    .graphicsLayer { alpha = Vuelo.alfaDestino(clave) }

/**
 * Una fila (o una tarjeta) que abre algo: se hunde al tocarla, vibra, y apunta de dónde sale lo que se abre (esta fila y su tapa), para
 * que nazca de aquí. [alMantener]: mantener pulsado.
 */
fun Modifier.abre(clave: String, alMantener: (() -> Unit)? = null, hundido: Float = 0.965f, alAbrir: () -> Unit): Modifier = composed {
    val sitio = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this.onGloballyPositioned { sitio[0] = it }
        .pulsable(hundido = hundido, alMantener = alMantener) { Origen.apunta(clave, sitio[0], Tapas.de(clave)); alAbrir() }
}

/** Qué hay abierto por encima (para que Atrás cierre siempre lo de más arriba). */
object CapasAbiertas {
    var ficha by mutableStateOf(false)
    var pagina by mutableStateOf(false)
}

/**
 * La ficha (o lo que sea) por encima de la app: nace de la fila que tocaste (o, abierta desde fuera, aparece en su sitio), la tapa vuela de
 * la fila a su hueco y al cerrar vuelve; el papel aparece y se funde con la fila al acabar, sin saltos. Con el gesto de atrás de Android, la
 * ficha encoge con el dedo. [clave]: lo abierto (null: nada). [alInstante]: pedida por un widget al arrancar (ya puesta, sin animación).
 * [fondo]: el papel. [tapa]: cómo se pinta la tapa que vuela (null: no vuela nada). [contenido]: la ficha.
 */
@Composable
fun CapaQueNace(
    clave: String?,
    alCerrar: () -> Unit,
    fondo: Color,
    alInstante: Boolean = false,
    alUsarInstante: () -> Unit = {},
    esPagina: Boolean = false,
    /** Si Atrás le toca a esta capa (false si hay algo suyo abierto encima que lo atiende). */
    atrasActivo: Boolean = true,
    tapa: (@Composable (String, Modifier) -> Unit)? = null,
    contenido: @Composable (String, Int) -> Unit,
) {
    var visible by remember { mutableStateOf(if (alInstante) clave else null) }
    var origen by remember { mutableStateOf<Origen.Datos?>(null) }
    var aperturas by remember { mutableIntStateOf(0) }
    val t = remember { Animatable(if (alInstante && clave != null) 1f else 0f) }
    val atras = remember { Animatable(0f) }
    var desdeIzquierda by remember { mutableStateOf(true) }
    val vista = Unit
    fun marca(abierta: Boolean) { if (esPagina) CapasAbiertas.pagina = abierta else CapasAbiertas.ficha = abierta }
    LaunchedEffect(clave) {
        if (clave != null && alInstante) {
            origen = null; visible = clave; aperturas++; atras.snapTo(0f); t.snapTo(1f); Vuelo.t = 1f
            marca(true); alUsarInstante(); return@LaunchedEffect
        }
        if (clave != null) {
            if (visible != clave) {
                origen = Origen.toma(clave) ?: Filas.de(clave)?.let { f -> Origen.Datos(clave, f.boundsInRoot(), Tapas.de(clave)?.boundsInRoot()) }
                visible = clave; aperturas++; t.snapTo(0f); atras.snapTo(0f)
            }
            marca(true)
            val vuela = tapa != null && origen?.tapa != null
            if (vuela) Vuelo.clave = clave
            t.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 230f)) { if (vuela) Vuelo.t = value }
            if (vuela) Vuelo.clave = null
        } else if (visible != null) {
            Vibra.tic(vista)
            val vuela = tapa != null && origen?.tapa != null
            if (vuela) { Vuelo.destino = Vuelo.destinoAhora(); Vuelo.clave = visible }
            launch { atras.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) }
            t.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) { if (vuela) Vuelo.t = value }
            if (vuela) { Vuelo.clave = null; Vuelo.destino = null }
            visible = null; atras.snapTo(0f); marca(false)
        }
    }
    // (en el ordenador, Esc o ⌘[ la cierran)
    Atras(enabled = clave != null && atrasActivo && (esPagina.not() || !CapasAbiertas.ficha)) { alCerrar() }
    val k = visible ?: return
    val o = origen
    val densidad = LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer {
        val tt = t.value; val p = atras.value
        clip = true
        shape = FormaEntre(o?.fila, tt, with(densidad) { 26.dp.toPx() } * (p * 4f).coerceAtMost(1f))
        val s = (1f - 0.1f * p) * (if (o == null) 0.96f + 0.04f * tt else 1f)
        scaleX = s; scaleY = s
        translationX = (if (desdeIzquierda) 1f else -1f) * p * with(densidad) { 24.dp.toPx() }
        alpha = if (o == null) tt.coerceIn(0f, 1f) else fundidoBorde(tt)
    }) {
        Box(Modifier.fillMaxSize().background(fondo))
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = if (clave == null) ((t.value - 0.45f) / 0.55f).coerceIn(0f, 1f) else (t.value * 2.5f).coerceIn(0f, 1f)
        }) { contenido(k, aperturas) }
    }
    // La tapa que vuela entre la fila y la ficha
    val desde = o?.tapa
    val hasta = Vuelo.destino
    if (tapa != null && desde != null && hasta != null && Vuelo.clave == k) {
        with(densidad) {
            tapa(k, Modifier.size(hasta.width.toDp(), hasta.height.toDp()).graphicsLayer {
                val r = lerpRect(desde, hasta, t.value)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = r.left; translationY = r.top
                scaleX = r.width / hasta.width; scaleY = r.height / hasta.height
                shadowElevation = 18.dp.toPx() * t.value
                alpha = Vuelo.alfaCopia()
            })
        }
    }
}

/**
 * Las pestañas de las secciones: la barra se desliza sola con el dedo (sin mover la página) y el bloque del acento va pegado a la página
 * (sigue al dedo, se estira a mitad de camino y se recoge al llegar); el color del texto sigue al bloque; al cambiar de página, la barra trae
 * la elegida al centro; los números ruedan. [fondo], [tinta], [acento], [sobreAcento]: los colores de la app; [letra] y [cifras]: sus letras.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PestanasPager(
    nombres: List<String>, pager: PagerState, cuentas: List<Int?>,
    fondo: Color, tinta: Color, acento: Color, sobreAcento: Color, letra: FontFamily, cifras: FontFamily,
    modifier: Modifier = Modifier, filete: Color = tinta,
    /** El toque de cada pestaña (por defecto, el de la familia; las apps con mando pasan el suyo, con su marco de foco). */
    toque: ((Modifier, Boolean, () -> Unit) -> Modifier)? = null,
    onSel: (Int) -> Unit,
) {
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    var anchoFila by remember { mutableIntStateOf(0) }
    val sel = pager.currentPage
    val densidad = LocalDensity.current
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0 || scroll.maxValue <= 0) return@LaunchedEffect
        scroll.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, scroll.maxValue), spring(dampingRatio = 0.95f, stiffness = 220f))
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                drawContent()
                val f = 28.dp.toPx()
                val izq = (scroll.value / f).coerceIn(0f, 1f); val der = ((scroll.maxValue - scroll.value) / f).coerceIn(0f, 1f)
                if (izq > 0f) drawRect(Brush.horizontalGradient(listOf(fondo.copy(alpha = izq), Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (der > 0f) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, fondo.copy(alpha = der)), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll).height(IntrinsicSize.Max)
            .drawBehind {
                val p = pager.currentPage + pager.currentPageOffsetFraction
                val i0 = kotlin.math.floor(p).toInt().coerceIn(0, nombres.size - 1)
                val i1 = (i0 + 1).coerceAtMost(nombres.size - 1)
                val f = (p - i0).coerceIn(0f, 1f)
                val a = sitios[i0] ?: return@drawBehind
                val b = sitios[i1] ?: a
                var x = a.first + (b.first - a.first) * f
                var w = a.second + (b.second - a.second) * f
                if (i0 != i1) {
                    val estira = kotlin.math.sin(Math.PI * f).toFloat() * minOf(with(densidad) { 26.dp.toPx() }, kotlin.math.abs(b.first - a.first) * 0.18f)
                    x -= estira * 0.3f; w += estira
                }
                drawRect(acento, Offset(x, 0f), Size(w, size.height))
            }, verticalAlignment = Alignment.CenterVertically) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                val cerca = (1f - kotlin.math.abs(pager.currentPage + pager.currentPageOffsetFraction - i)).coerceIn(0f, 1f)
                Row(Modifier.fillMaxHeight().onGloballyPositioned { c -> sitios[i] = kotlin.math.round(c.positionInParent().x).toInt() to c.size.width }
                    .let { m -> if (toque != null) toque(m, on) { onSel(i) } else m.pulsable(hundido = 0.94f, vibra = if (on) null else Vibra::tic) { onSel(i) } }
                    .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(n.uppercase(), style = TextStyle(lerp(tinta, sobreAcento, cerca), 12.sp, FontWeight.Black, fontFamily = letra, letterSpacing = 1.sp), maxLines = 1)
                    cuentas.getOrNull(i)?.let { c ->
                        BasicText(" ", style = TextStyle(fontSize = 13.sp))
                        Rodante(c) { v -> BasicText("$v", style = TextStyle(lerp(acento, sobreAcento, cerca), 13.sp, FontWeight.ExtraBold, fontFamily = cifras), maxLines = 1) }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(filete))
    }
}
