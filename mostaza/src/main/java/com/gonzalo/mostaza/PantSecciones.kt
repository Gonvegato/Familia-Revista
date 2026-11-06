package com.gonzalo.mostaza

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Las secciones de tu biblioteca: Viendo · Pendiente · Visto · Abandonado, cada una con sus películas y series, en rejilla. */
@Composable
fun PantSecciones(d: Destino, alVolver: () -> Unit, alAbrir: (MetaBreve) -> Unit, alIr: (Destino) -> Unit) {
    val ctx = LocalContext.current
    val secciones = listOf(Destino.Viendo to Estado.VIENDO, Destino.Pendiente to Estado.PENDIENTE, Destino.Visto to Estado.VISTO, Destino.Abandonado to Estado.ABANDONADO)
    val estado = secciones.firstOrNull { it.first == d }?.second ?: Estado.VIENDO
    EspejoCuenta.version
    var orden by remember(estado) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    var filtro by remember(estado) { mutableStateOf(OrdenGuardado.filtro(ctx, estado)) }
    val todos = Biblioteca.titulos.toList()
    Biblioteca.version
    val lista = todos.filter { it.estado == estado }
        .filter { when (filtro) { OrdenGuardado.Filtro.PELICULAS -> !it.esSerie; OrdenGuardado.Filtro.SERIES -> it.esSerie; else -> true } }
        .let { l -> when (orden) {
            OrdenGuardado.Orden.RECIENTES -> l.sortedByDescending { maxOf(it.ultima, it.fin, it.anadido) }
            OrdenGuardado.Orden.ANADIDOS -> l.sortedByDescending { it.anadido }
            OrdenGuardado.Orden.TITULO -> l.sortedBy { it.nombre.lowercase() }
            OrdenGuardado.Orden.ANO -> l.sortedByDescending { it.ano.take(4) }
        } }
    val m = margen()
    val ancho = if (esAncha()) 150.dp else 104.dp
    LazyVerticalGrid(GridCells.Adaptive(ancho), Modifier.fillMaxSize().background(Paper), contentPadding = PaddingValues(start = m, end = m, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                RevistaTopBar(alVolver, "Mostaza")
                Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                FitTitle(estado.nombre, 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(3.dp)
                PestanasRevista(secciones.map { it.second.nombre }, secciones.indexOfFirst { it.second == estado }, cuentas = secciones.map { s -> todos.count { it.estado == s.second } }) { i -> alIr(secciones[i].first) }
                Spacer(Modifier.height(6.dp))
                FiltrosRevista(OrdenGuardado.Filtro.entries.map { it.nombre }, filtro.ordinal) { i -> filtro = OrdenGuardado.Filtro.entries[i]; OrdenGuardado.ponFiltro(ctx, estado, filtro) }
                Row { Text("ORDEN  ", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 9.dp))
                    FiltrosRevista(OrdenGuardado.Orden.entries.map { it.nombre }, orden.ordinal) { i -> orden = OrdenGuardado.Orden.entries[i]; OrdenGuardado.pon(ctx, estado, orden) } }
                Filete(1.dp, Line)
                if (lista.isEmpty()) Nota(when (estado) {
                    Estado.VIENDO -> "Nada a medias. Lo que ves entra aquí solo cuando llevas 10 minutos viéndolo."
                    Estado.PENDIENTE -> "Nada pendiente. En la ficha de cualquier película o serie, «+ Añadir»."
                    Estado.VISTO -> "Aún no has terminado nada aquí. Una película está vista al 90 %; una serie, con todos sus episodios estrenados vistos."
                    Estado.ABANDONADO -> "Nada abandonado."
                }, Modifier.padding(top = 18.dp))
            }
        }
        items(lista, key = { it.ref }) { t ->
            val p = Biblioteca.ultimo(t.ref)
            TarjetaPoster(t.imagen, t.nombre, androidx.compose.ui.unit.Dp.Unspecified, if (estado == Estado.VIENDO) seguirTexto(t) else listOf(tipoNombre(t.tipo), t.ano).filter { it.isNotBlank() }.joinToString(" · "),
                if (estado == Estado.VIENDO && p != null && !p.visto) p.fraccion else null) { alAbrir(t.breve()) }
        }
    }
}
