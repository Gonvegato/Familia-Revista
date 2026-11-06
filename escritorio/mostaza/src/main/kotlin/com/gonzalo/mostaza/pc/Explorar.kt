package com.gonzalo.mostaza.pc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «Explorar»: con TMDB, películas o series por orden, plataforma y género; sin TMDB, los catálogos de tus addons (como en el móvil). */
@Composable
fun PantExplorar(nav: Nav) { if (Tmdb.activo) PantExplorarTmdb(nav) else PantExplorarAddons(nav) }

@Composable
private fun PantExplorarTmdb(nav: Nav) {
    val ambito = rememberCoroutineScope()
    var tv by remember { mutableStateOf(false) }
    var genero by remember { mutableLongStateOf(0L) }
    var plataforma by remember { mutableLongStateOf(0L) }
    var orden by remember { mutableStateOf(Tmdb.Orden.POPULARES) }
    val generos by produceState<Map<Long, String>>(emptyMap(), tv) { value = withContext(Dispatchers.IO) { Tmdb.generos(tv) } }
    val plataformas by produceState<List<Plataforma>>(emptyList()) { value = withContext(Dispatchers.IO) { Tmdb.plataformas().take(12) } }
    var cosas by remember { mutableStateOf<List<MetaBreve>?>(null) }
    var pagina by remember { mutableIntStateOf(1) }
    var cargandoMas by remember { mutableStateOf(false) }
    var fin by remember { mutableStateOf(false) }
    LaunchedEffect(tv, genero, plataforma, orden) {
        cosas = null; pagina = 1; fin = false
        val l = withContext(Dispatchers.IO) { Tmdb.descubre(tv, genero, plataforma, orden, 1) }
        cosas = l; fin = l.isEmpty()
    }
    Rejilla({
        CabeceraPagina("El catálogo", "Explorar")
        PestanasRevista(listOf("Películas", "Series"), if (tv) 1 else 0) { i -> tv = i == 1; genero = 0L }
        Spacer(Modifier.height(6.dp))
        Etiqueta("Orden", Modifier.padding(top = 4.dp))
        FiltrosRevista(Tmdb.Orden.entries.map { it.nombre }, orden.ordinal) { i -> orden = Tmdb.Orden.entries[i] }
        if (plataformas.isNotEmpty()) {
            Etiqueta("Plataforma", Modifier.padding(top = 4.dp))
            val ops = listOf(0L to "Todas") + plataformas.map { it.id to it.nombre }
            FiltrosRevista(ops.map { it.second }, ops.indexOfFirst { it.first == plataforma }.coerceAtLeast(0)) { i -> plataforma = ops[i].first }
        }
        if (generos.isNotEmpty()) {
            Etiqueta("Género", Modifier.padding(top = 4.dp))
            val ops = listOf(0L to "Todos") + generos.entries.sortedBy { it.value }.map { it.key to it.value }
            FiltrosRevista(ops.map { it.second }, ops.indexOfFirst { it.first == genero }.coerceAtLeast(0)) { i -> genero = ops[i].first }
        }
        Filete(1.dp, Line)
        if (cosas == null) Nota("Cargando…", Modifier.padding(top = 14.dp))
        else if (cosas!!.isEmpty()) Nota("No hay nada con esto (o TMDB no responde ahora mismo).", Modifier.padding(top = 14.dp))
    }, cosas.orEmpty().map { b -> (b.tipo + b.id) to @Composable { TarjetaPoster("", b.poster, b.nombre, Dp.Unspecified, b.ano) { nav.abre(b) } } },
    pie = if (cosas?.isNotEmpty() == true && !fin) ({
        EnlaceRevista(if (cargandoMas) "Cargando…" else "Más →", acento = true) {
            if (cargandoMas) return@EnlaceRevista
            cargandoMas = true
            ambito.launch {
                val ya = cosas.orEmpty()
                val mas = withContext(Dispatchers.IO) { Tmdb.descubre(tv, genero, plataforma, orden, pagina + 1) }.filter { n -> ya.none { it.id == n.id } }
                pagina++; cosas = ya + mas; fin = mas.isEmpty(); cargandoMas = false
            }
        }
    }) else null)
}

