package com.gonzalo.cobalto.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.familia.pc.revista.*
import java.time.LocalDate

/**
 * Las cifras de una obra (las del móvil y las de Atrio): las palabras, escenas y capítulos, la meta (y cuándo llegarás a tu ritmo),
 * los últimos 30 días, cómo va el manuscrito (borrador, revisión, final), los sprints y las palabras de cada capítulo.
 */
@Composable
fun CifrasObra(obra: Doc, abrirEscena: (String) -> Unit) {
    val e = Cuaderno.escritos
    val ver = e.version
    val pid = obra.id
    val caps = remember(ver, pid) { e.capitulos(pid) }
    val escenas = remember(ver, pid) { e.escenasDe(pid) }
    val total = escenas.sumOf { it.i("palabras") }
    val meta = obra.i("meta")
    val diaria = obra.i("diaria")
    val dias = remember(ver, pid) { e.ultimosDias(30, pid) }
    val ultimos14 = dias.takeLast(14).map { it.second }
    val ritmo = if (ultimos14.any { it > 0 }) ultimos14.sum() / 14.0 else 0.0
    val hoy = e.escritas(Escritos.hoy(), pid)
    val mejor = dias.maxByOrNull { it.second }
    val estados = escenas.groupingBy { it.i("estado") }.eachCount()
    val maxCap = (caps.maxOfOrNull { e.palabrasCapitulo(it.id) } ?: 1).coerceAtLeast(1)
    val racha = e.racha(pid)
    Pagina(ancho = 1100.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Cifra(Formato.cifra(total), "palabras", acento = true)
            Cifra("${escenas.size}", if (escenas.size == 1) "escena" else "escenas")
            Cifra("${caps.size}", if (caps.size == 1) "capítulo" else "capítulos")
            Cifra(Formato.cifra(if (escenas.isEmpty()) 0 else total / escenas.size), "por escena")
            Cifra("${maxOf(1, (total + 219) / 220)}", "min de lectura")
        }
        if (meta > 0) {
            Spacer(Modifier.height(26.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
            Antetitulo("Meta de la obra")
            Spacer(Modifier.height(10.dp))
            Progreso(total.toFloat() / meta, colorEscrito(obra.s("color")), 8.dp)
            val pct = total * 100L / meta
            val falta = (meta - total).coerceAtLeast(0)
            Text("$pct % · " + if (falta == 0) "meta alcanzada" else "faltan ${Formato.cifra(falta)} palabras" + if (ritmo >= 1) {
                val d = Math.ceil(falta / ritmo).toLong()
                " · a tu ritmo de los últimos 14 días (${Formato.cifra(ritmo.toInt())} al día), el " + Formato.fecha(System.currentTimeMillis() + d * 86_400_000L)
            } else "", color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(26.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Últimos 30 días")
        Spacer(Modifier.height(12.dp))
        Barras(dias.map { it.second }, diaria)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 8.dp)) {
            Text("Hoy ${Formato.cifra(hoy)}" + if (diaria > 0) " de ${Formato.cifra(diaria)}" else "", color = Muted, fontFamily = Body, fontSize = 13.sp)
            Text("Racha de $racha ${if (racha == 1) "día" else "días"}", color = Muted, fontFamily = Body, fontSize = 13.sp)
            Text("${e.diasEscritos(pid)} días escribiendo en esta obra", color = Muted, fontFamily = Body, fontSize = 13.sp)
            if (mejor != null && mejor.second > 0) Text("Tu mejor día: ${Formato.cifra(mejor.second)} (${Formato.fechaSinAno(LocalDate.ofEpochDay(mejor.first))})", color = Muted, fontFamily = Body, fontSize = 13.sp)
        }
        if (escenas.isNotEmpty()) {
            Spacer(Modifier.height(26.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
            Antetitulo("Cómo va el manuscrito")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(12.dp)) {
                ESTADOS_ESCENA.indices.forEach { i -> val n = estados[i] ?: 0; if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colorEstado(i))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 8.dp)) {
                ESTADOS_ESCENA.forEachIndexed { i, n -> Row(verticalAlignment = Alignment.CenterVertically) { Punto(colorEstado(i), 10.dp); Spacer(Modifier.width(6.dp)); Text("${estados[i] ?: 0} ${n.lowercase()}", color = Charcoal, fontFamily = Body, fontSize = 13.sp) } }
            }
        }
        val sprints = remember(SprintEscribir.terminado, SprintEscribir.hasta) { SprintEscribir.historial(pid) }
        if (sprints.isNotEmpty()) { Spacer(Modifier.height(26.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp)); SprintsObra(pid) }
        if (caps.isNotEmpty()) {
            Spacer(Modifier.height(26.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
            Antetitulo("Palabras por capítulo")
            Spacer(Modifier.height(10.dp))
            caps.forEachIndexed { i, c ->
                val p = e.palabrasCapitulo(c.id)
                val primera = e.escenas(c.id).firstOrNull()
                Row(Modifier.fillMaxWidth().pointerHoverIcon(if (primera != null) PointerIcon.Hand else PointerIcon.Default).clickable(enabled = primera != null) { primera?.let { abrirEscena(it.id) } }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Text(c.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(240.dp))
                    Box(Modifier.weight(1f).height(10.dp).background(Sand)) { Box(Modifier.fillMaxHeight().fillMaxWidth(p.toFloat() / maxCap).background(if (Publica.hecho(e, c)) Acento else Charcoal.copy(alpha = 0.75f))) }
                    Text(Formato.cifra(p), color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                }
            }
            Text("En acento, los capítulos terminados.", color = Muted, fontFamily = Body, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
