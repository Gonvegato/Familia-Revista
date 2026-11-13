package com.gonzalo.familia

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

/** La tapa que va volando (para esconder, mientras tanto, la de la fila y la de destino). */
object Vuelo {
    var clave by mutableStateOf<String?>(null)
    /** Dónde está la tapa de destino (la de la ficha), en la pantalla. */
    var destino by mutableStateOf<Rect?>(null)
}

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
