package com.gonzalo.cobre.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** El grupo de mentira con tus favoritos, el de todos y los de verdad (como en el móvil). */
const val G_FAVORITOS = "\u0001favoritos"
const val G_TODOS = "\u0001todos"
fun nombreGrupo(g: String) = when (g) { G_FAVORITOS -> "★ Favoritos"; G_TODOS -> "Todos"; "" -> "Sin grupo"; else -> g }

/** La lista que estabas mirando (`ajustes/cobre_directo` → `lista`) o la primera de este ordenador. */
fun listaActual(soloXtream: Boolean = false): Lista? {
    val ls = Listas.usables().filter { !soloXtream || it.esXtream }
    return ls.firstOrNull { it.id == AjustesDirecto.lista() } ?: ls.firstOrNull()
}

/** Lo de una lista ya leído del disco (la primera vez, fuera del hilo de la pantalla); se vuelve a leer cuando cambia. */
@Composable
fun rememberCargada(l: Lista?): Cargada? {
    val v = Tele.version
    val c by produceState(l?.let { Tele.enMemoria(it.id) }, l, v) { value = l?.let { withContext(Dispatchers.IO) { Tele.cargada(it) } } }
    return c
}

/** Elegir la lista (si tienes más de una): sus nombres, la de ahora subrayada. */
@Composable
fun SelectorLista(actual: Lista?, soloXtream: Boolean = false, alCambiar: (Lista) -> Unit) {
    val ls = Listas.usables().filter { !soloXtream || it.esXtream }
    if (ls.size <= 1 || actual == null) return
    FiltrosRevista(ls.map { it.nombre }, ls.indexOfFirst { it.id == actual.id }.coerceAtLeast(0), Modifier.padding(top = 10.dp)) { alCambiar(ls[it]) }
}

/** Sin ninguna lista: qué hacer (Cobre no trae ninguna). */
@Composable
fun SinListas(nav: Nav) {
    Spacer(Modifier.height(24.dp))
    Nota("Aún no tienes ninguna lista en este ordenador. Nut no trae ningún canal ni ninguna lista: pones tú la tuya (una dirección M3U, un archivo M3U o el login de tu servidor de Xtream Codes).")
    Spacer(Modifier.height(10.dp))
    MagLink("Añadir una lista", "Ajustes → Listas", accent = true) { nav.ir(Pantalla.Ajustes(0)) }
}

/**
 * «Directo»: los grupos de la lista (con tus favoritos primero y sin los que escondes) y sus canales, con lo que echan ahora y lo que va; a
 * dos columnas (los grupos a la izquierda), como en la tablet y en la tele. Un clic (o Intro) en un canal lo pone en mpv, con los de su grupo
 * para cambiar de canal; la ★ lo pone en tus favoritos (en la cuenta, en todos tus dispositivos).
 */
