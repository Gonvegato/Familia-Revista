package com.gonzalo.marfil

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout

/** Cómo se puede ordenar un estante. */
enum class OrdenLibros(val nombre: String) {
    RECIENTES("Última lectura"), AVANCE("Más avanzado"), ANADIDOS("Último añadido"), TERMINADO("Fecha de fin"), TITULO("Título A-Z"), AUTOR("Autor A-Z"), PAGINAS("Más páginas")
}

/**
 * Los órdenes que tienen sentido en cada estante (y solo esos):
 *  - Leyendo: última lectura, más avanzado, título, autor, más páginas (el «añadido» de algo que ya lees no dice nada).
 *  - Pendientes: último añadido, título, autor, más páginas (sin lectura ni avance: aún no los has empezado).
 *  - Leídos: fecha de fin, título, autor, más páginas (la «última lectura» de un leído es su fecha de fin: estaba repetido).
 *  - Abandonados: última lectura, más avanzado (hasta dónde llegaste), título, autor.
 */
fun ordenesDe(e: Estado): List<OrdenLibros> = when (e) {
    Estado.LEYENDO -> listOf(OrdenLibros.RECIENTES, OrdenLibros.AVANCE, OrdenLibros.TITULO, OrdenLibros.AUTOR, OrdenLibros.PAGINAS)
    Estado.PENDIENTE -> listOf(OrdenLibros.ANADIDOS, OrdenLibros.TITULO, OrdenLibros.AUTOR, OrdenLibros.PAGINAS)
    Estado.LEIDO -> listOf(OrdenLibros.TERMINADO, OrdenLibros.TITULO, OrdenLibros.AUTOR, OrdenLibros.PAGINAS)
    Estado.ABANDONADO -> listOf(OrdenLibros.RECIENTES, OrdenLibros.AVANCE, OrdenLibros.TITULO, OrdenLibros.AUTOR)
}

/** El orden con el que empieza cada estante: lo pendiente, por lo último añadido; lo leído, por la fecha de fin; el resto, por lo último abierto. */
fun ordenInicial(e: Estado): OrdenLibros = ordenesDe(e).first()

/** Ordena una lista de libros. Los que no tienen autor (o páginas) van al final. */
fun ordenar(o: OrdenLibros, l: List<Libro>): List<Libro> = when (o) {
    OrdenLibros.RECIENTES -> l.sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    OrdenLibros.AVANCE -> l.sortedWith(compareByDescending<Libro> { it.fraccion }.thenByDescending { it.ultima })
    OrdenLibros.ANADIDOS -> l.sortedByDescending { it.anadido }
    OrdenLibros.TERMINADO -> l.sortedWith(compareByDescending<Libro> { it.terminado }.thenByDescending { it.ultima })
    OrdenLibros.TITULO -> l.sortedBy { norm(it.titulo) }
    OrdenLibros.AUTOR -> l.sortedWith(compareBy<Libro> { it.autor.isBlank() }.thenBy { norm(it.autor) }.thenBy { norm(it.titulo) })
    OrdenLibros.PAGINAS -> l.sortedWith(compareBy<Libro> { it.paginas <= 0 }.thenByDescending { it.paginas }.thenBy { norm(it.titulo) })
}

/** El orden que has elegido para cada estante: se recuerda y viaja con tu cuenta (`ajustes/marfil_orden`, un campo por estante). */
object OrdenGuardado {
    val GRUPO: EspejoCuenta.Grupo = EspejoCuenta.Grupo("marfil_orden", "marfil_orden", { EspejoCuenta.Tipo.TEXTO })
    private fun prefs(c: android.content.Context) = EspejoCuenta.prefs(c, GRUPO)
    fun de(c: android.content.Context, e: Estado): OrdenLibros = ordenesDe(e).firstOrNull { it.name == prefs(c).getString(e.name, null) } ?: ordenInicial(e)
    fun pon(c: android.content.Context, e: Estado, o: OrdenLibros) { prefs(c).edit().putString(e.name, o.name).apply() }
}

