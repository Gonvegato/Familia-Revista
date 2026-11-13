// Copiado de familia/ (Android) por herramientas: lo mismo en el Mac. Lo de Android (vibrar, el gesto de atrás, las preferencias) cambia por lo del ordenador.
package com.gonzalo.familia.pc.mov

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * El movimiento de la familia: muelles en vez de curvas fijas, todo interrumpible, cada gesto con su vibración corta.
 * (Lo que se probó en la maqueta «Seshat en movimiento».)
 */
object Muelle {
    /** Lo general: llega rápido y asienta sin rebotar de más. */
    fun <T> suave(): AnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 320f)
    /** Para cosas que se desplazan mucho (páginas, la ficha): un poco más lento, sin rebote. */
    fun <T> ancho(): AnimationSpec<T> = spring(dampingRatio = 0.88f, stiffness = 260f)
    /** Para lo pequeño que se suelta (un botón que vuelve, el sello): con un poco de rebote. */
    fun <T> vivo(): AnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 700f)
    /** La selección que salta de una casilla a otra. */
    fun <T> seleccion(): AnimationSpec<T> = spring(dampingRatio = 0.74f, stiffness = 480f)
    /** Lo que se llena (barras). */
    fun <T> llenar(): AnimationSpec<T> = spring(dampingRatio = 0.92f, stiffness = 150f)
}

/** Las vibraciones: cortas y distintas según lo que pasa. */
object Vibra {
    /** (en el ordenador no vibra nada: se deja para que el código sea el mismo que en el móvil) */
    fun tic(v: Any?) {}
    fun clic(v: Any?) {}
    fun golpe(v: Any?) {}
    fun hecho(v: Any?) {}
}

/**
 * Pulsar se nota: lo tocado se hunde un poco al apretar y vuelve con un muelle al soltar, y vibra según [vibra].
 * Sin onda gris (la revista no la lleva). [alMantener]: mantener pulsado (vibra «golpe»).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pulsable(
    enabled: Boolean = true,
    hundido: Float = 0.965f,
    vibra: ((Any?) -> Unit)? = Vibra::clic,
    alMantener: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val fuente = remember { MutableInteractionSource() }
    val apretado by fuente.collectIsPressedAsState()
    val escala = remember { Animatable(1f) }
    val vista = Unit
    LaunchedEffect(apretado) {
        if (apretado) escala.animateTo(hundido, tween(110)) else escala.animateTo(1f, Muelle.vivo())
    }
    this
        .graphicsLayer { scaleX = escala.value; scaleY = escala.value }
        .combinedClickable(
            interactionSource = fuente, indication = null, enabled = enabled,
            onLongClick = alMantener?.let { f -> { Vibra.golpe(vista); f() } },
            onClick = { vibra?.invoke(vista); onClick() },
        )
}

/**
 * Pasar de página cuesta poco: basta un 15 % del ancho (o un gesto rápido corto), y la página asienta con un muelle.
 * Nunca salta más de una página por gesto.
 */
@Composable
fun pasoDePagina(estado: PagerState): TargetedFlingBehavior = PagerDefaults.flingBehavior(
    state = estado,
    pagerSnapDistance = PagerSnapDistance.atMost(1),
    snapAnimationSpec = spring(dampingRatio = 0.88f, stiffness = 260f),
    snapPositionalThreshold = 0.15f,
)

/**
 * Un número que rueda al cambiar: el nuevo entra desde abajo si sube (desde arriba si baja) y el viejo se va. [contenido] lo pinta
 * con las letras de cada app.
 */
@Composable
fun Rodante(valor: Int, modifier: Modifier = Modifier, contenido: @Composable (Int) -> Unit) {
    AnimatedContent(valor, modifier, transitionSpec = {
        val sube = targetState > initialState
        (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { if (sube) it else -it } + fadeIn(tween(120))) togetherWith
            (slideOutVertically(tween(160)) { if (sube) -it else it } + fadeOut(tween(120)))
    }, label = "rodante") { contenido(it) }
}

/**
 * Las entradas: cada cosa de una página hace su entrada (sube un poco y aparece, escalonada) **una sola vez** por sesión.
 * Así, al volver a una página, al deslizar o al bajar por una lista, nada desaparece y vuelve a entrar (eso era el parpadeo).
 */
class Entradas {
    private val hechas = mutableStateMapOf<String, Boolean>()
    /** ¿Le toca entrar a [clave]? (y la apunta como entrada). */
    fun toca(clave: String): Boolean = if (hechas.containsKey(clave)) false else { hechas[clave] = true; true }
}

val LocalEntradas = staticCompositionLocalOf { Entradas() }

/** La entrada de un elemento: si le toca, sube [desde] y aparece con un muelle, tras [orden] × 45 ms. */
fun Modifier.entrada(clave: String, orden: Int = 0, desde: Int = 26): Modifier = composed {
    val entradas = LocalEntradas.current
    val toca = remember(clave) { entradas.toca(clave) }
    if (!toca) return@composed this
    val px = with(LocalDensity.current) { desde.dp.toPx() }
    val y = remember { Animatable(px) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * orden.coerceIn(0, 10))
        launch { a.animateTo(1f, spring(dampingRatio = 1f, stiffness = 300f)) }
        y.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 240f))
    }
    // (sin capa aparte: con una, lo que sobresale de su caja —la etiqueta de estado que pisa la foto, por ejemplo— salía cortado hasta que
    // la entrada acababa; así la transparencia se aplica a cada cosa y nada se recorta)
    graphicsLayer { translationY = y.value; alpha = a.value; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha }
}

/** Una fracción que se llena desde 0 la primera vez que sale [clave] (barras de avance); luego sigue sus cambios con un muelle. */
@Composable
fun llenado(clave: String, fraccion: Float, retraso: Long = 140): Float {
    val entradas = LocalEntradas.current
    val toca = remember(clave) { entradas.toca("barra:$clave") }
    val v = remember(clave) { Animatable(if (toca) 0f else fraccion) }
    LaunchedEffect(clave, fraccion) {
        if (toca && v.value == 0f) delay(retraso)
        v.animateTo(fraccion, Muelle.llenar())
    }
    return v.value
}


/**
 * Algo que aparece poco a poco según [f] (0: no está; 1: está entero): su ancho y su opacidad crecen juntos, así lo que tiene al lado se
 * aparta suavemente en vez de saltar. (Para lo que sale y entra al plegar una cabecera, por ejemplo.)
 */
fun Modifier.apareceSegun(f: Float): Modifier = this.then(Modifier.clipToBounds().layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0))
    val w = (p.width * f.coerceIn(0f, 1f)).roundToInt()
    layout(w, p.height) { if (w > 0) p.placeWithLayer(0, 0) { alpha = f.coerceIn(0f, 1f); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha } }
})

/**
 * El muelle de [pulsable] para algo que ya tiene su propio toque (y su propia [fuente] de interacciones, por ejemplo con el marco del
 * foco del mando): al apretar se hunde un poco y al soltar vuelve con un muelle. Así las apps que se usan también con mando lo tienen
 * sin cambiar cómo se enfocan.
 */
fun Modifier.muelleAlPulsar(fuente: androidx.compose.foundation.interaction.InteractionSource, hundido: Float = 0.965f): Modifier = composed {
    val apretado by fuente.collectIsPressedAsState()
    val escala = remember { Animatable(1f) }
    LaunchedEffect(apretado) { if (apretado) escala.animateTo(hundido, tween(110)) else escala.animateTo(1f, Muelle.vivo()) }
    graphicsLayer { scaleX = escala.value; scaleY = escala.value }
}