@Composable
fun PantDirecto(nav: Nav) {
    var lista by remember { mutableStateOf(listaActual()) }
    val cg = rememberCargada(lista)
    val ocultos = lista?.let { MiCuenta.ocultos(it.id) }.orEmpty()
    val favs = MiCuenta.favoritos()
    var grupo by remember(lista?.id) { mutableStateOf(AjustesDirecto.grupo().takeIf { lista?.id == AjustesDirecto.lista() }?.ifBlank { null } ?: if (favs.any { it.lista == lista?.id }) G_FAVORITOS else G_TODOS) }
    var verOcultos by remember { mutableStateOf(false) }
    val orden = AjustesDirecto.orden()
    val grupos = remember(cg, ocultos, verOcultos) { cg?.todosLosGrupos.orEmpty().filter { verOcultos || it !in ocultos } }
    val canales = remember(cg, grupo, ocultos, favs, orden) {
        val c = cg ?: return@remember emptyList<Canal>()
        when (grupo) {
            G_FAVORITOS -> Tele.favoritos(c, favs)
            G_TODOS -> Tele.visibles(c, orden = orden)
            else -> Tele.visibles(c, grupo, orden).ifEmpty { if (grupo in ocultos) Tele.ordena(c.canales.filter { it.grupo == grupo }, orden) else emptyList() }
        }
    }
    fun elige(g: String) { grupo = g; lista?.let { AjustesDirecto.ponDonde(it.id, if (g.startsWith("\u0001")) "" else g) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 26.dp)) {
        CabeceraPagina("La tele", "Directo") {
            lista?.let { l -> EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Actualizando…" else "Actualizar") { Tele.actualiza(l, todo = true) } }
        }
        SelectorLista(lista) { lista = it; AjustesDirecto.ponDonde(it.id, "") }
        val l = lista
        when {
            l == null -> SinListas(nav)
            cg == null -> { Spacer(Modifier.height(20.dp)); Nota(Tele.ocupada[l.id] ?: Tele.errores[l.id]?.let { "No se ha podido: $it" } ?: "Leyendo los canales…") }
            else -> Row(Modifier.fillMaxSize().padding(top = 10.dp)) {
                val todos = listOf(G_FAVORITOS, G_TODOS) + grupos
                val eg = rememberLazyListState()
                Box(Modifier.width(290.dp).fillMaxHeight()) {
                    LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = eg) {
                        itemsIndexed(todos, key = { _, g -> g }) { i, g ->
                            val on = g == grupo
                            Text(nombreGrupo(g) + if (g in ocultos) " (escondido)" else "", color = if (on) Acento else if (g in ocultos) Muted else Charcoal, fontFamily = Grotesca,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().pulsable { elige(g) }.padding(vertical = 8.dp, horizontal = 6.dp))
                            if (i == 1) Filete(1.dp, Line)
                        }
                        item { if (ocultos.isNotEmpty()) EnlaceRevista(if (verOcultos) "No ver los escondidos" else "Ver los escondidos (${ocultos.size})", Modifier.padding(top = 10.dp)) { verOcultos = !verOcultos } }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(eg), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) { ListaCanales(nav, l, cg, grupo, canales, ocultos, favs) }
            }
        }
    }
}

@Composable
private fun ListaCanales(nav: Nav, l: Lista, cg: Cargada, grupo: String, canales: List<Canal>, ocultos: Set<String>, favs: List<Favorito>) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("${nombreGrupo(grupo)} · ${Formato.cifra(canales.size)} canales", Modifier.weight(1f))
        if (!grupo.startsWith("\u0001")) EnlaceRevista(if (grupo in ocultos) "Volver a enseñar" else "Esconder el grupo") { MiCuenta.ocultaGrupo(l.id, grupo, grupo !in ocultos) }
    }
    if (canales.isEmpty()) { Nota(if (grupo == G_FAVORITOS) "Aún no tienes favoritos en esta lista: márcalos con ★." else "No hay canales aquí.", Modifier.padding(top = 12.dp)); return }
    val ahora = remember(Tele.version, canales) { System.currentTimeMillis() }
    val claves = remember(favs) { favs.filter { it.lista == l.id }.associateBy { it.canal } }
    val estado = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = estado) {
            itemsIndexed(canales, key = { _, c -> c.clave }) { i, c ->
                FilaCanal(l, cg, c, i, ahora, claves[c.clave], if (grupo == G_FAVORITOS) i else -1, canales.size) { nav.ver(Encargo.Directo(l, canales, i)) }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/**
 * Una fila de canal: su número, su logo, su nombre, lo que echan (con lo que va) y lo siguiente; a la derecha, la estrella (y, en tus
 * favoritos, subir o bajar el canal: el orden va en la cuenta).
 */
@Composable
fun FilaCanal(l: Lista, cg: Cargada, c: Canal, i: Int, ahora: Long, fav: Favorito?, puestoFav: Int = -1, total: Int = 0, alVer: () -> Unit) {
    val (ya, luego) = remember(c.clave, ahora, cg) { Tele.ahoraYDespuesRapido(cg, c, ahora) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).pulsable { alVer() }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${if (c.numero > 0) c.numero else i + 1}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 36.dp).padding(end = 8.dp))
            Logo(c.logo, c.nombre, 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (ya != null || luego != null) AhoraYDespues(ya, luego, ahora, Modifier.padding(top = 2.dp))
                else if (c.grupo.isNotBlank()) Text(c.grupo.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
            }
        }
        if (puestoFav >= 0 && fav != null) {
            if (puestoFav > 0) EnlaceRevista("↑") { MiCuenta.mueveFavorito(fav.ref, -1) }
            Spacer(Modifier.width(8.dp))
            if (puestoFav < total - 1) EnlaceRevista("↓") { MiCuenta.mueveFavorito(fav.ref, 1) }
        }
        Text(if (fav != null) "★" else "☆", color = if (fav != null) Acento else Muted, fontSize = 24.sp,
            modifier = Modifier.pulsable { MiCuenta.alternaFavorito(l.id, c) }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
    Filete(1.dp, Line)
}
