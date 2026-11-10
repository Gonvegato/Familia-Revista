package com.gonzalo.marfil

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gonzalo.mesa.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * Seshat en la dirección D («la mesa y la estantería»): abajo, la barra con Hoy, Biblioteca, Añadir y Tú.
 *  - Hoy: lo que lees ahora, grande, con «Seguir leyendo»; tu semana; lo demás que tienes a medias y lo siguiente (tu cola).
 *  - Biblioteca: todas las portadas en una pantalla, con el buscador arriba y los estantes de antes (leyendo, pendientes, leídos,
 *    abandonados, sagas, autores, escritos) como filtros.
 *  - Añadir: el botón del medio (elige uno o varios archivos).
 *  - Tú: el reto del año, tus cifras, lo leído este año y los ajustes.
 */

/** Las pestañas de la barra (el índice se guarda al girar la pantalla). */
enum class Mesa { Hoy, Biblioteca, Anadir, Tu }

/** Los filtros de la biblioteca: los cuatro estados y las tres vistas que antes eran páginas. */
enum class Estante(val nombre: String, val estado: Estado?) {
    Leyendo("Leyendo", Estado.LEYENDO), Pendientes("Pendientes", Estado.PENDIENTE), Leidos("Leídos", Estado.LEIDO), Abandonados("Abandonados", Estado.ABANDONADO),
    Sagas("Sagas", null), Autores("Autores", null), Escritos("Escritos", null)
}

/**
 * «Lo siguiente»: la cola de lo que quieres empezar después, en el orden que tú le das (desde la ficha de un pendiente: «Poner el
 * siguiente»). Es de cada dispositivo. Lo que ya no está pendiente sale solo de la cola.
 */
object Cola {
    val ids = mutableStateListOf<String>()
    private fun p(c: Context) = c.getSharedPreferences("marfil_cola", Context.MODE_PRIVATE)
    fun cargar(c: Context) { ids.clear(); ids.addAll(p(c).getString("ids", "").orEmpty().split('\n').filter { it.isNotBlank() }) }
    private fun guardar(c: Context) = p(c).edit().putString("ids", ids.joinToString("\n")).apply()
    fun esta(clave: String) = clave in ids
    /** Lo pone el primero (o lo quita si ya estaba). */
    fun alterna(c: Context, clave: String) { if (!ids.remove(clave)) ids.add(0, clave); guardar(c) }
    /** Los pendientes en orden: primero los de la cola (en su orden) y luego los demás como vengan. */
    fun ordena(pendientes: List<Libro>): List<Libro> {
        val porClave = pendientes.associateBy { it.clave }
        val primero = ids.mapNotNull { porClave[it] }
        return primero + pendientes.filter { it.clave !in ids }
    }
}

private val PESTANAS = listOf(Pestana("Hoy", Icons.Rounded.Home), Pestana("Biblioteca", Icons.Rounded.AutoStories), Pestana("Añadir", Icons.Rounded.Add, principal = true), Pestana("Tú", Icons.Rounded.Person))

/** La app: las tres pantallas y la barra. [pestana] y [estante] los lleva quien llama (para Atrás y para lo que piden de fuera). */
@Composable
fun PantMesa(pestana: Mesa, alPestana: (Mesa) -> Unit, estante: Estante, alEstante: (Estante) -> Unit, alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit,
             alAnadir: () -> Unit, alAjustes: () -> Unit, escritoPedido: String? = null, alEscritoAbierto: () -> Unit = {}) {
    Andamio(PESTANAS, pestana.ordinal, { i -> if (Mesa.entries[i] == Mesa.Anadir) alAnadir() else alPestana(Mesa.entries[i]) }) {
        when (pestana) {
            Mesa.Biblioteca -> PaginaBiblioteca(estante, alEstante, alAbrir, alLeer, alAbrirSaga, alAnadir, escritoPedido, alEscritoAbierto)
            Mesa.Tu -> PaginaTu(alAbrir, alAjustes) { alEstante(Estante.Leidos); alPestana(Mesa.Biblioteca) }
            else -> PaginaHoy(alAbrir, alLeer, alAnadir) { e -> alEstante(e); alPestana(Mesa.Biblioteca) }
        }
    }
}

// ───────────────────────────────────────────────────────────── Hoy

