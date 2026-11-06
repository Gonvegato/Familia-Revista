package com.gonzalo.marfil.pc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.VentanaFamilia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** El EPUB: se abre (en segundo plano) por donde ibas en cualquier dispositivo y se lee en páginas o deslizando. */
@Composable
fun LectorEpub(l: Libro, f: File, volver: Pantalla, nav: Nav, ir: String = "", panel: Panel? = null, busqueda: String = "") {
    var estado by remember(f) { mutableStateOf<EstadoLector?>(null) }
    var fallo by remember(f) { mutableStateOf("") }
    LaunchedEffect(f) {
        withContext(Dispatchers.IO) {
            runCatching {
                val e = Epub(f)
                val libro = LibroAbierto(e)
                e.secciones.indices.forEach { e.seccion(it) }
                // Por dónde ibas: lo más lejos que has llegado en cualquier dispositivo, salvo que donde lo dejaste vaya más allá (como el móvil)
                val p = Libros.posicion(l.id)
                val (cfi, fr) = p?.let { if (it.mejorFraccion > 0 && it.mejorFraccion >= it.fraccion && it.mejorCfi.isNotBlank()) it.mejorCfi to it.mejorFraccion else it.cfi to it.fraccion } ?: ("" to 0.0)
                fun lugar(c: String) = c.takeIf { it.isNotBlank() }?.let { e.ubica(it) }?.let { u -> u.desde?.let { Sitio(u.seccion, it.first, it.second) } ?: Sitio(u.seccion, 0) }
                val donde = lugar(cfi) ?: if (fr > 0) libro.deFraccion(fr).let { (s, x) -> libro.sitioEnSeccion(s, x) } else Sitio(libro.conTexto.firstOrNull() ?: 0, 0)
                // Desde la ficha se puede abrir justo en un subrayado (y se puede volver a donde ibas)
                val pedido = lugar(ir)
                estado = EstadoLector(l, libro, pedido ?: donde, fr).also { st ->
                    if (pedido != null) { st.vuelta = donde to fr; e.ubica(ir)?.hasta?.let { h -> st.destello = pedido to Sitio(pedido.seccion, h.first, h.second) } }
                    st.panel = panel; st.busqueda = busqueda
                }
            }.onFailure { fallo = it.message ?: "No se puede abrir este EPUB." }
        }
    }
    DisposableEffect(f) { onDispose { runCatching { estado?.epub?.close() } } }
    if (fallo.isNotBlank()) { Column(Modifier.padding(40.dp)) { VolverRevista({ nav.ir(volver) }); Vacio("No se puede abrir", fallo) }; return }
    val st = estado ?: run { Box(Modifier.fillMaxSize().background(Paper)) { Vacio("Abriendo…", modifier = Modifier.align(Alignment.Center)) }; return }
    VistaLector(st, volver, nav)
}

/** La forma de la página: cuánto mide (px), dónde va cada una y si se ven dos (el libro abierto). */
class Geometria(val ancho: Int, val alto: Int, val anchoTexto: Int, val altoTexto: Int, val origenes: List<IntOffset>) {
    val dos: Boolean get() = origenes.size == 2
}

/** Lo que hay bajo el ratón: la sección, el bloque y la letra (y si cae de verdad sobre el texto). */
private class Golpe(val seccion: Int, val bloque: Int, val letra: Int, val capa: TextLayoutResult?, val encima: Boolean)

/** Las imágenes del libro ya leídas (por su ruta en el EPUB). */
private val imagenes = ConcurrentHashMap<String, ImageBitmap>()
private val sinImagen = ConcurrentHashMap.newKeySet<String>()
private fun imagenDe(e: Epub, ruta: String): ImageBitmap? {
    imagenes[ruta]?.let { return it }
    if (ruta in sinImagen) return null
    return runCatching { e.bytes(ruta)?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() } }.getOrNull()
        ?.also { imagenes[ruta] = it } ?: run { sinImagen += ruta; null }
}

