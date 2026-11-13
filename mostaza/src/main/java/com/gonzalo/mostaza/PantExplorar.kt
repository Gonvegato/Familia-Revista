package com.gonzalo.mostaza

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Los catálogos que se pueden recorrer en «Explorar» (los de solo buscar, no). */
private fun explorables(a: Addon) = a.manifiesto.catalogos.filter { !it.soloBusqueda && !it.necesitaOtraCosa }

/**
 * «Explorar»: los catálogos de tus addons, por tipo (películas, series…), con sus filtros de género o de año (los que da cada catálogo), en
 * rejilla y por páginas. Lo último que eliges viaja con tu cuenta.
 */
@Composable
fun PantExplorar(alVolver: () -> Unit, alAbrir: (MetaBreve) -> Unit) {
    Tmdb.version
    if (Tmdb.activo) { PantExplorarTmdb(alVolver, alAbrir); return }
    PantExplorarAddons(alVolver, alAbrir)
}

/** «Explorar» sin TMDB: los catálogos de tus addons. */
@Composable
private fun PantExplorarAddons(alVolver: () -> Unit, alAbrir: (MetaBreve) -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val addons by produceState<List<Addon>?>(null, Addons.version) { value = runCatching { Addons.conManifiesto() }.getOrDefault(emptyList()) }
    val pares = addons.orEmpty().flatMap { a -> explorables(a).map { a to it } }
    val tipos = pares.map { it.second.tipo }.distinct().sortedBy { when (it) { "movie" -> 0; "series" -> 1; else -> 2 } }
    val (tipo0, cat0, gen0) = remember { AjustesPortada.explorar(ctx) }
    var tipo by remember { mutableStateOf(tipo0) }
    if (tipos.isNotEmpty() && tipo !in tipos) tipo = tipos.first()
    val delTipo = pares.filter { it.second.tipo == tipo }
    var catClave by remember { mutableStateOf(cat0) }
    val sel = delTipo.firstOrNull { "${it.first.manifiesto.id}|${it.second.id}" == catClave } ?: delTipo.firstOrNull()
    var genero by remember { mutableStateOf(gen0) }
    val generos = sel?.second?.generos.orEmpty()
    if (sel != null && genero.isNotBlank() && genero !in generos) genero = ""
    if (sel != null && sel.second.generoObligatorio && genero.isBlank() && generos.isNotEmpty()) genero = generos.first()
    var cosas by remember { mutableStateOf<List<MetaBreve>?>(null) }
    var cargandoMas by remember { mutableStateOf(false) }
    var fin by remember { mutableStateOf(false) }
    LaunchedEffect(sel?.first?.url, sel?.second?.id, tipo, genero) {
        cosas = null; fin = false
        val s = sel ?: run { cosas = emptyList(); return@LaunchedEffect }
        AjustesPortada.ponExplorar(ctx, tipo, "${s.first.manifiesto.id}|${s.second.id}", genero)
        val l = Catalogos.lista(s.first, s.second, if (genero.isNotBlank()) listOf("genre" to genero) else emptyList())
        cosas = l; fin = l.size < 20 || !s.second.pagina
    }
    val m = margen()
    val ancho = if (esAncha()) 150.dp else 104.dp
    LazyVerticalGrid(GridCells.Adaptive(ancho), Modifier.fillMaxSize().background(Paper), contentPadding = PaddingValues(start = m, end = m, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                RevistaTopBar(alVolver, "Hathor")
                Antetitulo("Tus addons", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                FitTitle("Explorar", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(3.dp)
                when {
                    addons == null -> Nota("Leyendo tus addons…", Modifier.padding(top = 14.dp))
                    tipos.isEmpty() -> Nota("Ninguno de tus addons tiene catálogos. Cinemeta (el oficial) sí: lo puedes poner en «Addons».", Modifier.padding(top = 14.dp))
                    else -> {
                        PestanasRevista(tipos.map { tiposNombre(it) }, tipos.indexOf(tipo)) { i -> tipo = tipos[i]; catClave = ""; genero = "" }
                        Spacer(Modifier.height(6.dp))
                        val varios = delTipo.map { it.first.url }.distinct().size > 1
                        FiltrosRevista(delTipo.map { (a, c) -> if (varios) "${c.nombre} · ${a.nombre}" else c.nombre }, delTipo.indexOf(sel).coerceAtLeast(0)) { i ->
                            catClave = "${delTipo[i].first.manifiesto.id}|${delTipo[i].second.id}"; genero = ""
                        }
                        if (generos.isNotEmpty()) {
                            Etiqueta(if (sel?.second?.porAno == true) "Año" else "Género", Modifier.padding(top = 4.dp))
                            val ops = (if (sel?.second?.generoObligatorio == true) emptyList() else listOf("Todos")) + generos
                            FiltrosRevista(ops, ops.indexOf(genero.ifBlank { "Todos" }).coerceAtLeast(0)) { i -> genero = if (ops[i] == "Todos" && sel?.second?.generoObligatorio != true) "" else ops[i] }
                        }
                        Filete(1.dp, Line)
                        if (cosas == null) Nota("Cargando…", Modifier.padding(top = 14.dp))
                        else if (cosas!!.isEmpty()) Nota("Este catálogo no trae nada ahora mismo.", Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        items(cosas.orEmpty(), key = { it.tipo + it.id }) { b -> TarjetaPoster(b.poster, b.nombre, Dp.Unspecified, b.ano, clave = b.id) { alAbrir(b) } }
        if (cosas?.isNotEmpty() == true && !fin) item(span = { GridItemSpan(maxLineSpan) }) {
            EnlaceRevista(if (cargandoMas) "Cargando…" else "Más →", acento = true) {
                val s = sel ?: return@EnlaceRevista
                if (cargandoMas) return@EnlaceRevista
                cargandoMas = true
                ambito.launch {
                    val ya = cosas.orEmpty()
                    val ex = listOfNotNull(genero.takeIf { it.isNotBlank() }?.let { "genre" to it }, "skip" to ya.size.toString())
                    val mas = runCatching { Addons.catalogo(s.first, s.second, ex) }.getOrDefault(emptyList()).filter { n -> ya.none { it.id == n.id } }
                    cosas = ya + mas; fin = mas.isEmpty(); cargandoMas = false
                }
            }
        }
    }
}

/** Buscar en todos los catálogos de tus addons que buscan (cada uno en su fila). */
/**
 * Lo que sale en el buscador que crece desde BUSCAR, según escribes: lo de tu biblioteca al momento y, tras una pausa corta al teclear,
 * lo de TMDB (o de tus addons, si no hay TMDB).
 */
@Composable
fun ResultadosBuscar(q: String, alAbrir: (MetaBreve) -> Unit) {
    val t = q.trim()
    var resultados by remember { mutableStateOf<List<Triple<Addon, Catalogo, List<MetaBreve>>>?>(null) }
    var deTmdb by remember { mutableStateOf<List<MetaBreve>?>(null) }
    var buscando by remember { mutableStateOf(false) }
    val enTu = Biblioteca.titulos.toList().filter { t.length >= 2 && it.nombre.contains(t, ignoreCase = true) }
    LaunchedEffect(t) {
        resultados = null; deTmdb = null; buscando = false
        if (t.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        buscando = true
        try {
            if (Tmdb.activo) deTmdb = try { Tmdb.busca(t) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
            else resultados = try { Addons.busca(t) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
        } finally { buscando = false }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = margen())) {
        if (enTu.isNotEmpty()) FilaPosters("En tu biblioteca", enTu.map { it.breve() }, alAbrir, sub = { b -> enTu.firstOrNull { it.idStremio == b.id }?.estado?.nombre.orEmpty() },
            imagen = { b -> enTu.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster })
        if (buscando) Nota("Buscando «$t»…", Modifier.padding(top = 18.dp))
        deTmdb?.let { l ->
            if (l.isEmpty()) Nota("Nada con «$t».", Modifier.padding(top = 18.dp))
            val pelis = l.filter { it.tipo == "movie" }; val series = l.filter { it.tipo == "series" }
            if (pelis.isNotEmpty()) FilaPosters("Películas", pelis, alAbrir)
            if (series.isNotEmpty()) FilaPosters("Series", series, alAbrir)
        }
        resultados?.let { r ->
            if (r.isEmpty()) Nota("Nada en tus addons con «$t».", Modifier.padding(top = 18.dp))
            r.forEach { (a, c, l) -> FilaPosters("${tiposNombre(c.tipo)} · ${a.nombre}", l, alAbrir) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun PantBuscar(alVolver: () -> Unit, alAbrir: (MetaBreve) -> Unit) {
    val ambito = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var resultados by remember { mutableStateOf<List<Triple<Addon, Catalogo, List<MetaBreve>>>?>(null) }
    var deTmdb by remember { mutableStateOf<List<MetaBreve>?>(null) }
    val enTu = Biblioteca.titulos.toList().filter { q.length >= 2 && it.nombre.contains(q.trim(), ignoreCase = true) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    fun busca() {
        val t = q.trim(); if (t.length < 2 || buscando) return
        buscando = true
        ambito.launch {
            if (Tmdb.activo) { deTmdb = runCatching { Tmdb.busca(t) }.getOrDefault(emptyList()); resultados = emptyList() }
            else resultados = runCatching { Addons.busca(t) }.getOrDefault(emptyList())
            buscando = false
        }
    }
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = m)) {
        RevistaTopBar(alVolver, "Hathor")
        Antetitulo(if (Tmdb.activo) "Películas y series" else "En tus addons", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Buscar", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Una película o una serie…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp),
                    cursorBrush = SolidColor(Acento), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { busca() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(foco))
            }
            EnlaceRevista(if (buscando) "Buscando…" else "Buscar", acento = true) { busca() }
        }
        Filete(2.dp)
        if (enTu.isNotEmpty()) FilaPosters("En tu biblioteca", enTu.map { it.breve() }, alAbrir, sub = { b -> enTu.firstOrNull { it.idStremio == b.id }?.estado?.nombre.orEmpty() },
            imagen = { b -> enTu.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster })
        deTmdb?.let { l ->
            if (l.isEmpty()) Nota("Nada con «${q.trim()}».", Modifier.padding(top = 18.dp))
            val pelis = l.filter { it.tipo == "movie" }; val series = l.filter { it.tipo == "series" }
            if (pelis.isNotEmpty()) FilaPosters("Películas", pelis, alAbrir)
            if (series.isNotEmpty()) FilaPosters("Series", series, alAbrir)
        }
        resultados?.takeIf { deTmdb == null }?.let { r ->
            if (r.isEmpty()) Nota("Nada en tus addons con «${q.trim()}».", Modifier.padding(top = 18.dp))
            r.forEach { (a, c, l) -> FilaPosters("${tiposNombre(c.tipo)} · ${a.nombre}", l, alAbrir) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

