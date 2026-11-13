package com.gonzalo.marfil.pc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.mov.Atajos
import com.gonzalo.familia.pc.mov.CapaBuscar
import com.gonzalo.familia.pc.mov.CapaQueNace
import com.gonzalo.familia.pc.mov.CapasAbiertas
import com.gonzalo.familia.pc.mov.Teclas
import com.gonzalo.familia.pc.mov.filaDe
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch

sealed class Pantalla {
    data object Portada : Pantalla()
    data class Lista(val estado: Estado) : Pantalla()
    data class Ficha(val id: String, val volver: Pantalla) : Pantalla()
    /** Leer un libro: por donde ibas o, con [ir], en ese sitio (un subrayado); [panel]: abierto con ese panel. */
    data class Leer(val id: String, val volver: Pantalla, val ir: String = "", val panel: Panel? = null, val busqueda: String = "") : Pantalla()
    data object Escritos : Pantalla()
    data class Capitulo(val ref: String) : Pantalla()
    data object Ajustes : Pantalla()
}

class Nav(val ir: (Pantalla) -> Unit, val aviso: (String) -> Unit)

/** Las páginas de Seshat, en el orden de las pestañas (como en el móvil). */
private val ESTADOS = listOf(Estado.LEYENDO, Estado.PENDIENTE, Estado.LEIDO, Estado.ABANDONADO)
private val NOMBRES = listOf("Portada", "Leyendo", "Pendientes", "Leídos", "Abandonados", "Escritos")
private fun paginaDe(p: Pantalla): Int? = when (p) {
    Pantalla.Portada -> 0
    is Pantalla.Lista -> 1 + ESTADOS.indexOf(p.estado)
    Pantalla.Escritos -> 5
    else -> null
}

