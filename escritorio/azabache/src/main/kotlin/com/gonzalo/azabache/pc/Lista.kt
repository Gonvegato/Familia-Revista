package com.gonzalo.azabache.pc

import androidx.compose.foundation.VerticalScrollbar
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { Cabecera(76.sp, texto = k.title.uppercase()) }
                        Spacer(Modifier.width(20.dp))
                        BotonAcento("+ Añadir", alto = 44.dp, modifier = Modifier.width(150.dp)) { anadir = true }
                    }
                    FileteCabecera()
                    Spacer(Modifier.height(14.dp))
                    PestanasRevista(nombres, pestana, cuentas = grupos.map { it.size }) { pestana = it }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(buscar, { buscar = it }, Modifier.width(360.dp), placeholder = { Text("Buscar en ${k.title.lowercase()}") }, singleLine = true)
                        Spacer(Modifier.width(24.dp))
                        FiltrosRevista(listOf("Recientes", "Nota", "Nombre"), orden) { orden = it }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (vistas.isEmpty()) Vacio(if (q.isNotEmpty()) "Nada con «$buscar»" else "Aún no hay nada aquí", if (q.isEmpty()) "Añade uno con «+ Añadir» o desde Hiedra y Marfil." else "")
                }
            }
            items(vistas, key = { it.id }) { f -> Tarjeta(f, k) { nav.ir(Pantalla.Detalle(k.id, f.id, Pantalla.Lista(k.id))) } }
        }
        VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
    if (anadir) DialogoAnadir(k, pestana, nav) { anadir = false }
}