@Composable
private fun VistaLector(st: EstadoLector, volver: Pantalla, nav: Nav) {
    LaFamilia.cambios
    val scope = rememberCoroutineScope()
    val foco = remember { FocusRequester() }
    val dens = LocalDensity.current
    val a = st.ajustes
    val tema = a.temaAhora()
    val oscuro = tema in AjustesLectura.OSCUROS || (tema == "app" && Modo.oscuro)
    val cols = AjustesLectura.colores(tema, listOf(Paper, Charcoal, Muted, Crudo))
    val c = Colores(cols[0], cols[1], cols[2], cols[3], if (tema == "app") Acento else Color(Skin.tonosPara(oscuro).general), oscuro)
    // Los subrayados y marcadores de la cuenta (los del móvil y los de aquí), y dónde cae cada uno en este archivo
    val marcas = Marcas.de(st.l.id).filter { !it.delTextoDeUnPdf }
    val subrayados = marcas.filter { it.esSubrayado }
    val marcadores = marcas.filter { !it.esSubrayado }
    val lugares = remember(st) { HashMap<String, Epub.Lugar?>() }
    fun lugar(m: Marca) = lugares.getOrPut(m.valor) { runCatching { st.epub.ubica(m.valor) }.getOrNull() }
    /** Los subrayados de una sección, por bloque: el tramo de letras y su marca. */
    fun deSeccion(s: Int): Map<Int, List<Pair<IntRange, Marca>>> {
        val r = HashMap<Int, MutableList<Pair<IntRange, Marca>>>()
        for (m in subrayados) {
            val u = lugar(m) ?: continue
            if (u.seccion != s) continue
            val (b1, l1) = u.desde ?: continue; val (b2, l2) = u.hasta ?: continue
            for (b in b1..b2) { val bl = st.libro.bloques(s).getOrNull(b) ?: continue; val ini = if (b == b1) l1 else 0; val fin = if (b == b2) l2 else bl.largo; if (fin > ini) r.getOrPut(b) { ArrayList() } += (ini until fin) to m }
        }
        return r
    }
    val subSeccion = remember(subrayados.map { it.valor + it.nota }, st.sitio.seccion) { deSeccion(st.sitio.seccion) }

    // Por dónde vas: al pararte se apunta (en la cuenta, a los pocos segundos; al salir, al momento), y un salto largo pregunta, como en el móvil
    LaunchedEffect(st) {
        var primera = true
        var antes: Pair<Double, Long>? = null
        snapshotFlow { Triple(st.sitio, st.pagina, st.ajustes.flujo) }.distinctUntilChanged().collectLatest { (x, pg, flujo) ->
            if (flujo == "paginated" && pg < 0) return@collectLatest
            if (primera) { primera = false; antes = st.fraccion to System.currentTimeMillis(); return@collectLatest }
            Lectura.actividad()
            val fr = st.fraccion
            // Tu ritmo (bytes por minuto, como lo mide el móvil): al pasar una página normal, sin pararte demasiado
            antes?.let { (f0, t0) ->
                val dt = System.currentTimeMillis() - t0
                val bytes = (fr - f0) * st.epub.total
                if (st.direccion > 0 && bytes > 0 && dt in 4_000..300_000 && bytes < 6 * 1500) {
                    val bpm = bytes / (dt / 60_000.0)
                    if (bpm in 200.0..20_000.0) { val v = st.ajustes.velocidad; st.ajustes = st.ajustes.con("speed", (if (v > 0) v * 0.85 + bpm * 0.15 else bpm).roundToInt().toLong()) }
                }
            }
            antes = fr to System.currentTimeMillis()
            val b = st.libro.bloques(x.seccion).getOrNull(x.bloque) ?: return@collectLatest
            val cfi = st.epub.cfiDe(b, x.letra)
            val etiqueta = st.libro.etiqueta(x)
            val salto = abs(fr - st.ancla) >= 0.25 - 1e-9
            if (!salto) { st.ancla = fr; st.preguntado = false }
            else if (!st.preguntado) {
                st.preguntado = true
                val de = (st.ancla * 100).toInt(); val aa = (fr * 100).toInt()
                st.avisa("Has saltado del $de % al $aa %. ¿Vas por aquí de verdad?", "Voy por aquí") {
                    scope.launch(Dispatchers.IO) { runCatching { Libros.fija(st.l.id, cfi, fr, etiqueta) } }
                    st.ancla = fr; st.preguntado = false
                    st.avisa("Marcado: vas por el ${(fr * 100).toInt()} %")
                }
            }
            Lectura.apunta { Libros.leido(st.l.id, cfi, fr, etiqueta, salto = salto) }
            delay(4_000)
            withContext(Dispatchers.IO) { Lectura.guardaYa() }
        }
    }
    // Los ajustes que cambias aquí van a la cuenta (este libro, en todos tus dispositivos; y de partida para los demás)
    LaunchedEffect(st) {
        var primera = true
        snapshotFlow { st.ajustes }.distinctUntilChanged().collectLatest { aj ->
            if (primera) { primera = false; return@collectLatest }
            // (la velocidad sola no merece una subida cada página: se junta)
            delay(if (aj.m.filterKeys { it != "speed" } == AjustesLectura.de(st.l.id).m.filterKeys { it != "speed" }) 180_000 else 1_200)
            withContext(Dispatchers.IO) { runCatching { AjustesLectura.guarda(st.l.id, aj) } }
        }
    }
    LaunchedEffect(st.aviso) { val av = st.aviso ?: return@LaunchedEffect; delay((av.hasta - System.currentTimeMillis()).coerceAtLeast(0)); if (st.aviso === av) st.aviso = null }
    LaunchedEffect(st.destello) { if (st.destello != null) { delay(2600); st.destello = null } }
    // Lo que significa una palabra (el Wikcionario, en el idioma del libro)
    LaunchedEffect(st.definicion) {
        val d = st.definicion ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { Diccionario.define(d.palabra, Diccionario.lengua(st.epub.idioma)) } }
            .onSuccess { d.resultado = it }.onFailure { d.fallo = "No se ha podido consultar el Wikcionario (¿sin conexión?)." }
    }

    fun copia(t: String) { runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(t), null) }; st.avisa("Copiado") }
    fun textoDe(sel: Seleccion): String {
        val bs = st.libro.bloques(sel.seccion)
        return (sel.desde.first..sel.hasta.first).mapNotNull { i -> bs.getOrNull(i)?.let { b -> sel.en(i, b.largo)?.let { r -> b.texto.text.substring(r.first, r.last + 1) } } }
            .joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }
    fun subraya(sel: Seleccion, conNota: Boolean) {
        val bs = st.libro.bloques(sel.seccion)
        val (b1, l1) = sel.desde; val (b2, l2) = sel.hasta
        val cfi = st.epub.cfiTramo(bs[b1], l1, bs[b2], l2) ?: return
        st.seleccion = null; st.menu = null
        subrayados.firstOrNull { it.valor == cfi }?.let { if (conNota) st.editando = it; return }
        val texto = textoDe(sel).takeIf { it.isNotBlank() } ?: return
        val x = Sitio(sel.seccion, b1, l1)
        val m = Marcas.subraya(st.l.id, cfi, texto, sel.seccion, st.libro.etiqueta(x), st.libro.fraccion(sel.seccion, st.libro.fraccionEnSeccion(x)))
        if (conNota) st.editando = m else st.avisa("Subrayado")
    }
    fun quita(m: Marca) {
        Marcas.quita(m); st.menu = null
        st.avisa(if (m.esSubrayado) "Subrayado quitado" else "Marcador quitado", "Deshacer") { Marcas.recupera(m) }
    }
    /** El marcador de lo que se ve (si lo hay). */
    fun marcadorAqui(): Marca? {
        val p = st.paginados[st.sitio.seccion]
        return marcadores.firstOrNull { m ->
            val u = lugar(m)
            if (u != null && u.seccion == st.sitio.seccion && u.desde != null) {
                if (st.ajustes.flujo == "paginated" && p != null && st.pagina >= 0) p.paginaDe(u.desde.first, u.desde.second) in st.pagina until st.pagina + st.porVista
                else abs(st.libro.fraccion(u.seccion, st.libro.fraccionEnSeccion(Sitio(u.seccion, u.desde.first, u.desde.second))) - st.fraccionInicio) < 0.004
            } else abs((m.fraccion ?: -1.0) - st.fraccion) < 0.0025
        }
    }
    fun alternaMarcador() {
        marcadorAqui()?.let { quita(it); return }
        val b = st.libro.bloques(st.sitio.seccion).getOrNull(st.sitio.bloque) ?: return
        val fr = st.fraccion
        Marcas.marca(st.l.id, st.epub.cfiDe(b, st.sitio.letra), st.libro.etiqueta(st.sitio).ifBlank { "${(fr * 100).toInt()} %" }, fr, st.sitio.seccion)
        st.avisa("Página marcada")
    }
    fun abreEnlace(en: Epub.Enlace, x: Float, y: Float) {
        if (en.externo.isNotBlank()) { Thread { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(en.externo)) } }.start(); return }
        if (en.nota || en.quizas) st.epub.textoDeNota(en)?.let { (nombre, textos) -> st.nota = NotaAbierta(nombre, textos, en, x, y); return }
        st.saltaA(st.libro.destino(en))
    }
    fun cierraTodo(): Boolean = when {
        st.editando != null -> { st.editando = null; true }
        st.nota != null -> { st.nota = null; true }
        st.definicion != null -> { st.definicion = null; true }
        st.menu != null -> { st.menu = null; st.seleccion = null; true }
        st.seleccion != null -> { st.seleccion = null; true }
        st.panel != null -> { st.panel = null; true }
        st.inmersivo -> { st.inmersivo = false; VentanaFamilia.pantallaCompleta = false; true }
        else -> false
    }
    /** Solo el libro: sin barras y la ventana a pantalla completa (F, o Esc para volver). */
    fun alternaInmersivo() {
        st.inmersivo = !st.inmersivo
        VentanaFamilia.pantallaCompleta = st.inmersivo
        if (st.inmersivo) st.avisa("Solo el libro · F o Esc para volver")
    }

    val lista = rememberLazyListState()
    val continuo = a.flujo == "scrolled"
    LaunchedEffect(continuo) { if (!continuo) st.pagina = st.paginados[st.sitio.seccion]?.let { st.alinea(it.paginaDe(st.sitio.bloque, st.sitio.letra)) } ?: -1 }
    // En continuo: cada bloque del libro en orden (la sección y el bloque), y el principio de cada sección en esa lista
    val todos = remember(st) { st.libro.secciones.indices.flatMap { s -> st.libro.bloques(s).indices.map { s to it } } }
    fun indiceDe(x: Sitio): Int = todos.indexOfFirst { it.first == x.seccion && it.second >= x.bloque }.let { if (it < 0) todos.indexOfFirst { p -> p.first >= x.seccion }.coerceAtLeast(0) else it }

    fun avanza(dir: Int) {
        Lectura.actividad()
        if (continuo) scope.launch { lista.animateScrollBy(dir * lista.layoutInfo.viewportSize.height * 0.86f) }
        else if (dir > 0) { if (!st.siguiente() && st.vecina(st.sitio.seccion, 1) == null && st.paginados[st.sitio.seccion] != null) st.avisa("Fin del libro") }
        else { if (!st.anterior() && st.vecina(st.sitio.seccion, -1) == null && st.paginados[st.sitio.seccion] != null) st.avisa("Principio del libro") }
    }

    Box(Modifier.fillMaxSize().background(c.papel).focusRequester(foco).focusable().onKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown) return@onKeyEvent false
        Lectura.actividad()
        val mando = k.isCtrlPressed || k.isMetaPressed
        when {
            mando && k.key == Key.F -> { st.panel = Panel.BUSCAR; true }
            mando && k.key == Key.C -> { st.seleccion?.let { copia(textoDe(it)) }; true }
            mando && (k.key == Key.Equals || k.key == Key.Plus || k.key == Key.NumPadAdd) -> { st.ajustes = st.ajustes.con("size", (a.tamano + 10).coerceAtMost(240).toLong()); true }
            mando && (k.key == Key.Minus || k.key == Key.NumPadSubtract) -> { st.ajustes = st.ajustes.con("size", (a.tamano - 10).coerceAtLeast(60).toLong()); true }
            mando && k.key == Key.D -> { alternaMarcador(); true }
            mando -> false
            k.key == Key.Escape -> cierraTodo()
            k.key == Key.DirectionRight || k.key == Key.PageDown || (k.key == Key.Spacebar && !k.isShiftPressed) -> { avanza(1); true }
            k.key == Key.DirectionLeft || k.key == Key.PageUp || (k.key == Key.Spacebar && k.isShiftPressed) -> { avanza(-1); true }
            k.key == Key.DirectionDown -> { if (continuo) scope.launch { lista.animateScrollBy(120f) } else avanza(1); true }
            k.key == Key.DirectionUp -> { if (continuo) scope.launch { lista.animateScrollBy(-120f) } else avanza(-1); true }
            k.key == Key.MoveHome -> { if (continuo) scope.launch { lista.scrollToItem(indiceDe(Sitio(st.sitio.seccion, 0))) } else st.extremoDelCapitulo(false); true }
            k.key == Key.MoveEnd -> { if (continuo) scope.launch { lista.scrollToItem(indiceDe(Sitio(st.vecina(st.sitio.seccion, 1) ?: st.sitio.seccion, 0)).let { if (it > 0) it - 1 else it }) } else st.extremoDelCapitulo(true); true }
            k.key == Key.F -> { alternaInmersivo(); true }
            k.key == Key.B -> { alternaMarcador(); true }
            k.key == Key.T || k.key == Key.I -> { st.panel = if (st.panel == Panel.INDICE) null else Panel.INDICE; true }
            k.key == Key.N -> { st.panel = if (st.panel == Panel.NOTAS) null else Panel.NOTAS; true }
            else -> false
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            if (!st.inmersivo) BarraArriba(st, c, volver, nav, marcadorAqui() != null, ::alternaMarcador, ::alternaInmersivo)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().onGloballyPositioned { st.origenArea = it.positionInRoot() }) {
                val W = constraints.maxWidth; val H = constraints.maxHeight
                val em = with(dens) { (LETRA_BASE * a.tamano / 100f).sp.toPx() }
                val margenV = with(dens) { when (a.margen) { "s" -> 26.dp; "l" -> 62.dp; else -> 42.dp }.toPx() }
                val lado = maxOf(with(dens) { 28.dp.toPx() }, W * when (a.margen) { "s" -> 0.04f; "l" -> 0.11f; else -> 0.07f })
                // Dos páginas si lo pides («Dos en horizontal») y caben dos columnas de al menos 24 letras «m» de ancho
                val dos = !continuo && a.columnas && (W - 2 * lado - 3 * em) / 2 >= 24 * em
                val anchoTexto = if (dos) minOf(30 * em, (W - 2 * lado - 3.2f * em) / 2) else minOf(34 * em, W - 2 * lado)
                val altoTexto = (H - 2 * margenV).coerceAtLeast(4 * em)
                val hueco = if (dos) maxOf(3.2f * em, (W - 2 * anchoTexto) / 3f).coerceAtMost(7 * em) else 0f
                val origenes = if (dos) listOf(IntOffset(((W - hueco) / 2 - anchoTexto).roundToInt(), margenV.roundToInt()), IntOffset(((W + hueco) / 2).roundToInt(), margenV.roundToInt()))
                    else listOf(IntOffset(((W - anchoTexto) / 2).roundToInt(), margenV.roundToInt()))
                val geo = Geometria(W, H, anchoTexto.toInt(), altoTexto.toInt(), origenes)
                st.porVista = if (dos) 2 else 1
                val claveAhora = "${a.m.filterKeys { it != "speed" && it != "theme" && it != "warm" && it != "nightAuto" }}|${geo.anchoTexto}|${geo.altoTexto}|$dos|${dens.density}"
                SideEffect { if (st.clave != claveAhora) st.clave = claveAhora }
                if (continuo) AreaContinua(st, c, geo, todos, lista, ::indiceDe, ::deSeccion, alAbrirEnlace = ::abreEnlace)
                else AreaPaginada(st, c, geo, subSeccion, alAbrirEnlace = ::abreEnlace, avanza = ::avanza)
                if (marcadorAqui() != null) Canvas(Modifier.align(Alignment.TopEnd).padding(end = 26.dp).size(16.dp, 30.dp)) {
                    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(size.width / 2, size.height * 0.8f); lineTo(0f, size.height); close() }, c.acento)
                }
            }
            if (!st.inmersivo) BarraAbajo(st, c)
        }
        // La luz cálida (menos luz azul), encima de todo el libro
        if (a.calida > 0) Canvas(Modifier.fillMaxSize()) { drawRect(Color(1f, 0.47f, 0.08f, (a.calida * 0.28).toFloat()), blendMode = BlendMode.Multiply) }
        // Volver a donde estabas
        st.vuelta?.let { (x, fr) ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (st.inmersivo) 24.dp else 76.dp)) {
                PildoraLector("← Volver a donde estabas (${(fr * 100).toInt()} %)", c) { st.vuelta = null; st.ve(x, -1) }
            }
        }
        st.menu?.let { MenuDeTexto(st, it, c, onSubrayar = { s, n -> subraya(s, n) }, onCopiar = ::copia, onQuitar = ::quita, textoDe = ::textoDe) }
        st.nota?.let { NotaAlPieVista(st, it, c) }
        st.definicion?.let { DefinicionVista(st, it, c) }
        PanelesLector(st, c, onQuitar = ::quita)
        st.editando?.let { EditorNota(st, it, c) }
        st.aviso?.let { av -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (st.inmersivo) 70.dp else 120.dp)) { AvisoVista(av, c) { st.aviso = null } } }
    }
    LaunchedEffect(st.panel, st.editando) { if (st.panel == null && st.editando == null) runCatching { foco.requestFocus() } }
    // Si sales de la pantalla completa (F11), vuelven las barras; al cerrar el libro, la ventana como estaba
    LaunchedEffect(VentanaFamilia.pantallaCompleta) { if (!VentanaFamilia.pantallaCompleta && st.inmersivo) st.inmersivo = false }
    DisposableEffect(st) { onDispose { if (st.inmersivo) VentanaFamilia.pantallaCompleta = false } }
}

