package com.gonzalo.cobre.pc

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
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
private class Cosa(val clave: String, val nombre: String, val poster: String, val sub: String, val progreso: Float?, val abrir: () -> Pantalla?)

private val RECIENTES = Categoria("\u0001recientes", "Lo último")
private val SEGUIR = Categoria("\u0001seguir", "Seguir viendo")

@Composable fun PantPeliculas(nav: Nav, hay: Boolean) = PantVideoclub(nav, series = false)
@Composable fun PantSeries(nav: Nav, hay: Boolean) = PantVideoclub(nav, series = true)

/**
 * «Películas» y «Series» (el videoclub de un login de Xtream): «Seguir viendo» (lo que dejaste a medias, aquí o en otro dispositivo), «Lo
 * último» y las categorías del servidor, con sus pósteres. El videoclub se baja a trozos la primera vez que entras (y otra vez si tiene más
 * de un día) y se lee del disco por categoría: no se tiene entero en memoria (como en el móvil).
 */
@Composable
private fun PantVideoclub(nav: Nav, series: Boolean) {
    var lista by remember { mutableStateOf(listaActual(soloXtream = true)) }
    LaunchedEffect(lista?.id) { lista?.let { Tele.videoclub(it) } }
    val que = if (series) "series" else "peliculas"
    val v = Tele.version
    val cats by produceState(emptyList<Categoria>(), lista, v) { value = lista?.let { l -> withContext(Dispatchers.IO) { Tele.almacen.categorias(l, que) } }.orEmpty() }
    val seguir = lista?.let { l -> MiCuenta.seguir().filter { it.lista == l.id && it.esEpisodio == series } }.orEmpty()
    var cat by remember(lista?.id, series) { mutableStateOf(if (seguir.isNotEmpty()) SEGUIR else RECIENTES) }
    val cosas by produceState<List<Cosa>?>(null, lista, cat, v, series, seguir) {
        val l = lista ?: run { value = emptyList(); return@produceState }
        value = withContext(Dispatchers.IO) {
            if (cat === SEGUIR) seguir.mapNotNull { p ->
                if (p.esEpisodio) Tele.almacen.serie(l, p.serie)?.let { s -> Cosa("s${s.id}", s.nombre, s.poster, "T${p.temporada} · E${p.episodio}", if (p.visto) null else p.fraccion) { Pantalla.FichaSerie(l, s, Pantalla.Series) } }
                else Tele.almacen.pelicula(l, p.id)?.let { x -> Cosa("p${x.id}", x.nombre, x.poster, "vas por ${Formato.tiempo(p.pos)}", p.fraccion) { Pantalla.FichaPelicula(l, x, Pantalla.Peliculas) } }
            } else if (series) {
                val s = if (cat === RECIENTES) Tele.almacen.series(l).sortedByDescending { it.modificada }.take(80) else Tele.almacen.series(l, cat.id)
                s.map { x -> Cosa("s${x.id}", x.nombre, x.poster, x.ano, null) { Pantalla.FichaSerie(l, x, Pantalla.Series) } }
            } else {
                val p = if (cat === RECIENTES) Tele.almacen.peliculas(l).sortedByDescending { it.anadida }.take(80) else Tele.almacen.peliculas(l, cat.id)
                p.map { x -> Cosa("p${x.id}", x.nombre, x.poster, x.ano, null) { Pantalla.FichaPelicula(l, x, Pantalla.Peliculas) } }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 26.dp)) {
        CabeceraPagina("El videoclub", if (series) "Series" else "Películas") { EnlaceRevista("Buscar") { nav.ir(Pantalla.Buscar) } }
        SelectorLista(lista, soloXtream = true) { lista = it }
        val l = lista
        if (l == null) {
            Spacer(Modifier.height(20.dp))
            Nota("Las películas y las series vienen con un login de Xtream Codes (una lista M3U solo trae canales). Añade uno en Ajustes → Listas.")
            MagLink("Tus listas", accent = true) { nav.ir(Pantalla.Ajustes(0)) }
            return@Column
        }
        val todas = (if (seguir.isNotEmpty()) listOf(SEGUIR) else emptyList()) + listOf(RECIENTES) + cats
        LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(todas, key = { it.id }) { c -> FiltroRevista(c.nombre, c.id == cat.id) { cat = c } }
        }
        Filete(1.dp, Line)
        val c = cosas
        when {
            c == null -> Nota("Leyendo…", Modifier.padding(top = 16.dp))
            c.isEmpty() -> Nota(Tele.ocupada[l.id] ?: Tele.errores[l.id] ?: "No hay nada aquí todavía.", Modifier.padding(top = 16.dp))
            else -> {
                val estado = rememberLazyGridState()
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(top = 12.dp, end = 12.dp), state = estado,
                        contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 30.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(c, key = { it.clave }) { x -> TarjetaPoster(x.poster, x.nombre, Dp.Unspecified, x.sub, x.progreso) { x.abrir()?.let { nav.ir(it) } } }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

/** La página de una ficha: el póster a la izquierda y lo demás a la derecha (como en la tablet y en la tele). */
@Composable
private fun PaginaFicha(nav: Nav, poster: String, ante: String, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Pagina(ancho = 1100.dp) {
        CabeceraPagina(ante, titulo, volver = { nav.volver() })
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            ImagenCosa(poster, titulo, Modifier.width(260.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(30.dp))
            Column(Modifier.weight(1f), content = contenido)
        }
    }
}

/** La ficha de una película: el póster, de qué va, quién sale, y verla (o seguir donde la dejaste, aquí o en otro dispositivo). */
@Composable
fun PantFichaPelicula(l: Lista, p: Pelicula, nav: Nav) {
    var error by remember { mutableStateOf<String?>(null) }
    val ficha by produceState<FichaPelicula?>(null, p.id) {
        value = withContext(Dispatchers.IO) { runCatching { Tele.almacen.fichaPelicula(l, p) }.onFailure { error = (it as? IptvError)?.message ?: "No se ha podido leer la ficha." }.getOrNull() }
    }
    val pr = MiCuenta.progreso(l.id, DatosCuenta.videoPelicula(p))
    val f = ficha
    val peli = f?.pelicula ?: p
    PaginaFicha(nav, peli.poster, "Película" + if (peli.ano.isNotBlank()) " · ${peli.ano}" else "", peli.nombre) {
        val datos = listOfNotNull(f?.duracionSeg?.takeIf { it > 0 }?.let { Formato.duracion(it * 1000) }, f?.generos?.ifBlank { null }, peli.nota.takeIf { it > 0 }?.let { "★ %.1f".format(java.util.Locale.forLanguageTag("es-ES"), it) })
        if (datos.isNotEmpty()) Etiqueta(datos.joinToString(" · "), Modifier.padding(bottom = 10.dp))
        val sigue = pr?.takeIf { !it.visto && Visto.desde(it.pos, it.dur) > 0 }
        BotonAcento(if (sigue != null) "Seguir (${Formato.tiempo(sigue.pos)})" else "Ver", modifier = Modifier.fillMaxWidth()) { nav.ver(Encargo.DePelicula(l, peli, f?.duracionSeg ?: 0L)) }
        if (sigue != null) {
            Spacer(Modifier.height(8.dp))
            BotonSuave("Desde el principio", modifier = Modifier.fillMaxWidth()) {
                Reanudar.pon(l.id, DatosCuenta.videoPelicula(peli), 0, sigue.dur); MiCuenta.ponProgreso(sigue.copy(pos = 0, ultima = System.currentTimeMillis()))
                nav.ver(Encargo.DePelicula(l, peli, f?.duracionSeg ?: 0L))
            }
        }
        pr?.let { x -> Spacer(Modifier.height(8.dp)); EnlaceRevista(if (x.visto) "Marcar como no vista" else "Marcar como vista") { MiCuenta.marcaVisto(x, !x.visto) } }
        Spacer(Modifier.height(16.dp))
        when {
            f == null && error == null -> Nota("Leyendo la ficha…")
            f == null -> Nota(error!!)
            else -> {
                if (f.descripcion.isNotBlank()) Text(f.descripcion, color = Charcoal, fontFamily = Grotesca, fontSize = 18.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(12.dp))
                if (f.director.isNotBlank()) MagFact("Dirección", f.director)
                if (f.reparto.isNotBlank()) { Etiqueta("Reparto", Modifier.padding(top = 8.dp)); Cuerpo(f.reparto) }
                if (f.estreno.isNotBlank()) MagFact("Estreno", f.estreno)
                if (f.pais.isNotBlank()) MagFact("País", f.pais)
            }
        }
    }
}

/** La ficha de una serie: sus temporadas (en pestañas) y sus episodios, con lo visto de cada uno, y «Seguir» con el que toca (como en el móvil). */
@Composable
fun PantFichaSerie(l: Lista, s: Serie, nav: Nav) {
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
    PaginaFicha(nav, serie.poster, "Serie" + if (serie.ano.isNotBlank()) " · ${serie.ano}" else "", serie.nombre) {
        val datos = listOfNotNull(serie.generos.ifBlank { null }, serie.nota.takeIf { it > 0 }?.let { "★ %.1f".format(java.util.Locale.forLanguageTag("es-ES"), it) }, f?.let { "${it.temporadas.count { t -> t.numero > 0 }} temporadas" })
        if (datos.isNotEmpty()) Etiqueta(datos.joinToString(" · "), Modifier.padding(bottom = 10.dp))
        if (toca != null) BotonAcento((if (progresos.isEmpty()) "Ver" else "Seguir") + " · T${toca.temporada} · E${toca.numero}", modifier = Modifier.fillMaxWidth()) {
            nav.ver(Encargo.DeEpisodio(l, serie, f, toca))
        }
        if (serie.trama.isNotBlank()) Text(serie.trama, color = Charcoal, fontFamily = Grotesca, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 14.dp))
        when {
            f == null && error == null -> Nota("Leyendo los episodios…", Modifier.padding(top = 12.dp))
            f == null -> Nota(error!!, Modifier.padding(top = 12.dp))
            else -> {
                Spacer(Modifier.height(14.dp))
                PestanasRevista(f.temporadas.map { it.nombre }, f.temporadas.indexOfFirst { it.numero == temp }.coerceAtLeast(0)) { temp = f.temporadas[it].numero }
                f.episodios[temp].orEmpty().forEach { e ->
                    val p = progresos[e.id]
                    Row(Modifier.fillMaxWidth().pulsable { nav.ver(Encargo.DeEpisodio(l, serie, f, e)) }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.numero}", color = if (p?.visto == true) Muted else Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tituloEpisodio(e, serie.nombre), color = if (p?.visto == true) Muted else Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(e.duracionSeg.takeIf { it > 0 }?.let { Formato.duracion(it * 1000) }, if (p?.visto == true) "visto" else p?.takeIf { it.pos > 0 }?.let { "vas por ${Formato.tiempo(it.pos)}" })
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
