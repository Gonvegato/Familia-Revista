package com.gonzalo.mostaza

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/*
 * El foco del mando, en toda la app. Con un mando (o un teclado), la cruceta mueve el foco de una cosa a otra y A la pulsa; lo que tiene
 * el foco tiene que verse a un metro, en la tablet sobre la mesa: un marco del acento, redondeado y un poco por fuera, con un velo suave
 * del mismo color. Aparece y se va con una animación corta (140 ms), sin mover nada de sitio (se dibuja encima, no ocupa).
 * Con el dedo no sale nunca: en modo táctil nada tiene el foco.
 */

/** Dibuja el marco de foco cuando [src] tiene el foco. [fuera]: cuánto sobresale; [radio]: lo redondeado de las esquinas. */
fun Modifier.marcoFoco(src: MutableInteractionSource, fuera: Dp = 4.dp, radio: Dp = 6.dp): Modifier = composed {
    val foco by src.collectIsFocusedAsState()
    val t by animateFloatAsState(if (foco) 1f else 0f, tween(140), label = "foco")
    val acento = Acento
    drawWithContent {
        drawContent()
        if (t > 0f) {
            val o = fuera.toPx()
            val r = CornerRadius(radio.toPx())
            val esquina = Offset(-o, -o); val tam = Size(size.width + 2 * o, size.height + 2 * o)
            drawRoundRect(acento.copy(alpha = 0.10f * t), topLeft = esquina, size = tam, cornerRadius = r)
            drawRoundRect(acento.copy(alpha = t), topLeft = esquina, size = tam, cornerRadius = r, style = Stroke(2.dp.toPx() * (0.6f + 0.4f * t)))
        }
    }
}

/**
 * Para las filas que se deslizan (los estantes): la fila se ensancha [d] por cada lado, sin mover lo de dentro (con un contentPadding
 * igual), para que el marco de foco y el pequeño aumento de la portada enfocada no se corten en los bordes.
 */
fun Modifier.sangrado(d: Dp): Modifier = this.layout { m, c ->
    val px = d.roundToPx()
    if (!c.hasBoundedWidth) { val p = m.measure(c); return@layout layout(p.width, p.height) { p.place(0, 0) } }
    val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * px, maxWidth = c.maxWidth + 2 * px))
    layout(c.maxWidth, p.height) { p.place(-px, 0) }
}

/**
 * Una página que se pone encima de otra (la ficha, la saga): con la cruceta del mando no se sale de ella hacia lo que queda debajo (que
 * sigue ahí, tapado, para volver donde estabas). Atrás (B) la cierra.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.atrapaFoco(): Modifier = this.focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()

/** Como `clickable`, y con el marco de foco del mando ([marcoFoco]). */
fun Modifier.tocable(enabled: Boolean = true, role: Role? = null, fuera: Dp = 4.dp, forma: androidx.compose.ui.graphics.Shape? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    // (dirección D: con [forma], lo pulsable se recorta redondo; el marco de foco va por fuera, sin recortar)
    this.marcoFoco(src, fuera, if (forma != null) 20.dp else 6.dp).let { if (forma != null) it.clip(forma) else it }.clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
}