/** «4 h 01 min», «52 min». */
internal fun minutosLargo(min: Long): String = if (min < 60) "$min min" else "${min / 60} h ${"%02d".format(min % 60)} min"

/** La línea de un libro a medias: «Umberto Eco · página 200 de 540» (o el tanto por ciento si no se saben sus páginas). */
internal fun lineaAhora(l: Libro): String = listOfNotNull(
    l.autor.ifBlank { null },
    if (l.paginas > 0) "página ${Formato.cifra(Math.round(l.fraccion * l.paginas).coerceIn(0, l.paginas))} de ${Formato.cifra(l.paginas)}" else porcentaje(l.fraccion)
).joinToString(" · ")

@Composable
fun PaginaHoy(alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAnadir: () -> Unit, alEstante: (Estante) -> Unit) {
    val ctx = LocalContext.current
    val libros = Biblioteca.propios()
    val leyendo = libros.filter { it.estado == Estado.LEYENDO }.sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    val ahora = leyendo.firstOrNull()
    val siguiente = remember(libros, Cola.ids.toList(), EspejoCuenta.version) {
        // De cada saga, el siguiente que te queda; los de tu cola, delante
        val uno = Sagas.agrupar(ordenar(OrdenGuardado.de(ctx, Estado.PENDIENTE), libros.filter { it.estado == Estado.PENDIENTE })).map { f ->
            when (f) { is FilaLista.Suelto -> f.libro; is FilaLista.DeSaga -> f.miembros.first() }
        }
        Cola.ordena(uno)
    }
    val lectura = remember(libros.map { it.id to it.msPropios to it.ultima }) { datosLectura(ctx, libros) }
    PaginaM(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(0.dp))
        CabeceraM("Seshat") { NotaM(LocalDate.now().let { "${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES"))} ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).trimEnd('.')}" }, lineas = 1) }
        when {
            libros.isEmpty() -> VacioM("Tu biblioteca está vacía", "Añade tu primer libro (EPUB, PDF, MOBI, AZW3, FB2, CBZ o TXT). Lo que estés leyendo saldrá aquí, con un botón para seguir.", boton = "Añadir un libro", alBoton = alAnadir)
            ahora == null -> VacioM("No estás leyendo nada", "Empieza uno de tus pendientes y saldrá aquí, con un botón para seguir donde lo dejaste.", boton = if (siguiente.isEmpty()) "Añadir un libro" else "Ver tus pendientes",
                alBoton = { if (siguiente.isEmpty()) alAnadir() else alEstante(Estante.Pendientes) })
            else -> TarjetaAhora("Leyendo", ahora.titulo, lineaAhora(ahora), if (ahora.enOtroDispositivo) "Está en otro dispositivo: abrir su ficha" else "Seguir leyendo", avance = ahora.fraccion,
                alAbrir = { alAbrir(ahora) }, alBoton = { if (ahora.enOtroDispositivo) alAbrir(ahora) else alLeer(ahora) }) { PortadaLibro(ahora, Modifier.fillMaxSize(), radio = 0, conAutor = false) }
        }
        if (libros.isNotEmpty()) {
            val total = lectura.semana.sumOf { it.second }
            Column {
                Semana("Esta semana · ${minutosLargo(total)}" + (if (lectura.hoyMin > 0) " · hoy ${minutosLargo(lectura.hoyMin)}" else ""), lectura.semana.map { it.second.toFloat() }, hoy = 6,
                    letras = lectura.semana.map { it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")).uppercase() })
                if (lectura.racha > 1) NotaM("${lectura.racha} días seguidos leyendo", Modifier.padding(top = 8.dp))
            }
        }
        val otros = leyendo.drop(1)
        if (otros.isNotEmpty()) FilaDePortadas("También lees", "${otros.size} →", { alEstante(Estante.Leyendo) }, otros, alAbrir)
        if (siguiente.isNotEmpty()) FilaDePortadas("Lo siguiente", "${libros.count { it.estado == Estado.PENDIENTE }} →", { alEstante(Estante.Pendientes) }, siguiente.take(12), alAbrir)
        Spacer(Modifier.height(4.dp))
    }
}