/** Lo que dice debajo del título de un libro: autor y cómo va. */
fun metaLibro(l: Libro, conEstado: Boolean = false): String = listOf(
    l.autor,
    when (l.estado) { Estado.LEYENDO -> porcentaje(l.fraccion); Estado.LEIDO -> ""; Estado.PENDIENTE -> if (conEstado) "pendiente" else ""; Estado.ABANDONADO -> if (conEstado) "abandonado" else "" },
    if (l.enOtroDispositivo) "en otro dispositivo" else ""
).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Un libro en una lista, con la portada grande (84×126 dp de serie; las portadas mandan): encima del título, pequeño y en el acento, su número en
 * la lista (01, 02…) o dentro de su saga; el título en grotesca, el autor en versalitas y, abajo, cómo va: el % y las páginas con su barra si lo
 * lees o lo dejaste, sus páginas si está pendiente, y cuándo lo terminaste si está leído.
 */
@Composable
fun FilaLibro(l: Libro, alAbrir: (Libro) -> Unit, modifier: Modifier = Modifier, portada: Dp = 84.dp, numero: Int = 0, conEstado: Boolean = false, sangria: Dp = 0.dp, extra: String = "", orden: Int = 0) {
    val alto = portada * 1.5f
    // (la fila mide lo que la portada o, si el texto no cabe —letra grande—, lo que el texto; así lo de abajo va abajo)
    Row(modifier.fillMaxWidth().clickable { alAbrir(l) }.padding(start = sangria, top = 14.dp, bottom = 14.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        PortadaLibro(l, Modifier.width(portada).height(alto), radio = 0)
        Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = alto).padding(start = 14.dp)) {
            val num = when { orden > 0 -> "%02d".format(orden); numero > 0 -> "Nº $numero"; else -> "" }
            if (num.isNotEmpty()) Text(num, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
            Text(l.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = if (portada >= 70.dp) 19.sp else 17.sp, lineHeight = if (portada >= 70.dp) 22.sp else 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (num.isEmpty()) 0.dp else 2.dp))
            if (l.autor.isNotBlank()) Text(l.autor.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            val meta = listOf(if (conEstado) estadoTexto(l) else "", if (l.enOtroDispositivo) "en otro dispositivo" else "", extra).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            PieFila(l)
        }
    }
}

private fun estadoTexto(l: Libro) = when (l.estado) { Estado.LEYENDO -> "leyendo"; Estado.PENDIENTE -> "pendiente"; Estado.LEIDO -> "leído"; Estado.ABANDONADO -> "abandonado" }

/** Lo de abajo de una fila: cómo va el libro según su estado. */
@Composable
private fun PieFila(l: Libro) {
    @Composable fun linea(izq: String, der: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(izq, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (der.isNotBlank()) Text(der, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1)
    }
    when (l.estado) {
        Estado.LEYENDO, Estado.ABANDONADO -> {
            linea(porcentaje(l.fraccion), Paginas.texto(l))
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).background(Line)) { Box(Modifier.fillMaxWidth(l.fraccion.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
        }
        Estado.PENDIENTE -> linea(Paginas.texto(l).uppercase(), "")
        Estado.LEIDO -> linea(if (l.terminado > 0) "TERMINADO EL ${Formato.fecha(l.terminado).uppercase()}" else "TERMINADO", if (l.paginas > 0) "${Formato.cifra(l.paginas)} PÁGS" else "")
    }
}

/** El abanico de portadas de una saga (hasta tres, una sobre otra), del tamaño de las portadas de las listas. */
@Composable
fun AbanicoSaga(libros: List<Libro>, modifier: Modifier = Modifier) {
    val v = libros.take(3)
    Box(modifier.width(84.dp + 16.dp * (v.size - 1).coerceAtLeast(0)).height(126.dp)) {
        v.asReversed().forEachIndexed { i, l ->
            val k = v.size - 1 - i
            PortadaLibro(l, Modifier.padding(start = 16.dp * k).width(84.dp).height(126.dp), radio = 0)
        }
    }
}

/** «3 libros · 1 leído»: lo que dice una saga. */
fun resumenSaga(todos: List<Libro>): String {
    val leidos = todos.count { it.estado == Estado.LEIDO }
    return listOfNotNull(
        "${todos.size} ${if (todos.size == 1) "libro" else "libros"}",
        if (leidos > 0) "$leidos ${if (leidos == 1) "leído" else "leídos"}" else null,
        if (todos.any { it.estado == Estado.LEYENDO }) "leyendo" else null
    ).joinToString(" · ")
}

/** Una saga en una lista: el abanico, su nombre y cuántos hay; un toque la abre y enseña sus libros; «Ver saga →» va a su página. */
@Composable
fun FilaSaga(f: FilaLista.DeSaga, abierta: Boolean, alAlternar: () -> Unit, alAbrirSaga: (Saga) -> Unit, alAbrirLibro: (Libro) -> Unit) {
    val todos = remember(f.saga, Biblioteca.libros.size) { Sagas.libros(f.saga) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = alAlternar).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AbanicoSaga(f.miembros)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(f.saga.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(("Saga · " + resumenSaga(todos)).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierta) "−" else "+", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        if (abierta) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp)) {
                f.miembros.forEach { l ->
                    Filete(1.dp, Line)
                    FilaLibro(l, alAbrirLibro, portada = 64.dp, numero = f.saga.claves.indexOf(l.clave) + 1)
                }
                EnlaceRevista("Ver la saga →", acento = true) { alAbrirSaga(f.saga) }
            }
        }
    }
    Filete(2.dp)
}

