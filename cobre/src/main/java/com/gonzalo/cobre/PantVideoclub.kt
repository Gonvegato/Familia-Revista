package com.gonzalo.cobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.PlayArrow
import coil.compose.AsyncImage
import com.gonzalo.iptv.Categoria
import com.gonzalo.iptv.DatosCuenta
import com.gonzalo.iptv.Episodio
import com.gonzalo.iptv.FichaPelicula
import com.gonzalo.iptv.FichaSerie
import com.gonzalo.iptv.IptvError
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie
import com.gonzalo.iptv.Visto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que se enseña de una película o una serie en la rejilla. */
private class Cosa(val id: Long, val nombre: String, val poster: String, val sub: String, val abrir: () -> Ficha)

private val RECIENTES = Categoria("\u0001recientes", "Lo último")

/**
 * «Películas» y «Series» (el videoclub de un login de Xtream): sus categorías (con «Lo último» primero) y sus pósteres. El videoclub se baja
 * a trozos la primera vez que entras (y otra vez si tiene más de un día) y se lee del disco por categoría: no se tiene entero en memoria.
 */
@Composable
fun PantVideoclub(series: Boolean, alVolver: () -> Unit, alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    Listas.version; Tele.version
    var lista by remember { mutableStateOf(listaActual(ctx)?.takeIf { it.esXtream } ?: Listas.usables().firstOrNull { it.esXtream }) }
    LaunchedEffect(lista?.id) { lista?.let { Tele.videoclub(it) } }
    val que = if (series) "series" else "peliculas"
    val cats by produceState(emptyList<Categoria>(), lista, Tele.version) { value = lista?.let { l -> withContext(Dispatchers.IO) { Tele.almacen.categorias(l, que) } }.orEmpty() }
    var cat by remember(lista?.id, series) { mutableStateOf(RECIENTES) }
    val cosas by produceState<List<Cosa>?>(null, lista, cat, Tele.version, series) {
        val l = lista ?: run { value = emptyList(); return@produceState }
        value = withContext(Dispatchers.IO) {
            if (series) {
                val s = if (cat === RECIENTES) Tele.almacen.series(l).sortedByDescending { it.modificada }.take(80) else Tele.almacen.series(l, cat.id)
                s.map { x -> Cosa(x.id, x.nombre, x.poster, x.ano, { Ficha.DeSerie(l, x) }) }
            } else {
                val p = if (cat === RECIENTES) Tele.almacen.peliculas(l).sortedByDescending { it.anadida }.take(80) else Tele.almacen.peliculas(l, cat.id)
                p.map { x -> Cosa(x.id, x.nombre, x.poster, x.ano, { Ficha.DePelicula(l, x) }) }
            }
        }
    }
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
        CabeceraSeccion(if (series) "Series" else "Películas", lista?.nombre ?: "El videoclub", alVolver) {
            EnlaceRevista(if (series) "Películas" else "Series", acento = true) { alIr(if (series) Destino.Peliculas else Destino.Series) }
            EnlaceRevista("Buscar") { alIr(Destino.Buscar) }
        }
        SelectorLista(lista, soloXtream = true) { lista = it }
        val l = lista
        if (l == null) { Nota("Las películas y las series vienen con un login de Xtream Codes (una lista M3U solo trae canales). Añade uno en Listas.", Modifier.padding(top = 20.dp)); MagLink("Tus listas", accent = true) { alIr(Destino.Listas) }; return@Column }
        val todas = listOf(RECIENTES) + cats
        LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(todas, key = { it.id }) { c -> FiltroRevista(c.nombre, c.id == cat.id) { cat = c } }
        }
        Filete(1.dp, Line)
        val c = cosas
        when {
            c == null -> Nota("Leyendo…", Modifier.padding(top = 16.dp))
            c.isEmpty() -> Nota(Tele.ocupada[l.id] ?: Tele.errores[l.id] ?: "No hay nada aquí todavía.", Modifier.padding(top = 16.dp))
            else -> LazyVerticalGrid(GridCells.Adaptive(if (esAncha()) 150.dp else 108.dp), Modifier.fillMaxSize().padding(top = 12.dp),
                contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 30.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(c, key = { _, x -> x.id }) { i, x -> TarjetaPoster(x.poster, x.nombre, sub = x.sub, modifier = if (i == 0) Modifier.focoInicial(cat.id) else Modifier) { alFicha(x.abrir()) } }
            }
        }
    }
}