// ---------------------------------------------------------------- páginas

@Composable
private fun AreaPaginada(st: EstadoLector, c: Colores, geo: Geometria, subSeccion: Map<Int, List<Pair<IntRange, Marca>>>, alAbrirEnlace: (Epub.Enlace, Float, Float) -> Unit, avanza: (Int) -> Unit) {
    val dens = LocalDensity.current
    val resolvedor = LocalFontFamilyResolver.current
    val medidor = remember(dens, resolvedor) { TextMeasurer(resolvedor, dens, LayoutDirection.Ltr, 0) }
    val geoP by rememberUpdatedState(geo)
    // Los capítulos en páginas (el de ahora y los de al lado), en segundo plano; mientras, se sigue viendo lo de antes
    LaunchedEffect(st) {
        snapshotFlow { st.clave to st.sitio.seccion }.distinctUntilChanged().collectLatest { (clave, s) ->
            if (clave.isBlank()) return@collectLatest
            delay(if (st.paginados.isEmpty()) 0 else 90)
            val paginador = Paginador(medidor, { b, ant -> estiloDe(st.ajustes, dens, b, ant) }, { r -> imagenDe(st.epub, r)?.let { IntSize(it.width, it.height) } }, dens.density)
            for (x in listOfNotNull(s, st.vecina(s, 1), st.vecina(s, -1))) {
                if (st.paginados[x]?.clave == clave) continue
                val p = withContext(Dispatchers.Default) { paginador.pagina(x, st.libro.bloques(x), geoP.anchoTexto, geoP.altoTexto) }.also { it.clave = clave }
                if (x == s) {
                    st.paginados.keys.filter { st.paginados[it]?.clave != clave }.forEach { st.paginados.remove(it) }
                    st.paginados[x] = p
                    if (st.sitio.seccion == x) st.pagina = st.alinea(p.paginaDe(st.sitio.bloque, st.sitio.letra))
                } else st.paginados[x] = p
            }
        }
    }
    val p = st.paginados[st.sitio.seccion]
    if (p == null || st.pagina < 0) return
    val clave = st.sitio.seccion to st.pagina
    // (el manejo del ratón vive mientras vive el lector: lo que cambia se lee siempre al día)
    val geoA by rememberUpdatedState(geo)
    val subA by rememberUpdatedState(subSeccion)
    val avanzaA by rememberUpdatedState(avanza)
    val enlaceA by rememberUpdatedState(alAbrirEnlace)
    // El ratón: arrastrar selecciona (por palabras), un clic en un subrayado abre su menú, en una llamada abre la nota y en los
    // márgenes pasa página; la rueda (o dos dedos en el trackpad) también pasa página
    val manejo = Modifier.pointerInput(st) {
        var acumulado = 0f; var ultimoEvento = 0L; var girado = false
        var ultimoClic = 0L; var ultimoSitio = Offset.Zero
        awaitPointerEventScope {
            var bajada: Offset? = null; var arrastrando = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val ch = ev.changes.firstOrNull() ?: continue
                when (ev.type) {
                    PointerEventType.Scroll -> {
                        // Un gesto (una vuelta de la rueda, un deslizamiento en el trackpad con su inercia) pasa una sola página
                        val d = ch.scrollDelta.y + ch.scrollDelta.x
                        val ahora = System.currentTimeMillis()
                        if (ahora - ultimoEvento > 250) { acumulado = 0f; girado = false }
                        ultimoEvento = ahora
                        if (!girado) { acumulado += d; if (abs(acumulado) >= 1f) { avanzaA(if (acumulado > 0) 1 else -1); girado = true } }
                        ch.consume()
                    }
                    PointerEventType.Press -> if (ev.buttons.isPrimaryPressed) {
                        bajada = ch.position; arrastrando = false
                        Lectura.actividad()
                    }
                    PointerEventType.Move -> {
                        val b0 = bajada
                        if (b0 != null && ev.buttons.isPrimaryPressed) {
                            if (!arrastrando && (ch.position - b0).getDistance() > 5f) {
                                golpe(st, geoA, b0)?.let { g -> g.capa?.let { cp -> val w = cp.getWordBoundary(g.letra.coerceIn(0, maxOf(0, cp.layoutInput.text.length - 1)))
                                    st.menu = null; st.seleccion = Seleccion(g.seccion, g.bloque, w.start, w.end, g.bloque, w.end); arrastrando = true } }
                            }
                            if (arrastrando) golpe(st, geoA, ch.position)?.let { g ->
                                val s = st.seleccion ?: return@let
                                val cp = g.capa ?: return@let
                                val w = cp.getWordBoundary(g.letra.coerceIn(0, maxOf(0, cp.layoutInput.text.length - 1)))
                                val antes = g.bloque < s.anclaBloque || (g.bloque == s.anclaBloque && g.letra < s.anclaDesde)
                                st.seleccion = s.copy(focoBloque = g.bloque, focoLetra = if (antes) w.start else w.end)
                            }
                            ch.consume()
                        }
                    }
                    PointerEventType.Release -> {
                        val b0 = bajada; bajada = null
                        if (arrastrando) {
                            arrastrando = false
                            st.seleccion?.let { s -> st.seleccion = s.copy(terminada = true); st.menu = MenuTexto(null, s, ch.position.x, ch.position.y) }
                            ch.consume(); continue
                        }
                        if (b0 == null) continue
                        val ahora = System.currentTimeMillis()
                        val doble = ahora - ultimoClic < 420 && (ch.position - ultimoSitio).getDistance() < 8f
                        ultimoClic = ahora; ultimoSitio = ch.position
                        if (st.menu != null || st.seleccion != null || st.nota != null || st.definicion != null) { st.menu = null; st.seleccion = null; st.nota = null; st.definicion = null; if (!doble) continue }
                        val g = golpe(st, geoA, ch.position)
                        if (doble && g?.capa != null && g.encima) {
                            // Doble clic: la palabra
                            val w = g.capa.getWordBoundary(g.letra.coerceIn(0, maxOf(0, g.capa.layoutInput.text.length - 1)))
                            if (w.end > w.start) { val s = Seleccion(g.seccion, g.bloque, w.start, w.end, g.bloque, w.end, true); st.seleccion = s; st.menu = MenuTexto(null, s, ch.position.x, ch.position.y) }
                            continue
                        }
                        if (g != null && g.encima) {
                            val b = st.libro.bloques(g.seccion).getOrNull(g.bloque)
                            val enlace = b?.texto?.getStringAnnotations(ETIQUETA_ENLACE, g.letra, g.letra + 1)?.firstOrNull()
                            if (enlace != null) { st.epub.enlace(enlace.item, g.seccion)?.let { enlaceA(it, ch.position.x, ch.position.y) }; continue }
                            val m = subA[g.bloque]?.firstOrNull { g.letra in it.first }?.second
                            if (m != null) { st.menu = MenuTexto(m, null, ch.position.x, ch.position.y); continue }
                        }
                        // Los márgenes (fuera del texto) pasan página
                        val x = ch.position.x
                        val izq = geoA.origenes.first().x; val der = geoA.origenes.last().x + geoA.anchoTexto
                        if (x < izq - 8) avanzaA(-1) else if (x > der + 8) avanzaA(1)
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().then(manejo)) {
        AnimatedContent(clave, Modifier.fillMaxSize(), transitionSpec = {
            if (!st.ajustes.animado) ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
            else {
                val d = st.direccion
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { w -> d * w / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { w -> -d * w / 3 } + fadeOut(tween(180)))
            }
        }, label = "pagina") { (s, pg) ->
            val pd = st.paginados[s] ?: return@AnimatedContent
            val sub = if (s == st.sitio.seccion) subSeccion else emptyMap()
            Box(Modifier.fillMaxSize()) {
                // El lomo del libro abierto, entre las dos páginas
                if (geo.dos) Canvas(Modifier.fillMaxSize()) {
                    val x = (geo.origenes[0].x + geo.anchoTexto + geo.origenes[1].x) / 2f
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.tinta.copy(alpha = if (c.oscuro) 0.10f else 0.06f), Color.Transparent), x - 40f, x + 40f), Offset(x - 40f, 0f), Size(80f, size.height))
                }
                for (k in 0 until geo.origenes.size) {
                    val pag = pd.paginas.getOrNull(pg + k) ?: continue
                    Box(Modifier.absoluteOffset { geo.origenes[k] }) {
                        for (t in pag.trozos) {
                            val b = st.libro.bloques(s).getOrNull(t.bloque) ?: continue
                            TrozoVista(st, t, b, c, sub[t.bloque].orEmpty(), st.seleccion?.takeIf { it.seccion == s }?.en(t.bloque, b.largo),
                                st.destello?.takeIf { it.first.seccion == s && t.bloque in it.first.bloque..it.second.bloque }?.let { (d1, d2) -> (if (t.bloque == d1.bloque) d1.letra else 0) until (if (t.bloque == d2.bloque) d2.letra else b.largo) })
                        }
                    }
                }
            }
        }
    }
}

