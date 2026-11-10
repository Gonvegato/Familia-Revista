package com.gonzalo.mostaza

import kotlinx.coroutines.flow.first
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Hathor entera en una pantalla, como Seshat y Bastet: la cabecera de tinta (BUSCAR · EXPLORAR · fecha · ⚙ y «HATHOR.»), una sola fila
 * de pestañas (Portada, Viendo, Pendientes, Vistas, Abandonadas, Próximos) y las páginas, que se pasan con la pestaña o con el dedo.
 * Dentro de cada estado, el menú pequeño de siempre (Películas · Series): un toque filtra, otro lo quita. Explorar, Buscar, Addons y
 * Ajustes van encima, como la ficha.
 */

/**
 * Los órdenes que tienen sentido en cada página (y solo esos):
 *  - Viendo: lo último que viste, nombre (el «añadido» o el año de algo que ya ves no dicen nada).
 *  - Pendientes: lo último que añadiste, año, nombre.
 *  - Vistas: cuándo la terminaste, año, nombre.
 *  - Abandonadas: lo último que viste, nombre.
 */
fun ordenesDe(e: Estado): List<OrdenGuardado.Orden> = when (e) {
    Estado.VIENDO, Estado.ABANDONADO -> listOf(OrdenGuardado.Orden.RECIENTES, OrdenGuardado.Orden.TITULO)
    Estado.PENDIENTE -> listOf(OrdenGuardado.Orden.ANADIDOS, OrdenGuardado.Orden.ANO, OrdenGuardado.Orden.TITULO)
    Estado.VISTO -> listOf(OrdenGuardado.Orden.RECIENTES, OrdenGuardado.Orden.ANO, OrdenGuardado.Orden.TITULO)
}

/** El nombre de un orden en una página («Recientes» en Vistas es «Terminadas»; en Viendo, «Última vez»). */
fun nombreOrden(e: Estado, o: OrdenGuardado.Orden): String = when {
    o == OrdenGuardado.Orden.RECIENTES && e == Estado.VISTO -> "Terminadas"
    o == OrdenGuardado.Orden.RECIENTES -> "Última vez"
    else -> o.nombre
}

/** El orden guardado de una página, si es de los suyos; si no, el primero de los suyos. */
fun ordenDePagina(c: android.content.Context, e: Estado): OrdenGuardado.Orden = OrdenGuardado.de(c, e).takeIf { it in ordenesDe(e) } ?: ordenesDe(e).first()

/** Cuándo viste algo por última vez: el título o, si es más nuevo, su último vídeo. */
fun ultimaVez(t: Titulo): Long = maxOf(t.ultima, Biblioteca.ultimo(t.ref)?.ultima ?: 0L)

/** Lo de una página, en su orden: en Viendo y Abandonadas, «Recientes» es lo último que viste (por sus vídeos); en Vistas, cuándo la terminaste. */
fun ordenarPagina(e: Estado, o: OrdenGuardado.Orden, l: List<Titulo>): List<Titulo> = when (o) {
    OrdenGuardado.Orden.RECIENTES -> if (e == Estado.VISTO) l.sortedByDescending { maxOf(it.fin, it.ultima) } else l.map { it to ultimaVez(it) }.sortedByDescending { it.second }.map { it.first }
    OrdenGuardado.Orden.ANADIDOS -> l.sortedByDescending { it.anadido }
    OrdenGuardado.Orden.TITULO -> l.sortedBy { it.nombre.lowercase() }
    OrdenGuardado.Orden.ANO -> l.sortedByDescending { it.ano.take(4) }
}

/** «12 H 30», «45 MIN», «—» (lo visto, en corto, para las listas). */
fun horasCortas(ms: Long): String { val m = ms / 60_000L; return when { m <= 0 -> "SIN VER"; m < 60 -> "$m MIN"; else -> "${m / 60} H ${"%02d".format(m % 60)}" } }

/** Una fecha que no se parte al final de una línea («9 nov 2026» siempre junta). */
fun fechaJunta(ms: Long): String = Formato.fecha(ms).replace(' ', ' ')

/** «hace 3 días», «hoy», «ayer». */
fun haceCuanto(ms: Long, hoy: LocalDate = LocalDate.now()): String {
    val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val n = java.time.temporal.ChronoUnit.DAYS.between(d, hoy)
    return when { n <= 0 -> "hoy"; n == 1L -> "ayer"; n < 7 -> "hace $n días"; n < 14 -> "hace una semana"; n < 60 -> "hace ${n / 7} semanas"; else -> "el " + Formato.fecha(ms) }
}

