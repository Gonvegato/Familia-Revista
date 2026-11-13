// Copiado de familia/ (Android) por herramientas: lo mismo en el Mac. Lo de Android (vibrar, el gesto de atrás, las preferencias) cambia por lo del ordenador.
package com.gonzalo.familia.pc.mov

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/**
 * De dónde sale lo que se abre: la fila que tocaste y su tapa (en coordenadas de la pantalla). La ficha nace de esa fila y la tapa vuela
 * desde ahí hasta su sitio; al cerrar, vuelve. Se apunta justo antes de abrir y se toma al abrir ([toma]).
 */
object Origen {
    class Datos(val clave: String, val fila: Rect?, val tapa: Rect?)
    private var pendiente: Datos? = null

    fun apunta(clave: String, fila: LayoutCoordinates?, tapa: LayoutCoordinates?) {
        pendiente = Datos(clave, fila?.takeIf { it.isAttached }?.boundsInRoot(), tapa?.takeIf { it.isAttached }?.boundsInRoot())
    }

    /** El origen apuntado para [clave] (y se olvida); null si se abrió desde otro sitio (un widget, un aviso…). */
    fun toma(clave: String): Datos? = pendiente?.takeIf { it.clave == clave }.also { pendiente = null }
}

/**
 * La tapa que va volando entre la fila y la ficha. [t] es por dónde va (0: en la fila; 1: en la ficha). Al despegar y al aterrizar, la copia
 * que vuela y la tapa de verdad se funden (no se cambia una por otra de golpe): así no se nota que no son idénticas (con o sin autor
 * pintado, con o sin marco).
 */
object Vuelo {
    var clave by mutableStateOf<String?>(null)
    /** Dónde está la tapa de destino (la de la ficha), en la pantalla. */
    var destino by mutableStateOf<Rect?>(null)
    /** La tapa de destino (para saber dónde está justo al cerrar, aunque se haya movido al bajar o con el gesto de atrás). */
    var coords: LayoutCoordinates? = null
    var t by androidx.compose.runtime.mutableFloatStateOf(0f)

    private fun despega() = (t / 0.12f).coerceIn(0f, 1f)
    private fun aterriza() = ((1f - t) / 0.15f).coerceIn(0f, 1f)
    /** Lo que se ve de la copia que vuela. */
    fun alfaCopia(): Float = minOf(despega(), aterriza())
    /** Lo que se ve de la tapa de la fila (de donde sale y adonde vuelve). */
    fun alfaOrigen(id: String): Float = if (clave != id) 1f else 1f - despega()
    /** Lo que se ve de la tapa de la ficha. */
    fun alfaDestino(id: String): Float = if (clave != id) 1f else 1f - aterriza()
    /** Dónde está ahora la tapa de destino (o la última que se supo). */
    fun destinoAhora(): Rect? = coords?.takeIf { it.isAttached }?.boundsInRoot() ?: destino
}

/** Lo que se ve del papel que crece desde una fila (o una palabra): aparece al empezar y se funde al acabar, sin aparecer ni irse de golpe. */
fun fundidoBorde(t: Float): Float = (t / 0.22f).coerceIn(0f, 1f)

fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/**
 * La forma de algo que crece desde [desde] (la fila tocada) hasta ocupar todo, a la altura [t] (0: la fila; 1: todo), con esquinas que se
 * redondean al principio y, con [extraRadio], durante el gesto de atrás.
 */
class FormaEntre(private val desde: Rect?, private val t: Float, private val extraRadio: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val todo = Rect(0f, 0f, size.width, size.height)
        val r = if (desde == null) todo else lerpRect(desde, todo, t.coerceIn(0f, 1.2f))
        val radio = with(density) { 16.dp.toPx() } * (1f - t).coerceIn(0f, 1f) + extraRadio
        return Outline.Rounded(RoundRect(r, CornerRadius(radio)))
    }
}

/** Un aviso abajo («Movido a Leídos · DESHACER») que entra con un muelle y se va solo. */
object Avisos {
    class Aviso(val texto: String, val deshacer: (() -> Unit)?, val n: Long)
    var actual by mutableStateOf<Aviso?>(null)
        private set
    fun di(texto: String, deshacer: (() -> Unit)? = null) { actual = Aviso(texto, deshacer, System.nanoTime()) }
    fun quita() { actual = null }
}

/** Donde salen los avisos: se pone una vez, encima de todo, en la raíz de la app. */
@Composable
fun BoxScope.CapaAvisos(fondo: Color, letra: Color, acento: Color, fuente: FontFamily) {
    val aviso = Avisos.actual ?: return
    val y = remember(aviso.n) { Animatable(1f) }
    LaunchedEffect(aviso.n) {
        y.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 380f))
        delay(2600)
        y.animateTo(1.4f, tween(220))
        if (Avisos.actual?.n == aviso.n) Avisos.quita()
    }
    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp).fillMaxWidth()
        .graphicsLayer { translationY = y.value * (size.height + 40.dp.toPx()) }
        .background(fondo).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(aviso.texto.uppercase(), Modifier.weight(1f), style = TextStyle(letra, 12.sp, FontWeight.Black, fontFamily = fuente, letterSpacing = 1.2.sp))
        aviso.deshacer?.let { f ->
            Box(Modifier.pulsable(vibra = Vibra::tic) { Avisos.quita(); f() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                BasicText("DESHACER", style = TextStyle(acento, 12.sp, FontWeight.Black, fontFamily = fuente, letterSpacing = 1.2.sp))
            }
        }
    }
}
