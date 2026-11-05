package com.gonzalo.marfil.pc

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lo que se está leyendo ahora: el tiempo (cada minuto, a la cuenta) y la posición pendiente de guardar (al cerrar el libro o la app,
 * siempre). El tiempo cuenta mientras el libro está abierto, la ventana delante y has tocado algo hace menos de 3 minutos.
 */
object Lectura {
    @Volatile var libro: String? = null
    @Volatile private var pendiente: (() -> Unit)? = null
    @Volatile var ultimaActividad = 0L
    private var acumulado = 0L

    fun apunta(guardar: () -> Unit) { pendiente = guardar }
    fun guardaYa() { pendiente?.let { runCatching { it() } }; pendiente = null }
    fun actividad() { ultimaActividad = System.currentTimeMillis() }

    /** Cada [paso] ms que pasan leyendo (lo llama el lector). */
    @Synchronized fun tic(id: String, paso: Long) {
        val ventana = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null
        if (!ventana || System.currentTimeMillis() - ultimaActividad > 180_000L) return
        acumulado += paso
        if (acumulado >= 60_000L) vacia(id)
    }
    @Synchronized fun vacia(id: String) { val a = acumulado; acumulado = 0; if (a > 0) runCatching { Libros.sumaTiempo(id, a) } }
    fun alCerrar() { guardaYa(); libro?.let { vacia(it) } }
}

private enum class Fondo(val nombre: String) { TEMA("Como la app"), PAPEL("Papel"), SEPIA("Sepia"), NOCHE("Noche") }

/** El lector: el libro entero por donde ibas (aquí o en el móvil), con su índice, el tamaño de la letra y el fondo. */
@Composable
fun PantLector(id: String, volver: Pantalla, nav: Nav) {
    val l = Libros.por(id)
    var archivo by remember(id) { mutableStateOf(Libros.archivo(id)) }
    var error by remember(id) { mutableStateOf("") }
    var bajando by remember(id) { mutableStateOf(0f) }
    LaunchedEffect(id) {
        if (archivo == null && l?.nube != null) {
            val r = withContext(Dispatchers.IO) { runCatching { NubeLibros.baja(l) { n, t -> if (t > 0) bajando = n.toFloat() / t } } }
            r.onSuccess { archivo = it }.onFailure { error = it.message ?: "No se ha podido bajar de tu kDrive." }
        } else if (archivo == null) error = "Este libro no está en este ordenador ni en tu kDrive."
    }
    DisposableEffect(id) { Lectura.libro = id; Lectura.actividad(); onDispose { Lectura.guardaYa(); Lectura.vacia(id); Lectura.libro = null } }
    LaunchedEffect(id) { while (true) { delay(15_000); Lectura.tic(id, 15_000) } }
    val f = archivo
    Box(Modifier.fillMaxSize().background(Paper)) {
        when {
            l == null -> Vacio("Este libro ya no está")
            f == null && error.isBlank() -> Vacio("Bajando de tu kDrive… ${(bajando * 100).toInt()} %")
            f == null -> Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("No se puede abrir", error) }
            f.extension.lowercase() == "epub" -> LectorEpub(l, f, volver, nav)
            f.extension.lowercase() in setOf("pdf", "cbz") -> LectorPaginas(l, f, volver, nav)
            else -> Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("Este formato no se lee aquí", "Marfil del ordenador lee EPUB, PDF y cómics (CBZ). Ábrelo en el móvil.") }
        }
    }
}

/** La barra de arriba del lector. */
@Composable
private fun BarraLector(l: Libro, volver: Pantalla, nav: Nav, extra: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        VolverRevista({ Lectura.guardaYa(); nav.ir(volver) }, texto = "Biblioteca")
        Spacer(Modifier.width(24.dp))
        Text(l.titulo, Modifier.weight(1f), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.width(24.dp))
        extra()
    }
}

