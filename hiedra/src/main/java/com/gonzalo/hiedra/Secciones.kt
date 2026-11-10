package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
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
import kotlin.math.roundToInt

/*
 * Bastet entera en una pantalla, como Seshat: la cabecera de tinta (BUSCAR · + AÑADIR · fecha · ⚙ y «BASTET.»), una sola fila de
 * pestañas (Portada, Jugando, Pendientes, Pasados, Abandonados, Sagas, Sistemas) y las páginas, que se pasan con la pestaña o con el dedo.
 * Dentro de cada estado siguen los menús pequeños de Bastet (PC · Emulación · Android · Web): un toque filtra, otro lo quita.
 */

/**
 * Los órdenes que tienen sentido en cada sección (y solo esos):
 *  - Jugando: última partida, más jugados, nombre (el «añadido» de algo que ya juegas no dice nada).
 *  - Pendientes: último añadido, más cortos (lo que suele durar la historia), nombre (sin partidas ni horas: aún no los has empezado).
 *  - Pasados: cuándo lo pasaste, más jugados, nombre.
 *  - Abandonados: última partida, más jugados (hasta dónde llegaste), nombre.
 */
fun ordenesDe(e: Estado): List<Orden> = when (e) {
    Estado.JUGANDO -> listOf(Orden.Recientes, Orden.Jugados, Orden.Nombre)
    Estado.PENDIENTE -> listOf(Orden.Anadidos, Orden.Duracion, Orden.Nombre)
    Estado.PASADO -> listOf(Orden.Pasados, Orden.Jugados, Orden.Nombre)
    Estado.ABANDONADO -> listOf(Orden.Recientes, Orden.Jugados, Orden.Nombre)
}

/** El orden con el que empieza cada sección: el primero de los suyos. */
fun ordenInicial(e: Estado): Orden = ordenesDe(e).first()

/** Ordena una lista de juegos. Los que no tienen el dato (duración) van al final. */
fun ordenar(o: Orden, l: List<Juego>): List<Juego> = when (o) {
    Orden.Recientes -> recientes(l)
    Orden.Nombre -> l.sortedBy { Catalogo.norm(it.titulo) }
    Orden.Jugados -> l.sortedWith(compareByDescending<Juego> { it.msJugados }.thenByDescending { it.veces })
    Orden.Anadidos -> l.sortedByDescending { it.anadido }
    Orden.Pasados -> l.sortedWith(compareByDescending<Juego> { it.finalizado }.thenByDescending { it.ultima })
    Orden.Duracion -> l.sortedWith(compareBy<Juego> { it.durHistoria <= 0 }.thenBy { it.durHistoria }.thenBy { Catalogo.norm(it.titulo) })
}

/** El orden que has elegido para cada sección: se recuerda y viaja con tu cuenta (`ajustes/hiedra_orden`, un campo por sección). */
object OrdenGuardado {
    val GRUPO: EspejoCuenta.Grupo = EspejoCuenta.Grupo("hiedra_orden", "hiedra_orden", { EspejoCuenta.Tipo.TEXTO })
    private fun prefs(c: Context) = EspejoCuenta.prefs(c, GRUPO)
    fun de(c: Context, e: Estado): Orden = ordenesDe(e).firstOrNull { it.name == prefs(c).getString(e.name, null) } ?: ordenInicial(e)
    fun pon(c: Context, e: Estado, o: Orden) { prefs(c).edit().putString(e.name, o.name).apply() }
}

/** Lo que dice debajo del título de un juego: de dónde es, sus horas y, si se pide, su estado. */
fun metaJuego(j: Juego, conEstado: Boolean = false): String = listOf(
    etiquetaDe(j),
    if (j.msJugados >= 60_000) tiempoDe(j.msJugados) else "",
    if (conEstado && j.estado != Estado.PENDIENTE) j.estado.nombre.lowercase() else "",
    if (j.enOtroDispositivo) "en otro dispositivo" else ""
).filter { it.isNotBlank() }.joinToString(" · ")

/** De dónde es un juego, en largo: «Game Boy Advance», «Android», «PC»… (con varios lanzadores, todos). */
fun plataformaDe(j: Juego): String = if (j.sinLanzador) j.plataforma.ifBlank { "Ficha" } else j.lanzadores.joinToString(" · ") { l ->
    when (l.tipo) { TipoLanzador.ROM -> Catalogo.sistema(l.sistema)?.nombre ?: l.sistema.uppercase(); TipoLanzador.APP -> "Android"; TipoLanzador.WEB -> "Navegador"; TipoLanzador.PC -> "PC" }
}.let { it.split(" · ").distinct().joinToString(" · ") }

