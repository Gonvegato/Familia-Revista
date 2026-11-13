package com.gonzalo.cobre.pc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.gonzalo.familia.pc.revista.*
import com.gonzalo.iptv.Buscar
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class Hallado(val canales: List<Triple<Lista, Cargada, Canal>>, val peliculas: List<Pair<Lista, Pelicula>>, val series: List<Pair<Lista, Serie>>)

/**
 * «Buscar»: en los canales de todas tus listas de este ordenador y, en las de Xtream, en sus películas y sus series (sin mirar acentos ni
 * mayúsculas). Lo del videoclub se recorre en el disco: no se tiene entero en memoria.
 */
@Composable
fun PantBuscar(nav: Nav) {
    var texto by remember { mutableStateOf("") }
    val campo = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { campo.requestFocus() } }
    val v = Tele.version
    val hallado by produceState<Hallado?>(null, texto, v) {
        val q = Buscar.normal(texto)
        if (q.length < 2) { value = null; return@produceState }
        delay(250)
        value = withContext(Dispatchers.IO) {
            val ls = Listas.usables()
            val canales = ls.flatMap { l -> Tele.cargada(l)?.let { cg -> cg.canales.filter { Buscar.normal(it.nombre).contains(q) }.take(40).map { Triple(l, cg, it) } }.orEmpty() }
            val pelis = ls.filter { it.esXtream }.flatMap { l -> Tele.almacen.buscaPeliculas(l, texto, 30).map { l to it } }
            val series = ls.filter { it.esXtream }.flatMap { l -> Tele.almacen.buscaSeries(l, texto, 30).map { l to it } }
            Hallado(canales, pelis, series)
        }
    }
    Pagina(ancho = 1180.dp) {
        CabeceraPagina("Nut", "Buscar canal", volver = { nav.volver() })
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth().focusRequester(campo), label = { Text("Un canal, una película o una serie") }, singleLine = true)
        val h = hallado
        when {
            texto.trim().length < 2 -> Nota("Escribe al menos dos letras.", Modifier.padding(top = 16.dp))
            h == null -> Nota("Buscando…", Modifier.padding(top = 16.dp))
            h.canales.isEmpty() && h.peliculas.isEmpty() && h.series.isEmpty() -> Nota("Nada con «${texto.trim()}».", Modifier.padding(top = 16.dp))
            else -> {
                if (h.canales.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp)); Antetitulo("Canales"); Spacer(Modifier.height(6.dp))
                    val favs = MiCuenta.favoritos()
                    val ahora = remember(h) { System.currentTimeMillis() }
                    h.canales.forEachIndexed { i, (l, cg, c) ->
                        FilaCanal(l, cg, c, i, ahora, favs.firstOrNull { it.lista == l.id && it.canal == c.clave }) {
                            val delGrupo = Tele.visibles(cg, c.grupo).ifEmpty { listOf(c) }
                            nav.ver(Encargo.Directo(l, delGrupo, delGrupo.indexOfFirst { it.clave == c.clave }.coerceAtLeast(0)))
                        }
                    }
                }
                if (h.peliculas.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp)); Antetitulo("Películas"); Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(h.peliculas, key = { (l, p) -> "${l.id}p${p.id}" }) { (l, p) -> TarjetaPoster(p.poster, p.nombre, 150.dp, p.ano) { nav.ir(Pantalla.FichaPelicula(l, p, Pantalla.Buscar)) } }
                    }
                }
                if (h.series.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp)); Antetitulo("Series"); Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(h.series, key = { (l, s) -> "${l.id}s${s.id}" }) { (l, s) -> TarjetaPoster(s.poster, s.nombre, 150.dp, s.ano) { nav.ir(Pantalla.FichaSerie(l, s, Pantalla.Buscar)) } }
                    }
                }
            }
        }
    }
}
