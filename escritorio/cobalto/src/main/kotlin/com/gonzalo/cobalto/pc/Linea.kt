package com.gonzalo.cobalto.pc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.gonzalo.familia.pc.revista.*

/*
 * La línea temporal: una línea vertical con lo que pasa en la historia, en el orden en que pasa (no el del libro). De cualquier
 * suceso sale una rama a un lado: una trama entera, con sus sucesos, que baja en paralelo y puede volver a la principal. Dónde
 * va cada cosa lo calcula [EscribirEstructura.linea] (el mismo código que el móvil y el PC).
 */

private val COL = 28.dp
private val PUNTO_Y = 24.dp

@Composable
fun LineaObra(obra: Doc, abre: AbreDoc, abrirEscena: (String) -> Unit) {
    val e = Cuaderno.escritos
    val ver = e.version
    val pid = obra.id
    val hs = remember(ver, pid) { e.hitos(pid) }
    val rs = remember(ver, pid) { e.ramas(pid) }
    val L = remember(ver, pid) { EscribirEstructura.linea(hs, rs) }
    val porId = remember(ver, pid) { (hs + rs).associateBy { it.id } }
    val conFecha = remember(ver, pid) { e.escenasDe(pid).filter { it.s("cuando").isNotBlank() } }
    var menu by remember { mutableStateOf(-1) }
    class F(val f: EscribirEstructura.Fila, val d: Doc, val o: Double)
    val orden = remember(L) {
        L.filas.mapNotNull { f ->
            val d = porId[f.id] ?: return@mapNotNull null
            val t = L.tramos[f.id]
            F(f, d, when (f.tipo) { "hito" -> d.orden; "inicio" -> t!!.ini; else -> t!!.fin })
        }
    }
    fun ordenTras(i: Int) = orden.getOrNull(i + 1)?.let { (orden[i].o + it.o) / 2 } ?: (orden[i].o + 1000)
    fun colorEn(c: Int, o: Double): Color {
        if (c == 0) return Charcoal
        val r = rs.find { x -> L.tramos[x.id]?.let { it.col == c && o >= it.ini && o <= it.fin } == true }
        return colorEscrito(r?.s("color").orEmpty(), Charcoal)
    }
    fun extremo(c: Int, i: Int, f: EscribirEstructura.Fila): Int = when {
        f.tipo == "inicio" && c == f.col -> 1
        f.tipo == "fin" && c == f.col -> 2
        c == 0 && i == 0 -> 1
        c == 0 && i == orden.lastIndex -> 2
        c > 0 && f.tipo == "hito" && f.col == c && orden.drop(i + 1).none { x -> c in x.f.activas && (x.f.tipo != "inicio" || x.f.col != c) } -> 2
        else -> 0
    }
    /** Un suceso nuevo (en la línea [rama], en el orden [o]), que se abre para rellenarlo. */
    fun nuevoHito(rama: String, o: Double) {
        val h = e.nuevo(Escritos.T_HITO, pid) { put("titulo", ""); put("cuando", ""); put("nota", ""); put("orden", o); if (rama.isNotBlank()) put("rama", rama) }
        abre.editar(h.id, esNuevo = true)
    }
    /** Una rama nueva que nace en [desde], con su primer suceso justo después (antes de [siguiente]). */
    fun nuevaRama(desde: Double, siguiente: Double?) {
        val r = e.nuevo(Escritos.T_RAMA, pid) { put("titulo", "Rama"); put("texto", ""); put("color", COLORES_ESCRITO[(e.ramas(pid).size + 3) % COLORES_ESCRITO.size]); put("desde", desde) }
        e.nuevo(Escritos.T_HITO, pid) { put("titulo", ""); put("cuando", ""); put("nota", ""); put("rama", r.id); put("orden", if (siguiente != null) (desde + siguiente) / 2 else desde + 500) }
        abre.editar(r.id, esNuevo = true)
    }
    val lista = rememberLazyListState()
    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 14.dp), state = lista, contentPadding = PaddingValues(bottom = 50.dp)) {
                item("intro") {
                    Column(Modifier.padding(bottom = 14.dp).widthIn(max = 900.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            EnlaceRevista("+ Nuevo suceso", acento = true) { nuevoHito("", (hs.maxOfOrNull { it.orden } ?: 0.0) + 1000) }
                            EnlaceRevista("+ Nueva rama") { nuevaRama(hs.lastOrNull()?.orden ?: 0.0, null) }
                        }
                        Text("Lo que pasa en tu historia, en el orden en que pasa. De cualquier suceso puede salir una rama: una trama que baja en paralelo y, si quieres, vuelve a la principal.",
                            color = Muted, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                if (orden.isEmpty()) item("vacio") {
                    Column(Modifier.padding(vertical = 24.dp).widthIn(max = 760.dp)) {
                        Text("La línea de tu historia", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Apunta lo que pasa (aunque no salga en el libro, o salga en otro orden) y saca ramas para las tramas que van a la vez.",
                            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 27.sp)
                    }
                }
                items(orden.size, key = { orden[it].f.tipo + orden[it].f.id }) { i ->
                    val (f, d, o) = Triple(orden[i].f, orden[i].d, orden[i].o)
                    val hover = remember { MutableInteractionSource() }
                    val encima by hover.collectIsHoveredAsState()
                    Row(Modifier.fillMaxWidth().widthIn(max = 980.dp).height(IntrinsicSize.Min).hoverable(hover)) {
                        val colPunto = if (f.tipo == "hito") colorEscrito(d.s("color").ifBlank { porId[d.s("rama")]?.s("color").orEmpty() }, colorEn(f.col, o)) else colorEn(f.col, o)
                        Canvas(Modifier.width(COL * L.columnas).fillMaxHeight()) {
                            val col = COL.toPx(); val py = PUNTO_Y.toPx(); val w = 2.dp.toPx()
                            f.activas.forEach { c ->
                                val x = c * col + col / 2
                                val (y0, y1) = when (extremo(c, i, f)) { 1 -> py to size.height; 2 -> 0f to py; else -> 0f to size.height }
                                drawLine(colorEn(c, o), Offset(x, y0), Offset(x, y1), w)
                            }
                            if (f.tipo != "hito") drawLine(colorEn(f.col, o), Offset(col / 2, py), Offset(f.col * col + col / 2, py), w)
                            val r = if (f.tipo == "hito") 6.5.dp.toPx() else 4.5.dp.toPx()
                            drawCircle(colPunto, r, Offset(f.col * col + col / 2, py))
                            if (f.tipo == "hito" && encima) drawCircle(colPunto.copy(alpha = 0.25f), r * 1.9f, Offset(f.col * col + col / 2, py))
                        }
                        if (f.tipo == "hito") {
                            val esc = e.get(d.s("eid"))
                            Row(Modifier.weight(1f).padding(start = 8.dp, bottom = 14.dp)) {
                                Column(Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand).clickable { abre.ver(d.id) }.padding(top = 10.dp, end = 4.dp)) {
                                    if (d.s("cuando").isNotBlank()) Text(d.s("cuando").uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                                    Text(d.s("titulo").ifBlank { "Sin título" }, color = if (encima) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp)
                                    if (d.s("nota").isNotBlank()) Text(d.s("nota").lineSequence().first().take(220), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (esc != null) Text("→ " + esc.s("titulo").ifBlank { "escena" }, Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { abrirEscena(esc.id) }.padding(vertical = 2.dp),
                                        color = Acento, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Box {
                                    Text("⋯", Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { menu = i }.padding(top = 8.dp, start = 10.dp, end = 10.dp), color = if (encima) Charcoal else Muted.copy(alpha = 0.5f), fontSize = 20.sp)
                                    DropdownMenu(menu == i, { menu = -1 }, containerColor = Paper) {
                                        DropdownMenuItem({ Text("Un suceso justo después", fontFamily = Body) }, { menu = -1; nuevoHito(d.s("rama"), ordenTras(i)) })
                                        DropdownMenuItem({ Text("Una rama desde aquí", fontFamily = Body) }, { menu = -1; nuevaRama(d.orden, orden.getOrNull(i + 1)?.o) })
                                        if (i > 0) DropdownMenuItem({ Text("Subir", fontFamily = Body) }, { menu = -1; e.edita(d.id) { put("orden", EscribirEstructura.entre(orden.getOrNull(i - 2)?.o, orden[i - 1].o)) } })
                                        if (i < orden.lastIndex) DropdownMenuItem({ Text("Bajar", fontFamily = Body) }, { menu = -1; e.edita(d.id) { put("orden", EscribirEstructura.entre(orden[i + 1].o, orden.getOrNull(i + 2)?.o)) } })
                                        DropdownMenuItem({ Text("Editar", fontFamily = Body) }, { menu = -1; abre.editar(d.id) })
                                    }
                                }
                            }
                        } else {
                            val c = colorEn(f.col, o)
                            Column(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 14.dp).clip(esquina(6.dp)).background(c.copy(alpha = 0.08f)).border(1.dp, c.copy(alpha = 0.5f), esquina(6.dp))
                                .pointerHoverIcon(PointerIcon.Hand).clickable { abre.editar(d.id) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                Text(if (f.tipo == "inicio") "NACE LA RAMA" else "VUELVE A LA PRINCIPAL", color = c, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                                Text(d.s("titulo").ifBlank { "Rama" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                if (f.tipo == "inicio" && d.s("texto").isNotBlank()) Text(d.s("texto").lineSequence().first().take(160), color = Muted, fontFamily = Body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(lista), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        // Las escenas que dicen cuándo pasan (su «cuándo», en el panel del editor)
        if (conFecha.isNotEmpty()) {
            Filete(1.dp, Line, Modifier.fillMaxHeight().width(1.dp))
            Column(Modifier.width(280.dp).fillMaxHeight().padding(start = 18.dp)) {
                Antetitulo("Escenas con fecha", tam = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                conFecha.forEach { s ->
                    Column(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickable { abrirEscena(s.id) }.padding(vertical = 7.dp)) {
                        Text(s.s("cuando").uppercase(), color = Acento, fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text(s.s("titulo").ifBlank { "Sin título" }, color = Charcoal, fontFamily = Display, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}