/**
 * Une trozos con « · » sin dejar una palabra suelta al principio de una línea: en cada trozo, la última palabra va pegada a la anterior
 * («~28 H», «7 veces», «nov 2026» no se parten), y el punto medio, pegado al trozo de antes.
 */
fun unidos(trozos: List<String?>): String = trozos.filterNotNull().filter { it.isNotBlank() }.joinToString("\u00A0· ") { t ->
    val i = t.lastIndexOf(' '); if (i > 0) t.substring(0, i) + '\u00A0' + t.substring(i + 1) else t
}

/** Una fecha que no se parte al final de una línea («9 nov 2026» siempre junta). */
fun fechaJunta(ms: Long): String = Formato.fecha(ms).replace(' ', '\u00A0')

/** Horas en corto para las listas: «12 H 30», «45 MIN», «—». */
fun horasCortas(ms: Long): String { val m = ms / 60_000L; return when { m <= 0 -> "SIN JUGAR"; m < 60 -> "$m MIN"; else -> "${m / 60} H ${"%02d".format(m % 60)}" } }

/**
 * Un juego en una lista, con la portada grande (84×126 dp de serie, como en Seshat): encima del título, pequeño y en el acento, su número en la
 * lista (01, 02…) o dentro de su saga; el título en grotesca, de dónde es en versalitas y, abajo, cómo va según su estado: jugando, sus horas
 * (y la barra de la historia si se sabe lo que dura); pendiente, lo que suele durar; pasado, cuándo lo pasaste; abandonado, hasta dónde llegaste.
 * Un toque abre la ficha; «JUGAR →», a la derecha de abajo, juega.
 */
@Composable
fun FilaJuego(j: Juego, alAbrir: (Juego) -> Unit, alJugar: ((Juego) -> Unit)? = null, modifier: Modifier = Modifier, portada: Dp = 84.dp, numero: Int = 0, conEstado: Boolean = false, extra: String = "", orden: Int = 0) {
    val alto = portada * 1.5f
    // (la fila mide lo que la portada o, si el texto no cabe —letra grande—, lo que el texto; así lo de abajo va abajo)
    Row(modifier.fillMaxWidth().tocable { alAbrir(j) }.testTag("fila-" + j.id).padding(top = 14.dp, bottom = 14.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(portada).height(alto)) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = (portada.value * 0.4f).toInt()) }
        Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = alto).padding(start = 14.dp)) {
            val num = when { orden > 0 -> "%02d".format(orden); numero > 0 -> "Nº $numero"; else -> "" }
            if (num.isNotEmpty()) Text(num, color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text(j.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = if (portada >= 70.dp) 19.sp else 17.sp, lineHeight = if (portada >= 70.dp) 22.sp else 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (num.isEmpty()) 0.dp else 2.dp))
            Text(plataformaDe(j), color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            val meta = listOf(if (conEstado) j.estado.nombre.lowercase() else "", if (j.enOtroDispositivo) "en otro dispositivo" else "", extra).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            PieFila(j, if (alJugar != null && !j.sinLanzador) ({ alJugar(j) }) else null)
        }
    }
}

