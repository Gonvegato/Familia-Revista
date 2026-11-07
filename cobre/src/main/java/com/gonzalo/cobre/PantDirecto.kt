package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista

/** El grupo de mentira con tus favoritos, el de todos y los de verdad. */
const val G_FAVORITOS = "\u0001favoritos"
const val G_TODOS = "\u0001todos"
fun nombreGrupo(g: String) = when (g) { G_FAVORITOS -> "★ Favoritos"; G_TODOS -> "Todos"; "" -> "Sin grupo"; else -> g }

/** Elegir la lista (si tienes más de una): sus nombres, la de ahora subrayada. */
@Composable
fun SelectorLista(actual: Lista?, soloXtream: Boolean = false, alCambiar: (Lista) -> Unit) {
    val ls = Listas.usables().filter { !soloXtream || it.esXtream }
    if (ls.size <= 1 || actual == null) return
    FiltrosRevista(ls.map { it.nombre }, ls.indexOfFirst { it.id == actual.id }.coerceAtLeast(0)) { alCambiar(ls[it]) }
}

/** La cabecera de las secciones: «← Cobre», el antetítulo y el titular, y lo que vaya a la derecha. */
@Composable
fun CabeceraSeccion(titulo: String, ante: String, alVolver: () -> Unit, acciones: @Composable RowScope.() -> Unit = {}) {
    RevistaTopBar(alVolver, "Nut", acciones)
    Antetitulo(ante, Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
    FitTitle(titulo, 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
    Filete(3.dp)
}

/**
 * «Directo»: los grupos de la lista (con tus favoritos primero y sin los que escondes) y sus canales, con lo que echan ahora y lo que va.
 * En la tablet y en la tele, a dos columnas (los grupos a la izquierda); en el móvil, los grupos en una fila que se desliza.
 */
@Composable
fun PantDirecto(alVolver: () -> Unit, alIr: (Destino) -> Unit) {
    val ctx = LocalContext.current
    Listas.version; EspejoCuenta.version
    var lista by remember { mutableStateOf(listaActual(ctx)) }
    val cg = rememberCargada(lista)
    val ocultos = lista?.let { MiCuenta.ocultos(it.id) }.orEmpty()
    val favs = MiCuenta.favoritos()
    var grupo by remember(lista?.id) { mutableStateOf(AjustesDirecto.grupo(ctx).takeIf { lista?.id == AjustesDirecto.lista(ctx) }?.ifBlank { null } ?: if (favs.any { it.lista == lista?.id }) G_FAVORITOS else G_TODOS) }
    var verOcultos by remember { mutableStateOf(false) }
    val grupos = remember(cg, ocultos, verOcultos) { cg?.todosLosGrupos.orEmpty().filter { verOcultos || it !in ocultos } }
    val canales = remember(cg, grupo, ocultos, favs, EspejoCuenta.version) {
        val c = cg ?: return@remember emptyList<Canal>()
        when (grupo) {
            G_FAVORITOS -> favs.filter { it.lista == c.lista.id }.mapNotNull { c.porClave[it.canal] }
            G_TODOS -> Tele.visibles(c, ctx)
            else -> Tele.visibles(c, ctx, grupo).ifEmpty { if (grupo in ocultos) c.canales.filter { it.grupo == grupo } else emptyList() }
        }
    }
    fun elige(g: String) { grupo = g; lista?.let { AjustesDirecto.ponDonde(ctx, it.id, if (g.startsWith("\u0001")) "" else g) } }
    val ancho = esAncha()
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        CabeceraSeccion("Directo", lista?.nombre ?: "Tus canales", alVolver) {
            EnlaceRevista("Guía", acento = true) { alIr(Destino.Guia) }
            lista?.let { l -> EnlaceRevista(if (Tele.ocupada.containsKey(l.id)) "Actualizando…" else "Actualizar") { Tele.actualiza(l, todo = true) } }
        }
        SelectorLista(lista) { lista = it; AjustesDirecto.ponDonde(ctx, it.id, "") }
        val l = lista
        when {
            l == null -> { Spacer(Modifier.height(20.dp)); Nota("Aún no tienes ninguna lista en este dispositivo."); MagLink("Añadir una lista", accent = true) { alIr(Destino.Listas) } }
            cg == null -> { Spacer(Modifier.height(20.dp)); Nota(Tele.ocupada[l.id] ?: Tele.errores[l.id] ?: "Leyendo los canales…") }
            else -> {
                val todos = listOf(G_FAVORITOS, G_TODOS) + grupos
                if (ancho) Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    LazyColumn(Modifier.width(300.dp).fillMaxHeight()) {
                        itemsIndexed(todos, key = { _, g -> g }) { i, g ->
                            val on = g == grupo
                            Text(nombreGrupo(g) + if (g in ocultos) " (escondido)" else "", color = if (on) Acento else if (g in ocultos) Muted else Charcoal, fontFamily = Display,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().let { if (on) it.focoInicial(l.id) else it }.tocable { elige(g) }.padding(vertical = 9.dp, horizontal = 4.dp))
                            if (i == 1) Filete(1.dp, Line)
                        }
                        item { if (ocultos.isNotEmpty()) EnlaceRevista(if (verOcultos) "No ver los escondidos" else "Ver los escondidos (${ocultos.size})", Modifier.padding(top = 10.dp)) { verOcultos = !verOcultos } }
                    }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) { ListaCanales(l, cg, grupo, canales, ocultos) }
                } else Column(Modifier.fillMaxSize()) {
                    LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(todos, key = { it }) { g -> FiltroRevista(nombreGrupo(g), g == grupo) { elige(g) } }
                    }
                    Filete(1.dp, Line)
                    ListaCanales(l, cg, grupo, canales, ocultos)
                }
            }
        }
    }
}

