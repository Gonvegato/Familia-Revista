package com.gonzalo.azabache.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-ES")
private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
private val DIA_LARGO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)

/** «5 nov 2026» de un día desde 1970 ("" si no hay fecha). */
fun fecha(dia: Long): String = if (dia <= 0) "" else LocalDate.ofEpochDay(dia).format(FECHA)
fun diaLargo(d: LocalDate): String = d.format(DIA_LARGO).replaceFirstChar { it.uppercase() }

/** «2 h 10 min». */
fun duracion(min: Long): String = when { min <= 0 -> "—"; min < 60 -> "$min min"; min % 60 == 0L -> "${min / 60} h"; else -> "${min / 60} h ${min % 60} min" }

/** Cómo está: «Pasado», «Leyendo», «Pendiente»… */
fun estadoDe(f: Ficha, k: Kind): String = if (f.valorada) k.doneWord.removeSuffix("s") else k.statuses[f.estado.coerceIn(0, 2)]

/** La nota como se enseña: nunca un 0 que no has puesto. */
fun notaTexto(f: Ficha): String = if (f.sinValorar) "Sin valorar" else f.general.toString()

/** Lo que se dice de una ficha debajo de su nombre. */
fun metaDe(f: Ficha, k: Kind): String = listOf(f.creador, f.plataforma, if (f.valorada && f.terminado > 0) fecha(f.terminado) else "").filter { it.isNotBlank() }.joinToString(" · ")

/** Una ficha en una cuadrícula: la portada, el nombre y la nota. */
@Composable
fun Tarjeta(f: Ficha, k: Kind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    Column(modifier.hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(bottom = 6.dp)) {
        Box {
            PortadaCosa(f.portada, f.portadaWeb, f.nombre, Modifier.fillMaxWidth().graphicsLayer { val e = if (encima) 1.02f else 1f; scaleX = e; scaleY = e }, pie = if (f.valorada) "" else k.unit)
            if (f.valorada) {
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(esquina(4.dp)).background(if (f.sinValorar) Crudo else Acento).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(notaTexto(f), color = if (f.sinValorar) Muted else SobreAcento, fontFamily = Body, fontSize = if (f.sinValorar) 10.sp else 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (f.fav) Text("★", Modifier.align(Alignment.TopEnd).padding(8.dp), color = Dorado, fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(f.nombre, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(metaDe(f, k).ifBlank { estadoDe(f, k) }, color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una fila de una lista (para el diario, las sagas…): una portada pequeña, el nombre y lo que se pase. */
@Composable
fun FilaFicha(f: Ficha, k: Kind, sub: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PortadaCosa(f.portada, f.portadaWeb, f.nombre, Modifier.width(44.dp), pie = "")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.nombre, color = Charcoal, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        derecha()
    }
}

/** Un antetítulo de sección con su enlace a la derecha («VER TODO →»). */
@Composable
fun Seccion(titulo: String, enlace: String? = null, onEnlace: () -> Unit = {}) {
    Spacer(Modifier.height(26.dp))
    Filete(2.dp)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Antetitulo(titulo, Modifier.weight(1f))
        if (enlace != null) EnlaceRevista(enlace, acento = true) { onEnlace() }
    }
}

/** El texto en cursiva de las entradillas. */
@Composable
fun Entradilla(t: String) = Text(t, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp)
