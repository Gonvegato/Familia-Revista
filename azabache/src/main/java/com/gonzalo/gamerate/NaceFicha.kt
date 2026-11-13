package com.gonzalo.gamerate

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gonzalo.familia.FormaEntre
import com.gonzalo.familia.fundidoBorde

/**
 * De dónde nace la ficha de Jonsu: al pasar de la portada (o una página) a una ficha, la franja de la pantalla que tocaste (todo el ancho,
 * centrada en el dedo); de una ficha a otra, de ninguna (se funde, como siempre). Al cerrarla, vuelve a la misma franja.
 */
class NaceFicha {
    var origen: Rect? = null; private set
    private var antes: Any? = null
    fun cambia(ahora: Any, toque: Offset, ancho: Float, alto: Float) {
        if (antes == null) { antes = ahora; return }
        if (antes == ahora) return
        val deCasa = antes is String; val aCasa = ahora is String
        origen = when {
            deCasa && !aCasa -> toque.takeIf { it.isSpecified }?.let { Rect(0f, it.y - alto / 2f, ancho, it.y + alto / 2f) }
            !deCasa && !aCasa -> null
            else -> origen
        }
        antes = ahora
    }
}

/**
 * La ficha de Jonsu, dentro de la transición de pantallas: nace recortada desde [origen] (el papel crece de la franja a la pantalla entera,
 * fundiéndose al empezar) y, al cerrarse, vuelve a ella; con el gesto de atrás ([atras], de 0 a 1) encoge y se aparta con el dedo. Las
 * portadas compartidas siguen volando por encima, como antes.
 */
@Composable
fun CapaFichaJonsu(escena: AnimatedVisibilityScope, origen: Rect?, atras: Float, desdeIzquierda: Boolean, contenido: @Composable () -> Unit) {
    val t by escena.transition.animateFloat(transitionSpec = {
        if (targetState == EnterExitState.Visible) spring(dampingRatio = 0.9f, stiffness = 230f) else spring(dampingRatio = 1f, stiffness = 300f)
    }, label = "nace") { if (it == EnterExitState.Visible) 1f else 0f }
    Box(Modifier.fillMaxSize().graphicsLayer {
        val p = atras
        val radio = 26.dp.toPx() * (p * 4f).coerceAtMost(1f)
        if (origen != null) {
            clip = true
            shape = FormaEntre(origen, t, radio)
            alpha = fundidoBorde(t)
        } else if (p > 0f) {
            clip = true
            shape = FormaEntre(null, 1f, radio)
        }
        val s = 1f - 0.1f * p
        scaleX = s; scaleY = s
        translationX = (if (desdeIzquierda) 1f else -1f) * p * 24.dp.toPx()
    }.let { if (origen != null) it.background(Paper) else it }) { contenido() }
}