/** Las filas (libros sueltos y sagas) de una lista de libros, con las sagas que estén abiertas. */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.filas(
    libros: List<Libro>, abiertas: Set<String>, alternar: (String) -> Unit, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, conEstado: Boolean = false
) {
    val filas = Sagas.agrupar(libros)
    itemsIndexed(filas, key = { _, it -> when (it) { is FilaLista.Suelto -> "l:" + it.libro.id; is FilaLista.DeSaga -> "s:" + it.saga.id } }) { i, f ->
        when (f) {
            is FilaLista.Suelto -> { FilaLibro(f.libro, alAbrir, conEstado = conEstado, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty(), orden = i + 1); Filete(2.dp) }
            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in abiertas, { alternar(f.saga.id) }, alAbrirSaga, alAbrir)
        }
    }
}

private fun vacioDe(e: Estado) = when (e) {
    Estado.LEYENDO -> "No tienes nada a medias. Abre un libro y saldrá aquí."
    Estado.PENDIENTE -> "No tienes nada pendiente: lo que añadas sin empezar saldrá aquí."
    Estado.LEIDO -> "Aún no has terminado ninguno. Cuando llegues al final, saldrá aquí."
    Estado.ABANDONADO -> "No has abandonado ninguno. Si dejas un libro, márcalo como abandonado desde su ficha."
}

/** Una página de un estante: título enorme y sus libros (con las sagas juntas). */
@Composable
private fun PaginaEstante(estado: Estado, titulo: String, todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var orden by remember(estado, EspejoCuenta.version) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    val libros = remember(todos, estado, orden) { ordenar(orden, todos.filter { it.estado == estado }) }
    var abiertas by rememberSaveable(estado) { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo(titulo, Modifier.padding(top = 14.dp), color = Acento, conCuadro = false)
                // Dirección C: tres bloques de cifras (cuántos, horas, páginas)
                val horas = libros.sumOf { it.msLeidos } / 3_600_000L
                val paginas = libros.sumOf { it.paginas.coerceAtLeast(0) }
                RejillaCifras(listOf(
                    BloqueCifra("${libros.size}", if (libros.size == 1) "libro" else "libros", Acento, SobreAcento),
                    BloqueCifra("${horas}H", "leídas", Paper, Charcoal),
                    BloqueCifra(if (paginas >= 10_000) "%.1fK".format(paginas / 1000.0) else "$paginas", "páginas", Bloque2, Color.White),
                ), Modifier.padding(top = 8.dp))
                // Cómo se ordena este estante (se recuerda)
                // («Terminado» solo en Leídos)
                val ordenes = ordenesDe(estado)
                FiltrosRevista(ordenes.map { it.nombre }, ordenes.indexOf(orden).coerceAtLeast(0), Modifier.padding(top = 6.dp)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden) }
                Filete(2.dp)
            }
        }
        if (libros.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (todos.isEmpty()) "Todavía no hay nada aquí." else vacioDe(estado), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
                if (todos.isEmpty()) MagLink("Añadir un libro", accent = true, onClick = alAnadir)
            }
        }
        filas(libros, abiertas, { id -> abiertas = if (id in abiertas) abiertas - id else abiertas + id }, alAbrir, alAbrirSaga)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Los autores: un toque en uno enseña sus libros (con sus sagas juntas). */
