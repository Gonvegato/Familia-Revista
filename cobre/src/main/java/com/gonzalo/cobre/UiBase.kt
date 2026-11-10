package com.gonzalo.cobre

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
    Text(t, modifier, color = color, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = (tam * 0.9f).sp, lineHeight = (tam * 1.02f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

@Composable
fun Etiqueta(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t, modifier, color = color, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

@Composable
fun Cuerpo(t: String, modifier: Modifier = Modifier, color: Color = Muted, tam: Int = 14, lineas: Int = Int.MAX_VALUE) =
    Text(t, modifier, color = color, fontFamily = Body, fontSize = tam.sp, lineHeight = (tam * 1.4f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

/** Botón principal: relleno de acento, en versalitas (el «BigAction» de Atrio). */
@Composable
fun BotonAcento(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, alto: Dp = 52.dp, alHacer: () -> Unit) {
    Row(
        modifier.heightIn(min = alto).tocableRedondo(16.dp, onClick = alHacer).background(Acento).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = SobreAcento, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
    }
}

/**
 * El botón ancho de la ficha de Atrio («BigAction»): ocupa todo el ancho, relleno del acento (o de la tinta, el secundario), con su icono y el texto en
 * versalitas. Con las esquinas rectas de la revista son los «cuadrados anchos».
 */
@Composable
fun AccionGrande(icono: ImageVector?, texto: String, modifier: Modifier = Modifier, acento: Boolean = true, enabled: Boolean = true, alHacer: () -> Unit) {
    val fondo = if (acento) Acento else Sand
    val letra = if (acento) SobreAcento else Charcoal
    Row(
        modifier.heightIn(min = 52.dp).tocableRedondo(16.dp, enabled, onClick = alHacer).background(if (enabled) fondo else fondo.copy(alpha = 0.4f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icono != null) { Icon(icono, null, tint = letra, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        // (con la letra grande, en dos líneas antes que cortado)
        Text(texto, color = letra, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text("→", color = letra, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

/** Botón secundario: contorno de tinta, en versalitas. */
@Composable
fun BotonSuave(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, peligro: Boolean = false, alHacer: () -> Unit) {
    val col = if (peligro) Acento else Charcoal
    Row(
        modifier.heightIn(min = 44.dp).tocableRedondo(100.dp, onClick = alHacer).background(Sand).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = col, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, color = col, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Icono con acción. En la revista, solo el icono (sin círculo ni caja), como en Atrio. */
@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, modifier: Modifier = Modifier, fondo: Color = Color.Transparent, tinta: Color = Charcoal, tam: Dp = 44.dp, alHacer: () -> Unit) {
    Box(
        modifier.size(tam).tocableRedondo(100.dp, onClick = alHacer).background(fondo).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) { Icon(icono, null, tint = tinta, modifier = Modifier.size(tam * 0.5f)) }
}

/** Filtro: texto en versalitas, el elegido en acento y subrayado (el de Atrio). */
@Composable
fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, cuenta: Int? = null, alHacer: () -> Unit) {
    FiltroRevista(texto + (if (cuenta != null) " $cuenta" else ""), activo, modifier.padding(end = 8.dp), alHacer)
}

/** Panel: en la revista, sin tarjeta; un filete grueso arriba separa los bloques. */
@Composable
fun Panel(modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    // Dirección D: una tarjeta redonda en superficie, sin borde
    val forma = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().let { if (alHacer != null) it.tocableRedondo(22.dp, onClick = alHacer) else it.clip(forma) }.background(Crudo).padding(16.dp),
        content = contenido
    )
}

/** Una fila de ajuste: título, descripción y algo a la derecha. */
@Composable
fun Fila(titulo: String, descripcion: String? = null, modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, final: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().let { if (alHacer != null) it.tocable(onClick = alHacer) else it }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (descripcion != null) Cuerpo(descripcion, tam = 13)
        }
        if (final != null) { Spacer(Modifier.width(12.dp)); final() }
    }
}

/** Interruptor (cuadrado en la revista, como el de Atrio). */
@Composable
fun Interruptor(activo: Boolean, alCambiar: (Boolean) -> Unit) {
    val fondo by animateColorAsState(if (activo) Acento else Sand, label = "sw")
    val forma = if (Skin.forma.esq == Esq.Redonda) RoundedCornerShape(50) else esquina(4.dp)
    Box(Modifier.width(48.dp).height(28.dp).tocableRedondo(100.dp, role = Role.Switch) { alCambiar(!activo) }.background(fondo).padding(3.dp)) {
        Box(Modifier.align(if (activo) Alignment.CenterEnd else Alignment.CenterStart).size(22.dp).clip(if (Skin.forma.esq == Esq.Redonda) CircleShape else esquina(3.dp)).background(if (activo) SobreAcento else Muted))
    }
}