/** La ficha de una película: el póster, de qué va, quién sale, y verla (o seguir donde la dejaste, aquí o en otro dispositivo). */
@Composable
fun PantFichaPelicula(l: Lista, p: Pelicula, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val ficha by produceState<FichaPelicula?>(null, p.id) {
        value = withContext(Dispatchers.IO) { runCatching { Tele.almacen.fichaPelicula(l, p) }.onFailure { error = (it as? IptvError)?.message ?: "No se ha podido leer la ficha." }.getOrNull() }
    }
    val pr = MiCuenta.progreso(l.id, DatosCuenta.videoPelicula(p))
    val f = ficha
    val peli = f?.pelicula ?: p
    PaginaFicha(alCerrar, peli.poster, f?.fondo.orEmpty(), "Película" + if (peli.ano.isNotBlank()) " · ${peli.ano}" else "", peli.nombre) {
        val datos = listOfNotNull(f?.duracionSeg?.takeIf { it > 0 }?.let { duracion((it / 60).toInt()) }, f?.generos?.ifBlank { null }, peli.nota.takeIf { it > 0 }?.let { "★ %.1f".format(it) })
        if (datos.isNotEmpty()) Etiqueta(datos.joinToString(" · "), Modifier.padding(bottom = 10.dp))
        val sigue = pr?.takeIf { !it.visto && Visto.desde(it.pos, it.dur) > 0 }
        AccionGrande(androidx.compose.material.icons.Icons.Rounded.PlayArrow, if (sigue != null) "Seguir (${tiempo(sigue.pos)})" else "Ver", Modifier.fillMaxWidth().focoInicial(p.id)) {
            Reproducir.pon(ctx, Encargo.DePelicula(l, peli, f?.duracionSeg ?: 0L))
        }
        if (sigue != null) { Spacer(Modifier.height(8.dp)); BotonSuave("Desde el principio", modifier = Modifier.fillMaxWidth()) { Reanudar.pon(ctx, l.id, DatosCuenta.videoPelicula(peli), 0, sigue.dur); MiCuenta.ponProgreso(sigue.copy(pos = 0, ultima = System.currentTimeMillis())); Reproducir.pon(ctx, Encargo.DePelicula(l, peli, f?.duracionSeg ?: 0L)) } }
        pr?.let { x -> Spacer(Modifier.height(8.dp)); EnlaceRevista(if (x.visto) "Marcar como no vista" else "Marcar como vista") { MiCuenta.marcaVisto(x, !x.visto) } }
        Spacer(Modifier.height(16.dp))
        when {
            f == null && error == null -> Nota("Leyendo la ficha…")
            f == null -> Nota(error!!)
            else -> {
                if (f.descripcion.isNotBlank()) Text(f.descripcion, color = Charcoal, fontFamily = Display, fontSize = 18.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(12.dp))
                if (f.director.isNotBlank()) MagFact("Dirección", f.director)
                if (f.reparto.isNotBlank()) { Etiqueta("Reparto", Modifier.padding(top = 8.dp)); Cuerpo(f.reparto) }
                if (f.estreno.isNotBlank()) MagFact("Estreno", f.estreno)
                if (f.pais.isNotBlank()) MagFact("País", f.pais)
            }
        }
    }
}

