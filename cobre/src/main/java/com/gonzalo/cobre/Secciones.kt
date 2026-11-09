package com.gonzalo.cobre

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.iptv.futbol.Clasificacion
import com.gonzalo.iptv.futbol.Competiciones
import com.gonzalo.iptv.futbol.Equipo
import com.gonzalo.iptv.futbol.Hora
import com.gonzalo.iptv.futbol.Orden
import com.gonzalo.iptv.futbol.Partido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * Nut 0.8.0: la app entera en una pantalla, como Seshat, Bastet y Hathor. La cabecera de tinta («NUT.»), una fila de pestañas
 * (PORTADA · HOY · COMPETICIONES · TUS EQUIPOS · IPTV) y las páginas, que se pasan tocando la pestaña o deslizando. Las fichas (un
 * partido, un equipo, una competición, una película…) se abren encima.
 */

/** Las páginas de Nut, en el orden de las pestañas. */
val PAGINAS = listOf(Destino.Portada, Destino.Hoy, Destino.Competiciones, Destino.Futbol, Destino.Tele)
private val NOMBRES_PAGINAS = listOf("Portada", "Hoy", "Competiciones", "Tus equipos", "IPTV")

/** La página (índice de [PAGINAS]) de un destino; -1 si no es una página (va encima). */
fun paginaDe(d: Destino): Int = PAGINAS.indexOf(d)

// ---------------------------------------------------------------- la forma (V/E/D) y el puesto

/** La racha de un equipo: sus últimos resultados (V, E, D) y su puesto en la tabla. */
object FormaEquipo {
    /** El resultado de un partido terminado para [equipo]: 'V', 'E' o 'D' (null si no lo jugó, no ha terminado o no tiene goles). */
    fun resultado(p: Partido, equipo: String): Char? {
        if (!p.estado.terminado || !p.juega(equipo)) return null
        val gl = p.golesLocal ?: return null; val gv = p.golesVisitante ?: return null
        val (a, b) = if (p.local.id == equipo) gl to gv else gv to gl
        if (a == b && p.penaltis.isNotBlank()) {
            // (empate decidido en los penaltis: «4-3»; cuenta como empate en la forma, como en las tablas)
            return 'E'
        }
        return when { a > b -> 'V'; a < b -> 'D'; else -> 'E' }
    }

    /** Los [n] últimos resultados, del más antiguo (izquierda) al último (derecha). */
    fun de(partidos: Collection<Partido>, equipo: String, n: Int = 5): List<Char> =
        Orden.jugados(partidos, equipo).mapNotNull { resultado(it, equipo) }.take(n).reversed()

    /** El puesto de un equipo en una clasificación (la primera tabla en la que salga), o null. */
    fun puesto(c: Clasificacion, equipo: String): Int? = c.tablas.firstNotNullOfOrNull { (_, f) -> f.firstOrNull { it.equipo.id == equipo }?.puesto }

    val VICTORIA = Color(0xFF3C8D5A)
    val EMPATE = Color(0xFF6F6860)
    val DERROTA = Color(0xFFB8433A)
    fun color(r: Char): Color = when (r) { 'V' -> VICTORIA; 'D' -> DERROTA; else -> EMPATE }
}

/** Los cinco últimos en bloques de color (verde, gris, rojo) con su letra. */
@Composable
fun BloquesForma(forma: List<Char>, modifier: Modifier = Modifier, grande: Boolean = false) {
    val lado = if (grande) 34.dp else 26.dp
    Row(modifier.semantics { contentDescription = "Últimos: " + forma.joinToString(", ") { when (it) { 'V' -> "victoria"; 'D' -> "derrota"; else -> "empate" } } },
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        forma.forEach { r ->
            Box(Modifier.size(lado).background(FormaEquipo.color(r)), contentAlignment = Alignment.Center) {
                Text("$r", color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = if (grande) 16.sp else 13.sp, maxLines = 1)
            }
        }
        repeat(5 - forma.size) { Box(Modifier.size(lado).background(Line)) }
    }
}

// ---------------------------------------------------------------- la pantalla principal