/** «película · 2024» o «serie · T2 E5» (por dónde vas, si la estás viendo). */
fun lineaTipo(t: Titulo): String {
    val p = Biblioteca.ultimo(t.ref)
    return if (t.esSerie) listOfNotNull("serie", if (p != null && p.temporada > 0 && t.estado != Estado.PENDIENTE) "T${p.temporada} E${p.episodio}" else t.ano.ifBlank { null }).joinToString(" · ")
    else listOfNotNull("película", t.ano.ifBlank { null }).joinToString(" · ")
}

/**
 * Una película o una serie en una lista, con su portada grande (84×126 dp, como en Seshat y Bastet): encima del título, pequeño y en el
 * acento, su número en la lista (01, 02…); el título en grotesca, «película · 2024» o «serie · T2 E5» en versalitas y, abajo, cómo va según
 * su estado. Un toque abre la ficha; «SEGUIR →» (en lo que ves) la abre y sigue donde lo dejaste.
 */
@Composable
fun FilaTitulo(t: Titulo, alAbrir: (Titulo) -> Unit, alVer: ((Titulo) -> Unit)? = null, modifier: Modifier = Modifier, portada: Dp = 84.dp, orden: Int = 0, conEstado: Boolean = false) {
    val alto = portada * 1.5f
    Row(modifier.fillMaxWidth().tocable { alAbrir(t) }.testTag("fila-" + t.ref).padding(top = 14.dp, bottom = 14.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(portada).height(alto)) { PortadaTitulo(t, Modifier.matchParentSize()) }
        Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = alto).padding(start = 14.dp)) {
            if (orden > 0) Text("%02d".format(orden), color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text(t.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (orden > 0) 2.dp else 0.dp))
            Text((lineaTipo(t) + if (conEstado) " · " + t.estado.nombre.lowercase() else ""), color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            PieFila(t, if (alVer != null && (t.estado == Estado.VIENDO || t.estado == Estado.ABANDONADO)) ({ alVer(t) }) else null)
        }
    }
}

/** Lo de abajo de una fila: cómo va según su estado (y, en lo que ves, la barra de lo que llevas y «SEGUIR →»). */
@Composable
private fun PieFila(t: Titulo, alVer: (() -> Unit)?) {
    val p = Biblioteca.ultimo(t.ref)
    val ms = Biblioteca.msVistos(t.ref)
    val izq = when (t.estado) {
        // (el capítulo ya va arriba, «SERIE · T2 E5»: aquí solo lo que queda, para que quepa en una línea junto a «SEGUIR →»)
        Estado.VIENDO -> if (p == null) "SIN EMPEZAR" else porDonde(p, t.esSerie).split(" · ").filterNot { it.matches(Regex("T\\d+|E\\d+")) }.joinToString(" · ").ifBlank { porDonde(p, t.esSerie) }.uppercase()
        Estado.ABANDONADO -> (if (p != null) "LA DEJASTE EN " + porDonde(p, t.esSerie).substringBefore(" · quedan").uppercase() else "ABANDONADA") + (if (ms >= 60_000L) " · " + horasCortas(ms) else "")
        Estado.PENDIENTE -> listOfNotNull(if (!t.esSerie && t.minutos > 0) "DURA ${duracion(t.minutos).uppercase()}" else null, if (t.anadido > 0) "AÑADIDA EL ${fechaJunta(t.anadido).uppercase()}" else null).joinToString(" · ").ifBlank { "PENDIENTE" }
        Estado.VISTO -> listOfNotNull(if (t.fin > 0) "TERMINADA EL ${fechaJunta(t.fin).uppercase()}" else "VISTA", if (ms >= 60_000L) horasCortas(ms) else null).joinToString(" · ")
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(izq, color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, modifier = Modifier.weight(1f))
        if (alVer != null) Text("SEGUIR →", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.tocable(onClick = alVer).padding(start = 10.dp, top = 6.dp, bottom = 2.dp))
    }
    if ((t.estado == Estado.VIENDO || t.estado == Estado.ABANDONADO) && p != null && !p.visto && p.fraccion > 0f)
        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).background(Line)) { Box(Modifier.fillMaxWidth(p.fraccion).fillMaxHeight().background(Acento)) }
}

