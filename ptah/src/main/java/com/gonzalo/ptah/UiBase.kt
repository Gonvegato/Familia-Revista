package com.gonzalo.ptah

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto con la letra de títulos. */
@Composable
fun Titulo(t: String, tam: Int = 28, modifier: Modifier = Modifier, color: Color = Charcoal, lineas: Int = 2) =
    Text(t, modifier, color = color, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = tam.sp, lineHeight = (tam * 1.1f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

@Composable
fun Etiqueta(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, modifier, color = color, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)

@Composable
fun Cuerpo(t: String, modifier: Modifier = Modifier, color: Color = Muted, tam: Int = 14, lineas: Int = Int.MAX_VALUE) =
    Text(t, modifier, color = color, fontFamily = Body, fontSize = tam.sp, lineHeight = (tam * 1.4f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

/** Botón principal: relleno de acento, en versalitas (el «BigAction» de Atrio). */
@Composable
fun BotonAcento(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, alto: Dp = 52.dp, alHacer: () -> Unit) {
    Row(
        modifier.heightIn(min = alto).clip(pildora()).background(Acento).tocable(onClick = alHacer).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = SobreAcento, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/**
 * El botón ancho de la ficha de Atrio («BigAction»): ocupa todo el ancho, relleno del acento (o de la tinta, el secundario), con su icono y el texto en
 * versalitas. Con las esquinas rectas de la revista son los «cuadrados anchos».
 */
@Composable
fun AccionGrande(icono: ImageVector?, texto: String, modifier: Modifier = Modifier, acento: Boolean = true, enabled: Boolean = true, alHacer: () -> Unit) {
    val fondo = Charcoal
    val letra = Paper
    Row(
        modifier.heightIn(min = 52.dp).clip(pildora()).background(if (enabled) fondo else fondo.copy(alpha = 0.35f)).tocable(enabled = enabled, onClick = alHacer)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = letra, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = letra, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)  // (en dos líneas antes que cortado)
    }
}

/** Botón secundario: contorno de tinta, en versalitas. */
@Composable
fun BotonSuave(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, peligro: Boolean = false, alHacer: () -> Unit) {
    val col = if (peligro) Coral else Charcoal
    Row(
        modifier.heightIn(min = 44.dp).clip(pildora()).border(1.dp, if (peligro) Coral else Edge, pildora()).tocable(onClick = alHacer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = col, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = col, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Icono con acción. En la revista, solo el icono (sin círculo ni caja), como en Atrio. */
@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, modifier: Modifier = Modifier, fondo: Color = Color.Transparent, tinta: Color = Charcoal, tam: Dp = 44.dp, alHacer: () -> Unit) {
    Box(
        modifier.size(tam).clip(CircleShape).let { if (fondo != Color.Transparent) it.background(fondo) else it }
            .tocable(onClick = alHacer).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) { Icon(icono, null, tint = tinta, modifier = Modifier.size(tam * 0.5f)) }
}

/** Filtro: texto en versalitas, el elegido en acento y subrayado (el de Atrio). */
@Composable
fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, cuenta: Int? = null, alHacer: () -> Unit) {
    FiltroRevista(texto + (if (cuenta != null) " $cuenta" else ""), activo, modifier.padding(end = 14.dp), alHacer)
}

/** Panel: una tarjeta redonda (0.4.0: fuera el purismo de la revista; se entiende mejor qué va con qué). */
@Composable
fun Panel(modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Tarjeta(modifier.padding(vertical = 5.dp), alHacer = alHacer, contenido = contenido)
}

/** Una fila de ajuste: título, descripción y algo a la derecha. */
@Composable
fun Fila(titulo: String, descripcion: String? = null, modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, final: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().let { if (alHacer != null) it.tocable(onClick = alHacer) else it }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp)
            if (descripcion != null) Cuerpo(descripcion, tam = 13)
        }
        if (final != null) { Spacer(Modifier.width(12.dp)); final() }
    }
}

/** Interruptor (cuadrado en la revista, como el de Atrio). */
@Composable
fun Interruptor(activo: Boolean, alCambiar: (Boolean) -> Unit) {
    val fondo by animateColorAsState(if (activo) Acento else Sand, label = "sw")
    val forma = RoundedCornerShape(50)
    Box(Modifier.width(48.dp).height(28.dp).clip(forma).background(fondo).border(1.dp, if (activo) Acento else Line, forma).tocable(role = Role.Switch) { alCambiar(!activo) }.padding(3.dp)) {
        Box(Modifier.align(if (activo) Alignment.CenterEnd else Alignment.CenterStart).size(22.dp).clip(CircleShape).background(if (activo) Color.White else Muted))
    }
}