@Composable
private fun LectorEpub(l: Libro, f: File, volver: Pantalla, nav: Nav) {
    var libro by remember(f) { mutableStateOf<Epub?>(null) }
    var todos by remember(f) { mutableStateOf<List<Bloque>>(emptyList()) }
    var inicio by remember(f) { mutableStateOf(0) }
    var fallo by remember(f) { mutableStateOf("") }
    LaunchedEffect(f) {
        withContext(Dispatchers.IO) {
            runCatching {
                val e = Epub(f)
                val bs = e.secciones.indices.flatMap { e.bloques(it) }
                // Por dónde ibas: la posición (la misma que guarda el móvil) o, si no encaja con este archivo, el porcentaje
                val p = Libros.posicion(l.id)
                var i = p?.cfi?.let { Cfi.lee(it) }?.bloque(e)?.let { (s, b) -> bs.indexOfFirst { it.seccion == s }.takeIf { it >= 0 }?.plus(b) }
                if (i == null && p != null && p.fraccion > 0) i = indicePorFraccion(e, bs, p.fraccion)
                libro = e; todos = bs; inicio = (i ?: 0).coerceIn(0, (bs.size - 1).coerceAtLeast(0))
            }.onFailure { fallo = it.message ?: "No se puede abrir este EPUB." }
        }
    }
    DisposableEffect(f) { onDispose { runCatching { libro?.close() } } }
    val e = libro
    if (fallo.isNotBlank()) { Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("No se puede abrir", fallo) }; return }
    if (e == null) { Vacio("Abriendo…"); return }
    val estado = rememberLazyListState(inicio)
    var tam by remember { mutableStateOf(20f) }
    var fondo by remember { mutableStateOf(Fondo.TEMA) }
    var indice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val foco = remember { FocusRequester() }
    // Por dónde vas: al pararte, se apunta (en la cuenta, a los pocos segundos; al salir, al momento)
    LaunchedEffect(estado) {
        var primera = true
        snapshotFlow { estado.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { i ->
            if (primera) { primera = false; return@collectLatest }
            Lectura.actividad()
            val b = todos.getOrNull(i) ?: return@collectLatest
            val guarda = { Libros.leido(l.id, Cfi.de(e, b), fraccionDe(e, todos, i), etiqueta(e, todos, i)) }
            Lectura.apunta(guarda)
            delay(4_000)
            withContext(Dispatchers.IO) { Lectura.guardaYa() }
        }
    }
    val (papel, tinta) = when (fondo) { Fondo.TEMA -> Paper to Charcoal; Fondo.PAPEL -> androidx.compose.ui.graphics.Color(0xFFFBF7EF) to androidx.compose.ui.graphics.Color(0xFF1C1915); Fondo.SEPIA -> androidx.compose.ui.graphics.Color(0xFFF1E4CC) to androidx.compose.ui.graphics.Color(0xFF3A2E1F); Fondo.NOCHE -> androidx.compose.ui.graphics.Color(0xFF141210) to androidx.compose.ui.graphics.Color(0xFFD9D0C1) }
    Column(Modifier.fillMaxSize().background(papel).focusRequester(foco).focusable().onPreviewKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        Lectura.actividad()
        val salto = when (k.key) { Key.PageDown, Key.Spacebar -> 1; Key.PageUp -> -1; else -> 0 }
        if (salto != 0) { scope.launch { estado.animateScrollBy(salto * 0.85f * estado.layoutInfo.viewportSize.height) }; true } else false
    }) {
        BarraLector(l, volver, nav) {
            EnlaceRevista("Índice") { indice = !indice }
            Spacer(Modifier.width(18.dp))
            EnlaceRevista("A−") { tam = (tam - 1).coerceAtLeast(14f) }
            Spacer(Modifier.width(10.dp))
            EnlaceRevista("A+") { tam = (tam + 1).coerceAtMost(32f) }
            Spacer(Modifier.width(18.dp))
            EnlaceRevista(fondo.nombre) { fondo = Fondo.entries[(fondo.ordinal + 1) % Fondo.entries.size] }
        }
        Row(Modifier.weight(1f)) {
            if (indice) Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 12.dp)) {
                Antetitulo("Índice")
                e.indice.forEach { en ->
                    Text(en.titulo, Modifier.padding(start = (en.nivel * 14).dp, top = 8.dp).clickable {
                        val i = destino(e, todos, en)
                        scope.launch { estado.scrollToItem(i) }; indice = false
                    }, color = tinta, fontFamily = Display, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (e.indice.isEmpty()) Text("Este libro no trae índice.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        itemsIndexed(todos) { i, b ->
                            Box(Modifier.widthIn(max = (tam * 34).dp).fillMaxWidth().padding(horizontal = 28.dp)) { BloqueVista(e, b, tam, tinta, i > 0 && todos[i - 1].seccion != b.seccion) }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        val i = estado.firstVisibleItemIndex
        Text("${(fraccionDe(e, todos, i) * 100).toInt()} %  ·  ${etiqueta(e, todos, i)}", Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Muted, fontFamily = Body, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}

/** Un bloque del libro, pintado. */
@Composable
private fun BloqueVista(e: Epub, b: Bloque, tam: Float, tinta: androidx.compose.ui.graphics.Color, nuevaSeccion: Boolean) {
    val alto = (tam * 1.6f).sp
    Column {
        if (nuevaSeccion) Spacer(Modifier.height((tam * 3).dp))
        when (b.tipo) {
            Bloque.Tipo.TITULO1 -> Text(b.texto, Modifier.fillMaxWidth().padding(vertical = (tam * 0.8f).dp), color = tinta, fontFamily = Display, fontSize = (tam * 1.7f).sp, lineHeight = (tam * 2f).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Bloque.Tipo.TITULO2 -> Text(b.texto, Modifier.fillMaxWidth().padding(vertical = (tam * 0.6f).dp), color = tinta, fontFamily = Display, fontSize = (tam * 1.35f).sp, lineHeight = (tam * 1.7f).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Bloque.Tipo.TITULO3 -> Text(b.texto, Modifier.fillMaxWidth().padding(vertical = (tam * 0.5f).dp), color = tinta, fontFamily = Display, fontSize = (tam * 1.12f).sp, lineHeight = (tam * 1.5f).sp, fontWeight = FontWeight.Bold)
            Bloque.Tipo.CITA -> Text(b.texto, Modifier.fillMaxWidth().padding(start = (tam * 1.4f).dp, top = 4.dp, bottom = 4.dp), color = tinta, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = alto)
            Bloque.Tipo.SEPARADOR -> Text("* * *", Modifier.fillMaxWidth().padding(vertical = (tam * 0.8f).dp), color = Muted, fontFamily = Display, fontSize = tam.sp, textAlign = TextAlign.Center)
            Bloque.Tipo.IMAGEN -> {
                val img by produceState<ImageBitmap?>(null, b.imagen) { value = withContext(Dispatchers.IO) { runCatching { e.bytes(b.imagen)?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() } }.getOrNull() } }
                img?.let { Image(it, b.texto.text, Modifier.fillMaxWidth().heightIn(max = 760.dp).padding(vertical = 10.dp), contentScale = ContentScale.Fit) }
            }
            Bloque.Tipo.PARRAFO -> Text(b.texto, Modifier.fillMaxWidth().padding(bottom = (tam * 0.55f).dp), color = tinta, fontFamily = Display, fontSize = tam.sp, lineHeight = alto, textAlign = TextAlign.Justify)
        }
    }
}

/** El porcentaje leído en el bloque [i] (como el móvil: por el tamaño de cada capítulo, y dentro de él por lo que llevas). */
fun fraccionDe(e: Epub, bs: List<Bloque>, i: Int): Double {
    val b = bs.getOrNull(i) ?: return 0.0
    val antes = e.secciones.take(b.seccion).sumOf { it.tamano }
    val deLa = bs.filter { it.seccion == b.seccion }
    val total = deLa.sumOf { it.largo }.coerceAtLeast(1)
    val hasta = deLa.takeWhile { it !== b }.sumOf { it.largo }
    return ((antes + e.secciones[b.seccion].tamano * (hasta.toDouble() / total)) / e.total).coerceIn(0.0, 1.0)
}

fun indicePorFraccion(e: Epub, bs: List<Bloque>, fr: Double): Int {
    if (bs.isEmpty()) return 0
    var lo = 0; var hi = bs.size - 1
    while (lo < hi) { val m = (lo + hi + 1) / 2; if (fraccionDe(e, bs, m) <= fr) lo = m else hi = m - 1 }
    return lo
}

/** El capítulo en el que estás (del índice del libro). */
fun etiqueta(e: Epub, bs: List<Bloque>, i: Int): String {
    val b = bs.getOrNull(i) ?: return ""
    return e.indice.lastOrNull { en -> en.seccion < b.seccion || (en.seccion == b.seccion && destino(e, bs, en) <= i) }?.titulo.orEmpty()
}

/** El bloque al que lleva una entrada del índice. */
fun destino(e: Epub, bs: List<Bloque>, en: Entrada): Int {
    val primero = bs.indexOfFirst { it.seccion == en.seccion }.coerceAtLeast(0)
    if (en.ancla.isBlank()) return primero
    return bs.indexOfFirst { it.seccion == en.seccion && en.ancla in it.ids }.takeIf { it >= 0 } ?: primero
}

/** PDF y cómics: las páginas una debajo de otra, a lo ancho que quieras. */
@Composable
private fun LectorPaginas(l: Libro, f: File, volver: Pantalla, nav: Nav) {
    val doc = remember(f) { runCatching { if (f.extension.lowercase() == "pdf") Pdf(f) else Comic(f) }.getOrNull() }
    DisposableEffect(f) { onDispose { runCatching { doc?.close() } } }
    if (doc == null || doc.total == 0) { Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("No se puede abrir este archivo") }; return }
    val p = remember(f) { Libros.posicion(l.id) }
    val inicio = remember(f) { (p?.cfi?.let { PosicionPaginas.pagina(it) } ?: p?.fraccion?.let { (it * (doc.total - 1)).toInt() } ?: 0).coerceIn(0, doc.total - 1) }
    val estado = rememberLazyListState(inicio)
    var ancho by remember { mutableStateOf(900) }
    LaunchedEffect(estado) {
        var primera = true
        snapshotFlow { estado.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { i ->
            if (primera) { primera = false; return@collectLatest }
            Lectura.actividad()
            Lectura.apunta { Libros.leido(l.id, PosicionPaginas.cfi(i), if (doc.total <= 1) 1.0 else i.toDouble() / (doc.total - 1), "Página ${i + 1}") }
            delay(4_000)
            withContext(Dispatchers.IO) { Lectura.guardaYa() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BarraLector(l, volver, nav) {
            EnlaceRevista("−") { ancho = (ancho - 120).coerceAtLeast(400) }
            Spacer(Modifier.width(10.dp))
            EnlaceRevista("+") { ancho = (ancho + 120).coerceAtMost(1800) }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(doc.total) { i ->
                    val img by produceState<ImageBitmap?>(null, i, ancho) { value = withContext(Dispatchers.IO) { doc.pagina(i, ancho) } }
                    Box(Modifier.width(ancho.dp).aspectRatio(img?.let { it.width.toFloat() / it.height } ?: 0.7f).background(Crudo)) {
                        img?.let { Image(it, "Página ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Text("Página ${estado.firstVisibleItemIndex + 1} de ${doc.total}", Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Muted, fontFamily = Body, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