@Composable
private fun PaginaAutores(todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val autores = remember(todos) { todos.filter { it.autor.isNotBlank() }.groupBy { norm(it.autor) }.entries.sortedBy { it.key }.map { (k, ls) -> Triple(k, ls.first().autor, ls) } }
    var abiertos by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sagasAbiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Autores · ${autores.size}", Modifier.padding(top = 14.dp, bottom = 8.dp), color = Acento, conCuadro = false)
                Filete(2.dp)
            }
        }
        if (autores.isEmpty()) item { Text("Aún no hay autores: los libros sin autor no salen aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 30.dp)) }
        autores.forEach { (k, nombre, ls) ->
            item(key = "a:$k") {
                val abierto = k in abiertos
                Row(Modifier.fillMaxWidth().clickable { abiertos = if (abierto) abiertos - k else abiertos + k }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Text("${ls.size} ${if (ls.size == 1) "libro" else "libros"}".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (abierto) "  −" else "  +", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Filete(1.dp, Line)
            }
            if (k in abiertos) {
                val filas = Sagas.agrupar(ls.sortedBy { norm(it.titulo) })
                items(filas, key = { "a:$k:" + when (it) { is FilaLista.Suelto -> "l" + it.libro.id; is FilaLista.DeSaga -> "s" + it.saga.id } }) { f ->
                    Column(Modifier.padding(start = 12.dp)) {
                        when (f) {
                            is FilaLista.Suelto -> { FilaLibro(f.libro, alAbrir, conEstado = true, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty()); Filete(1.dp, Line) }
                            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in sagasAbiertas, { sagasAbiertas = if (f.saga.id in sagasAbiertas) sagasAbiertas - f.saga.id else sagasAbiertas + f.saga.id }, alAbrirSaga, alAbrir)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Todas las sagas, por nombre: cada una con su abanico y sus libros desplegables; desde aquí se crea una nueva (y se abre para meterle libros). */
@Composable
private fun PaginaSagas(todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val sagas = Sagas.lista.toList()
    val filas = remember(sagas, todos) { sagas.sortedBy { norm(it.nombre) }.map { FilaLista.DeSaga(it, Sagas.libros(it, todos)) } }
    var abiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nueva by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sagas · ${sagas.size}", Modifier.padding(top = 14.dp, bottom = 8.dp), color = Acento, conCuadro = false)
                Filete(2.dp)
                EnlaceRevista("+ Nueva saga", Modifier.padding(vertical = 4.dp), acento = true) { nueva = "" }
                Filete(1.dp, Line)
            }
        }
        if (filas.isEmpty()) item {
            Text("Aún no hay sagas. Créala aquí o desde la ficha de un libro («Saga»), y luego ve metiendo sus libros en orden.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 30.dp))
        }
        items(filas, key = { "s:" + it.saga.id }) { f ->
            FilaSaga(f, f.saga.id in abiertas, { abiertas = if (f.saga.id in abiertas) abiertas - f.saga.id else abiertas + f.saga.id }, alAbrirSaga, alAbrir)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    nueva?.let { n ->
        DialogoNuevaSaga(n, alCambiar = { nueva = it }, alCerrar = { nueva = null }) { nombre -> nueva = null; alAbrirSaga(Sagas.crear(nombre)) }
    }
}

/** Lo que sale al buscar: todos los libros que cuadran, estén en el estante que estén. */
@Composable
private fun Resultados(busca: String, todos: List<Libro>, alAbrir: (Libro) -> Unit) {
    val q = norm(busca)
    val l = remember(busca, todos) {
        todos.filter { norm(it.titulo).contains(q) || norm(it.autor).contains(q) || Sagas.de(it.clave)?.let { s -> norm(s.nombre).contains(q) } == true }
            .sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(2.dp) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        itemsIndexed(l, key = { _, it -> it.id }) { i, x -> FilaLibro(x, alAbrir, conEstado = true, orden = i + 1); Filete(2.dp) }
    }
}

/** Las páginas de Seshat, en el orden de las pestañas: la portada y luego las secciones (los índices son los de [Destino]). */
val PAGINAS = listOf(Destino.Portada, Destino.Leyendo, Destino.Pendientes, Destino.Leidos, Destino.Abandonados, Destino.Autores, Destino.Sagas, Destino.Escritos)
private val NOMBRES_PAGINAS = listOf("Portada", "Leyendo", "Pendientes", "Leídos", "Abandonados", "Autores", "Sagas", "Escritos")

/**
 * Seshat entera en una pantalla: la cabecera fija (BUSCAR · + AÑADIR · fecha · ⚙ y «SESHAT.»), una sola fila de pestañas (Portada, Leyendo,
 * Pendientes, Leídos, Abandonados, Autores, Sagas, Escritos) y las páginas, que se pasan tocando la pestaña o deslizando el dedo. No hay
 * submenús: cada sección es una página más, a la derecha de la portada. [pagina] es la que toca (la de [Destino]); [alPagina] avisa cuando cambia.
 * Al bajar en una página, el nombre grande se recoge y quedan la fila pequeña y las pestañas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantPrincipal(
    pagina: Int, alPagina: (Int) -> Unit, alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit,
    alAjustes: () -> Unit, escritoPedido: String? = null, alEscritoAbierto: () -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = pagina.coerceIn(0, PAGINAS.size - 1)) { PAGINAS.size }
    val ambito = rememberCoroutineScope()
    // Lo que pide fuera (Atrás, un widget, otra app) mueve las páginas; lo que mueves tú con el dedo, se cuenta fuera
    LaunchedEffect(pagina) { val p = pagina.coerceIn(0, PAGINAS.size - 1); if (pager.currentPage != p) pager.animateScrollToPage(p) }
    LaunchedEffect(pager.settledPage) { alPagina(pager.settledPage) }
    var buscando by rememberSaveable { mutableStateOf(false) }
    var busca by rememberSaveable { mutableStateOf("") }
    val todos = Biblioteca.propios()
    val cuentas: List<Int?> = buildList {
        add(null)
        listOf(Estado.LEYENDO, Estado.PENDIENTE, Estado.LEIDO, Estado.ABANDONADO).forEach { e -> add(todos.count { it.estado == e }) }
        add(todos.filter { it.autor.isNotBlank() }.map { norm(it.autor) }.distinct().size); add(Sagas.lista.size); add(null)
    }
    // El nombre grande se recoge al bajar (y vuelve al subir): lo que la página aún no ha movido, lo mueve la cabecera
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    var altoNombre by remember { mutableIntStateOf(0) }
    var recogido by remember { mutableFloatStateOf(0f) }
    val conexion = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (available.y >= 0f || altoNombre == 0) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, -(recogido - antes))
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val antes = recogido; recogido = (recogido - available.y).coerceIn(0f, altoNombre.toFloat())
                return Offset(0f, antes - recogido)
            }
        }
    }
    val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
    Column(Modifier.fillMaxSize().background(Paper).nestedScroll(conexion)) {
        // ---- La cabecera de la dirección C: un bloque de tinta con la fila pequeña y «SESHAT.» en grotesca enorme
        Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (altoNombre > 0 && recogido >= altoNombre - 1) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                // Plegada del todo: el nombre, pequeño, a la izquierda de la fila (y ningún hueco negro debajo)
                if (altoNombre > 0 && recogido >= altoNombre - 1) NombreConPunto("Seshat", 22.sp, Paper, Modifier.padding(end = 14.dp))
                EnlaceRevista("Buscar", color = if (buscando) Acento else Paper.copy(alpha = 0.75f)) { buscando = !buscando; if (!buscando) busca = "" }
                EnlaceRevista("+ Añadir", Modifier.padding(start = 14.dp), color = Acento) { alAnadir() }
                Spacer(Modifier.weight(1f))
                if (!(altoNombre > 0 && recogido >= altoNombre - 1)) Text(java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale("es", "ES")).format(java.util.Date()), color = Paper.copy(alpha = 0.75f), fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, "Ajustes", tint = Paper.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp).clickable(onClick = alAjustes).padding(6.dp).size(20.dp))
            }
            // El nombre: su alto se mide una vez y se va quitando según se recoge
            // Al recogerse, el hueco se encoge y el nombre encoge con él (desde la esquina de arriba a la izquierda) y se desvanece: nunca se
            // corta a media letra (antes se recortaba por arriba y se veían las letras partidas a distintas alturas)
            Box(Modifier.fillMaxWidth().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                if (altoNombre != p.height) altoNombre = p.height
                val h = (p.height - recogido.toInt()).coerceAtLeast(0)
                val escala = if (p.height > 0) h.toFloat() / p.height else 1f
                layout(p.width, h) {
                    if (h > 0) p.placeWithLayer(0, 0) {
                        scaleX = escala; scaleY = escala
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        alpha = ((escala - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    }
                }
            }) {
                NombreConPunto("Seshat", if (ancho >= 700) 104.sp else 64.sp, Paper, Modifier.padding(top = 2.dp, bottom = 12.dp))
            }
        }
        PestanasPaginas(NOMBRES_PAGINAS, pager.currentPage, cuentas) { i -> buscando = false; busca = ""; ambito.launch { pager.animateScrollToPage(i) } }
        if (buscando) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CampoRevista("Buscar", busca, "título, autor o saga") { busca = it } }
        if (busca.isNotBlank()) Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { Resultados(busca, todos, alAbrir) }
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { PAGINAS[it].name }) { i ->
            when (PAGINAS[i]) {
                Destino.Portada -> PaginaPortada(alAbrir = alAbrir, alLeer = alLeer, alAnadir = alAnadir, alIr = { d -> ambito.launch { pager.animateScrollToPage(PAGINAS.indexOf(d).coerceAtLeast(0)) } }, alAjustes = alAjustes)
                Destino.Escritos -> PantEscritos(alVolver = {}, alLeer = alLeer, abrir = escritoPedido, alAbierto = alEscritoAbierto, enPagina = true)
                else -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding()) {
                    when (PAGINAS[i]) {
                        Destino.Leyendo -> PaginaEstante(Estado.LEYENDO, "Leyendo", todos, alAbrir, alAbrirSaga, alAnadir)
                        Destino.Pendientes -> PaginaEstante(Estado.PENDIENTE, "Pendientes", todos, alAbrir, alAbrirSaga, alAnadir)
                        Destino.Leidos -> PaginaEstante(Estado.LEIDO, "Leídos", todos, alAbrir, alAbrirSaga, alAnadir)
                        Destino.Abandonados -> PaginaEstante(Estado.ABANDONADO, "Abandonados", todos, alAbrir, alAbrirSaga, alAnadir)
                        Destino.Sagas -> PaginaSagas(todos, alAbrir, alAbrirSaga)
                        else -> PaginaAutores(todos, alAbrir, alAbrirSaga)
                    }
                }
            }
        }
    }
}

/** Las pestañas de las secciones (como las de Atrio): la elegida en tinta con el subrayado de acento; su número, en acento. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int?>, onSel: (Int) -> Unit) {
    // La fila se desplaza sola solo si la pestaña elegida no se ve entera (y entonces la centra): así no se corta la de al lado
    val scroll = androidx.compose.foundation.rememberScrollState()
    val sitios = remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { androidx.compose.runtime.mutableIntStateOf(0) }
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
                Row(Modifier.fillMaxHeight().onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x).let { kotlin.math.round(it).toInt() } to c.size.width }.clickable { onSel(i) }
                    .background(if (on) Acento else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Dirección C: la elegida, en un bloque del acento
                    Text(n.uppercase(), color = if (on) SobreAcento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
                    cuentas.getOrNull(i)?.let { c -> Text(" $c", color = if (on) SobreAcento else Acento, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
        Filete(2.dp)
    }
}
