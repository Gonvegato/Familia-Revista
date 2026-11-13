package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Programa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «Guía»: la parrilla, canal a canal, con una ventana de tres horas que se mueve hacia atrás y hacia delante (media hora a media hora,
 * o «Ahora»). Cada programa es una casilla: la de lo que se emite, en el color del acento; con el mando se va de una a otra. Pulsar lo que
 * se emite pone el canal; lo demás enseña de qué va.
 */
@Composable
fun PantGuia(alVolver: () -> Unit, alIr: (Destino) -> Unit) {
    val ctx = LocalContext.current
    Listas.version
    var lista by remember { mutableStateOf(listaActual(ctx)) }
    val cg = rememberCargada(lista)
    val favs = MiCuenta.favoritos()
    var grupo by remember(lista?.id) { mutableStateOf(if (favs.any { it.lista == lista?.id }) G_FAVORITOS else G_TODOS) }
    val ahora = remember { System.currentTimeMillis() }
    val media = 30 * 60_000L
    var desde by remember { mutableStateOf(ahora / media * media) }
    val horas = if (esAncha()) 3 else 2
    val hasta = desde + horas * 3_600_000L
    var info by remember { mutableStateOf<Pair<Canal, Programa>?>(null) }
    val ocultos = lista?.let { MiCuenta.ocultos(it.id) }.orEmpty()
    val canales = remember(cg, grupo, favs, ocultos) {
        val c = cg ?: return@remember emptyList<Canal>()
        when (grupo) { G_FAVORITOS -> favs.filter { it.lista == c.lista.id }.mapNotNull { c.porClave[it.canal] }; G_TODOS -> Tele.visibles(c, ctx); else -> Tele.visibles(c, ctx, grupo) }
    }
    val m = margen()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
            CabeceraSeccion("Guía", lista?.nombre ?: "Lo que echan", alVolver) {
                EnlaceRevista("Directo", acento = true) { alIr(Destino.Directo) }
                lista?.let { l -> EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Bajando…" else "Bajar la guía") { Tele.actualiza(l, todo = true) } }
            }
            SelectorLista(lista) { lista = it }
            val l = lista
            if (l == null) { Nota("Aún no tienes ninguna lista en este dispositivo.", Modifier.padding(top = 20.dp)); return@Column }
            if (cg == null) { Nota(Tele.ocupada[l.id] ?: "Leyendo los canales…", Modifier.padding(top = 20.dp)); return@Column }
            if (cg.guia == null) Nota(Tele.ocupada[l.id] ?: Tele.errores[l.id] ?: Tele.almacen.estado(l, "guia").error.ifBlank { "Esta lista aún no tiene guía. Pulsa «Bajar la guía» (si la lista no dice de dónde sacarla, pon una dirección XMLTV en Listas)." }, Modifier.padding(top = 8.dp))
            val grupos = listOf(G_FAVORITOS, G_TODOS) + cg.todosLosGrupos.filter { it !in ocultos }
            LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(grupos, key = { it }) { g -> FiltroRevista(nombreGrupo(g), g == grupo) { grupo = g } }
            }
            // La ventana de horas: ← Antes · Ahora · Después →
            val izq = if (esAncha()) 230.dp else 128.dp
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(izq), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista("← Antes") { desde -= media }
                    Spacer(Modifier.width(10.dp))
                    EnlaceRevista("Ahora", acento = true) { desde = System.currentTimeMillis() / media * media }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val w = maxWidth
                    for (k in 0 until horas * 2) {
                        Text(hora(desde + k * media), color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.offset(x = w * (k.toFloat() / (horas * 2))))
                    }
                }
                EnlaceRevista("Después →", Modifier.padding(start = 8.dp)) { desde += media }
            }
            Filete(1.dp)
            LazyColumn(Modifier.fillMaxSize()) {
                items(canales, key = { it.clave }) { c -> FilaGuia(l, cg, c, desde, hasta, izq, canales.firstOrNull()?.clave == c.clave, alVer = { Reproducir.canal(ctx, l, canales, c) }, alInfo = { info = c to it }) }
                item { Spacer(Modifier.height(30.dp)) }
            }
        }
        info?.let { (c, p) ->
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99000000)).tocable { info = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 560.dp).padding(24.dp).background(Paper).padding(22.dp).atrapaFoco()) {
                    Antetitulo("${c.nombre} · ${franja(p)}", Modifier.padding(bottom = 6.dp))
                    Text(p.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp)
                    if (p.subtitulo.isNotBlank()) Text(p.subtitulo, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp)
                    if (p.categoria.isNotBlank() || p.episodio.isNotBlank()) Etiqueta(listOf(p.categoria, p.episodio).filter { it.isNotBlank() }.joinToString(" · "), Modifier.padding(top = 6.dp))
                    if (p.descripcion.isNotBlank()) Text(p.descripcion, color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(10.dp))
                    MagLink("Ver ${c.nombre} ahora", accent = true, modifier = Modifier.focoInicial(p)) { info = null; Reproducir.canal(ctx, l = lista!!, canales = canales, c = c) }
                    MagLink("Cerrar") { info = null }
                }
            }
        }
    }
}

/** Una fila de la parrilla: el canal a la izquierda y sus programas en la ventana, cada uno de su largo. */
@Composable
private fun FilaGuia(l: Lista, cg: Cargada, c: Canal, desde: Long, hasta: Long, izq: Dp, primera: Boolean, alVer: () -> Unit, alInfo: (Programa) -> Unit) {
    val ahora = System.currentTimeMillis()
    val progs by produceState(emptyList<Programa>(), c.clave, desde, cg) {
        value = withContext(Dispatchers.IO) { runCatching { Tele.programas(l, c).filter { it.fin > desde && it.inicio < hasta } }.getOrDefault(emptyList()) }
    }
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(izq).fillMaxHeight().tocable { alVer() }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(c.logo, c.nombre, Modifier.padding(end = 8.dp), 38.dp)
            Text(c.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp)) {
            val w = maxWidth; val total = (hasta - desde).toFloat()
            if (progs.isEmpty()) Text("Sin guía", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
            progs.forEachIndexed { k, p ->
                val a = ((maxOf(p.inicio, desde) - desde) / total).coerceIn(0f, 1f); val b = ((minOf(p.fin, hasta) - desde) / total).coerceIn(0f, 1f)
                if (b > a) {
                    val en = p.emitiendose(ahora)
                    Box(Modifier.offset(x = w * a).width(w * (b - a)).fillMaxHeight().padding(end = 3.dp).clip(esquina(3.dp)).background(if (en) Acento.copy(alpha = 0.18f) else Crudo)
                        .let { if (primera && k == 0) it.focoInicial(desde) else it }.tocable(fuera = 1.dp) { if (en) alVer() else alInfo(p) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
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
