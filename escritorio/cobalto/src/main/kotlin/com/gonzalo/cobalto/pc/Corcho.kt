@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EscribirEstructura
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.Formato
import com.gonzalo.familia.pc.revista.*

/** La primera línea de verdad de un texto (sin títulos ni saltos de escena). */
private fun primeraLinea(t: String) = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") && it != "***" }.orEmpty().replace("*", "")

/**
 * El manuscrito en cuadros, como un corcho (los «cuadrados» del móvil): cada parte con su resumen y, debajo, sus capítulos en
 * tarjetas (su color, su número, si está terminado, el resumen o su primera línea, cómo van sus escenas y sus palabras). Un clic
 * abre el capítulo para escribir; con el botón derecho, editarlo, moverlo o meterlo en una parte.
 */
@Composable
fun CorchoObra(obra: Doc, abrirEscena: (String) -> Unit, editar: (Doc) -> Unit) {
    val e = Cuaderno.escritos
    val ver = e.version
    val pid = obra.id
    val est = remember(ver, pid) { EscribirEstructura.estructura(e.deTipo(Escritos.T_CAP, pid), e.partes(pid)) }
    val arriba = est.items.map { if (it is EscribirEstructura.Item.Parte) it.parte else (it as EscribirEstructura.Item.Cap).cap }
    /** Sube o baja algo de arriba (partes y capítulos sueltos comparten el orden), o un capítulo dentro de su parte. */
    fun mover(x: Doc, d: Int) {
        val hermanos = if (x.t == Escritos.T_CAP && e.get(x.s("aid")) != null) est.libro.filter { it.s("aid") == x.s("aid") } else arriba
        val i = hermanos.indexOfFirst { it.id == x.id }; val j = i + d
        if (i < 0 || j !in hermanos.indices) return
        val (antes, despues) = if (d < 0) hermanos.getOrNull(j - 1)?.id to hermanos[j].id else hermanos[j].id to hermanos.getOrNull(j + 1)?.id
        if (x.t == Escritos.T_CAP) e.mueveCapitulo(x.id, x.s("aid").takeIf { e.get(it) != null }.orEmpty(), antes, despues) else e.mueve(x.id, antes, despues)
    }
    fun abre(c: Doc) {
        val s = e.escenas(c.id).firstOrNull() ?: e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "") }
        abrirEscena(s.id)
    }
    val estados = remember(ver, pid) { e.escenasDe(pid).groupingBy { it.i("estado") }.eachCount() }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 18.dp, bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                Text("${est.libro.size} ${if (est.libro.size == 1) "capítulo" else "capítulos"} · ${Formato.cifra(e.palabrasObra(pid))} palabras", color = Muted, fontFamily = Body, fontSize = 13.sp)
                ESTADOS_ESCENA.forEachIndexed { i, n -> Row(verticalAlignment = Alignment.CenterVertically) { Punto(colorEstado(i), 9.dp); Spacer(Modifier.width(5.dp)); Text("${estados[i] ?: 0} ${n.lowercase()}", color = Muted, fontFamily = Body, fontSize = 12.sp) } }
            }
            Text("Un clic abre el capítulo para escribir; con el botón derecho, se edita, se mueve o se mete en una parte.", color = Muted.copy(alpha = 0.8f), fontFamily = Body, fontSize = 11.sp)
            if (est.items.isEmpty()) Vacio("El manuscrito en cuadros", "Crea capítulos (y partes, si quieres): aquí los verás como en un corcho.")
            // Los capítulos sueltos que van seguidos, juntos en una fila; cada parte, con su cabecera
            val grupos = ArrayList<Pair<Doc?, List<Doc>>>()
            est.items.forEach { it ->
                when (it) {
                    is EscribirEstructura.Item.Parte -> grupos += it.parte to it.caps
                    is EscribirEstructura.Item.Cap -> if (grupos.isNotEmpty() && grupos.last().first == null) grupos[grupos.lastIndex] = null to (grupos.last().second + it.cap) else grupos += null to listOf(it.cap)
                }
            }
            grupos.forEach { (parte, caps) ->
                Spacer(Modifier.height(22.dp))
                if (parte != null) ContextMenuArea(items = { listOf(
                    ContextMenuItem("Editar la parte…") { editar(parte) },
                    ContextMenuItem("Mover antes") { mover(parte, -1) },
                    ContextMenuItem("Mover después") { mover(parte, 1) },
                ) }) {
                    Column(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { editar(parte) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(colorEscrito(parte.s("color"), Acento)))
                            Spacer(Modifier.width(8.dp))
                            Text(parte.s("titulo").ifBlank { "Parte" }.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("  ${caps.size} ${if (caps.size == 1) "capítulo" else "capítulos"} · ${Formato.cifra(caps.sumOf { e.palabrasCapitulo(it.id) })} palabras", color = Muted, fontFamily = Body, fontSize = 11.sp)
                        }
                        if (parte.s("resumen").isNotBlank()) Text(parte.s("resumen"), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(6.dp)); Filete(2.dp)
                    }
                } else { Antetitulo("Capítulos", tam = 11.sp); Spacer(Modifier.height(6.dp)); Filete(1.dp, Line) }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    caps.forEach { c ->
                        ContextMenuArea(items = { buildList {
                            add(ContextMenuItem("Abrir para escribir") { abre(c) })
                            add(ContextMenuItem("Editar el capítulo…") { editar(c) })
                            add(ContextMenuItem("Mover antes") { mover(c, -1) })
                            add(ContextMenuItem("Mover después") { mover(c, 1) })
                        } }) { TarjetaCapitulo(e, c, est.libro.indexOfFirst { it.id == c.id } + 1) { abre(c) } }
                    }
                    if (parte != null) NuevoCuadro("Capítulo en esta parte") {
                        val c = e.nuevo(Escritos.T_CAP, pid) { put("titulo", "Capítulo ${e.capitulos(pid).size + 1}"); put("aid", parte.id) }
                        abrirEscena(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "") }.id)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** La tarjeta de un capítulo en el corcho. */