/** Lo de abajo de una fila: cómo va el juego según su estado y, a la derecha, «JUGAR →». */
@Composable
private fun PieFila(j: Juego, alJugar: (() -> Unit)?) {
    val izq = when (j.estado) {
        Estado.JUGANDO, Estado.ABANDONADO -> unidos(listOf(horasCortas(j.msJugados), j.avance?.let { "${(it * 100).toInt()} %" }, if (j.ultima > 0) haceCuanto(j.ultima).uppercase() else null))
        Estado.PENDIENTE -> if (j.durHistoria > 0) "SUELE DURAR ${tiempoDe(j.durHistoria * 60_000L).uppercase()}" else if (j.anadido > 0) "AÑADIDO EL ${fechaJunta(j.anadido).uppercase()}" else "PENDIENTE"
        Estado.PASADO -> unidos(listOf(if (j.finalizado > 0) "PASADO EL ${fechaJunta(j.finalizado).uppercase()}" else "PASADO", if (j.msJugados >= 60_000) horasCortas(j.msJugados) else null))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(izq, color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, modifier = Modifier.weight(1f))
        if (alJugar != null) Text(if (j.enPc && !j.abrible) "PC →" else "JUGAR →", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.tocable(onClick = alJugar).padding(start = 10.dp, top = 6.dp, bottom = 2.dp))
    }
    if ((j.estado == Estado.JUGANDO || j.estado == Estado.ABANDONADO) && j.avance != null)
        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).background(Line)) { Box(Modifier.fillMaxWidth(j.avance!!.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** El abanico de portadas de una saga (hasta tres, una sobre otra), del tamaño de las portadas de las listas. */
@Composable
fun AbanicoSaga(juegos: List<Juego>, modifier: Modifier = Modifier, ancho: Dp = 84.dp) {
    val v = juegos.take(3)
    val paso = ancho * 0.19f
    Box(modifier.width(ancho + paso * (v.size - 1).coerceAtLeast(0)).height(ancho * 1.5f)) {
        v.asReversed().forEachIndexed { i, j ->
            val k = v.size - 1 - i
            Box(Modifier.padding(start = paso * k).width(ancho).height(ancho * 1.5f)) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = (ancho.value * 0.4f).toInt()) }
        }
    }
}

/** «3 juegos · 1 pasado · jugando»: lo que dice una saga. */
fun resumenSaga(todos: List<Juego>): String {
    val pasados = todos.count { it.estado == Estado.PASADO }
    return listOfNotNull(
        "${todos.size} ${if (todos.size == 1) "juego" else "juegos"}",
        if (pasados > 0) "$pasados ${if (pasados == 1) "pasado" else "pasados"}" else null,
        if (todos.any { it.estado == Estado.JUGANDO }) "jugando" else null
    ).joinToString("\u00A0· ")
}

/** Una saga en una lista: el abanico, su nombre y cuántos hay; un toque la abre y enseña sus juegos; «Ver la saga →» va a su página. */
@Composable
fun FilaSaga(f: FilaLista.DeSaga, abierta: Boolean, alAlternar: () -> Unit, alAbrirSaga: (Saga) -> Unit, alAbrirJuego: (Juego) -> Unit, alJugar: ((Juego) -> Unit)? = null) {
    val todos = remember(f.saga, Biblioteca.juegos.size) { Sagas.juegos(f.saga) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().tocable(onClick = alAlternar).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AbanicoSaga(f.miembros)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(f.saga.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(("Saga\u00A0· " + resumenSaga(todos)), color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierta) "−" else "+", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        }
        if (abierta) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp)) {
                f.miembros.forEach { j ->
                    Filete(1.dp, Line)
                    FilaJuego(j, alAbrirJuego, alJugar, portada = 64.dp, numero = f.saga.juegos.indexOf(j.id) + 1)
                }
                EnlaceRevista("Ver la saga →", acento = true) { alAbrirSaga(f.saga) }
            }
        }
    }
    Filete(1.dp, Line)
}

/** Las filas (juegos sueltos y sagas) de una lista de juegos, con las sagas que estén abiertas. */
private fun LazyListScope.filas(
    juegos: List<Juego>, abiertas: Set<String>, alternar: (String) -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit, conEstado: Boolean = false
) {
    val filas = Sagas.agrupar(juegos)
    itemsIndexed(filas, key = { _, it -> when (it) { is FilaLista.Suelto -> "j:" + it.juego.id; is FilaLista.DeSaga -> "s:" + it.saga.id } }) { i, f ->
        when (f) {
            is FilaLista.Suelto -> { FilaJuego(f.juego, alAbrir, alJugar, conEstado = conEstado, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty(), orden = i + 1); Filete(1.dp, Line) }
            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in abiertas, { alternar(f.saga.id) }, alAbrirSaga, alAbrir, alJugar)
        }
    }
}

private fun vacioDe(e: Estado, m: Menu?) = when {
    m != null -> "Nada de ${m.nombre} aquí. Toca «${m.nombre.uppercase()}» otra vez para ver toda la sección."
    e == Estado.JUGANDO -> "No estás jugando a nada. Abre un juego o márcalo «Jugando» en su ficha y saldrá aquí."
    e == Estado.PENDIENTE -> "No tienes nada pendiente: lo que añadas sin empezar saldrá aquí."
    e == Estado.PASADO -> "Aún no has pasado ninguno. Cuando lo termines, márcalo en su ficha y saldrá aquí."
    else -> "No has abandonado ninguno. Si dejas un juego, márcalo como abandonado desde su ficha."
}