/** La ficha de una serie: sus temporadas (en pestañas) y sus episodios, con lo visto de cada uno, y «Seguir» con el que toca. */
@Composable
fun PantFichaSerie(l: Lista, s: Serie, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val ficha by produceState<FichaSerie?>(null, s.id) {
        value = withContext(Dispatchers.IO) { runCatching { Tele.almacen.fichaSerie(l, s) }.onFailure { error = (it as? IptvError)?.message ?: "No se ha podido leer la ficha." }.getOrNull() }
    }
    val f = ficha
    val serie = f?.serie ?: s
    val progresos = MiCuenta.progresos().filter { it.lista == l.id && it.serie == s.id }.associateBy { it.id }
    // El que toca: el último que viste (o el siguiente, si ya lo terminaste); si no, el primero
    val toca: Episodio? = f?.let { fs ->
        val ult = progresos.values.maxByOrNull { it.ultima }?.let { p -> fs.todos.firstOrNull { it.id == p.id }?.let { e -> if (p.visto) fs.siguiente(e) ?: e else e } }
        ult ?: fs.todos.firstOrNull { it.temporada > 0 } ?: fs.todos.firstOrNull()
    }
    var temp by remember(f) { mutableStateOf(toca?.temporada ?: f?.temporadas?.firstOrNull()?.numero ?: 1) }
    PaginaFicha(alCerrar, serie.poster, serie.fondo, "Serie" + if (serie.ano.isNotBlank()) " · ${serie.ano}" else "", serie.nombre) {
        val datos = listOfNotNull(serie.generos.ifBlank { null }, serie.nota.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, f?.let { "${it.temporadas.count { t -> t.numero > 0 }} temporadas" })
        if (datos.isNotEmpty()) Etiqueta(datos.joinToString(" · "), Modifier.padding(bottom = 10.dp))
        if (toca != null) AccionGrande(androidx.compose.material.icons.Icons.Rounded.PlayArrow, (if (progresos.isEmpty()) "Ver" else "Seguir") + " · T${toca.temporada} · E${toca.numero}", Modifier.fillMaxWidth().focoInicial(s.id)) {
            Reproducir.pon(ctx, Encargo.DeEpisodio(l, serie, f, toca))
        }
        if (serie.trama.isNotBlank()) Text(serie.trama, color = Charcoal, fontFamily = Display, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 14.dp))
        when {
            f == null && error == null -> Nota("Leyendo los episodios…", Modifier.padding(top = 12.dp))
            f == null -> Nota(error!!, Modifier.padding(top = 12.dp))
            else -> {
                Spacer(Modifier.height(14.dp))
                PestanasRevista(f.temporadas.map { it.nombre }, f.temporadas.indexOfFirst { it.numero == temp }.coerceAtLeast(0)) { temp = f.temporadas[it].numero }
                f.episodios[temp].orEmpty().forEach { e ->
                    val p = progresos[e.id]
                    Row(Modifier.fillMaxWidth().tocable { Reproducir.pon(ctx, Encargo.DeEpisodio(l, serie, f, e)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.numero}", color = if (p?.visto == true) Muted else Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tituloEpisodio(e, serie.nombre), color = if (p?.visto == true) Muted else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(e.duracionSeg.takeIf { it > 0 }?.let { duracion((it / 60).toInt()) }, if (p?.visto == true) "visto" else p?.takeIf { it.pos > 0 }?.let { "vas por ${tiempo(it.pos)}" })
                            if (sub.isNotEmpty()) Etiqueta(sub.joinToString(" · "))
                            if (p != null && !p.visto && p.fraccion > 0f) Barra(p.fraccion, Modifier.padding(top = 4.dp).widthIn(max = 280.dp))
                        }
                        if (p != null) EnlaceRevista(if (p.visto) "No vista" else "Vista") { MiCuenta.marcaVisto(p, !p.visto) }
                    }
                    Filete(1.dp, Line)
                }
            }
        }
    }
}

/** La página de una ficha (por encima de la sección): el póster y los datos a dos columnas en lo ancho; uno debajo de otro en el móvil. */
@Composable
private fun PaginaFicha(alCerrar: () -> Unit, poster: String, fondo: String, ante: String, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    val m = margen()
    Box(Modifier.fillMaxSize().background(Paper).atrapaFoco()) {
        if (fondo.isNotBlank()) AsyncImage(fondo, null, contentScale = ContentScale.Crop, alpha = 0.12f, modifier = Modifier.fillMaxWidth().height(320.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = m)) {
            RevistaTopBar(alCerrar, "Volver")
            RevistaHeadline(titulo, ante)
            Filete(3.dp)
            Spacer(Modifier.height(16.dp))
            if (esAncha()) Row(Modifier.fillMaxWidth()) {
                Poster(poster, titulo, Modifier.width(260.dp))
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f), content = contenido)
            } else Column {
                Poster(poster, titulo, Modifier.width(170.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                contenido()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