/** Una fila de portadas que se desliza: su rótulo, el enlace a su estante y las portadas (con su avance). */
@Composable
private fun FilaDePortadas(rotulo: String, enlace: String, alEnlace: () -> Unit, libros: List<Libro>, alAbrir: (Libro) -> Unit) {
    Column {
        Rotulo(rotulo, enlace = enlace, alEnlace = alEnlace)
        Spacer(Modifier.height(8.dp))
        // (se sale de los márgenes de la página para deslizarse de borde a borde)
        Row(Modifier.layoutSinMargen().horizontalScroll(rememberScrollState()).padding(horizontal = Medida.margen), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            libros.forEach { l -> PortadaConAvance(l.titulo, Modifier.width(96.dp), avance = l.fraccion.takeIf { l.estado == Estado.LEYENDO }, alAbrir = { alAbrir(l) }) { PortadaLibro(l, Modifier.fillMaxSize(), radio = 0) } }
        }
    }
}

/** Deja que una fila ocupe todo el ancho de la pantalla aunque la página tenga márgenes. */
private fun Modifier.layoutSinMargen(): Modifier = this.then(Modifier.layout { m, c ->
    val extra = Medida.margen.roundToPx() * 2
    val p = m.measure(c.copy(maxWidth = c.maxWidth + extra, minWidth = (c.minWidth + extra).coerceAtMost(c.maxWidth + extra)))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
})