/** El menú pequeño de siempre (Películas · Series) con sus cuentas, en bloques de la dirección C: el activo, relleno de tinta; tocarlo otra vez lo quita. */
@Composable
fun MenusPequenos(sel: OrdenGuardado.Filtro, cuentas: Map<OrdenGuardado.Filtro, Int>, onSel: (OrdenGuardado.Filtro) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(OrdenGuardado.Filtro.PELICULAS, OrdenGuardado.Filtro.SERIES).forEach { m ->
            val on = m == sel
            val n = cuentas[m] ?: 0
            Row(Modifier.tocable(fuera = 2.dp) { onSel(if (on) OrdenGuardado.Filtro.TODO else m) }.background(if (on) Charcoal else Color.Transparent).border(2.dp, Charcoal)
                .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.nombre, color = if (on) Paper else if (n == 0) Muted else Charcoal, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(" $n", color = if (on) Paper else Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** El nombre de una sección en su página. */
private fun nombreDe(e: Estado) = when (e) { Estado.VIENDO -> "Viendo"; Estado.PENDIENTE -> "Pendientes"; Estado.VISTO -> "Vistas"; Estado.ABANDONADO -> "Abandonadas" }

/** Una página de un estado: su rejilla de cifras, el menú pequeño, el orden y sus películas y series. */
@Composable
private fun PaginaEstado(estado: Estado, todos: List<Titulo>, alAbrir: (Titulo) -> Unit, alVer: (Titulo) -> Unit, alExplorar: () -> Unit) {
    val ctx = LocalContext.current
    EspejoCuenta.version
    var orden by remember(estado, EspejoCuenta.version) { mutableStateOf(ordenDePagina(ctx, estado)) }
    var filtro by remember(estado, EspejoCuenta.version) { mutableStateOf(OrdenGuardado.filtro(ctx, estado)) }
    val delEstado = remember(todos, estado) { todos.filter { it.estado == estado } }
    val cuentas = mapOf(OrdenGuardado.Filtro.PELICULAS to delEstado.count { !it.esSerie }, OrdenGuardado.Filtro.SERIES to delEstado.count { it.esSerie })
    val lista = remember(delEstado, filtro, orden, Biblioteca.version) {
        ordenarPagina(estado, orden, delEstado.filter { when (filtro) { OrdenGuardado.Filtro.PELICULAS -> !it.esSerie; OrdenGuardado.Filtro.SERIES -> it.esSerie; else -> true } })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo(nombreDe(estado), Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
                // Dirección C: tres bloques de cifras (películas, series, horas vistas)
                val horas = lista.sumOf { Biblioteca.msVistos(it.ref) } / 3_600_000L
                val pelis = lista.count { !it.esSerie }; val series = lista.count { it.esSerie }
                RejillaCifras(listOf(
                    BloqueCifra("$pelis", if (pelis == 1) "película" else "películas", AcentoBloque, SobreBloque),
                    BloqueCifra("$series", if (series == 1) "serie" else "series", Paper, Charcoal),
                    BloqueCifra("${horas}H", "vistas", Bloque2, Color.White),
                ), Modifier.padding(top = 8.dp))
                MenusPequenos(filtro, cuentas) { filtro = it; OrdenGuardado.ponFiltro(ctx, estado, it) }
                val ordenes = ordenesDe(estado)
                FiltrosRevista(ordenes.map { nombreOrden(estado, it) }, ordenes.indexOf(orden).coerceAtLeast(0), Modifier.padding(top = 2.dp)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden) }
                Filete(1.dp, Line)
            }
        }
        if (lista.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (delEstado.isNotEmpty()) "Nada de ${filtro.nombre.lowercase()} aquí. Toca «${filtro.nombre.uppercase()}» otra vez para verlo todo." else seccionVacia(estado),
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
                if (todos.isEmpty()) BotonC("Explorar", Modifier.padding(top = 12.dp), onClick = alExplorar)
            }
        }
        itemsIndexed(lista, key = { _, it -> it.ref }) { i, t -> FilaTitulo(t, alAbrir, alVer, orden = i + 1); Filete(1.dp, Line) }
        item { Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) }
    }
}