@Composable
private fun ListaCanales(l: Lista, cg: Cargada, grupo: String, canales: List<Canal>, ocultos: Set<String>) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("${nombreGrupo(grupo)} · ${Formato.cifra(canales.size)} canales", Modifier.weight(1f))
        if (!grupo.startsWith("\u0001")) EnlaceRevista(if (grupo in ocultos) "Volver a enseñar" else "Esconder el grupo") { MiCuenta.ocultaGrupo(l.id, grupo, grupo !in ocultos) }
    }
    if (canales.isEmpty()) { Nota(if (grupo == G_FAVORITOS) "Aún no tienes favoritos en esta lista: márcalos con ★." else "No hay canales aquí.", Modifier.padding(top = 12.dp)); return }
    val ahora = remember(Tele.version) { System.currentTimeMillis() }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(canales, key = { _, c -> c.clave }) { i, c ->
            FilaCanal(l, cg, c, i, ahora) { Reproducir.canal(ctx, l, canales, c) }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

/** Una fila de canal: su número, su logo, su nombre, lo que echan (con lo que va) y lo siguiente; a la derecha, la estrella. */
@Composable
fun FilaCanal(l: Lista, cg: Cargada, c: Canal, i: Int, ahora: Long, alVer: () -> Unit) {
    val (ya, luego) = remember(c.clave, ahora, cg) { Tele.ahoraYDespuesRapido(cg, c, ahora) }
    val fav = MiCuenta.esFavorito(l.id, c.clave)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).tocable { alVer() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // (el número, en una línea y tan ancho como haga falta: con el texto grande, «21698» se partía en dos; el nombre del canal, en
            // hasta tres líneas: en una sola, con letra grande, no se leía ninguno)
            Text("${if (c.numero > 0) c.numero else i + 1}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.widthIn(min = 34.dp).padding(end = 8.dp))
            Logo(c.logo, c.nombre, Modifier.padding(end = 12.dp), 40.dp)
            Column(Modifier.weight(1f)) {
                Text(c.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (ya != null) {
                    Text("${hora(ya.inicio)} · ${ya.titulo}", color = Charcoal.copy(alpha = 0.85f), fontFamily = Body, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Barra(ya.avance(ahora), Modifier.padding(top = 4.dp, end = 40.dp).widthIn(max = 360.dp))
                }
                if (luego != null) Text("${hora(luego.inicio)} · ${luego.titulo}", color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (ya == null && luego == null && c.grupo.isNotBlank()) Text(c.grupo.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
            }
        }
        Text(if (fav) "★" else "☆", color = if (fav) Acento else Muted, fontSize = 22.sp,
            modifier = Modifier.tocable { MiCuenta.alternaFavorito(l.id, c) }.padding(horizontal = 8.dp, vertical = 8.dp))
    }
    Filete(1.dp, Line)
}
