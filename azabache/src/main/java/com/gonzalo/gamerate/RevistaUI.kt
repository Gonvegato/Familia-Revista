package com.gonzalo.gamerate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Las piezas comunes de la revista en el móvil, las mismas que en el PC (revista.css): el enlace en versalitas, el
 * «← Volver», las pestañas (versalitas, la elegida en tinta con el subrayado de acento y el número en acento) y los
 * filtros (versalitas, el elegido en acento y subrayado). Todas las pantallas usan estas, para que se vean igual.
 */

/** Un enlace de acción en versalitas («BUSCAR», «EDITAR», «+ AÑADIR»…). */
@Composable
fun EnlaceRevista(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, onClick: () -> Unit) {
    Text(texto.uppercase(), color = if (acento) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1,
        modifier = modifier.semantics { role = Role.Button }.press(haptic = false, onClick = onClick).padding(vertical = 10.dp))
}

/** «← VOLVER» (o «← AZABACHE» en lo que cuelga de la portada): siempre el mismo, arriba a la izquierda. */
@Composable
fun VolverRevista(onClick: () -> Unit, texto: String = "Volver") = EnlaceRevista("← $texto", onClick = onClick)

/** Las pestañas de una página: versalitas; la elegida en tinta con el subrayado de acento; su número, en acento. */
@Composable
fun PestanasRevista(nombres: List<String>, sel: Int, modifier: Modifier = Modifier, cuentas: List<Int?>? = null, onSel: (Int) -> Unit) {
    // La fila se desplaza sola hasta la pestaña elegida si no se ve entera (al entrar en «Sagas» desde la portada, por ejemplo)
    val desplazamiento = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel], anchoFila) {
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
            }.horizontalScroll(desplazamiento), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x + desplazamiento.value).roundToInt() to c.size.width }
                    .semantics { selected = on; role = Role.Tab }.press(haptic = false) { onSel(i) }
                    .drawBehind { if (on) drawRect(ColGeneral, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
                    .padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(n.uppercase(), color = if (on) Charcoal else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    cuentas?.getOrNull(i)?.let { c -> Text(" $c", color = if (on) Charcoal else Muted,  // (el rojo, solo para lo que se toca: la pestaña activa y los enlaces)
                         fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1) }
                }
            }
        }
        Filete(1.dp, Line)
    }
}

/** Filtros u opciones de una sola elección, en una fila que se desliza: versalitas; el elegido en acento y subrayado. */
@Composable
fun FiltrosRevista(opciones: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    val desplazamiento = rememberScrollState()
    Row(modifier.fillMaxWidth().drawWithContent {
            // Un degradado en los bordes cuando hay más opciones por un lado o por otro (para que no parezcan cortadas)
            drawContent()
            val f = 28.dp.toPx()
            if (desplazamiento.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
            if (desplazamiento.value < desplazamiento.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
        }.horizontalScroll(desplazamiento), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        opciones.forEachIndexed { i, o -> FiltroRevista(o, i == sel) { onSel(i) } }
    }
}

/** Un filtro suelto (que se enciende y se apaga): versalitas, encendido en acento y subrayado. */
@Composable
fun FiltroRevista(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(texto.uppercase(), color = if (on) ColGeneral else Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
        modifier = modifier.semantics { selected = on }.press(haptic = false, onClick = onClick).padding(top = 8.dp, bottom = 13.dp)
            .drawBehind { if (on) drawRect(ColGeneral, topLeft = Offset(0f, size.height - 4.dp.toPx()), size = Size(size.width, 1.5.dp.toPx())) })
}

/**
 * Las pestañas de Ajustes, las mismas que en Hiedra, Marfil y Cobalto: Apariencia · Cuenta · Avanzado. [sel]: 0 Apariencia (la pantalla
 * de estilo), 1 Cuenta (tu perfil, la pareja, los objetivos…), 2 Avanzado.
 */
@Composable
fun PestanasAjustes(sel: Int, ir: (Screen) -> Unit) =
    PestanasRevista(listOf("Apariencia", "Cuenta", "Avanzado"), sel, Modifier.padding(bottom = 14.dp)) {
        if (it == sel) return@PestanasRevista
        when (it) {
            0 -> Skin.open = true
            1 -> { Skin.open = false; ir(Screen.Profile) }
            else -> { Skin.open = false; ir(Screen.Settings) }
        }
    }

/** Ir a una pestaña de Ajustes desde la de Apariencia (que se pinta por encima, fuera de la navegación de la app). */
object AjustesNav { var ir by androidx.compose.runtime.mutableStateOf<Screen?>(null) }


/**
 * La cabecera plegada de la portada: al bajar, el nombre y lo de arriba (buscar, ajustes) se quedan fijos en una barra fina, y el resto de
 * la cabecera se va con la página. Igual en Hiedra, Marfil y Cobalto.
 */
@Composable
fun BarraPlegada(nombre: String, visible: Boolean, fondo: androidx.compose.ui.graphics.Color, acciones: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().background(fondo).statusBarsPadding().padding(horizontal = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(nombre.uppercase(), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.weight(1f))
                acciones()
            }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        }
    }
}