/**
 * Nut entera en una pantalla: la cabecera de tinta (BUSCAR CANAL · + EQUIPOS · fecha · ⚙ y «NUT.»), una fila de pestañas y las páginas.
 * Al bajar en una página, el nombre grande se recoge (encoge con su hueco, sin cortarse ni dejar banda vacía).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantPrincipal(pagina: Int, alPagina: (Int) -> Unit, alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val pager = rememberPagerState(initialPage = pagina.coerceIn(0, PAGINAS.size - 1)) { PAGINAS.size }
    val ambito = rememberCoroutineScope()
    val ctx = LocalContext.current
    LaunchedEffect(pagina) { val p = pagina.coerceIn(0, PAGINAS.size - 1); if (pager.currentPage != p) pager.animateScrollToPage(p) }
    LaunchedEffect(pager.settledPage) { alPagina(pager.settledPage) }
    EspejoCuenta.version
    val nEquipos = AjustesFutbol.equipos(ctx).size
    val cuentas: List<Int?> = listOf(null, null, null, nEquipos.takeIf { it > 0 }, null)
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
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val ancho = minOf(cfg.screenWidthDp, 1080)
    val tablet = cfg.screenWidthDp >= 700
    // (en la tele, más pequeña: lo que importa tiene que verse en la primera pantalla)
    val bajita = enTele() || cfg.screenHeightDp < 600
    val plegada = altoNombre > 0 && recogido >= altoNombre - 1
    val irA: (Destino) -> Unit = { d -> val p = paginaDe(d); if (p >= 0) ambito.launch { pager.animateScrollToPage(p) } else alIr(d) }
    Column(Modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = if (tablet) 40.dp else 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (plegada) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (plegada) NombreConPunto("Nut", 22.sp, Paper, Modifier.padding(end = 14.dp))
                EnlaceRevista("Buscar canal", Modifier.focoInicial("principal"), color = Paper.copy(alpha = 0.75f)) { alIr(Destino.Buscar) }
                EnlaceRevista("+ Equipos", Modifier.padding(start = 14.dp), color = acentoSobreTinta()) { alIr(Destino.AjustesFutbol) }
                Spacer(Modifier.weight(1f))
                if (!plegada) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = Paper.copy(alpha = 0.75f), fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, "Ajustes", tint = Paper.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp).tocable { alIr(Destino.Ajustes) }.padding(6.dp).size(20.dp))
            }
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
                NombreConPunto("Nut", if (bajita) 56.sp else if (ancho >= 700) 104.sp else 72.sp, Paper, Modifier.padding(top = 2.dp, bottom = 12.dp))
            }
        }
        PestanasPaginas(NOMBRES_PAGINAS, pager.currentPage, cuentas) { i -> ambito.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { PAGINAS[it].name }) { i ->
            when (PAGINAS[i]) {
                Destino.Portada -> PaginaPortada(irA, alFicha)
                Destino.Hoy -> PantDia(alIr = irA, alFicha = alFicha, enPagina = true)
                Destino.Competiciones -> PantCompeticiones(alVolver = {}, alFicha = alFicha, enPagina = true)
                Destino.Futbol -> PantFutbol(alVolver = {}, alIr = irA, alFicha = alFicha, enPagina = true)
                else -> PantPortada(alIr = irA, alFicha = alFicha, enPagina = true)
            }
        }
    }
}

/** El acento sobre la tinta de la cabecera (o sobre otro [fondo]): el elegido, ajustado para leerse ahí. */
fun acentoSobreTinta(fondo: Color = Charcoal): Color = acentoLegible(AcentoBloque, fondo, if (fondo.luminance2() > 0.5f) Color.Black else if (fondo == Charcoal) Paper else Color.White)

/** Las pestañas de las páginas (dirección C): la elegida, en un bloque del acento. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int?>, onSel: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    var anchoFila by remember { mutableIntStateOf(0) }
    var relleno by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        if (anchoFila <= 0 || sitios[sel] == null) return@LaunchedEffect
        val obj = desplazamientoPestanas(sitios, sel, anchoFila, scroll.value)
        val contenido = scroll.maxValue + anchoFila - relleno
        relleno = (obj + anchoFila - contenido).coerceAtLeast(0)
        kotlinx.coroutines.withTimeoutOrNull(600) { snapshotFlow { scroll.maxValue }.first { it >= obj } }
        if (obj != scroll.value) scroll.animateScrollTo(obj)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
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
                    Text(n.uppercase(), color = if (on) SobreBloque else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
                    cuentas.getOrNull(i)?.let { c -> Text(" $c", color = if (on) SobreBloque else Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1) }
                }
            }
            if (relleno > 0) Spacer(Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { relleno.toDp() }))
        }
        Filete(2.dp)
    }
}

// ---------------------------------------------------------------- la portada

/** El título de un bloque de la portada: en grotesca, con su cifra o su enlace a la derecha. */
@Composable
private fun TituloBloque(t: String, modifier: Modifier = Modifier, derecha: String? = null, alDerecha: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 26.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 25.sp, maxLines = 2, modifier = Modifier.weight(1f))
        derecha?.let { d -> Text(d.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1,
            modifier = Modifier.padding(start = 10.dp, bottom = 3.dp).let { if (alDerecha != null) it.tocable(onClick = alDerecha) else it }) }
    }
    Filete(2.dp)
}