@Composable
private fun TarjetaCapitulo(e: Escritos, c: Doc, n: Int, onClick: () -> Unit) {
    val esc = e.escenas(c.id)
    val color = colorEscrito(c.s("color"), Charcoal)
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    val marcas = listOfNotNull("Capítulo $n", "Terminado".takeIf { Publica.hecho(e, c) }, "Compartido".takeIf { c.b("comp") }).joinToString(" · ")
    val texto = c.s("resumen").ifBlank { primeraLinea(esc.firstOrNull()?.s("texto").orEmpty()) }
    Column(Modifier.width(236.dp).heightIn(min = 176.dp).clip(esquina(6.dp)).background(if (encima) lerpColor(Crudo, color, 0.1f) else Crudo).border(1.dp, if (encima) color else Line, esquina(6.dp))
        .hoverable(hover).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(color))
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(marcas.uppercase(), color = if (Publica.hecho(e, c)) Acento else Muted, fontFamily = Body, fontSize = 9.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(texto.ifBlank { "Sin escribir todavía." }, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val cuenta = IntArray(3); esc.forEach { cuenta[it.i("estado").coerceIn(0, 2)]++ }
            if (esc.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) { (0..2).forEach { i -> if (cuenta[i] > 0) Box(Modifier.weight(cuenta[i].toFloat()).fillMaxHeight().background(colorEstado(i))) } }
            Text("${esc.size} ${if (esc.size == 1) "escena" else "escenas"} · ${Formato.cifra(e.palabrasCapitulo(c.id))} palabras", color = Muted, fontFamily = Body, fontSize = 11.sp)
        }
    }
}

/** El cuadro vacío de «+ …» al final de una fila. */
@Composable
private fun NuevoCuadro(texto: String, onClick: () -> Unit) {
    Column(Modifier.width(236.dp).heightIn(min = 176.dp).clip(esquina(6.dp)).border(1.dp, Line, esquina(6.dp)).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("+", color = Muted, fontFamily = Grotesca, fontSize = 34.sp)
        Text(texto.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