/**
 * Seshat en el ordenador (desde la 1.3.0, la cara del móvil): la cabecera de tinta, las pestañas y las páginas; encima, la ficha de un libro
 * (nace de la fila que pulsas y vuelve a ella; Esc la cierra), Ajustes (desde ⚙) y el buscador (crece desde BUSCAR, o ⌘F). El lector y
 * los capítulos de Thot van a pantalla completa, como antes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppMarfil(inicial: Pantalla = Pantalla.Portada) {
    LaFamilia.cambios
    // Lo que va a pantalla completa (el lector, un capítulo de Thot); null: las páginas
    var completa by remember { mutableStateOf(inicial.takeIf { it is Pantalla.Leer || it is Pantalla.Capitulo }) }
    val pager = rememberPagerState(initialPage = paginaDe((inicial as? Pantalla.Ficha)?.volver ?: inicial) ?: 0) { NOMBRES.size }
    var ficha by remember { mutableStateOf((inicial as? Pantalla.Ficha)?.id) }
    var fichaAlInstante by remember { mutableStateOf(inicial is Pantalla.Ficha) }
    var ajustes by remember { mutableStateOf(inicial == Pantalla.Ajustes) }
    var buscando by remember { mutableStateOf(false) }
    var aperturaBusca by remember { mutableIntStateOf(0) }
    var anadir by remember { mutableStateOf(false) }
    val sitioBuscar = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember {
        Nav({ p ->
            when (p) {
                is Pantalla.Leer, is Pantalla.Capitulo -> completa = p
                is Pantalla.Ficha -> { completa = null; paginaDe(p.volver)?.let { n -> scope.launch { pager.scrollToPage(n) } }; ficha = p.id }
                Pantalla.Ajustes -> { completa = null; ajustes = true }
                else -> { completa = null; ficha = null; paginaDe(p)?.let { n -> scope.launch { pager.animateScrollToPage(n) } } }
            }
        }) { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } }
    }
    fun abreBusca() { if (!buscando) { aperturaBusca++; buscando = true } }

    when (val p = completa) {
        is Pantalla.Leer -> { Box(Modifier.fillMaxSize()) { PantLector(p.id, p.volver, nav, p.ir, p.panel, p.busqueda); SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp)) }; return }
        is Pantalla.Capitulo -> { Box(Modifier.fillMaxSize()) { PantCapitulo(p.ref, nav) }; return }
        else -> {}
    }
    Teclas { k -> if (Atajos.mando(k) && k.key == Key.F) { abreBusca(); true } else false }
    val libros = Libros.todos()
    val cuentas = listOf<Int?>(null) + ESTADOS.map { e -> libros.count { it.estado == e } } + listOf(null)
    Box(Modifier.fillMaxSize().background(Paper)) {
        PaginasC("Seshat", NOMBRES, cuentas, pager, alAjustes = { ajustes = true }, enlaces = {
            EnlaceCabecera("Buscar", Paper.copy(alpha = 0.75f), atajo = TeclaMando + "F", modifier = Modifier.onGloballyPositioned { sitioBuscar[0] = it }) { abreBusca() }
            EnlaceCabecera("+ Añadir libro", Acento) { anadir = true }
        }) { i ->
            when (i) {
                0 -> PaginaPortada(nav)
                5 -> PantEscritos(nav)
                else -> PaginaLista(ESTADOS[i - 1], nav)
            }
        }
        // El buscador: crece desde BUSCAR (la ficha que abre va encima; al cerrarla se vuelve aquí)
        if (buscando) CapaBuscar(desde = { sitioBuscar[0]?.takeIf { it.isAttached }?.boundsInRoot() }, apertura = aperturaBusca, pista = "Título, autor o una frase que subrayaste",
            papel = Paper, tinta = Charcoal, acento = Acento, gris = Muted, letra = Body, bloqueado = CapasAbiertas.ficha, alCerrado = { buscando = false }) { q -> ResultadosBusca(q, nav) }
        CapaQueNace(if (ajustes) "ajustes" else null, alCerrar = { ajustes = false }, fondo = Paper, esPagina = true) { _, _ -> PantAjustes(nav) { ajustes = false } }
        CapaQueNace(ficha, alCerrar = { ficha = null }, fondo = Paper, alInstante = fichaAlInstante, alUsarInstante = { fichaAlInstante = false }) { id, _ ->
            PantFicha(id, nav) { ficha = null }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (anadir) DialogoAnadir(nav) { anadir = false }
}

/** Lo que sale en el buscador según escribes: los libros (por título o autor) y lo que subrayaste. */
@Composable
private fun ResultadosBusca(q: String, nav: Nav) {
    val n = Libros.norm(q)
    val todos = Libros.todos()
    val libros = if (n.isEmpty()) todos.filter { it.estado == Estado.LEYENDO }.take(6) else todos.filter { Libros.norm(it.titulo + " " + it.autor).contains(n) }
    val citas = if (n.length < 3) emptyList() else todos.flatMap { l -> Marcas.de(l.id, Marcas.SUBRAYADOS).filter { !it.delTextoDeUnPdf && Libros.norm(it.texto + " " + it.nota).contains(n) }.map { l to it } }.take(20)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 20.dp)) {
        Antetitulo(if (n.isEmpty()) "Sigues con" else if (libros.isEmpty() && citas.isEmpty()) "Nada con «$q»" else "Tus libros · ${libros.size}", color = Acento)
        libros.forEach { l ->
            Row(Modifier.fillMaxWidth().widthIn(max = 900.dp).filaDe(l.id).levanta { nav.ir(Pantalla.Ficha(l.id, Pantalla.Portada)) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PortadaCosa(l.portada, l.portadaUrl, l.titulo, Modifier.width(40.dp), pie = l.autor)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(l.titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(l.autor, l.estado.nombre).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1)
                }
            }
            Filete(1.dp, Line)
        }
        if (citas.isNotEmpty()) {
            Spacer(Modifier.height(18.dp)); Antetitulo("En tus subrayados · ${citas.size}", color = Acento)
            citas.forEach { (l, m) ->
                Column(Modifier.fillMaxWidth().widthIn(max = 900.dp).levanta { nav.ir(Pantalla.Leer(l.id, Pantalla.Ficha(l.id, Pantalla.Portada), ir = m.valor)) }.padding(vertical = 10.dp)) {
                    Text("«${m.texto}»", color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 23.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Etiqueta(listOf(l.titulo, m.etiqueta).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), Modifier.padding(top = 4.dp))
                }
                Filete(1.dp, Line)
            }
        }
    }
}
