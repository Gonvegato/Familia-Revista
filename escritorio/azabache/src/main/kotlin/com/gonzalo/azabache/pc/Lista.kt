package com.gonzalo.azabache.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.revista.*

/** Lo que guardas de una categoría: terminados (con su nota), en curso, pendientes y abandonados. */
@Composable
fun PantLista(k: Kind, nav: Nav) {
    LaFamilia.cambios
    var pestana by remember(k.id) { mutableStateOf(0) }
    var buscar by remember(k.id) { mutableStateOf("") }
    var orden by remember(k.id) { mutableStateOf(0) }
    var anadir by remember(k.id) { mutableStateOf(false) }
    // Portadas (la cuadrícula) o índice (como la biblioteca del móvil: el título y la nota en cifras grandes, a dos columnas)
    var indice by remember { mutableStateOf(false) }
    val fichas = Cuenta.fichas(k.id)
    val grupos = listOf(fichas.filter { it.valorada }, fichas.filter { !it.valorada && it.estado == 1 }, fichas.filter { !it.valorada && it.estado == 0 }, fichas.filter { !it.valorada && it.estado == 2 })
    val nombres = listOf(k.doneWord, k.statuses[1], k.statuses[0] + "s", k.statuses[2] + "s")
    val q = normName(buscar)
    val vistas = grupos[pestana].filter { q.isEmpty() || normName(it.nombre).contains(q) || normName(it.creador).contains(q) || normName(it.plataforma).contains(q) }.let { l ->
        when (orden) {
            1 -> l.sortedWith(compareBy<Ficha> { it.sinValorar }.thenByDescending { it.general })
            2 -> l.sortedBy { normName(it.nombre) }
            else -> l.sortedByDescending { if (it.valorada && it.terminado > 0) it.terminado * 86_400_000L else it.actividad }
        }
    }
    val estado = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    // «SECCIÓN · 12 JUEGOS» sobre el título, como en el móvil y en las hermanas
                    val total = grupos.sumOf { it.size }
                    androidx.compose.material3.Text("SECCIÓN · $total ${if (total == 1) k.unit else k.units}".uppercase(), color = Acento, fontFamily = Body, fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { Cabecera(76.sp, texto = k.title.uppercase()) }
                        Spacer(Modifier.width(20.dp))
                        BotonAcento("+ Añadir", alto = 44.dp, modifier = Modifier.padding(bottom = 12.dp).width(150.dp)) { anadir = true }
                    }
                    FileteCabecera()
                    Spacer(Modifier.height(14.dp))
                    PestanasRevista(nombres, pestana, cuentas = grupos.map { it.size }) { pestana = it }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(buscar, { buscar = it }, Modifier.width(360.dp), placeholder = { Text("Buscar en ${k.title.lowercase()}") }, singleLine = true)
                        Spacer(Modifier.width(24.dp))
                        Box(Modifier.weight(1f)) { FiltrosRevista(listOf("Recientes", "Nota", "Nombre"), orden) { orden = it } }
                        FiltrosRevista(listOf("Portadas", "Índice"), if (indice) 1 else 0, Modifier.width(220.dp)) { indice = it == 1 }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (vistas.isEmpty()) Vacio(if (q.isNotEmpty()) "Nada con «$buscar»" else "Aún no hay nada aquí", if (q.isEmpty()) "Añade uno con «+ Añadir» o desde Hiedra y Marfil." else "")
                }
            }
            if (!indice) items(vistas, key = { it.id }) { f -> Tarjeta(f, k) { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Lista(k.id))) } }
            else items(vistas.chunked(2), key = { "i" + it.first().id }, span = { GridItemSpan(maxLineSpan) }) { par ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                    par.forEach { f -> EntradaIndice(f, k, Modifier.weight(1f).fillMaxHeight()) { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Lista(k.id))) } }
                    if (par.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
    if (anadir) DialogoAnadir(k, pestana, nav) { anadir = false }
}

/** Una entrada del índice: la portada pequeña, el título en grande, quién lo hizo y la nota en cifras enormes (o «Sin valorar»). */
@Composable
fun EntradaIndice(f: Ficha, k: Kind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PortadaCosa(f.portada, f.portadaWeb, f.nombre, Modifier.width(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                FitTitle(f.nombre, 26.sp, 28.sp, letterSpacing = (-0.5).sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val linea = listOf(f.creador, f.plataforma).filter { it.isNotBlank() }.joinToString(" · ")
                if (linea.isNotBlank()) Text(linea.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            if (f.valorada) {
                if (f.sinValorar) Text("Sin valorar", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 16.sp)
                else Text("${f.general}", color = Charcoal, fontFamily = Display, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 48.sp, lineHeight = 48.sp)
            } else Text(estadoDe(f, k).uppercase(), color = if (f.estado == 1) Acento else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 1.2.sp)
        }
        Filete(1.dp, Line)
    }
}