/** Los menús pequeños (PC · Emulación · Android · Web) con sus cuentas, en bloques de la dirección C: el activo, relleno de tinta; tocarlo otra vez lo quita. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenusPequenos(sel: Menu?, cuentas: Map<Menu, Int>, onSel: (Menu?) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Menu.entries.forEach { m ->
            val on = m == sel
            val n = cuentas[m] ?: 0
            Row(Modifier.tocable(fuera = 2.dp) { onSel(if (on) null else m) }.background(if (on) Charcoal else Color.Transparent).border(2.dp, Charcoal)
                .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.nombre, color = if (on) Paper else if (n == 0) Muted else Charcoal, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(" $n", color = if (on) Paper else Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** El nombre de una sección en la página (y en su rejilla). */
private fun nombreDe(e: Estado) = when (e) { Estado.JUGANDO -> "Jugando"; Estado.PENDIENTE -> "Pendientes"; Estado.PASADO -> "Pasados"; Estado.ABANDONADO -> "Abandonados" }

/** Una página de una sección: su rejilla de cifras, los menús pequeños, el orden y sus juegos (con las sagas juntas). */
@Composable
private fun PaginaEstante(estado: Estado, todos: List<Juego>, menu: Menu?, alMenu: (Menu?) -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit) {
    val ctx = LocalContext.current
    var orden by remember(estado, EspejoCuenta.version) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    var sistema by rememberSaveable(estado) { mutableStateOf<String?>(null) }
    val delEstado = remember(todos, estado) { todos.filter { it.estado == estado } }
    val cuentas = remember(delEstado) { Menu.entries.associateWith { m -> delEstado.count { m in it.menus } } }
    val sistemas = remember(delEstado) { delEstado.filter { it.rom != null }.groupBy { it.sistema }.entries.sortedByDescending { it.value.size } }
    val juegos = remember(delEstado, menu, orden, sistema) { ordenar(orden, delEstado.filter { (menu == null || menu in it.menus) && (sistema == null || it.sistema == sistema) }) }
    var abiertas by rememberSaveable(estado) { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo(nombreDe(estado), Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
                // Dirección C: tres bloques de cifras (cuántos, horas, plataformas)
                val horas = juegos.sumOf { it.msJugados } / 3_600_000L
                val plataformas = juegos.flatMap { j -> if (j.sinLanzador) listOf(j.plataforma.ifBlank { "?" }) else j.lanzadores.map { etiquetaDe(it) } }.distinct().size
                RejillaCifras(listOf(
                    BloqueCifra("${juegos.size}", if (juegos.size == 1) "juego" else "juegos", AcentoBloque, SobreBloque),
                    BloqueCifra("${horas}H", "jugadas", Paper, Charcoal),
                    BloqueCifra("$plataformas", if (plataformas == 1) "plataforma" else "plataformas", Bloque2, Color.White),
                ), Modifier.padding(top = 8.dp))
                MenusPequenos(menu, cuentas) { sistema = null; alMenu(it) }
                // Cómo se ordena esta sección (se recuerda); solo los órdenes que dicen algo aquí
                val ordenes = ordenesDe(estado)
                FiltrosRevista(ordenes.map { it.nombre }, ordenes.indexOf(orden).coerceAtLeast(0), Modifier.padding(top = 2.dp)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden) }
                // En Emulación, también por sistema
                if (menu == Menu.EMULACION && sistemas.size > 1) FiltrosRevista(listOf("Todos") + sistemas.map { (Catalogo.sistema(it.key)?.corto ?: it.key) + " " + it.value.size }, sistemas.indexOfFirst { it.key == sistema } + 1) { i ->
                    sistema = if (i == 0) null else sistemas[i - 1].key
                }
                Filete(1.dp, Line)
            }
        }
        if (juegos.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (todos.isEmpty()) "Todavía no hay nada aquí." else vacioDe(estado, menu), color = Muted, fontFamily = Body, fontSize = 18.sp, lineHeight = 24.sp)
                if (todos.isEmpty()) BotonC("Añadir juegos", Modifier.padding(top = 12.dp), onClick = alAnadir)
            }
        }
        filas(juegos, abiertas, { id -> abiertas = if (id in abiertas) abiertas - id else abiertas + id }, alAbrir, alJugar, alAbrirSaga)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Todas las sagas, por nombre: cada una con su abanico y sus juegos desplegables; desde aquí se crea una nueva (y se abre para meterle juegos). */