/** Próximos capítulos: los episodios que vienen de tus series (los de TMDB, como el widget), por fecha. */
@Composable
private fun PaginaProximos(alAbrir: (Titulo) -> Unit) {
    val ctx = LocalContext.current
    Biblioteca.version
    val l = remember(Biblioteca.version) { Proximos.lista(ctx) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Próximos capítulos", Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
                val semana = l.count { x -> x.dia?.let { !it.isAfter(LocalDate.now().plusDays(6)) } == true }
                RejillaCifras(listOf(
                    BloqueCifra("${l.size}", if (l.size == 1) "capítulo" else "capítulos", AcentoBloque, SobreBloque),
                    BloqueCifra("$semana", "esta semana", Paper, Charcoal),
                    BloqueCifra("${l.map { it.ref }.distinct().size}", "series", Bloque2, Color.White),
                ), Modifier.padding(top = 8.dp))
                Filete(2.dp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (l.isEmpty()) item {
            Text(if (Tmdb.activo) "No hay capítulos anunciados de tus series. Cuando TMDB anuncie uno, saldrá aquí con su fecha."
                else "Con tu clave de TMDB (Ajustes → Catálogo), aquí salen los capítulos que vienen de las series que ves, con su fecha.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 30.dp))
        }
        itemsIndexed(l, key = { _, x -> x.ref + x.temporada + "x" + x.episodio }) { i, x ->
            val t = Biblioteca.titulos.firstOrNull { it.ref == x.ref }
            Row(Modifier.fillMaxWidth().tocable(enabled = t != null) { t?.let(alAbrir) }.padding(vertical = 14.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(64.dp).height(96.dp)) { if (t != null) PortadaTitulo(t, Modifier.matchParentSize()) else TapaDeTela(x.serie, Modifier.matchParentSize()) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("%02d".format(i + 1), color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    Text(x.serie, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Proximos.episodio(x), color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    x.dia?.let { d -> Text(Proximos.cuando(d), color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            Filete(1.dp, Line)
        }
        item { Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) }
    }
}

/** Las páginas de Hathor, en el orden de las pestañas: la portada, los estados y los próximos capítulos. */
val PAGINAS = listOf(Destino.Portada, Destino.Viendo, Destino.Pendiente, Destino.Visto, Destino.Abandonado, Destino.Proximos)
private val NOMBRES_PAGINAS = listOf("Portada", "Viendo", "Pendientes", "Vistas", "Abandonadas", "Próximos")

/** La página (índice de [PAGINAS]) de un destino; -1 si no es una página (Explorar, Buscar, Addons, Ajustes: van encima). */
fun paginaDe(d: Destino): Int = PAGINAS.indexOf(d)

/**
 * Hathor entera en una pantalla: la cabecera fija de tinta (BUSCAR · EXPLORAR · fecha · ⚙ y «HATHOR.»), una sola fila de pestañas y las
 * páginas, que se pasan tocando la pestaña o deslizando el dedo. [pagina] es la que toca (índice de [PAGINAS]); [alPagina] avisa cuando
 * cambia. Al bajar en una página, el nombre grande se recoge (encoge con su hueco, sin cortarse ni dejar banda vacía) y quedan la fila
 * pequeña y las pestañas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantPrincipal(pagina: Int, alPagina: (Int) -> Unit, alAbrir: (Titulo) -> Unit, alAbrirBreve: (MetaBreve) -> Unit, alVer: (Titulo) -> Unit, alIr: (Destino) -> Unit, alReto: () -> Unit = {}) {
    val pager = rememberPagerState(initialPage = pagina.coerceIn(0, PAGINAS.size - 1)) { PAGINAS.size }
    val ambito = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Lo que pide fuera (Atrás, un widget, otra app) mueve las páginas; lo que mueves tú con el dedo, se cuenta fuera
    LaunchedEffect(pagina) { val p = pagina.coerceIn(0, PAGINAS.size - 1); if (pager.currentPage != p) pager.animateScrollToPage(p) }
    LaunchedEffect(pager.settledPage) { alPagina(pager.settledPage) }
    Biblioteca.version
    val todos = Biblioteca.titulos.toList()
    val nProximos = remember(Biblioteca.version) { Proximos.lista(ctx).size }
    val cuentas: List<Int?> = buildList {
        add(null)
        listOf(Estado.VIENDO, Estado.PENDIENTE, Estado.VISTO, Estado.ABANDONADO).forEach { e -> add(todos.count { it.estado == e }) }
        add(nProximos)
    }
    // El nombre grande se recoge al bajar (y vuelve al subir): lo que la página aún no ha movido, lo mueve la cabecera
    var altoNombre by remember { mutableIntStateOf(0) }
    var recogido by remember { mutableFloatStateOf(0f) }
    val conexion = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || altoNombre == 0) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, -(recogido - antes))
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, antes - recogido)
            }
        }
    }
    val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    val plegada = altoNombre > 0 && recogido >= altoNombre - 1
    Column(Modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        // ---- La cabecera de la dirección C: un bloque de tinta con la fila pequeña y «HATHOR.» en grotesca enorme
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = if (tablet) 40.dp else 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (plegada) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                // Plegada del todo: el nombre, pequeño, a la izquierda de la fila (y ningún hueco negro debajo)
                if (plegada) NombreConPunto("Hathor", 22.sp, Paper, Modifier.padding(end = 14.dp))
                EnlaceRevista("Buscar", color = Paper.copy(alpha = 0.75f)) { alIr(Destino.Buscar) }
                EnlaceRevista("Explorar", Modifier.padding(start = 14.dp), color = acentoSobreTinta()) { alIr(Destino.Explorar) }
                Spacer(Modifier.weight(1f))
                if (!plegada) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = Paper.copy(alpha = 0.75f), fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, "Ajustes", tint = Paper.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp).tocable { alIr(Destino.Ajustes) }.padding(6.dp).size(20.dp))
            }
            // El nombre: su alto se mide una vez y se va quitando según se recoge; encoge con su hueco desde la esquina de arriba a la izquierda
            Box(Modifier.fillMaxWidth().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                if (altoNombre != p.height) altoNombre = p.height
                val h = (p.height - recogido.toInt()).coerceAtLeast(0)
                val escala = if (p.height > 0) h.toFloat() / p.height else 1f
                layout(p.width, h) {
                    if (h > 0) p.placeWithLayer(0, 0) {
                        scaleX = escala; scaleY = escala
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                }
            }) {
                NombreConPunto("Hathor", if (ancho >= 700) 104.sp else 64.sp, Paper, Modifier.padding(top = 2.dp, bottom = 12.dp))
            }
        }
        PestanasPaginas(NOMBRES_PAGINAS, pager.currentPage, cuentas) { i -> ambito.launch { pager.animateScrollToPage(i) } }
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { PAGINAS[it].name }) { i ->
            when (PAGINAS[i]) {
                Destino.Portada -> PaginaPortada(alAbrir = alAbrir, alAbrirBreve = alAbrirBreve, alVer = alVer, alReto = alReto,
                    alIr = { d -> val p = paginaDe(d); if (p >= 0) ambito.launch { pager.animateScrollToPage(p) } else alIr(d) })
                else -> Box(Modifier.fillMaxSize().padding(horizontal = if (tablet) 40.dp else 20.dp), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 960.dp)) {
                        when (PAGINAS[i]) {
                            Destino.Viendo -> PaginaEstado(Estado.VIENDO, todos, alAbrir, alVer) { alIr(Destino.Explorar) }
                            Destino.Pendiente -> PaginaEstado(Estado.PENDIENTE, todos, alAbrir, alVer) { alIr(Destino.Explorar) }
                            Destino.Visto -> PaginaEstado(Estado.VISTO, todos, alAbrir, alVer) { alIr(Destino.Explorar) }
                            Destino.Abandonado -> PaginaEstado(Estado.ABANDONADO, todos, alAbrir, alVer) { alIr(Destino.Explorar) }
                            else -> PaginaProximos(alAbrir)
                        }
                    }
                }
            }
        }
    }
}

/** El acento sobre la tinta de la cabecera (que es oscura de día y clara de noche): el elegido, ajustado para leerse ahí. */
fun acentoSobreTinta(): Color = acentoLegible(AcentoBloque, Charcoal, Paper)

/** Las pestañas de las páginas (dirección C): la elegida, en un bloque del acento; su número al lado. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int?>, onSel: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    var anchoFila by remember { mutableIntStateOf(0) }
    var relleno by remember { mutableIntStateOf(0) }   // hueco al final para que la fila pueda empezar en el borde de una pestaña
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        if (anchoFila <= 0 || sitios[sel] == null) return@LaunchedEffect
        val obj = desplazamientoPestanas(sitios, sel, anchoFila, scroll.value)
        val contenido = scroll.maxValue + anchoFila - relleno
        relleno = (obj + anchoFila - contenido).coerceAtLeast(0)
        kotlinx.coroutines.withTimeoutOrNull(600) { androidx.compose.runtime.snapshotFlow { scroll.maxValue }.first { it >= obj } }
        if (obj != scroll.value) scroll.animateScrollTo(obj)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                // Un degradado en los bordes cuando hay más pestañas por un lado o por otro (para que no parezcan cortadas)
                drawContent()
                val f = 14.dp.toPx()
                if (scroll.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (scroll.value < scroll.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll).height(IntrinsicSize.Max), verticalAlignment = Alignment.CenterVertically) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.fillMaxHeight().onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x).roundToInt() to c.size.width }.tocable(fuera = 0.dp) { onSel(i) }
                    .background(if (on) AcentoBloque else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, color = if (on) SobreBloque else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    cuentas.getOrNull(i)?.let { c -> Text(" $c", color = if (on) SobreBloque else Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
                }
            }
            if (relleno > 0) Spacer(Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { relleno.toDp() }))
        }
        Filete(1.dp, Line)
    }
}