// ───────────────────────────────────────────────────────────── Biblioteca

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaginaBiblioteca(estante: Estante, alEstante: (Estante) -> Unit, alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit,
                     escritoPedido: String? = null, alEscritoAbierto: () -> Unit = {}) {
    val ctx = LocalContext.current
    val todos = Biblioteca.propios()
    var busca by rememberSaveable { mutableStateOf("") }
    val cuentas = Estante.entries.map { e -> when (e) {
        Estante.Sagas -> Sagas.lista.size
        Estante.Autores -> todos.filter { it.autor.isNotBlank() }.map { norm(it.autor) }.distinct().size
        Estante.Escritos -> null
        else -> todos.count { it.estado == e.estado }
    } }
    PaginaM(hueco = 14.dp) {
        Spacer(Modifier.height(0.dp))
        Buscador(busca, "Buscar por título, autor o saga", icono = Icons.Rounded.Search) { busca = it }
        if (busca.isNotBlank()) { Resultados(busca, todos, alAbrir); return@PaginaM }
        val estado = estante.estado
        if (estado == null) {
            Filtros(Estante.entries.map { it.nombre }.zip(cuentas), estante.ordinal) { alEstante(Estante.entries[it]) }
            Box(Modifier.weight(1f)) {
                when (estante) {
                    Estante.Sagas -> PaginaSagas(todos, alAbrir, alAbrirSaga)
                    Estante.Autores -> PaginaAutores(todos, alAbrir, alAbrirSaga)
                    else -> PantEscritos(alVolver = {}, alLeer = alLeer, abrir = escritoPedido, alAbierto = alEscritoAbierto, enPagina = true)
                }
            }
            return@PaginaM
        }
        var orden by remember(estado, EspejoCuenta.version) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
        val libros = remember(todos, estado, orden, Cola.ids.toList()) { ordenar(orden, todos.filter { it.estado == estado }) }
        var eligeOrden by remember { mutableStateOf(false) }
        // Todo va dentro de la rejilla (los filtros y el orden suben con las portadas)
        LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Filtros(Estante.entries.map { it.nombre }.zip(cuentas), estante.ordinal) { alEstante(Estante.entries[it]) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    val horas = libros.sumOf { it.msLeidos } / 3_600_000L
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        NotaM(listOfNotNull("${libros.size} ${if (libros.size == 1) "libro" else "libros"}", if (horas > 0) "$horas h leídas" else null).joinToString(" · "), Modifier.weight(1f))
                        TextoM("Orden: ${orden.nombre.lowercase()}", Modifier.clickable { eligeOrden = !eligeOrden }.padding(vertical = 8.dp), 13, LocalTinta.current.acento, 1, FontWeight.SemiBold)
                    }
                    if (eligeOrden) { val ordenes = ordenesDe(estado); Filtros(ordenes.map { it.nombre to null }, ordenes.indexOf(orden)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden); eligeOrden = false } }
                }
            }
            if (libros.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                VacioM(if (todos.isEmpty()) "Todavía no hay nada aquí" else estante.nombre, if (todos.isEmpty()) "Añade tu primer libro y saldrá en esta estantería." else vacioDe(estado),
                    boton = if (todos.isEmpty()) "Añadir un libro" else null, alBoton = alAnadir)
            }
            items(libros, key = { it.id }) { l ->
                PortadaConAvance(l.titulo, avance = l.fraccion.takeIf { l.estado == Estado.LEYENDO || l.estado == Estado.ABANDONADO }, alAbrir = { alAbrir(l) }) { PortadaLibro(l, Modifier.fillMaxSize(), radio = 0) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ───────────────────────────────────────────────────────────── Tú

@Composable
fun PaginaTu(alAbrir: (Libro) -> Unit, alAjustes: () -> Unit, alLeidos: () -> Unit) {
    val ctx = LocalContext.current
    val libros = Biblioteca.propios()
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val meta = remember(libros) { Ajustes.metaAnual(ctx) }
    val hechos = terminadosEn(libros, ano)
    val leidosAno = libros.filter { l -> l.terminados.any { anoDe(it) == ano } }.sortedByDescending { l -> l.terminados.filter { anoDe(it) == ano }.maxOrNull() ?: 0L }
    PaginaM(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(0.dp))
        CabeceraM("Tú") { IconoM(Icons.Rounded.Settings, "Ajustes", conFondo = true, alHacer = alAjustes) }
        // El reto del año
        TarjetaM(alHacer = if (meta > 0) alLeidos else alAjustes) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                TituloM("Reto $ano", Modifier.weight(1f), tam = 20, lineas = 1)
                if (meta > 0) { TituloM("$hechos", tam = 22, lineas = 1); NotaM(" de $meta", Modifier.padding(bottom = 2.dp), tam = 15, lineas = 1) }
            }
            Spacer(Modifier.height(10.dp))
            if (meta > 0) { AvanceM(hechos.toFloat() / meta, alto = 8.dp); Spacer(Modifier.height(8.dp)); NotaM(ritmoReto(meta, hechos).texto()) }
            else { NotaM("$hechos ${if (hechos == 1) "terminado" else "terminados"} este año. Ponte un reto y verás cómo vas.", tam = 14); Spacer(Modifier.height(10.dp)); TextoM("Ponerte un reto →", tam = 15, color = LocalTinta.current.acento, peso = FontWeight.SemiBold) }
        }
        // Tus cifras
        val horas = libros.sumOf { it.msLeidos } / 3_600_000L
        val paginas = libros.filter { it.estado == Estado.LEIDO }.sumOf { it.paginas.coerceAtLeast(0) }
        RejillaCifras(listOf(
            BloqueCifra("${libros.count { it.estado == Estado.LEIDO }}", "leídos", Crudo, Charcoal, alLeidos),
            BloqueCifra("$horas h", "leyendo", Crudo, Charcoal),
            BloqueCifra(if (paginas >= 10_000) "%.1f mil".format(paginas / 1000.0) else "$paginas", "páginas", Crudo, Charcoal),
        ))
        // Lo leído este año
        Column {
            Rotulo("Tu $ano · $hechos ${if (hechos == 1) "leído" else "leídos"}", enlace = "Leídos →", alEnlace = alLeidos)
            Spacer(Modifier.height(8.dp))
            if (leidosAno.isEmpty()) NotaM("Aún no has terminado ninguno este año. Cuando acabes uno, su portada saldrá aquí.", tam = 14)
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                leidosAno.forEach { l -> PortadaConAvance(l.titulo, Modifier.width(84.dp), alAbrir = { alAbrir(l) }) { PortadaLibro(l, Modifier.fillMaxSize(), radio = 0) } }
            }
        }
        BotonM("Ajustes", principal = false, icono = Icons.Rounded.Settings, flecha = true, alHacer = alAjustes)
        NotaM("Seshat $VERSION", Modifier.fillMaxWidth(), tam = 12, centro = true)
        Spacer(Modifier.height(4.dp))
    }
}

/** Una barra de avance fina (la de siempre, redonda). */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) = AvanceM(f, modifier, alto = 6.dp)

/** El color del texto sobre el acento. */
internal fun sobreAcento(): androidx.compose.ui.graphics.Color = SobreAcento