@Composable
private fun PaginaSagas(todos: List<Juego>, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val sagas = Sagas.lista.toList()
    val filas = remember(sagas, todos) { sagas.sortedBy { Catalogo.norm(it.nombre) }.map { FilaLista.DeSaga(it, Sagas.juegos(it, todos)) } }
    var abiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nueva by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Antetitulo("Sagas · ${sagas.size}", Modifier.weight(1f), color = Acento, conCuadro = false)
                    EnlaceRevista("+ Nueva saga", acento = true) { nueva = "" }
                }
                Filete(1.dp, Line)
            }
        }
        if (filas.isEmpty()) item {
            Text("Aún no hay sagas. Créala aquí o desde la ficha de un juego («La saga»), y luego ve metiendo sus juegos en orden.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 30.dp))
        }
        items(filas, key = { "s:" + it.saga.id }) { f ->
            FilaSaga(f, f.saga.id in abiertas, { abiertas = if (f.saga.id in abiertas) abiertas - f.saga.id else abiertas + f.saga.id }, alAbrirSaga, alAbrir, alJugar)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    nueva?.let { n ->
        DialogoNuevaSaga(n, alCambiar = { nueva = it }, alCerrar = { nueva = null }) { nombre -> nueva = null; alAbrirSaga(Sagas.crear(nombre)) }
    }
}

/** Lo que sale al buscar: todos los juegos que cuadran, estén en la sección que estén. */
@Composable
private fun Resultados(busca: String, todos: List<Juego>, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit) {
    val q = Catalogo.norm(busca)
    val l = remember(busca, todos) {
        todos.filter { Catalogo.norm(it.titulo).contains(q) || Catalogo.norm(etiquetaDe(it)).contains(q) || Sagas.de(it)?.let { s -> Catalogo.norm(s.nombre).contains(q) } == true }
            .sortedWith(compareByDescending<Juego> { it.ultima }.thenByDescending { it.anadido })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(1.dp, Line) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Body, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        itemsIndexed(l, key = { _, it -> it.id }) { i, x -> FilaJuego(x, alAbrir, alJugar, conEstado = true, orden = i + 1); Filete(1.dp, Line) }
    }
}

/** Las páginas de Bastet, en el orden de las pestañas: la portada y luego las secciones (los índices son los de [Destino]). */
val PAGINAS = listOf(Destino.Portada, Destino.Jugando, Destino.Pendiente, Destino.Pasado, Destino.Abandonado, Destino.Sagas, Destino.Sistemas)
private val NOMBRES_PAGINAS = listOf("Portada", "Jugando", "Pendientes", "Pasados", "Abandonados", "Sagas", "Sistemas")

/** La página (índice de [PAGINAS]) de un destino; -1 si no es una página (Ajustes, Tu PC). */
fun paginaDe(d: Destino): Int = PAGINAS.indexOf(d)

/**
 * Bastet entera en una pantalla: la cabecera fija de tinta (BUSCAR · + AÑADIR · fecha · ⚙ y «BASTET.»), una sola fila de pestañas y las
 * páginas, que se pasan tocando la pestaña o deslizando el dedo. [pagina] es la que toca (índice de [PAGINAS]); [alPagina] avisa cuando
 * cambia. Al bajar en una página, el nombre grande se recoge (encoge con su hueco, sin cortarse ni dejar banda vacía) y quedan la fila
 * pequeña y las pestañas. Con mando, las pestañas y los enlaces se enfocan como todo lo demás.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantPrincipal(
    pagina: Int, alPagina: (Int) -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: (TipoLanzador?) -> Unit,
    alAjustes: (Int) -> Unit, alIr: (Destino) -> Unit, buscandoInicial: Boolean = false, alBuscado: () -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = pagina.coerceIn(0, PAGINAS.size - 1)) { PAGINAS.size }
    val ambito = rememberCoroutineScope()
    // Lo que pide fuera (Atrás, un widget, otra app) mueve las páginas; lo que mueves tú con el dedo, se cuenta fuera
    LaunchedEffect(pagina) { val p = pagina.coerceIn(0, PAGINAS.size - 1); if (pager.currentPage != p) pager.animateScrollToPage(p) }
    LaunchedEffect(pager.settledPage) { alPagina(pager.settledPage) }
    var buscando by rememberSaveable { mutableStateOf(buscandoInicial) }
    LaunchedEffect(buscandoInicial) { if (buscandoInicial) { buscando = true; alBuscado() } }
    var busca by rememberSaveable { mutableStateOf("") }
    // El menú pequeño elegido se mantiene al pasar de una sección a otra
    var menu by rememberSaveable { mutableStateOf<Menu?>(null) }
    val todos = Biblioteca.juegos.toList()
    val cuentas: List<Int?> = buildList {
        add(null)
        listOf(Estado.JUGANDO, Estado.PENDIENTE, Estado.PASADO, Estado.ABANDONADO).forEach { e -> add(todos.count { it.estado == e }) }
        add(Sagas.lista.size); add(null)
    }
    val foco = remember { FocusRequester() }
    LaunchedEffect(buscando) { if (buscando) runCatching { foco.requestFocus() } }
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
    // En la tablet, las páginas de listas no pasan de 960 dp de ancho (como una revista abierta: las filas no se estiran de lado a lado)
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    val plegada = altoNombre > 0 && recogido >= altoNombre - 1
    Column(Modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        // ---- La cabecera de la dirección C: un bloque de tinta con la fila pequeña y «BASTET.» en grotesca enorme
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = if (tablet) 40.dp else 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (plegada) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                // Plegada del todo: el nombre, pequeño, a la izquierda de la fila (y ningún hueco negro debajo)
                if (plegada) NombreConPunto("Bastet", 22.sp, Paper, Modifier.padding(end = 14.dp))
                EnlaceRevista("Buscar", color = if (buscando) Acento else Paper.copy(alpha = 0.75f)) { buscando = !buscando; if (!buscando) busca = "" }
                EnlaceRevista("+ Añadir", Modifier.padding(start = 14.dp), color = acentoSobreTinta()) { alAnadir(null) }
                Spacer(Modifier.weight(1f))
                if (!plegada) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = Paper.copy(alpha = 0.75f), fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, "Ajustes", tint = Paper.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp).tocable { alAjustes(0) }.padding(6.dp).size(20.dp))
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
                NombreConPunto("Bastet", if (ancho >= 700) 104.sp else 64.sp, Paper, Modifier.padding(top = 2.dp, bottom = 12.dp))
            }
        }
        PestanasPaginas(NOMBRES_PAGINAS, pager.currentPage, cuentas) { i -> buscando = false; busca = ""; ambito.launch { pager.animateScrollToPage(i) } }
        if (buscando) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CampoBusquedaRevista(busca, "título, sistema o saga", foco) { busca = it } }
        if (busca.isNotBlank()) Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = 960.dp)) { Resultados(busca, todos, alAbrir, alJugar) } }
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { PAGINAS[it].name }) { i ->
            when (PAGINAS[i]) {
                Destino.Portada -> PaginaPortada(alAbrir = alAbrir, alJugar = alJugar, alAnadir = alAnadir, alIr = { d -> val p = paginaDe(d); if (p >= 0) ambito.launch { pager.animateScrollToPage(p) } else alIr(d) },
                    alAbrirSaga = alAbrirSaga, alAjustes = alAjustes)
                Destino.Sistemas -> PantSistemas(alVolver = {}, alAnadirCarpeta = { alAnadir(null) }, enPagina = true)
                else -> Box(Modifier.fillMaxSize().padding(horizontal = if (tablet) 40.dp else 20.dp).navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 960.dp)) {
                        when (PAGINAS[i]) {
                            Destino.Jugando -> PaginaEstante(Estado.JUGANDO, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga) { alAnadir(null) }
                            Destino.Pendiente -> PaginaEstante(Estado.PENDIENTE, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga) { alAnadir(null) }
                            Destino.Pasado -> PaginaEstante(Estado.PASADO, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga) { alAnadir(null) }
                            Destino.Abandonado -> PaginaEstante(Estado.ABANDONADO, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga) { alAnadir(null) }
                            else -> PaginaSagas(todos, alAbrir, alJugar, alAbrirSaga)
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
    // La fila se desplaza sola solo si la pestaña elegida no se ve entera (y entonces la centra): así no se corta la de al lado
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel] != null, anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < scroll.value || x + w > scroll.value + anchoFila) scroll.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, scroll.maxValue))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                // Un degradado en los bordes cuando hay más pestañas por un lado o por otro (para que no parezcan cortadas)
                drawContent()
                val f = 28.dp.toPx()
                if (scroll.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (scroll.value < scroll.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll).height(IntrinsicSize.Max), verticalAlignment = Alignment.CenterVertically) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.fillMaxHeight().onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x).roundToInt() to c.size.width }.tocable(fuera = 0.dp) { onSel(i) }
                    .background(if (on) Acento else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Dirección C: la elegida, en un bloque del acento
                    Text(n, color = if (on) SobreAcento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    cuentas.getOrNull(i)?.let { c -> Text(" $c", color = if (on) SobreAcento else Acento, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
        Filete(1.dp, Line)
    }
}
