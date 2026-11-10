package com.gonzalo.mostaza

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La cabecera de una sección de la sala: su antetítulo, su nombre en grande y, al lado, cuántas cosas hay. */
@Composable
private fun CabeceraSala(ante: String, titulo: String, cuenta: String = "") {
    val tinta = LocalTinta.current
    Column {
        AnteSala(ante, tam = 16.sp, color = tinta.acento)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(titulo, color = tinta.tinta, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 64.sp, maxLines = 1)
            if (cuenta.isNotBlank()) {
                Spacer(Modifier.width(22.dp))
                Text(cuenta, color = tinta.gris, fontFamily = Body, fontSize = 26.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

/** Una fila de pastillas que se desliza (los géneros, que son muchos): con la cruceta se recorre y vuelve a la elegida. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PastillasSala(etiqueta: String, ops: List<String>, sel: Int, prefijo: String, alElegir: (Int) -> Unit) {
    val tinta = LocalTinta.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (etiqueta.isNotBlank()) Text(etiqueta, color = tinta.gris, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(200f) }) {
            LazyRow(Modifier.weight(1f).focusGroup(), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(ops) { i, n -> ChipSala(n, "$prefijo:$i", i == sel) { alElegir(i) } }
            }
        }
    }
}

/** La rejilla de pósteres de una sección (con su cabecera arriba, que ocupa toda la fila). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RejillaSala(cabecera: @Composable () -> Unit, contenido: LazyGridScope.() -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(120f) }) {
        LazyVerticalGrid(GridCells.Adaptive(190.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = MARGEN_H, end = MARGEN_H, top = 26.dp, bottom = 50.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.fillMaxWidth()) { cabecera() } }
            contenido()
        }
    }
}

// ---------------------------------------------------------------- Biblioteca: las cuatro secciones

/** Tu biblioteca en la sala: Viendo · Pendiente · Visto · Abandonado, con su filtro y su orden (los mismos que en el móvil). */
@Composable
fun BibliotecaSala(est: EstadoSala, ctl: ControlSala) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    Biblioteca.version
    val todos = Biblioteca.titulos.toList()
    val estado = est.estadoBib
    var orden by remember(estado) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    var filtro by remember(estado) { mutableStateOf(OrdenGuardado.filtro(ctx, estado)) }
    val lista = deSeccion(todos, estado, orden, filtro)
    SideEffect { est.primero["sec:BIBLIOTECA"] = lista.firstOrNull()?.let { "b:${it.ref}" } ?: "be:${estado.name}" }
    RejillaSala({
        CabeceraSala("Tu biblioteca", estado.nombre, "${lista.size} ${if (lista.size == 1) "título" else "títulos"}")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Estado.entries.forEach { e -> ChipSala("${e.nombre} ${todos.count { it.estado == e }}", "be:${e.name}", e == estado) { est.estadoBib = e } }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PastillasSala("Ver", OrdenGuardado.Filtro.entries.map { it.nombre }, filtro.ordinal, "bf") { i -> filtro = OrdenGuardado.Filtro.entries[i]; OrdenGuardado.ponFiltro(ctx, estado, filtro) } }
            Box(Modifier.weight(1.2f)) { PastillasSala("Orden", OrdenGuardado.Orden.entries.map { it.nombre }, orden.ordinal, "bo") { i -> orden = OrdenGuardado.Orden.entries[i]; OrdenGuardado.pon(ctx, estado, orden) } }
        }
        if (lista.isEmpty()) NotaSala(seccionVacia(estado), Modifier.padding(top = 24.dp).widthIn(max = 1300.dp))
    }) {
        items(lista, key = { it.ref }) { t ->
            val p = Biblioteca.ultimo(t.ref)
            CartaSala(t.breve(), "b:${t.ref}", est, ctl, if (estado == Estado.VIENDO) seguirTexto(t) else listOf(tipoNombre(t.tipo), t.ano).filter { it.isNotBlank() }.joinToString(" · "),
                if (estado == Estado.VIENDO && p != null && !p.visto) p.fraccion else null, t.imagen)
        }
    }
}

// ---------------------------------------------------------------- Explorar

/** «Explorar» en la sala: el catálogo de TMDB (si tienes la clave) o los catálogos de tus addons, con sus filtros, en rejilla y por páginas. */
@Composable
fun ExplorarSala(est: EstadoSala, ctl: ControlSala) {
    Tmdb.version
    if (Tmdb.activo) ExplorarTmdbSala(est, ctl) else ExplorarAddonsSala(est, ctl)
}

@Composable
private fun ExplorarTmdbSala(est: EstadoSala, ctl: ControlSala) {
    val ambito = rememberCoroutineScope()
    var tv by remember { mutableStateOf(false) }
    var genero by remember { mutableLongStateOf(0L) }
    var plataforma by remember { mutableLongStateOf(0L) }
    var orden by remember { mutableStateOf(Tmdb.Orden.POPULARES) }
    val generos by produceState<Map<Long, String>>(emptyMap(), tv, Tmdb.version) { value = withContext(Dispatchers.IO) { Tmdb.generos(tv) } }
    val plataformas by produceState<List<Plataforma>>(emptyList(), Tmdb.version) { value = withContext(Dispatchers.IO) { Tmdb.plataformas().take(12) } }
    var cosas by remember { mutableStateOf<List<MetaBreve>?>(null) }
    var pagina by remember { mutableIntStateOf(1) }
    var cargandoMas by remember { mutableStateOf(false) }
    var fin by remember { mutableStateOf(false) }
    LaunchedEffect(tv, genero, plataforma, orden, Tmdb.version) {
        cosas = null; pagina = 1; fin = false
        val l = withContext(Dispatchers.IO) { Tmdb.descubre(tv, genero, plataforma, orden, 1) }
        cosas = l; fin = l.isEmpty()
    }
    SideEffect { est.primero["sec:EXPLORAR"] = cosas?.firstOrNull()?.let { "x:${it.tipo}:${it.id}" } ?: "xk:0" }
    RejillaSala({
        CabeceraSala("El catálogo", "Explorar")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChipSala("Películas", "xk:0", !tv) { tv = false; genero = 0L }
            ChipSala("Series", "xk:1", tv) { tv = true; genero = 0L }
        }
        PastillasSala("Orden", Tmdb.Orden.entries.map { it.nombre }, orden.ordinal, "xo") { i -> orden = Tmdb.Orden.entries[i] }
        if (plataformas.isNotEmpty()) {
            val ops = listOf(0L to "Todas") + plataformas.map { it.id to it.nombre }
            PastillasSala("Plataforma", ops.map { it.second }, ops.indexOfFirst { it.first == plataforma }.coerceAtLeast(0), "xp") { i -> plataforma = ops[i].first }
        }
        if (generos.isNotEmpty()) {
            val ops = listOf(0L to "Todos") + generos.entries.sortedBy { it.value }.map { it.key to it.value }
            PastillasSala("Género", ops.map { it.second }, ops.indexOfFirst { it.first == genero }.coerceAtLeast(0), "xg") { i -> genero = ops[i].first }
        }
        if (cosas == null) NotaSala("Cargando…", Modifier.padding(top = 20.dp))
        else if (cosas!!.isEmpty()) NotaSala("No hay nada con esto (o TMDB no responde ahora mismo).", Modifier.padding(top = 20.dp))
    }) {
        items(cosas.orEmpty(), key = { it.tipo + it.id }) { b -> CartaSala(b, "x:${b.tipo}:${b.id}", est, ctl) }
        if (cosas?.isNotEmpty() == true && !fin) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(top = 10.dp)) {
                BotonSala(if (cargandoMas) "Cargando…" else "Más", "x:mas", icono = "↓") {
                    if (cargandoMas) return@BotonSala
                    cargandoMas = true
                    ambito.launch {
                        val ya = cosas.orEmpty()
                        val mas = withContext(Dispatchers.IO) { Tmdb.descubre(tv, genero, plataforma, orden, pagina + 1) }.filter { n -> ya.none { it.id == n.id } }
                        pagina++; cosas = ya + mas; fin = mas.isEmpty(); cargandoMas = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorarAddonsSala(est: EstadoSala, ctl: ControlSala) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val addons by produceState<List<Addon>?>(null, Addons.version) { value = runCatching { Addons.conManifiesto() }.getOrDefault(emptyList()) }
    // (los mismos que en «Explorar» del móvil: los de solo buscar, no)
    val pares = addons.orEmpty().flatMap { a -> a.manifiesto.catalogos.filter { !it.soloBusqueda && !it.necesitaOtraCosa }.map { a to it } }
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
    SideEffect { est.primero["sec:EXPLORAR"] = cosas?.firstOrNull()?.let { "x:${it.tipo}:${it.id}" } ?: "xt:0" }
    RejillaSala({
        CabeceraSala("Tus addons", "Explorar")
        Spacer(Modifier.height(14.dp))
        when {
            addons == null -> NotaSala("Leyendo tus addons…")
            tipos.isEmpty() -> NotaSala("Ninguno de tus addons tiene catálogos. Cinemeta (el oficial) sí: lo puedes poner en Menú → Addons.", Modifier.widthIn(max = 1300.dp))
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    tipos.forEachIndexed { i, x -> ChipSala(tiposNombre(x), "xt:$i", x == tipo) { tipo = x; catClave = ""; genero = "" } }
                }
                val varios = delTipo.map { it.first.url }.distinct().size > 1
                PastillasSala("Catálogo", delTipo.map { (a, c) -> if (varios) "${c.nombre} · ${a.nombre}" else c.nombre }, delTipo.indexOf(sel).coerceAtLeast(0), "xc") { i ->
                    catClave = "${delTipo[i].first.manifiesto.id}|${delTipo[i].second.id}"; genero = ""
                }
                if (generos.isNotEmpty()) {
                    val ops = (if (sel?.second?.generoObligatorio == true) emptyList() else listOf("Todos")) + generos
                    PastillasSala(if (sel?.second?.porAno == true) "Año" else "Género", ops, ops.indexOf(genero.ifBlank { "Todos" }).coerceAtLeast(0), "xg") { i ->
                        genero = if (ops[i] == "Todos" && sel?.second?.generoObligatorio != true) "" else ops[i]
                    }
                }
                if (cosas == null) NotaSala("Cargando…", Modifier.padding(top = 20.dp))
                else if (cosas!!.isEmpty()) NotaSala("Este catálogo no trae nada ahora mismo.", Modifier.padding(top = 20.dp))
            }
        }
    }) {
        items(cosas.orEmpty(), key = { it.tipo + it.id }) { b -> CartaSala(b, "x:${b.tipo}:${b.id}", est, ctl) }
        if (cosas?.isNotEmpty() == true && !fin) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(top = 10.dp)) {
                BotonSala(if (cargandoMas) "Cargando…" else "Más", "x:mas", icono = "↓") {
                    val s = sel ?: return@BotonSala
                    if (cargandoMas) return@BotonSala
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
}

// ---------------------------------------------------------------- Buscar

/**
 * Buscar en la sala: el campo se escribe con el teclado de la tele (el que da el sistema, que en Android TV trae también el micrófono) o,
 * si la tele sabe, hablando («Hablar»: el reconocimiento de voz del sistema). Lo que va saliendo, en filas: lo de tu biblioteca y, con
 * TMDB, películas y series (si no, lo que encuentra cada addon que busca).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuscarSala(est: EstadoSala, ctl: ControlSala) {
    val ctx = LocalContext.current
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val ambito = rememberCoroutineScope()
    val teclado = LocalSoftwareKeyboardController.current
    var editando by remember { mutableStateOf(false) }
    val campo = remember { FocusRequester() }
    var buscando by remember { mutableStateOf(false) }
    var resultados by remember { mutableStateOf<List<Triple<Addon, Catalogo, List<MetaBreve>>>?>(null) }
    var deTmdb by remember { mutableStateOf<List<MetaBreve>?>(null) }
    var buscado by remember { mutableStateOf("") }
    val q = est.consulta
    Biblioteca.version
    val enTu = Biblioteca.titulos.toList().filter { buscado.length >= 2 && it.nombre.contains(buscado, ignoreCase = true) }
    fun busca() {
        val t = est.consulta.trim(); if (t.length < 2 || buscando) return
        buscando = true; buscado = t
        ambito.launch {
            if (Tmdb.activo) { deTmdb = runCatching { Tmdb.busca(t) }.getOrDefault(emptyList()); resultados = emptyList() }
            else { deTmdb = null; resultados = runCatching { Addons.busca(t) }.getOrDefault(emptyList()) }
            buscando = false
        }
    }
    fun dejaDeEscribir() { editando = false; teclado?.hide() }
    // La voz: la del sistema (si la tele o la tablet la tienen)
    val voz = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES").putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quieres ver?")
    }
    val hayVoz = remember { runCatching { voz.resolveActivity(ctx.packageManager) != null }.getOrDefault(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { est.consulta = it.take(80); busca() }
    }
    LaunchedEffect(editando) { if (editando) { runCatching { campo.requestFocus() }; teclado?.show() } }
    val filas = buildList {
        if (enTu.isNotEmpty()) add(FilaSala("q:tu", "En tu biblioteca", enTu.map { it.breve() }, sub = { b -> enTu.firstOrNull { it.idStremio == b.id }?.estado?.nombre.orEmpty() },
            imagen = { b -> enTu.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster }))
        deTmdb?.let { l ->
            l.filter { it.tipo == "movie" }.takeIf { it.isNotEmpty() }?.let { add(FilaSala("q:pelis", "Películas", it)) }
            l.filter { it.tipo == "series" }.takeIf { it.isNotEmpty() }?.let { add(FilaSala("q:series", "Series", it)) }
        }
        if (deTmdb == null) resultados.orEmpty().forEach { (a, c, l) -> add(FilaSala("q:${a.manifiesto.id}:${c.tipo}:${c.id}", "${tiposNombre(c.tipo)} · ${a.nombre}", l)) }
    }
    SideEffect { est.primero["sec:BUSCAR"] = "q:campo" }
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(80f, inicio = 40f) }) {
        Column(Modifier.fillMaxSize().verticalScroll(est.pagina(est.vista)).padding(top = 26.dp, bottom = 50.dp)) {
            Column(Modifier.padding(horizontal = MARGEN_H)) {
                CabeceraSala(if (Tmdb.activo) "Películas y series" else "En tus addons", "Buscar")
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.width(1000.dp).height(92.dp).enfocable("q:campo", radio = 12.dp, crece = 1.02f) { editando = true }.clip(formaSala(12.dp))
                        .background(tinta.tarjeta).padding(horizontal = 28.dp), contentAlignment = Alignment.CenterStart) {
                        if (q.isEmpty() && !editando) Text("Una película o una serie…", color = tinta.gris, fontFamily = Body, fontSize = 34.sp, maxLines = 1)
                        BasicTextField(q, { est.consulta = it.take(80) }, singleLine = true,
                            textStyle = TextStyle(color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp),
                            cursorBrush = SolidColor(tinta.acento), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { dejaDeEscribir(); busca(); foco.pedido = "q:buscar" }, onDone = { dejaDeEscribir(); busca(); foco.pedido = "q:buscar" }),
                            modifier = Modifier.fillMaxWidth().focusRequester(campo).focusProperties { canFocus = editando }.onFocusChanged { f -> if (!f.hasFocus && editando) editando = false })
                    }
                    if (hayVoz) BotonSala("Hablar", "q:voz", icono = "🎙") { try { lanzador.launch(voz) } catch (e: ActivityNotFoundException) { est.avisa("Esta tele no tiene reconocimiento de voz.") } }
                    BotonSala(if (buscando) "Buscando…" else "Buscar", "q:buscar", principal = true) { dejaDeEscribir(); busca() }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (editando) "Escribe con el teclado de la tele y pulsa «Buscar» en él." else "OK en el campo para escribir" + (if (hayVoz) ", o «Hablar» para decirlo." else "."),
                    color = tinta.gris, fontFamily = Body, fontSize = 16.sp)
            }
            filas.forEach { f -> FilaPostersSala(f, est, ctl) }
            if (!buscando && buscado.isNotBlank() && filas.isEmpty()) NotaSala("Nada con «$buscado».", Modifier.padding(horizontal = MARGEN_H, vertical = 30.dp))
        }
    }
}