/** «Explorar» sin TMDB: los catálogos de tus addons, por tipo, con sus filtros de género o de año. Lo último que eliges viaja con tu cuenta. */
@Composable
private fun PantExplorarAddons(nav: Nav) {
    val ambito = rememberCoroutineScope()
    val addons by produceState<List<Addon>?>(null, Addons.version) { value = withContext(Dispatchers.IO) { runCatching { Addons.conManifiesto() }.getOrDefault(emptyList()) } }
    val pares = addons.orEmpty().flatMap { a -> Catalogos.explorables(a).map { a to it } }
    val tipos = pares.map { it.second.tipo }.distinct().sortedBy { when (it) { "movie" -> 0; "series" -> 1; else -> 2 } }
    val (tipo0, cat0, gen0) = remember { AjustesPortada.explorar() }
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
        val s = sel ?: run { cosas = if (addons == null) null else emptyList(); return@LaunchedEffect }
        AjustesPortada.ponExplorar(tipo, "${s.first.manifiesto.id}|${s.second.id}", genero)
        val l = withContext(Dispatchers.IO) { Catalogos.lista(s.first, s.second, if (genero.isNotBlank()) listOf("genre" to genero) else emptyList()) }
        cosas = l; fin = l.size < 20 || !s.second.pagina
    }
    Rejilla({
        CabeceraPagina("Tus addons", "Explorar")
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
    }, cosas.orEmpty().map { b -> (b.tipo + b.id) to @Composable { TarjetaPoster("", b.poster, b.nombre, Dp.Unspecified, b.ano) { nav.abre(b) } } },
    pie = if (cosas?.isNotEmpty() == true && !fin) ({
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
    }) else null)
}

/** «Buscar»: en todo TMDB (con tu clave) o en los catálogos de tus addons que buscan; y en tu biblioteca. */
@Composable
fun PantBuscar(nav: Nav) {
    val ambito = rememberCoroutineScope()
    var q by remember { mutableStateOf(EstadoBuscar.q) }
    var buscando by remember { mutableStateOf(false) }
    val enTu = Biblioteca.titulos().filter { q.length >= 2 && it.nombre.contains(q.trim(), ignoreCase = true) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    fun busca() {
        val t = q.trim(); if (t.length < 2 || buscando) return
        buscando = true; EstadoBuscar.q = t
        ambito.launch {
            if (Tmdb.activo) { EstadoBuscar.tmdb = runCatching { Tmdb.busca(t) }.getOrDefault(emptyList()); EstadoBuscar.addons = null }
            else { EstadoBuscar.addons = runCatching { Addons.busca(t) }.getOrDefault(emptyList()); EstadoBuscar.tmdb = null }
            buscando = false
        }
    }
    Pagina {
        CabeceraPagina(if (Tmdb.activo) "Películas y series" else "En tus addons", "Buscar")
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Una película o una serie…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp),
                    cursorBrush = SolidColor(Acento), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { busca() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(foco).onPreviewKeyEvent { k ->
                        if (k.type == KeyEventType.KeyDown && (k.key == Key.Enter || k.key == Key.NumPadEnter)) { busca(); true } else false
                    })
            }
            EnlaceRevista(if (buscando) "Buscando…" else "Buscar", acento = true) { busca() }
        }
        Filete(2.dp)
        if (enTu.isNotEmpty()) FilaPosters("En tu biblioteca", enTu.map { it.breve() }, { nav.abre(it) }, sub = { b -> enTu.firstOrNull { it.idStremio == b.id }?.estado?.nombre.orEmpty() },
            portada = { b -> enTu.firstOrNull { it.idStremio == b.id }?.portada.orEmpty() })
        EstadoBuscar.tmdb?.let { l ->
            if (l.isEmpty()) Nota("Nada con «${EstadoBuscar.q}».", Modifier.padding(top = 18.dp))
            FilaPosters("Películas", l.filter { it.tipo == "movie" }, { nav.abre(it) })
            FilaPosters("Series", l.filter { it.tipo == "series" }, { nav.abre(it) })
        }
        EstadoBuscar.addons?.let { r ->
            if (r.isEmpty()) Nota("Nada en tus addons con «${EstadoBuscar.q}».", Modifier.padding(top = 18.dp))
            r.forEach { (a, c, l) -> FilaPosters("${tiposNombre(c.tipo)} · ${a.nombre}", l, { nav.abre(it) }) }
        }
    }
}

/** Lo último que buscaste (al volver de una ficha sigue ahí). */
object EstadoBuscar {
    var q by mutableStateOf("")
    var tmdb by mutableStateOf<List<MetaBreve>?>(null)
    var addons by mutableStateOf<List<Triple<Addon, Catalogo, List<MetaBreve>>>?>(null)
}
