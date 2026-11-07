package com.gonzalo.cobre.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Programa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «21:00–22:30». */
fun franja(p: Programa) = "${Formato.hora(p.inicio)}–${Formato.hora(p.fin)}"

/**
 * «Guía»: la parrilla, canal a canal, con una ventana de tres horas que se mueve hacia atrás y hacia delante (media hora a media hora, o
 * «Ahora»), como en el móvil. Cada programa es una casilla: la de lo que se emite, en el color del acento; con el teclado o el mando se va de
 * una a otra. Pulsar lo que se emite pone el canal; lo demás enseña de qué va.
 */
@Composable
fun PantGuia(nav: Nav) {
    var lista by remember { mutableStateOf(listaActual()) }
    val cg = rememberCargada(lista)
    val favs = MiCuenta.favoritos()
    var grupo by remember(lista?.id) { mutableStateOf(if (favs.any { it.lista == lista?.id }) G_FAVORITOS else G_TODOS) }
    val media = 30 * 60_000L
    var desde by remember { mutableStateOf(System.currentTimeMillis() / media * media) }
    val horas = 3
    val hasta = desde + horas * 3_600_000L
    var info by remember { mutableStateOf<Pair<Canal, Programa>?>(null) }
    val ocultos = lista?.let { MiCuenta.ocultos(it.id) }.orEmpty()
    val orden = AjustesDirecto.orden()
    val canales = remember(cg, grupo, favs, ocultos, orden) {
        val c = cg ?: return@remember emptyList<Canal>()
        when (grupo) { G_FAVORITOS -> Tele.favoritos(c, favs); G_TODOS -> Tele.visibles(c, orden = orden); else -> Tele.visibles(c, grupo, orden) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 26.dp)) {
            CabeceraPagina("La tele", "Guía") {
                lista?.let { l -> EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Bajando…" else "Bajar la guía") { Tele.actualizaGuia(l) { e -> if (e != null) nav.aviso(e) } } }
            }
            SelectorLista(lista) { lista = it }
            val l = lista
            if (l == null) { SinListas(nav); return@Column }
            if (cg == null) { Nota(Tele.ocupada[l.id] ?: "Leyendo los canales…", Modifier.padding(top = 20.dp)); return@Column }
            if (cg.guia == null) Nota(Tele.ocupada[l.id] ?: Tele.errores[l.id] ?: Tele.almacen.estado(l, "guia").error.ifBlank { "Esta lista aún no tiene guía. Pulsa «Bajar la guía» (si la lista no dice de dónde sacarla, pon una dirección XMLTV en Ajustes → Listas)." }, Modifier.padding(top = 8.dp))
            val grupos = listOf(G_FAVORITOS, G_TODOS) + cg.todosLosGrupos.filter { it !in ocultos }
            LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(grupos, key = { it }) { g -> FiltroRevista(nombreGrupo(g), g == grupo) { grupo = g } }
            }
            // La ventana de horas: ← Antes · Ahora · Después →
            val izq = 250.dp
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(izq), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista("← Antes") { desde -= media }
                    Spacer(Modifier.width(10.dp))
                    EnlaceRevista("Ahora", acento = true) { desde = System.currentTimeMillis() / media * media }
                    Spacer(Modifier.width(10.dp))
                    Text(Formato.dia(desde), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val w = maxWidth
                    for (k in 0 until horas * 2) {
                        Text(Formato.hora(desde + k * media), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.offset(x = w * (k.toFloat() / (horas * 2))))
                    }
                }
                EnlaceRevista("Después →", Modifier.padding(start = 8.dp)) { desde += media }
            }
            Filete(1.dp)
            val estado = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = estado) {
                    items(canales, key = { it.clave }) { c -> FilaGuia(l, cg, c, desde, hasta, izq, alVer = { nav.ver(Encargo.Directo(l, canales, canales.indexOf(c).coerceAtLeast(0))) }, alInfo = { info = c to it }) }
                    item { Spacer(Modifier.height(30.dp)) }
                }
                VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        info?.let { (c, p) ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).pulsable { info = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 600.dp).padding(24.dp).background(Paper).padding(24.dp)) {
                    Antetitulo("${c.nombre} · ${Formato.dia(p.inicio)} · ${franja(p)}", Modifier.padding(bottom = 6.dp))
                    Text(p.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
                    if (p.subtitulo.isNotBlank()) Text(p.subtitulo, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp)
                    if (p.categoria.isNotBlank() || p.episodio.isNotBlank()) Etiqueta(listOf(p.categoria, p.episodio).filter { it.isNotBlank() }.joinToString(" · "), Modifier.padding(top = 6.dp))
                    if (p.descripcion.isNotBlank()) Text(p.descripcion, color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(10.dp))
                    val l = lista
                    if (l != null) MagLink("Ver ${c.nombre} ahora", accent = true) { info = null; nav.ver(Encargo.Directo(l, canales, canales.indexOf(c).coerceAtLeast(0))) }
                    MagLink("Cerrar") { info = null }
                }
            }
        }
    }
}

/** Una fila de la parrilla: el canal a la izquierda y sus programas en la ventana, cada uno de su largo. */
@Composable
private fun FilaGuia(l: Lista, cg: Cargada, c: Canal, desde: Long, hasta: Long, izq: Dp, alVer: () -> Unit, alInfo: (Programa) -> Unit) {
    val ahora = System.currentTimeMillis()
    val progs by produceState(emptyList<Programa>(), c.clave, desde, cg) {
        value = withContext(Dispatchers.IO) { runCatching { Tele.programas(l, c).filter { it.fin > desde && it.inicio < hasta } }.getOrDefault(emptyList()) }
    }
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(izq).fillMaxHeight().pulsable { alVer() }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${c.numero.takeIf { it > 0 } ?: ""}", color = Muted, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(36.dp))
            Logo(c.logo, c.nombre, 38.dp)
            Spacer(Modifier.width(8.dp))
            Text(c.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp)) {
            val w = maxWidth; val total = (hasta - desde).toFloat()
            if (progs.isEmpty()) Text("Sin guía", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
            progs.forEach { p ->
                val a = ((maxOf(p.inicio, desde) - desde) / total).coerceIn(0f, 1f); val b = ((minOf(p.fin, hasta) - desde) / total).coerceIn(0f, 1f)
                if (b > a) {
                    val en = p.emitiendose(ahora)
                    Box(Modifier.offset(x = w * a).width(w * (b - a)).fillMaxHeight().padding(end = 3.dp).clip(esquina(3.dp)).background(if (en) Acento.copy(alpha = 0.18f) else Crudo)
                        .pulsable(forma = esquina(3.dp)) { if (en) alVer() else alInfo(p) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Column {
                            Text(p.titulo, color = if (en) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(franja(p), color = Muted, fontFamily = Body, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    Filete(1.dp, Line)
}
