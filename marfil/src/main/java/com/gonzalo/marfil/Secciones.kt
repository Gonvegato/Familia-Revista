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
            if (num.isNotEmpty()) Text(num, color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text(l.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = if (portada >= 70.dp) 19.sp else 17.sp, lineHeight = if (portada >= 70.dp) 22.sp else 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (num.isEmpty()) 0.dp else 2.dp))
            if (l.autor.isNotBlank()) Text(l.autor, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            val meta = listOf(if (conEstado) estadoTexto(l) else "", if (l.enOtroDispositivo) "en otro dispositivo" else "", extra).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 18.sp,
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
        Text(izq, color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (der.isNotBlank()) Text(der, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
    }
    when (l.estado) {
        Estado.LEYENDO, Estado.ABANDONADO -> {
            linea(porcentaje(l.fraccion), Paginas.texto(l))
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).background(Line)) { Box(Modifier.fillMaxWidth(l.fraccion.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
        }
        Estado.PENDIENTE -> linea(Paginas.texto(l), "")
        Estado.LEIDO -> linea(if (l.terminado > 0) "Terminado el ${Formato.fecha(l.terminado)}" else "Terminado", if (l.paginas > 0) "${Formato.cifra(l.paginas)} págs" else "")
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
                Text(f.saga.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(("Saga · " + resumenSaga(todos)), color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierta) "−" else "+", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
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
    Filete(1.dp, Line)
}

/** Las filas (libros sueltos y sagas) de una lista de libros, con las sagas que estén abiertas. */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.filas(
    libros: List<Libro>, abiertas: Set<String>, alternar: (String) -> Unit, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, conEstado: Boolean = false
) {
    val filas = Sagas.agrupar(libros)
    itemsIndexed(filas, key = { _, it -> when (it) { is FilaLista.Suelto -> "l:" + it.libro.id; is FilaLista.DeSaga -> "s:" + it.saga.id } }) { i, f ->
        when (f) {
            is FilaLista.Suelto -> { FilaLibro(f.libro, alAbrir, conEstado = conEstado, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty(), orden = i + 1); Filete(1.dp, Line) }
            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in abiertas, { alternar(f.saga.id) }, alAbrirSaga, alAbrir)
        }
    }
}

internal fun vacioDe(e: Estado) = when (e) {
    Estado.LEYENDO -> "No tienes nada a medias. Abre un libro y saldrá aquí."
    Estado.PENDIENTE -> "No tienes nada pendiente: lo que añadas sin empezar saldrá aquí."
    Estado.LEIDO -> "Aún no has terminado ninguno. Cuando llegues al final, saldrá aquí."
    Estado.ABANDONADO -> "No has abandonado ninguno. Si dejas un libro, márcalo como abandonado desde su ficha."
}

/** Los autores: un toque en uno enseña sus libros (con sus sagas juntas). */
@Composable
internal fun PaginaAutores(todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val autores = remember(todos) { todos.filter { it.autor.isNotBlank() }.groupBy { norm(it.autor) }.entries.sortedBy { it.key }.map { (k, ls) -> Triple(k, ls.first().autor, ls) } }
    var abiertos by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sagasAbiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Autores · ${autores.size}", Modifier.padding(top = 14.dp, bottom = 8.dp), color = Acento, conCuadro = false)
                Filete(1.dp, Line)
            }
        }
        if (autores.isEmpty()) item { Text("Aún no hay autores: los libros sin autor no salen aquí.", color = Muted, fontFamily = Body, fontSize = 18.sp, modifier = Modifier.padding(top = 30.dp)) }
        autores.forEach { (k, nombre, ls) ->
            item(key = "a:$k") {
                val abierto = k in abiertos
                Row(Modifier.fillMaxWidth().clickable { abiertos = if (abierto) abiertos - k else abiertos + k }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    Text("${ls.size} ${if (ls.size == 1) "libro" else "libros"}", color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (abierto) "  −" else "  +", color = Acento, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
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
internal fun PaginaSagas(todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val sagas = Sagas.lista.toList()
    val filas = remember(sagas, todos) { sagas.sortedBy { norm(it.nombre) }.map { FilaLista.DeSaga(it, Sagas.libros(it, todos)) } }
    var abiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nueva by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sagas · ${sagas.size}", Modifier.padding(top = 14.dp, bottom = 8.dp), color = Acento, conCuadro = false)
                Filete(1.dp, Line)
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
internal fun Resultados(busca: String, todos: List<Libro>, alAbrir: (Libro) -> Unit) {
    val q = norm(busca)
    val l = remember(busca, todos) {
        todos.filter { norm(it.titulo).contains(q) || norm(it.autor).contains(q) || Sagas.de(it.clave)?.let { s -> norm(s.nombre).contains(q) } == true }
            .sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(1.dp, Line) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Body, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        itemsIndexed(l, key = { _, it -> it.id }) { i, x -> FilaLibro(x, alAbrir, conEstado = true, orden = i + 1); Filete(1.dp, Line) }
    }
}