/** Lo que hay bajo el ratón en las páginas que se ven. */
private fun golpe(st: EstadoLector, geo: Geometria, pos: Offset): Golpe? {
    val p = st.paginados[st.sitio.seccion] ?: return null
    if (st.pagina < 0) return null
    val k = if (geo.dos && pos.x > geo.ancho / 2f) 1 else 0
    val pag = p.paginas.getOrNull(st.pagina + k) ?: return null
    val o = geo.origenes[k]
    val x = pos.x - o.x; val y = pos.y - o.y
    val textos = pag.trozos.filter { it.capa != null }
    if (textos.isEmpty()) return null
    val t = textos.firstOrNull { y >= it.y && y <= it.y + it.alto } ?: textos.minBy { minOf(abs(y - it.y), abs(y - it.y - it.alto)) }
    val cp = t.capa!!
    val yy = (y - t.y).coerceIn(0f, t.alto - 1f) + t.subida
    val letra = cp.getOffsetForPosition(Offset(x - t.x, yy)).coerceIn(t.letraDesde, minOf(t.letraHasta, cp.layoutInput.text.length))
    val encima = y >= t.y && y <= t.y + t.alto && x >= t.x - 4 && x <= t.x + t.ancho + 4
    return Golpe(st.sitio.seccion, t.bloque, letra, cp, encima)
}

