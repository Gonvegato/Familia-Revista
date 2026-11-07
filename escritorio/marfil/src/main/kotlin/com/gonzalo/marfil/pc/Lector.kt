package com.gonzalo.marfil.pc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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

/** El lector: el libro entero por donde ibas (aquí o en el móvil), con lo que hayas subrayado en cualquier dispositivo. */
@Composable
fun PantLector(id: String, volver: Pantalla, nav: Nav, ir: String = "", panel: Panel? = null, busqueda: String = "") {
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
            f == null && error.isBlank() -> Column(Modifier.align(Alignment.Center).width(360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l.titulo, color = Charcoal, fontFamily = Display, fontSize = 28.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(Sand)) { Box(Modifier.fillMaxWidth(bajando.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
                Spacer(Modifier.height(8.dp))
                Antetitulo("Bajando de tu kDrive · ${(bajando * 100).toInt()} %", tam = 10.sp)
            }
            f == null -> Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("No se puede abrir", error) }
            f.extension.lowercase() == "epub" -> LectorEpub(l, f, volver, nav, ir, panel, busqueda)
            f.extension.lowercase() in setOf("pdf", "cbz") -> LectorPaginas(l, f, volver, nav)
            else -> Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("Este formato no se lee aquí", "Seshat del ordenador lee EPUB, PDF y cómics (CBZ). Ábrelo en el móvil.") }
        }
    }
}

/** La barra de arriba del lector de PDF y cómics. */
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

/** PDF y cómics: las páginas una debajo de otra, a lo ancho que quieras; con el teclado se pasa de página en página. */
@Composable
private fun LectorPaginas(l: Libro, f: File, volver: Pantalla, nav: Nav) {
    val doc = remember(f) { runCatching { if (f.extension.lowercase() == "pdf") Pdf(f) else Comic(f) }.getOrNull() }
    DisposableEffect(f) { onDispose { runCatching { doc?.close() } } }
    if (doc == null || doc.total == 0) { Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("No se puede abrir este archivo") }; return }
    val p = remember(f) { Libros.posicion(l.id) }
    val inicio = remember(f) { (p?.cfi?.let { PosicionPaginas.pagina(it) } ?: p?.fraccion?.let { (it * (doc.total - 1)).toInt() } ?: 0).coerceIn(0, doc.total - 1) }
    val estado = rememberLazyListState(inicio)
    var ancho by remember { mutableStateOf(900) }
    val scope = rememberCoroutineScope()
    val foco = remember { FocusRequester() }
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
    fun aPagina(i: Int) = scope.launch { estado.animateScrollToItem(i.coerceIn(0, doc.total - 1)) }
    Column(Modifier.fillMaxSize().focusRequester(foco).focusable().onKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown) return@onKeyEvent false
        Lectura.actividad()
        val i = estado.firstVisibleItemIndex
        when (k.key) {
            Key.DirectionRight, Key.PageDown -> { aPagina(i + 1); true }
            Key.DirectionLeft, Key.PageUp -> { aPagina(if (estado.firstVisibleItemScrollOffset > 0) i else i - 1); true }
            Key.Spacebar -> { scope.launch { estado.animateScrollBy((if (k.isShiftPressed) -1 else 1) * 0.85f * estado.layoutInfo.viewportSize.height) }; true }
            Key.DirectionDown -> { scope.launch { estado.animateScrollBy(140f) }; true }
            Key.DirectionUp -> { scope.launch { estado.animateScrollBy(-140f) }; true }
            Key.MoveHome -> { aPagina(0); true }
            Key.MoveEnd -> { aPagina(doc.total - 1); true }
            else -> if ((k.isCtrlPressed || k.isMetaPressed) && (k.key == Key.Equals || k.key == Key.Plus)) { ancho = (ancho + 120).coerceAtMost(1800); true }
                else if ((k.isCtrlPressed || k.isMetaPressed) && k.key == Key.Minus) { ancho = (ancho - 120).coerceAtLeast(400); true } else false
        }
    }) {
        BarraLector(l, volver, nav) {
            EnlaceRevista("−") { ancho = (ancho - 120).coerceAtLeast(400) }
            Spacer(Modifier.width(10.dp))
            EnlaceRevista("+") { ancho = (ancho + 120).coerceAtMost(1800) }
        }
        Filete(1.dp, Line)
        Box(Modifier.weight(1f).background(Sand.copy(alpha = 0.5f))) {
            LazyColumn(Modifier.fillMaxSize(), estado, contentPadding = PaddingValues(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(doc.total) { i ->
                    val img by produceState<ImageBitmap?>(null, i, ancho) { value = withContext(Dispatchers.IO) { doc.pagina(i, ancho) } }
                    Box(Modifier.width(ancho.dp).aspectRatio(img?.let { it.width.toFloat() / it.height } ?: 0.7f).background(Crudo)) {
                        img?.let { Image(it, "Página ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(estado), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Filete(1.dp, Line)
        // Lo leído: un clic en la barra lleva a esa página
        val i = estado.firstVisibleItemIndex
        val fr = if (doc.total <= 1) 1f else i.toFloat() / (doc.total - 1)
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxWidth().height(18.dp).mano.pointerInput(doc) {
                awaitPointerEventScope { while (true) { val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull() ?: continue
                    if (ev.type == PointerEventType.Release) aPagina(((ch.position.x / size.width.coerceAtLeast(1)) * (doc.total - 1)).toInt()) } }
            }) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2
                    drawRect(Line, Offset(0f, y - 1.5f), Size(size.width, 3f)); drawRect(Acento, Offset(0f, y - 1.5f), Size(size.width * fr, 3f))
                    drawCircle(Acento, 4.5f * density, Offset(size.width * fr, y))
                }
            }
            Row {
                Text("Página ${i + 1} de ${doc.total}", color = Muted, fontFamily = Body, fontSize = 11.5.sp)
                Spacer(Modifier.weight(1f))
                Text("${(fr * 100).toInt()} %", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
}
