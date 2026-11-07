package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.Buscar
import com.gonzalo.iptv.Canal
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Buscar en todas tus listas: canales (por su nombre) y, en las de Xtream, películas y series. Sin mirar acentos ni mayúsculas. */
@Composable
fun PantBuscar(alVolver: () -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    val listas = Listas.usables()
    class Resultado(val canales: List<Pair<Lista, Canal>>, val pelis: List<Pair<Lista, Pelicula>>, val series: List<Pair<Lista, Serie>>)
    val r by produceState<Resultado?>(null, q, listas) {
        if (q.trim().length < 2) { value = null; return@produceState }
        delay(250)
        value = withContext(Dispatchers.IO) {
            val n = Buscar.normal(q)
            Resultado(
                listas.flatMap { l -> Tele.cargada(l)?.canales.orEmpty().asSequence().filter { Buscar.normal(it.nombre).contains(n) }.take(80).map { l to it }.toList() },
                listas.filter { it.esXtream }.flatMap { l -> Tele.almacen.buscaPeliculas(l, q).map { l to it } },
                listas.filter { it.esXtream }.flatMap { l -> Tele.almacen.buscaSeries(l, q).map { l to it } },
            )
        }
    }
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        CabeceraSeccion("Buscar", "En tus listas", alVolver)
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            if (q.isEmpty()) Text("Un canal, una película, una serie…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp)
            BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp), cursorBrush = SolidColor(Acento),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(), modifier = Modifier.fillMaxWidth().focoInicial())
        }
        Filete(2.dp)
        val x = r
        if (x == null) { Nota(if (q.isBlank()) "Escribe al menos dos letras." else "Buscando…", Modifier.padding(top = 14.dp)); return@Column }
        if (x.canales.isEmpty() && x.pelis.isEmpty() && x.series.isEmpty()) { Nota("Nada con «$q».", Modifier.padding(top = 14.dp)); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            if (x.pelis.isNotEmpty() || x.series.isNotEmpty()) item {
                val ancho = if (esAncha()) 130.dp else 100.dp
                if (x.pelis.isNotEmpty()) {
                    Antetitulo("Películas", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    LazyRow(Modifier.sangrado(8.dp), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(x.pelis, key = { it.first.id + it.second.id }) { (l, p) -> TarjetaPoster(p.poster, p.nombre, ancho, p.ano) { alFicha(Ficha.DePelicula(l, p)) } }
                    }
                }
                if (x.series.isNotEmpty()) {
                    Antetitulo("Series", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    LazyRow(Modifier.sangrado(8.dp), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(x.series, key = { it.first.id + it.second.id }) { (l, s) -> TarjetaPoster(s.poster, s.nombre, ancho, s.ano) { alFicha(Ficha.DeSerie(l, s)) } }
                    }
                }
            }
            if (x.canales.isNotEmpty()) item { Antetitulo("Canales", Modifier.padding(top = 18.dp, bottom = 4.dp)) }
            items(x.canales, key = { it.first.id + it.second.clave }) { (l, c) ->
                val cg = Tele.enMemoria(l.id)
                if (cg != null) FilaCanal(l, cg, c, 0, System.currentTimeMillis()) { Reproducir.canal(ctx, l, x.canales.filter { it.first.id == l.id }.map { it.second }, c) }
            }
        }
    }
}