/** Un trozo de un bloque en su página: el texto (con los subrayados al revés), lo seleccionado, lo encontrado y la raya de las notas. */
@Composable
private fun TrozoVista(st: EstadoLector, t: Trozo, b: Bloque, c: Colores, subrayados: List<Pair<IntRange, Marca>>, seleccion: IntRange?, destello: IntRange?) {
    val dens = LocalDensity.current
    val medidor = rememberTextMeasurer(48)
    val tramos = subrayados.map { it.first }
    val capa: TextLayoutResult? = t.capa?.let { base ->
        val texto = remember(b, tramos, c.tinta, c.papel, c.acento) { textoPintado(b, tramos, c) }
        remember(texto, base.layoutInput.style, base.layoutInput.constraints, c.tinta) { medidor.measure(texto, base.layoutInput.style.copy(color = c.tinta), constraints = base.layoutInput.constraints) }
    }
    val img = if (t.capa == null) remember(b.imagen) { imagenDe(st.epub, b.imagen) } else null
    val conNota = subrayados.filter { it.second.nota.isNotBlank() }.map { it.first }
    Canvas(Modifier.absoluteOffset { IntOffset(t.x.roundToInt(), t.y.roundToInt()) }.size(with(dens) { t.ancho.toDp() }, with(dens) { t.alto.toDp() })) {
        if (capa == null) {
            img?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High,
                colorFilter = if (c.oscuro) ColorFilter.tint(Color.Black.copy(alpha = 0.14f), BlendMode.SrcAtop) else null) }
            return@Canvas
        }
        val sube = if (t.desde < capa.lineCount) capa.getLineTop(t.desde) else 0f
        clipRect(left = -3 * 20f * dens.density, top = 0f, right = size.width + 40f, bottom = size.height) {
            translate(top = -sube) {
                seleccion?.let { r -> drawPath(capa.getPathForRange(r.first, (r.last + 1).coerceAtMost(capa.layoutInput.text.length)), c.acento.copy(alpha = 0.26f)) }
                destello?.let { r -> if (r.last >= r.first) drawPath(capa.getPathForRange(r.first, (r.last + 1).coerceAtMost(capa.layoutInput.text.length)), c.acento.copy(alpha = 0.38f)) }
                drawText(capa)
                // Los subrayados con nota llevan una raya del acento en el margen, a su altura
                for (r in conNota) {
                    val l1 = capa.getLineForOffset(r.first).coerceAtLeast(t.desde); val l2 = capa.getLineForOffset(r.last).coerceAtMost(t.hasta - 1)
                    if (l2 < l1) continue
                    drawRect(c.acento, Offset(-14f * dens.density, capa.getLineTop(l1) + 3f), Size(2.5f * dens.density, capa.getLineBottom(l2) - capa.getLineTop(l1) - 6f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- deslizando

@Composable
private fun AreaContinua(st: EstadoLector, c: Colores, geo: Geometria, todos: List<Pair<Int, Int>>, lista: androidx.compose.foundation.lazy.LazyListState,
                         indiceDe: (Sitio) -> Int, deSeccion: (Int) -> Map<Int, List<Pair<IntRange, Marca>>>, alAbrirEnlace: (Epub.Enlace, Float, Float) -> Unit) {
    val dens = LocalDensity.current
    // Al entrar, al bloque del sitio; luego, lo que se ve es por dónde vas (y un salto —el índice, una nota…— lleva allí)
    var listo by remember { mutableStateOf(false) }
    val ultimo = remember { mutableStateOf(st.sitio) }
    LaunchedEffect(Unit) { lista.scrollToItem(indiceDe(st.sitio)); listo = true }
    LaunchedEffect(st.sitio, listo) {
        if (listo && st.sitio != ultimo.value) { ultimo.value = st.sitio; lista.scrollToItem(indiceDe(st.sitio)) }
    }
    LaunchedEffect(lista, listo) {
        if (!listo) return@LaunchedEffect
        snapshotFlow { lista.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val (s, b) = todos.getOrNull(i) ?: return@collect
            val x = Sitio(s, b, 0)
            ultimo.value = x
            if (x != st.sitio) { st.direccion = if (x > st.sitio) 1 else -1; st.sitio = x; st.pagina = 0; st.menu = null }
        }
    }
    val subCache = remember(LaFamilia.cambios) { HashMap<Int, Map<Int, List<Pair<IntRange, Marca>>>>() }
    val subCacheA by rememberUpdatedState(subCache)
    val deSeccionA by rememberUpdatedState(deSeccion)
    val enlaceC by rememberUpdatedState(alAbrirEnlace)
    val visibles = remember { HashMap<Pair<Int, Int>, Pair<LayoutCoordinates, TextLayoutResult>>() }
    var raiz by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun golpeC(pos: Offset): Golpe? {
        val r = raiz ?: return null
        val enRaiz = r.localToRoot(pos)
        var mejor: Golpe? = null; var dist = Float.MAX_VALUE
        for ((k, v) in visibles) {
            val (co, cp) = v
            if (!co.isAttached) continue
            val o = co.positionInRoot()
            val y = enRaiz.y - o.y
            val d = if (y < 0) -y else if (y > co.size.height) y - co.size.height else 0f
            if (d < dist) { dist = d; val l = cp.getOffsetForPosition(Offset(enRaiz.x - o.x, y.coerceIn(0f, co.size.height - 1f))); mejor = Golpe(k.first, k.second, l, cp, d == 0f) }
        }
        return mejor
    }
    val manejo = Modifier.onGloballyPositioned { raiz = it }.pointerInput(st) {
        awaitPointerEventScope {
            var bajada: Offset? = null; var arrastrando = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull() ?: continue
                when (ev.type) {
                    PointerEventType.Press -> if (ev.buttons.isPrimaryPressed) { bajada = ch.position; arrastrando = false; Lectura.actividad() }
                    PointerEventType.Move -> {
                        val b0 = bajada ?: continue
                        if (!ev.buttons.isPrimaryPressed) continue
                        if (!arrastrando && (ch.position - b0).getDistance() > 5f) golpeC(b0)?.let { g -> g.capa?.let { cp ->
                            val w = cp.getWordBoundary(g.letra.coerceIn(0, maxOf(0, cp.layoutInput.text.length - 1)))
                            st.menu = null; st.seleccion = Seleccion(g.seccion, g.bloque, w.start, w.end, g.bloque, w.end); arrastrando = true } }
                        if (arrastrando) {
                            golpeC(ch.position)?.let { g -> val s = st.seleccion; val cp = g.capa
                                if (s != null && cp != null && g.seccion == s.seccion) {
                                    val w = cp.getWordBoundary(g.letra.coerceIn(0, maxOf(0, cp.layoutInput.text.length - 1)))
                                    val antes = g.bloque < s.anclaBloque || (g.bloque == s.anclaBloque && g.letra < s.anclaDesde)
                                    st.seleccion = s.copy(focoBloque = g.bloque, focoLetra = if (antes) w.start else w.end)
                                } }
                            ch.consume()
                        }
                    }
                    PointerEventType.Release -> {
                        val b0 = bajada; bajada = null
                        if (arrastrando) { arrastrando = false; st.seleccion?.let { s -> st.seleccion = s.copy(terminada = true); st.menu = MenuTexto(null, s, ch.position.x, ch.position.y) }; ch.consume(); continue }
                        if (b0 == null || (ch.position - b0).getDistance() > 5f) continue
                        if (st.menu != null || st.seleccion != null || st.nota != null || st.definicion != null) { st.menu = null; st.seleccion = null; st.nota = null; st.definicion = null; continue }
                        val g = golpeC(ch.position) ?: continue
                        if (!g.encima) continue
                        val b = st.libro.bloques(g.seccion).getOrNull(g.bloque) ?: continue
                        b.texto.getStringAnnotations(ETIQUETA_ENLACE, g.letra, g.letra + 1).firstOrNull()?.let { en -> st.epub.enlace(en.item, g.seccion)?.let { enlaceC(it, ch.position.x, ch.position.y) }; continue }
                        val m = subCacheA.getOrPut(g.seccion) { deSeccionA(g.seccion) }[g.bloque]?.firstOrNull { g.letra in it.first }?.second
                        if (m != null) st.menu = MenuTexto(m, null, ch.position.x, ch.position.y)
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().then(manejo)) {
        LazyColumn(Modifier.fillMaxSize(), lista, contentPadding = PaddingValues(vertical = with(dens) { geo.origenes.first().y.toDp() }), horizontalAlignment = Alignment.CenterHorizontally) {
            itemsIndexed(todos, key = { _, it -> it }) { i, (s, bi) ->
                val b = st.libro.bloques(s)[bi]
                val anterior = if (bi > 0) st.libro.bloques(s)[bi - 1] else null
                val eb = estiloDe(st.ajustes, dens, b, anterior)
                val nuevaSeccion = bi == 0 && i > 0
                Column(Modifier.width(with(dens) { geo.anchoTexto.toDp() })) {
                    if (nuevaSeccion) {
                        Spacer(Modifier.height(with(dens) { (eb.antes * 2).toDp() } + 40.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.linea)); Spacer(Modifier.height(40.dp))
                    } else if (i > 0) Spacer(Modifier.height(with(dens) { eb.antes.toDp() }))
                    BloqueContinuo(st, b, eb, c, geo, subCache.getOrPut(s) { deSeccion(s) }[bi].orEmpty(), st.seleccion?.takeIf { it.seccion == s }?.en(bi, b.largo),
                        st.destello?.takeIf { it.first.seccion == s && bi in it.first.bloque..it.second.bloque }?.let { (d1, d2) -> (if (bi == d1.bloque) d1.letra else 0) until (if (bi == d2.bloque) d2.letra else b.largo) },
                        alPosicionar = { co, cp -> visibles[s to bi] = co to cp }, alSoltar = { visibles.remove(s to bi) })
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(lista), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun BloqueContinuo(st: EstadoLector, b: Bloque, eb: EstiloBloque, c: Colores, geo: Geometria, subrayados: List<Pair<IntRange, Marca>>, seleccion: IntRange?, destello: IntRange?,
                           alPosicionar: (LayoutCoordinates, TextLayoutResult) -> Unit, alSoltar: () -> Unit) {
    val dens = LocalDensity.current
    val medidor = rememberTextMeasurer(16)
    DisposableEffect(b) { onDispose { alSoltar() } }
    if (b.tipo == Bloque.Tipo.IMAGEN) {
        val img = remember(b.imagen) { imagenDe(st.epub, b.imagen) } ?: return
        val w = minOf(geo.anchoTexto.toFloat(), img.width * dens.density); var h = w * img.height / img.width
        val ww = if (h > geo.altoTexto * 0.94f) { h = geo.altoTexto * 0.94f; h * img.width / img.height } else w
        Canvas(Modifier.size(with(dens) { ww.toDp() }, with(dens) { h.toDp() })) {
            drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High,
                colorFilter = if (c.oscuro) ColorFilter.tint(Color.Black.copy(alpha = 0.14f), BlendMode.SrcAtop) else null)
        }
        return
    }
    val tramos = subrayados.map { it.first }
    val texto = remember(b, tramos, c.tinta, c.papel, c.acento) { textoPintado(b, tramos, c) }
    val ancho = (geo.anchoTexto - eb.izquierda - eb.derecha).toInt().coerceAtLeast(40)
    val capa = remember(texto, eb.estilo, ancho, c.tinta) { medidor.measure(texto, eb.estilo.copy(color = c.tinta), constraints = Constraints.fixedWidth(ancho)) }
    val conNota = subrayados.filter { it.second.nota.isNotBlank() }.map { it.first }
    Canvas(Modifier.padding(start = with(dens) { eb.izquierda.toDp() }).size(with(dens) { capa.size.width.toDp() }, with(dens) { capa.size.height.toDp() })
        .onGloballyPositioned { alPosicionar(it, capa) }) {
        seleccion?.let { r -> drawPath(capa.getPathForRange(r.first, (r.last + 1).coerceAtMost(capa.layoutInput.text.length)), c.acento.copy(alpha = 0.26f)) }
        destello?.let { r -> if (r.last >= r.first) drawPath(capa.getPathForRange(r.first, (r.last + 1).coerceAtMost(capa.layoutInput.text.length)), c.acento.copy(alpha = 0.38f)) }
        drawText(capa)
        for (r in conNota) {
            val l1 = capa.getLineForOffset(r.first); val l2 = capa.getLineForOffset(r.last)
            drawRect(c.acento, Offset(-14f * dens.density, capa.getLineTop(l1) + 3f), Size(2.5f * dens.density, capa.getLineBottom(l2) - capa.getLineTop(l1) - 6f))
        }
    }
}

/** El cursor de mano sobre lo que se toca. */
internal val Modifier.mano: Modifier get() = this.pointerHoverIcon(PointerIcon.Hand)