/**
 * La portada: el cartel de tu próximo partido (o el que se juega ahora, con el marcador y el minuto), las cifras, la racha de tus equipos,
 * lo que has visto esta semana, los partidos de hoy y tus equipos.
 */
@Composable
fun PaginaPortada(alIr: (Destino) -> Unit, alFicha: (Ficha) -> Unit) {
    val ctx = LocalContext.current
    EspejoCuenta.version; Futbol.version; TiempoVisto.version; ImagenesEquipo.version
    val eqs = AjustesFutbol.equipos(ctx)
    val ids = eqs.map { it.first.id }
    val todos = rememberPartidos()
    val ahora = System.currentTimeMillis()
    val cartel = todos?.let { Orden.tusPartidos(it, ids, ahora).firstOrNull() }
    val hoyL = rememberDia(Hora.diaDe(ahora))
    val m = margen()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        when {
            eqs.isEmpty() -> CartelSinEquipos(alIr)
            cartel != null -> CartelPartido(cartel, ids.toSet(), ahora) { alFicha(Ficha.DePartido(cartel)) }
            else -> CartelVacio(if (todos == null) "Mirando el calendario de tus equipos…" else "Tus equipos no tienen partidos a la vista (o no hay conexión con los datos del fútbol).")
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = m).widthIn(max = 960.dp)) {
            if (!Cuenta.vinculada) Nota("Sin cuenta todavía: entra en Ajustes → Cuenta (o en Jonsu, y Nut entra sola). Tus equipos, tus canales y lo que ves van con tu cuenta.", Modifier.padding(top = 16.dp))
            val sem = remember(TiempoVisto.version) { SemanaVista.de(TiempoVisto.dias(ctx)) }
            val enJuego = hoyL.orEmpty().count { it.estado.enDirecto }
            RejillaCifras(listOf(
                BloqueCifra(minutosCorto(sem.hoyMin), "Visto hoy", AcentoBloque, SobreBloque),
                BloqueCifra("$enJuego", "En juego", Paper, Charcoal) { alIr(Destino.Hoy) },
                BloqueCifra("${sem.racha}", "Días seguidos", Bloque2, Color.White)), Modifier.padding(top = 18.dp))
            // ---- La racha de tus equipos
            if (eqs.isNotEmpty()) {
                TituloBloque("La racha de tus equipos")
                eqs.forEach { (e, liga) -> FilaRacha(e, liga, todos) { alFicha(Ficha.DeEquipo(e, liga)) } }
            }
            // ---- Esta semana (lo que has visto en Nut)
            TituloBloque("Esta semana", derecha = "${minutosCorto(sem.totalMin)} · hoy ${minutosCorto(sem.hoyMin)}")
            SemanaTocable(sem)
            if (sem.totalMin == 0L) Text("Lo que veas en Nut (canales, partidos, películas) se suma aquí, día a día y en todos tus dispositivos.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
            // ---- Hoy: lo que se juega (lo que está en juego, primero)
            val mios = ids.toSet()
            val riv = AjustesFutbol.rivales(ctx).map { it.first.id }.toSet()
            val deHoy = hoyL?.let { l -> l.sortedWith(compareBy({ !it.estado.enDirecto }, { !(mios.any { m2 -> it.juega(m2) }) }, { it.estado.terminado }, { it.inicio })) }
            TituloBloque("Hoy", derecha = deHoy?.let { "${it.size} partidos →" }, alDerecha = { alIr(Destino.Hoy) })
            when {
                deHoy == null -> Nota("Mirando los partidos de hoy…", Modifier.padding(top = 10.dp))
                deHoy.isEmpty() -> Nota("Hoy no hay partidos en tus competiciones.", Modifier.padding(top = 10.dp))
                else -> deHoy.take(6).forEach { p -> FilaDia(p, mios, riv) { alFicha(Ficha.DePartido(p)) } }
            }
            // ---- Tus equipos, en grande
            TituloBloque("Tus equipos", derecha = "+ Elegir", alDerecha = { alIr(Destino.AjustesFutbol) })
            if (eqs.isEmpty()) Nota("Elige tus equipos y sus partidos saldrán los primeros, con sus canales.", Modifier.padding(top = 10.dp))
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                eqs.forEach { (e, liga) ->
                    Column(Modifier.width(96.dp).tocable { alFicha(Ficha.DeEquipo(e, liga)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        EscudoConPaspartu(e, 96.dp, sombra = 0.dp)
                        Text(e.corto, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PieFuente()
                Text("Nut · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
        }
    }
}

/** Un equipo en «La racha de tus equipos»: su escudo, su nombre, su puesto y sus cinco últimos. */
@Composable
private fun FilaRacha(e: Equipo, liga: String, todos: List<Partido>?, alAbrir: () -> Unit) {
    val ctx = LocalContext.current
    val suLiga = Competiciones.para(liga).first().slug
    val tabla = if (Competiciones.tieneTabla(suLiga)) rememberDato(suLiga, "tabla") { f -> Futbol.clasificacion(ctx, suLiga, f) } else null
    val puesto = tabla?.let { FormaEquipo.puesto(it, e.id) }
    val forma = todos?.let { FormaEquipo.de(it, e.id) }.orEmpty()
    Row(Modifier.fillMaxWidth().tocable(onClick = alAbrir).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EscudoEquipo(e, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(puesto?.let { "${it}º" }, Competiciones.de(suLiga).corto).joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
        }
        if (todos == null) Text("…", color = Muted, fontFamily = Body) else BloquesForma(forma)
    }
    Filete(1.dp, Line)
}

/** El alto del cartel: el de las hermanas, algo menos en pantallas bajas. */
@Composable
private fun altoCartel(): Dp = if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 700) 360.dp else 400.dp

/**
 * El cartel: tu próximo partido (o el que se juega AHORA, con el marcador y el minuto) sobre el fondo de tu equipo (o el color del club),
 * con los escudos enfrentados, cuándo, dónde y en qué canal; debajo, «VER EN TU CANAL →».
 */
@Composable
fun CartelPartido(p: Partido, mios: Set<String>, ahora: Long, alAbrir: () -> Unit) {
    val nuestro = if (p.local.id in mios) p.local else if (p.visitante.id in mios) p.visitante else p.local
    val canal = canalDe(p)
    Box(Modifier.fillMaxWidth().heightIn(min = altoCartel()).height(IntrinsicSize.Min).tocable(onClick = alAbrir)
        .semantics { contentDescription = "${p.local.nombre} contra ${p.visitante.nombre}" }) {
        FondoEquipo(nuestro, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(veloArriba()))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
        Column(Modifier.fillMaxWidth().padding(horizontal = margen()).padding(top = 18.dp, bottom = 20.dp)) {
            val blanco = Color.White.copy(alpha = 0.88f)
            EtiquetaBloque(if (p.estado.enDirecto) etiquetaPartido(p, ahora) else "Tu próximo partido")
            Text(p.competicion.nombre.uppercase(), color = blanco, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            Enfrentados(p, ahora, 104.dp)
            val cuando = if (p.estado.enDirecto) null else Hora.diaYHora(p.inicio, ahora)
            Text(listOfNotNull(cuando, p.estadio.ifBlank { null }).joinToString(" · "), color = blanco, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
            Text((if (canal != null) "En $canal" else "Sin canal elegido").uppercase(), color = if (canal != null) acentoSobreTinta(Color.Black) else blanco, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    BotonVerCanal(p)
}

/** Sin equipos: el cartel invita a elegirlos. */
@Composable
private fun CartelSinEquipos(alIr: (Destino) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().background(Bloque2).padding(horizontal = margen()).padding(top = 26.dp, bottom = 22.dp)) {
        EtiquetaBloque("Tu próximo partido")
        Text("Elige tus equipos", color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 40.sp, modifier = Modifier.padding(top = 16.dp))
        Text("Y aquí tendrás su próximo partido (o el que se juega ahora, con el marcador), con su canal a un toque, su racha y su clasificación.",
            color = Color.White.copy(alpha = 0.88f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 10.dp))
        EnlaceRevista("Seguir al FC Barcelona", Modifier.padding(top = 10.dp), color = acentoSobreTinta(Bloque2)) {
            AjustesFutbol.alterna(ctx, com.gonzalo.iptv.futbol.CanalesFutbol.SUGERIDO, Competiciones.LALIGA.slug)
        }
    }
    BotonC("Elegir equipos", fondo = AcentoBloque, letra = SobreBloque) { alIr(Destino.AjustesFutbol) }
}

@Composable
private fun CartelVacio(t: String) {
    Column(Modifier.fillMaxWidth().background(Bloque2).padding(horizontal = margen()).padding(top = 26.dp, bottom = 26.dp)) {
        EtiquetaBloque("Tu próximo partido")
        Text(t, color = Color.White.copy(alpha = 0.9f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 14.dp))
    }
}
