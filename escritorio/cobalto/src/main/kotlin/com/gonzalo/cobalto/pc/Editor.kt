@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.cobalto.pc

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.gonzalo.cobalto.AspectoTexto
import com.gonzalo.cobalto.Doc
import com.gonzalo.cobalto.EdicionTexto
import com.gonzalo.cobalto.Escritos
import com.gonzalo.cobalto.FormatoMarkdown
import com.gonzalo.cobalto.Formato
import com.gonzalo.cobalto.HistorialTexto
import com.gonzalo.cobalto.Tipografia
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Lo que estás escribiendo y aún no se ha guardado (se guarda a los 0,8 s de dejar de escribir, al cambiar de escena y al cerrar):
 * así cada tecla no es un cambio en la cuenta, y no se pierde nada. Guardar es [Escritos.escribe], que apunta las palabras de
 * hoy (lo que sube la cuenta de la escena): es el único sitio donde se cuentan.
 */
object Editor {
    @Volatile private var pendiente: Pair<String, String>? = null

    fun apunta(eid: String, texto: String) { pendiente = eid to texto }

    /** Lo que hay sin guardar de esa escena (o null). */
    fun pendienteDe(eid: String): String? = pendiente?.takeIf { it.first == eid }?.second

    /** Guarda ya lo que hubiera pendiente. */
    fun guardaYa() {
        val (eid, texto) = pendiente ?: return
        pendiente = null
        val e = Cuaderno.escritos
        e.escribe(eid, texto)
        e.instantanea(eid, texto)
    }
}

/** El Markdown ligero del móvil y, encima, la coincidencia elegida al buscar (más marcada que las demás). */
private class FormatoEditor(private val base: FormatoMarkdown, private val actual: IntRange?, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val a = base.estilo(text.text)
        val r = actual?.takeIf { it.first >= 0 && it.last < text.length } ?: return TransformedText(a, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(a).apply { addStyle(SpanStyle(background = color.copy(alpha = 0.55f), textDecoration = TextDecoration.Underline), r.first, r.last + 1) }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** La expresión que reconoce en el texto los nombres (y alias) de las fichas (la misma que el móvil). */
fun regexFichas(fichas: List<Doc>): Regex? {
    val ns = fichas.flatMap { f -> listOf(f.s("nombre")) + f.s("alias").split(',') }.map { it.trim() }.filter { it.length >= 2 }.distinctBy { it.lowercase() }.sortedByDescending { it.length }
    return if (ns.isEmpty()) null else Regex("(?<![\\p{L}\\p{N}])(" + ns.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
}

/** La ficha que nombra [palabra] (por su nombre o un alias). */
fun fichaDeNombre(fichas: List<Doc>, palabra: String): Doc? = fichas.firstOrNull { f -> (listOf(f.s("nombre")) + f.s("alias").split(',')).any { it.trim().equals(palabra.trim(), true) } }

/** El nombre de ficha que toca la posición [i] del texto (se busca solo alrededor, para no recorrer la escena entera). */
fun nombreFichaEn(texto: String, i: Int, rx: Regex?): IntRange? {
    rx ?: return null
    val desde = (i - 80).coerceAtLeast(0)
    val trozo = texto.substring(desde, (i + 80).coerceAtMost(texto.length))
    return rx.findAll(trozo).firstOrNull { i - desde >= it.range.first && i - desde <= it.range.last }?.range?.let { (it.first + desde)..(it.last + desde) }
}

/** ¿El puntero [p] está encima de una letra (la de [i] o la de antes), y no en el blanco del final de una línea? */
private fun sobreLetra(l: TextLayoutResult, i: Int, p: Offset): Boolean {
    val n = l.layoutInput.text.length
    return listOf(i - 1, i).filter { it in 0 until n }.any { l.getBoundingBox(it).inflate(2f).contains(p) }
}

/** Pone un globo junto a un trozo del texto ([r], en coordenadas del campo): debajo, o encima si abajo no cabe. */
private class JuntoA(private val r: Rect, private val hueco: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + r.left.roundToInt()).coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        val abajo = anchorBounds.top + r.bottom.roundToInt() + hueco
        val y = if (abajo + popupContentSize.height > windowSize.height - 8) anchorBounds.top + r.top.roundToInt() - hueco - popupContentSize.height else abajo
        return IntOffset(x, y.coerceAtLeast(8))
    }
}

/**
 * El editor de una escena, el modo escritor: el Markdown ligero de «Escribir» pintado mientras escribes (los mismos colores y
 * reglas que en el móvil, EscribirTexto.kt), la barra de formato, buscar y reemplazar (en la escena y en toda la obra), la
 * concentración (solo la página), la máquina de escribir (la línea donde escribes, a un 40 % del alto), la tipografía automática,
 * el tamaño y el tipo de letra, los sinónimos, los sprints, el panel (escena, fichas, notas, borradores e historial) y el pie con
 * las cuentas al momento. Los nombres de las fichas salen subrayados: con el ratón encima, su ficha; con doble clic, se abre.
 * Como el editor de Atrio del PC.
 */
@Composable
fun EditorEscena(esc: Doc, obra: Doc, nav: Nav, abre: AbreDoc, abrirEscena: (String) -> Unit, modifier: Modifier = Modifier) {
    val e = Cuaderno.escritos
    val ver = e.version
    PrefsEditor.asegura()
    val pid = obra.id
    val conc = Estudio.concentracion
    val eid = esc.id
    var v by remember(eid) { mutableStateOf(TextFieldValue(esc.s("texto"), TextRange(esc.s("texto").length))) }
    val historial = remember(eid) { HistorialTexto(v) }
    var escribiendo by remember(eid) { mutableStateOf(0L) }
    val inicio = remember(eid) { Escritos.palabras(esc.s("texto")) }
    var aviso by remember { mutableStateOf("") }
    LaunchedEffect(aviso) { if (aviso.isNotEmpty()) { delay(2600); aviso = "" } }

    // Lo que llega de fuera (otro dispositivo) mientras no escribes aquí, se ve al momento
    LaunchedEffect(esc.s("texto")) {
        val fuera = esc.s("texto")
        if (fuera != v.text && Editor.pendienteDe(eid) == null && System.currentTimeMillis() - escribiendo > 3_000) {
            v = TextFieldValue(fuera, TextRange(minOf(v.selection.start, fuera.length)))
            historial.apunta(v)
            if (escribiendo > 0) aviso = "Esta escena ha cambiado en otro dispositivo: ya la ves al día."
        }
    }
    LaunchedEffect(escribiendo) { if (escribiendo > 0) { delay(800); Editor.guardaYa() } }
    DisposableEffect(eid) { onDispose { Editor.guardaYa() } }
    // Una versión al abrir (si la última es de hace más de una hora) y otra cada 5 minutos (el historial)
    LaunchedEffect(eid) { e.instantanea(eid, esc.s("texto"), hueco = 3_600_000L); while (true) { delay(300_000); e.instantanea(eid, v.text) } }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Sinonimos.carga() } }

    val fichas = remember(ver, pid) { e.fichas(pid) }
    val rxFichas = remember(fichas) { regexFichas(fichas) }

    // ---- Buscar y reemplazar
    var buscar by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    var reemplazo by remember { mutableStateOf("") }
    var mayus by remember { mutableStateOf(false) }
    var actual by remember(eid) { mutableStateOf(-1) }
    // (otra búsqueda: aún ninguna elegida. El estado es siempre el mismo: lo que se pone desde una corrutina no se pierde)
    LaunchedEffect(consulta, mayus) { actual = -1 }
    var enLaObra by remember { mutableStateOf(false) }
    val coinc = remember(v.text, consulta, mayus, buscar) { if (buscar) Busqueda.coincidencias(v.text, consulta, mayus) else emptyList() }
    val rxBusca = remember(consulta, mayus, buscar) { if (buscar) Busqueda.rx(consulta, mayus) else null }
    val fBuscar = remember { FocusRequester() }
    var selAntes by remember { mutableStateOf<TextRange?>(null) }

    // ---- La página
    val foco = remember { FocusRequester() }
    val scroll = rememberScrollState()
    var alto by remember { mutableStateOf(0) }
    var diseno by remember { mutableStateOf<TextLayoutResult?>(null) }
    val dens = LocalDensity.current
    val margenArriba = if (conc) 72.dp else 34.dp
    val margenPx = with(dens) { margenArriba.toPx() }
    var sinonimos by remember { mutableStateOf<Pair<IntRange, List<List<String>>>?>(null) }
    var previa by remember { mutableStateOf<Pair<Doc, Rect>?>(null) }
    var encimaDe by remember { mutableStateOf<Pair<IntRange, Doc>?>(null) }
    var sprint by remember { mutableStateOf(false) }
    var atajos by remember { mutableStateOf(false) }

    fun pon(n: TextFieldValue, apuntar: Boolean = true) {
        if (apuntar) historial.apunta(n)
        v = n
        if (n.text != esc.s("texto") || Editor.pendienteDe(eid) != null) { Editor.apunta(eid, n.text); escribiendo = System.currentTimeMillis() }
    }
    // Lo que llega del teclado: si es un solo carácter, la tipografía automática (— « » …), que se deshace aparte
    fun teclea(n: TextFieldValue) {
        val antes = v
        val c = n.selection.start
        if (PrefsEditor.tipografia && n.selection.collapsed && c >= 1 && n.text.length == antes.text.length + 1 && n.composition.let { it == null || it.end <= c - 3 } &&
            n.text.regionMatches(0, antes.text, 0, c - 1) && n.text.regionMatches(c, antes.text, c - 1, n.text.length - c)) {
            Tipografia.aplica(n.text, c, n.text[c - 1])?.let { (t, k) ->
                historial.apunta(n); historial.corte()
                pon(TextFieldValue(t, TextRange(k))); historial.corte()
                return
            }
        }
        if (previa != null) previa = null
        pon(n)
    }
    fun aplica(f: (TextFieldValue) -> TextFieldValue) { pon(f(v)); runCatching { foco.requestFocus() } }
    fun deshace() { historial.deshace()?.let { pon(it, false) } }
    fun rehace() { historial.rehace()?.let { pon(it, false) } }
    fun abreSinonimos() {
        val r = Sinonimos.palabraEn(v.text, v.selection.start, v.selection.end)
        if (r == null) { aviso = "Pon el cursor en una palabra"; return }
        val palabra = v.text.substring(r.first, r.last + 1)
        if (!Sinonimos.cargado) Sinonimos.carga()
        val g = Sinonimos.busca(palabra)
        if (g.isEmpty()) aviso = "Sin sinónimos para «$palabra»" else sinonimos = r to g
    }
    fun ponSinonimo(r: IntRange, palabra: String, s: String) {
        val fin = (r.last + 1).coerceAtMost(v.text.length)
        if (r.first <= fin && v.text.substring(r.first, fin) == palabra) aplica { TextFieldValue(it.text.replaceRange(r.first, fin, s), TextRange(r.first + s.length)) }
        sinonimos = null
    }
    /** Lleva la página a [i] (el trozo, centrado). */
    suspend fun llevaA(i: Int) {
        val l = diseno ?: return
        val r = l.getCursorRect(i.coerceIn(0, l.layoutInput.text.length))
        val destino = (margenPx + r.top - alto * 0.35f).roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(destino, tween(180))
    }
    var irA by remember { mutableStateOf(-1) }
    LaunchedEffect(irA) { if (irA >= 0) { llevaA(irA); irA = -1 } }
    fun salta(paso: Int) {
        if (coinc.isEmpty()) return
        actual = Busqueda.salta(coinc, actual, paso, v.selection.end)
        val r = coinc[actual]
        v = v.copy(selection = TextRange(r.first, r.last + 1))
        irA = r.first
    }
    fun abreBusca() {
        buscar = true
        selAntes = v.selection
        if (!v.selection.collapsed) consulta = v.text.substring(minOf(v.selection.start, v.selection.end), maxOf(v.selection.start, v.selection.end)).take(80)
        runCatching { fBuscar.requestFocus() }
    }
    fun cierraBusca() {
        val ir = coinc.getOrNull(actual)?.let { TextRange(it.first, it.last + 1) } ?: selAntes
        buscar = false; enLaObra = false
        if (ir != null) v = v.copy(selection = ir)
        runCatching { foco.requestFocus() }
    }
    // Abrir esta escena desde «Buscar en la obra»: la búsqueda abierta, en la primera
    LaunchedEffect(eid) {
        val b = Estudio.buscarAlAbrir
        if (b != null && b.first == eid) { Estudio.buscarAlAbrir = null; buscar = true; consulta = b.second; delay(120); actual = -1
            Busqueda.coincidencias(v.text, consulta, mayus).firstOrNull()?.let { r -> actual = 0; v = v.copy(selection = TextRange(r.first, r.last + 1)); irA = r.first } }
    }
    LaunchedEffect(buscar) { if (buscar) runCatching { fBuscar.requestFocus() } }

    // ---- Máquina de escribir (la línea donde escribes, a un 40 % del alto) y, sin ella, que el cursor siempre se vea
    // (también con el máximo del desplazamiento: al abrir, el hueco de abajo llega después y la línea no subía hasta su sitio)
    LaunchedEffect(v.selection, diseno, PrefsEditor.maquina, alto, conc, scroll.maxValue) {
        val l = diseno ?: return@LaunchedEffect
        if (alto == 0 || buscar) return@LaunchedEffect
        val r = l.getCursorRect(v.selection.end.coerceIn(0, l.layoutInput.text.length))
        if (PrefsEditor.maquina) {
            val d = destinoMaquina(r.top, r.height, margenPx, alto, scroll.maxValue)
            if (kotlin.math.abs(d - scroll.value) > 1) scroll.animateScrollTo(d, tween(110))
        } else destinoVisible(r.top, r.height, margenPx, scroll.value, alto, scroll.maxValue)?.let { scroll.animateScrollBy((it - scroll.value).toFloat(), tween(90)) }
    }

    // ---- Lo que se ve: el formato, la letra, las cuentas
    val familia = when (PrefsEditor.fuente) { 0 -> Display; 1 -> Body; else -> FontFamily.Monospace }
    val tam = PrefsEditor.tam
    val tinta = Charcoal; val tenue = Muted.copy(alpha = 0.6f); val acento = Acento
    val actualRango = coinc.getOrNull(actual)
    val formato = remember(tinta, tenue, acento, tam, rxFichas, rxBusca, actualRango) { FormatoEditor(FormatoMarkdown(AspectoTexto(tinta, tenue, acento, Display, tam), rxFichas, rxBusca), actualRango, acento) }
    val cap = e.get(esc.s("cid"))
    val cuentas = remember(v.text, v.selection, ver) {
        Cuentas.de(v.text, v.selection, inicio, esc.i("palabras"), e.escritas(Escritos.hoy(), pid), cap?.let { e.palabrasCapitulo(it.id) } ?: 0, e.palabrasObra(pid))
    }

    // ---- Los atajos de toda la pantalla del editor (también con el foco en la búsqueda o el panel)
    fun atajo(k: KeyEvent): Boolean {
        if (k.type != KeyEventType.KeyDown) return false
        val p = Atajo.principal(k)
        when {
            k.key == Key.Escape -> when {
                sinonimos != null -> sinonimos = null
                previa != null -> previa = null
                buscar -> cierraBusca()
                conc -> Estudio.concentra(false)
                else -> return false
            }
            // (la pantalla completa, F11 o ⌃⌘F, la lleva la ventana de la familia)
            p && k.isShiftPressed && k.key == Key.F -> { Estudio.concentra(!conc); runCatching { foco.requestFocus() } }
            p && k.key == Key.F -> abreBusca()
            p && k.key == Key.G && buscar -> salta(if (k.isShiftPressed) -1 else 1)
            p && (k.key == Key.Equals || k.key == Key.Plus || k.key == Key.NumPadAdd) -> PrefsEditor.letra(+1)
            p && (k.key == Key.Minus || k.key == Key.NumPadSubtract) -> PrefsEditor.letra(-1)
            p && (k.key == Key.Zero || k.key == Key.NumPad0) -> PrefsEditor.letra(0)
            p && k.isShiftPressed && k.key == Key.M -> { PrefsEditor.maquina = !PrefsEditor.maquina; PrefsEditor.guarda() }
            p && k.isShiftPressed && k.key == Key.P -> { PrefsEditor.panel = !PrefsEditor.panel; PrefsEditor.guarda() }
            p && k.isShiftPressed && k.key == Key.S -> abreSinonimos()
            else -> return false
        }
        return true
    }

    Column(modifier.onPreviewKeyEvent { atajo(it) }) {
        if (!conc) {
            BarraEditor(
                puedeDeshacer = historial.puedeDeshacer, puedeRehacer = historial.puedeRehacer, onDeshacer = { deshace() }, onRehacer = { rehace() },
                onFormato = { aplica(it) }, onBuscar = { if (buscar) cierraBusca() else abreBusca() }, onSinonimos = { abreSinonimos() },
                onSprint = { if (SprintEscribir.activo()) { Editor.guardaYa(); SprintEscribir.termina(e, ya = true) } else sprint = true },
                onConcentracion = { Estudio.concentra(true); runCatching { foco.requestFocus() } }, onAtajos = { atajos = true }, buscando = buscar,
                alCambiar = { runCatching { foco.requestFocus() } }
            )
            if (buscar) BarraBuscar(
                consulta, { consulta = it }, reemplazo, { reemplazo = it }, mayus, { mayus = it }, coinc.size, actual, fBuscar,
                onSig = { salta(1) }, onAnt = { salta(-1) },
                onEsta = {
                    coinc.getOrNull(actual)?.let { r ->
                        // Cambia esta y elige la siguiente (en el texto ya cambiado)
                        val nv = Busqueda.reemplazaUna(v, r, reemplazo)
                        pon(nv)
                        val l = Busqueda.coincidencias(nv.text, consulta, mayus)
                        val i = Busqueda.salta(l, -1, 1, nv.selection.end)
                        actual = i
                        if (i >= 0) { v = v.copy(selection = TextRange(l[i].first, l[i].last + 1)); irA = l[i].first }
                    }
                },
                onEscena = {
                    val (t, n) = Busqueda.reemplazaTodas(v.text, consulta, reemplazo, mayus)
                    if (n > 0) { pon(TextFieldValue(t, TextRange(0))); aviso = "$n ${if (n == 1) "cambio" else "cambios"} en la escena" }
                },
                onObra = {
                    if (consulta.isNotEmpty()) {
                        Editor.guardaYa()
                        val n = e.reemplaza(pid, consulta, reemplazo, mayusculas = mayus)
                        e.get(eid)?.s("texto")?.let { t -> if (t != v.text) { v = TextFieldValue(t, TextRange(minOf(v.selection.start, t.length))); historial.apunta(v) } }
                        nav.aviso("$n ${if (n == 1) "cambio" else "cambios"} en toda la obra")
                    }
                },
                onVerObra = { enLaObra = true },
                onCierra = { cierraBusca() }
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---- La hoja
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { alto = it.height }) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = if (conc) 760.dp else 720.dp).fillMaxWidth().padding(horizontal = 28.dp)) {
                        Spacer(Modifier.height(margenArriba))
                        Box {
                            var ultimoClic by remember { mutableStateOf(0L to Offset.Zero) }
                            ContextMenuDataProvider(items = {
                                buildList {
                                    val r = Sinonimos.palabraEn(v.text, v.selection.start, v.selection.end)
                                    if (r != null) add(ContextMenuItem("Sinónimos de «${v.text.substring(r.first, r.last + 1).take(30)}»") { abreSinonimos() })
                                    nombreFichaEn(v.text, v.selection.start, rxFichas)?.let { nr -> fichaDeNombre(fichas, v.text.substring(nr.first, nr.last + 1))?.let { f ->
                                        add(ContextMenuItem("Abrir la ficha de ${f.s("nombre")}") { abre.ver(f.id) }) } }
                                    if (!v.selection.collapsed) {
                                        val sel = v.text.substring(minOf(v.selection.start, v.selection.end), maxOf(v.selection.start, v.selection.end)).take(80)
                                        add(ContextMenuItem("Buscar «${sel.take(24)}» en la obra") { consulta = sel; buscar = true; enLaObra = true })
                                    }
                                }
                            }) {
                                BasicTextField(
                                    v, { teclea(it) },
                                    Modifier.fillMaxWidth().heightIn(min = with(dens) { (alto * 0.55f).toDp() }.coerceAtLeast(320.dp)).focusRequester(foco)
                                        .onPreviewKeyEvent { k ->
                                            if (k.type != KeyEventType.KeyDown || !Atajo.principal(k)) return@onPreviewKeyEvent false
                                            when {
                                                k.key == Key.Z && !k.isShiftPressed -> { deshace(); true }
                                                (k.key == Key.Z && k.isShiftPressed) || (!Atajo.mac && k.key == Key.Y) -> { rehace(); true }
                                                k.key == Key.B -> { aplica { EdicionTexto.envuelve(it, "**") }; true }
                                                k.key == Key.I -> { aplica { EdicionTexto.envuelve(it, "*") }; true }
                                                k.key == Key.S && !k.isShiftPressed -> { Editor.guardaYa(); e.instantanea(eid, v.text, fuerza = true); aviso = "Versión guardada"; true }
                                                else -> false
                                            }
                                        }
                                        // Con el ratón encima de un nombre subrayado, su ficha; con doble clic, se abre
                                        .onPointerEvent(PointerEventType.Move) { ev ->
                                            val l = diseno ?: return@onPointerEvent
                                            val p = ev.changes.first().position
                                            val i = l.getOffsetForPosition(p)
                                            val nr = if (sobreLetra(l, i, p)) nombreFichaEn(v.text, i, rxFichas) else null
                                            val f = nr?.let { fichaDeNombre(fichas, v.text.substring(it.first, it.last + 1)) }
                                            encimaDe = if (nr != null && f != null) nr to f else null
                                        }
                                        .onPointerEvent(PointerEventType.Exit) { encimaDe = null }
                                        .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { ev ->
                                            val l = diseno ?: return@onPointerEvent
                                            val p = ev.changes.first().position
                                            previa = null
                                            if (ev.buttons.isSecondaryPressed) {
                                                // (el botón derecho lleva el cursor a donde pulsas, si no es dentro de lo elegido: el menú habla de esa palabra)
                                                val i = l.getOffsetForPosition(p)
                                                val a = minOf(v.selection.start, v.selection.end); val b = maxOf(v.selection.start, v.selection.end)
                                                if (i < a || i > b) v = v.copy(selection = TextRange(i))
                                                return@onPointerEvent
                                            }
                                            val ahora = System.currentTimeMillis()
                                            val (t0, p0) = ultimoClic
                                            ultimoClic = ahora to p
                                            if (ahora - t0 < 450 && (p - p0).getDistance() < 8f) {
                                                val i = l.getOffsetForPosition(p)
                                                nombreFichaEn(v.text, i, rxFichas)?.let { nr -> fichaDeNombre(fichas, v.text.substring(nr.first, nr.last + 1))?.let { f -> encimaDe = null; abre.ver(f.id) } }
                                            }
                                        },
                                    textStyle = TextStyle(color = Charcoal, fontFamily = familia, fontSize = (if (PrefsEditor.fuente == 2) tam - 2 else tam).sp, lineHeight = (tam * 1.68f).sp),
                                    cursorBrush = SolidColor(Acento),
                                    visualTransformation = formato,
                                    onTextLayout = { diseno = it },
                                    decorationBox = { inner ->
                                        Box {
                                            if (v.text.isEmpty()) Text("Empieza aquí… (# título, > cita, *cursiva*, **negrita**, *** salto de escena)", color = Muted, fontFamily = familia, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam * 1.68f).sp)
                                            inner()
                                        }
                                    }
                                )
                            }
                            // La vista previa de la ficha del nombre que tiene el ratón encima
                            LaunchedEffect(encimaDe?.second?.id, encimaDe?.first) {
                                val x = encimaDe
                                if (x == null) { delay(160); previa = null; return@LaunchedEffect }
                                delay(380)
                                val l = diseno ?: return@LaunchedEffect
                                val a = l.getBoundingBox(x.first.first.coerceIn(0, (l.layoutInput.text.length - 1).coerceAtLeast(0)))
                                val b = l.getBoundingBox(x.first.last.coerceIn(0, (l.layoutInput.text.length - 1).coerceAtLeast(0)))
                                previa = x.second to Rect(a.left, a.top, maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
                            }
                            previa?.let { (f, r) ->
                                Popup(popupPositionProvider = JuntoA(r, with(dens) { 8.dp.roundToPx() }), properties = PopupProperties(focusable = false)) { PreviaFicha(e, f) }
                            }
                            sinonimos?.let { (r, grupos) ->
                                val palabra = v.text.substring(r.first, (r.last + 1).coerceAtMost(v.text.length))
                                val l = diseno
                                val rect = l?.let { val c = it.getCursorRect(r.first.coerceIn(0, it.layoutInput.text.length)); val d = it.getCursorRect((r.last + 1).coerceIn(0, it.layoutInput.text.length)); Rect(c.left, c.top, maxOf(c.right, d.right), maxOf(c.bottom, d.bottom)) } ?: Rect.Zero
                                Popup(popupPositionProvider = JuntoA(rect, with(dens) { 8.dp.roundToPx() }), onDismissRequest = { sinonimos = null }, properties = PopupProperties(focusable = true),
                                    onKeyEvent = { k -> if (k.type == KeyEventType.KeyDown && k.key == Key.Escape) { sinonimos = null; true } else false }) {
                                    GloboSinonimos(palabra, grupos) { s -> ponSinonimo(r, palabra, s) }
                                }
                            }
                        }
                        Spacer(Modifier.height(with(dens) { (alto * if (PrefsEditor.maquina) 0.6f else 0.3f).toDp() }.coerceAtLeast(120.dp)))
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
                if (conc) SalidaConcentracion(onSalir = { Estudio.concentra(false); runCatching { foco.requestFocus() } }, Modifier.align(Alignment.TopEnd))
            }
            // ---- El panel de la escena
            if (PrefsEditor.panel && !conc) {
                Filete(1.dp, Charcoal, Modifier.fillMaxHeight().width(1.dp))
                PanelEscena(e, esc, v.text, Modifier.width(370.dp).fillMaxHeight(), abre = abre, abrirEscena = abrirEscena,
                    inserta = { x -> aplica { EdicionTexto.inserta(it, x) } }, pon = { t -> pon(TextFieldValue(t, TextRange(t.length))); Editor.guardaYa() },
                    onCierra = { PrefsEditor.panel = false; PrefsEditor.guarda() })
            }
        }
        PieEditor(e, obra, esc, cuentas, conc, aviso)
    }
    if (sprint) DialogoSprint(e, pid) { sprint = false; runCatching { foco.requestFocus() } }
    if (atajos) DialogoAtajos { atajos = false }
    if (enLaObra) DialogoBuscarObra(e, obra, consulta, onCerrar = { enLaObra = false }, abrir = { id, q ->
        enLaObra = false
        if (id == eid) { buscar = true; consulta = q } else { Estudio.buscarAlAbrir = id to q; abrirEscena(id) }
    }, verDoc = { enLaObra = false; abre.ver(it) })
    LaunchedEffect(eid) { runCatching { foco.requestFocus() } }
}

/** «Salir» y «Pantalla completa» arriba a la derecha en la concentración (apagados hasta que pasas por encima). */
@Composable
private fun SalidaConcentracion(onSalir: () -> Unit, modifier: Modifier) {
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    val c = if (encima) Charcoal else Muted.copy(alpha = 0.55f)
    Row(modifier.hoverable(hover).padding(top = 14.dp, end = 26.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(if (Estudio.pantallaCompleta) "VENTANA" else "PANTALLA COMPLETA", Modifier.pointerHoverIcon(PointerIcon.Hand).clickableSinFoco { Estudio.pantallaCompleta(!Estudio.pantallaCompleta) }.padding(6.dp),
            color = c, fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text("SALIR (ESC)", Modifier.pointerHoverIcon(PointerIcon.Hand).clickableSinFoco(onSalir).padding(6.dp), color = c, fontFamily = Body, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
    }
}

/** La barra de formato y de lo demás del editor (como la de Atrio): deshacer, formato, buscar, sinónimos y las preferencias. */
@Composable
private fun BarraEditor(
    puedeDeshacer: Boolean, puedeRehacer: Boolean, onDeshacer: () -> Unit, onRehacer: () -> Unit, onFormato: ((TextFieldValue) -> TextFieldValue) -> Unit,
    onBuscar: () -> Unit, onSinonimos: () -> Unit, onSprint: () -> Unit, onConcentracion: () -> Unit, onAtajos: () -> Unit, buscando: Boolean, alCambiar: () -> Unit,
) {
    val tinta = Charcoal; val linea = Line
    FlowRow(
        Modifier.fillMaxWidth().drawBehind {
            drawRect(tinta, Offset.Zero, Size(size.width, 2.dp.toPx()))
            drawRect(linea, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
        }.padding(vertical = 3.dp),
        verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tecla("↶", Atajo.texto("Deshacer (Ctrl+Z)"), tam = 16.sp) { if (puedeDeshacer) onDeshacer(); alCambiar() }
            Tecla("↷", Atajo.texto(if (Atajo.mac) "Rehacer (Ctrl+Mayús+Z)" else "Rehacer (Ctrl+Y)"), tam = 16.sp) { if (puedeRehacer) onRehacer(); alCambiar() }
            SeparadorBarra()
            Tecla("B", Atajo.texto("Negrita (Ctrl+B)"), peso = FontWeight.Black) { onFormato { EdicionTexto.envuelve(it, "**") } }
            Tecla("I", Atajo.texto("Cursiva (Ctrl+I)"), familia = Display, estilo = FontStyle.Italic, tam = 16.sp) { onFormato { EdicionTexto.envuelve(it, "*") } }
            Tecla("T", "Título (## al principio de la línea)", familia = Display, tam = 15.sp) { onFormato { EdicionTexto.prefijoLinea(it, "## ") } }
            Tecla("❝", "Cita (> al principio de la línea)", tam = 15.sp) { onFormato { EdicionTexto.prefijoLinea(it, "> ") } }
            Tecla("* * *", "Salto de escena") { onFormato { EdicionTexto.salto(it) } }
            Tecla("«»", "Comillas latinas") { onFormato { EdicionTexto.comillas(it) } }
            Tecla("—", "Raya (de diálogo)") { onFormato { EdicionTexto.inserta(it, "—") } }
            SeparadorBarra()
            Tecla("Buscar", Atajo.texto("Buscar y reemplazar (Ctrl+F)"), activa = buscando) { onBuscar() }
            Tecla("≈ Sinónimos", Atajo.texto("Sinónimos de la palabra (Ctrl+Mayús+S, o con el botón derecho)")) { onSinonimos() }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tecla("Letra −", Atajo.texto("Letra más pequeña (Ctrl+−)")) { PrefsEditor.letra(-1); alCambiar() }
            Text("${PrefsEditor.tam.roundToInt()}", color = Muted, fontFamily = Body, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
            Tecla("Letra +", Atajo.texto("Letra más grande (Ctrl++)")) { PrefsEditor.letra(+1); alCambiar() }
            Tecla(PrefsEditor.FUENTES[PrefsEditor.fuente], "Tipo de letra: con serifa, de palo seco o de máquina", familia = when (PrefsEditor.fuente) { 0 -> Display; 1 -> Body; else -> FontFamily.Monospace }) { PrefsEditor.otraFuente(); alCambiar() }
            Tecla("Máquina", Atajo.texto("Máquina de escribir: la línea donde escribes se queda a media altura (Ctrl+Mayús+M)"), activa = PrefsEditor.maquina) { PrefsEditor.maquina = !PrefsEditor.maquina; PrefsEditor.guarda(); alCambiar() }
            Tecla("— «»", "Tipografía automática: -- es una raya, ... son puntos suspensivos y las comillas son « »", activa = PrefsEditor.tipografia) { PrefsEditor.tipografia = !PrefsEditor.tipografia; PrefsEditor.guarda(); alCambiar() }
            SeparadorBarra()
            Tecla(if (SprintEscribir.activo()) "Terminar sprint" else "Sprint", "Sprint de escritura: escribir sin parar un rato, con una meta si quieres", activa = SprintEscribir.activo()) { onSprint() }
            Tecla("Concentración", Atajo.texto("Solo la página (Ctrl+Mayús+F; Esc para salir)")) { onConcentracion() }
            Tecla("Panel", Atajo.texto("Panel de la escena: escena, fichas, notas, borradores e historial (Ctrl+Mayús+P)"), activa = PrefsEditor.panel) { PrefsEditor.panel = !PrefsEditor.panel; PrefsEditor.guarda(); alCambiar() }
            Tecla("?", "Atajos de teclado") { onAtajos() }
        }
    }
}

/** Buscar y reemplazar: en la escena (con las coincidencias resaltadas) y en toda la obra. Dos filas: buscar y cambiar. */
@Composable
private fun BarraBuscar(
    consulta: String, onConsulta: (String) -> Unit, reemplazo: String, onReemplazo: (String) -> Unit, mayus: Boolean, onMayus: (Boolean) -> Unit,
    total: Int, actual: Int, foco: FocusRequester, onSig: () -> Unit, onAnt: () -> Unit, onEsta: () -> Unit, onEscena: () -> Unit, onObra: () -> Unit, onVerObra: () -> Unit, onCierra: () -> Unit,
) {
    val linea = Line
    Column(Modifier.fillMaxWidth().background(Crudo).drawBehind { drawRect(linea, Offset(0f, size.height - 1f), Size(size.width, 1f)) }.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoRevista(consulta, onConsulta, "Buscar", Modifier.weight(1f), foco = foco, pista = "Una palabra o una frase", alTeclear = { k ->
                if (k.type == KeyEventType.KeyDown && (k.key == Key.Enter || k.key == Key.NumPadEnter)) { if (k.isShiftPressed) onAnt() else onSig(); true } else false
            })
            Tecla("▲", Atajo.texto("Anterior (Mayús+Intro o Ctrl+Mayús+G)")) { onAnt() }
            Tecla("▼", Atajo.texto("Siguiente (Intro o Ctrl+G)")) { onSig() }
            Text(when { consulta.isEmpty() -> ""; total == 0 -> "Ninguna"; actual < 0 -> if (total == 1) "1 vez" else "$total veces"; else -> "${actual + 1} de $total" },
                color = if (consulta.isNotEmpty() && total == 0) Acento else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 64.dp))
            Tecla("Aa", "Distinguir mayúsculas", activa = mayus) { onMayus(!mayus) }
            AccionPeque("En toda la obra →") { onVerObra() }
            Tecla("✕", "Cerrar (Esc)") { onCierra() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CampoRevista(reemplazo, onReemplazo, "Cambiar por", Modifier.weight(1f), pista = "Lo que se pone en su lugar")
            AccionPeque("Esta", apagada = actual < 0) { onEsta() }
            AccionPeque("Todas aquí") { onEscena() }
            AccionPeque("Todas en la obra", acento = true) { onObra() }
        }
    }
}

/** El pie, siempre a la vista: palabras (y las elegidas), minutos, lo de esta sesión, la meta, el sprint y lo de hoy. */
@Composable
private fun PieEditor(e: Escritos, obra: Doc, esc: Doc, c: Cuentas, conc: Boolean, aviso: String) {
    val diaria = obra.i("diaria")
    val meta = esc.i("meta")
    val hover = remember { MutableInteractionSource() }
    val encima by hover.collectIsHoveredAsState()
    val apagado = conc && !encima
    val gris = if (apagado) Muted.copy(alpha = 0.5f) else Muted
    Column(Modifier.fillMaxWidth().hoverable(hover)) {
        if (diaria > 0 && !conc) Progreso(c.hoy.toFloat() / diaria, if (c.hoy >= diaria) Conseguido else Acento, 2.dp)
        else if (!conc) Filete(1.dp, Line)
        Row(Modifier.fillMaxWidth().padding(horizontal = if (conc) 28.dp else 4.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            @Composable fun Dato(t: String, color: Color = gris, peso: FontWeight = FontWeight.Normal) = Text(t, color = color, fontFamily = Body, fontSize = 12.5.sp, fontWeight = peso, maxLines = 1)
            Dato(if (c.elegidas > 0) "${Formato.cifra(c.elegidas)} de ${palabrasTexto(c.palabras)}" else palabrasTexto(c.palabras), if (apagado) gris else Charcoal, FontWeight.SemiBold)
            Dato("${c.minutos} min")
            if (c.sesion != 0) Dato((if (c.sesion > 0) "+" else "") + Formato.cifra(c.sesion) + " en esta sesión", if (c.sesion > 0 && !apagado) Acento else gris)
            if (meta > 0) Dato("meta ${Formato.cifra(meta)}", if (c.palabras >= meta) Conseguido else gris)
            if (aviso.isNotEmpty()) Dato(aviso, Acento)
            Spacer(Modifier.weight(1f))
            if (SprintEscribir.activo(obra.id)) TiraSprint(e) { Editor.guardaYa(); SprintEscribir.termina(e, ya = true) }
            if (diaria > 0) Dato("hoy ${Formato.cifra(c.hoy)}/${Formato.cifra(diaria)}", if (c.hoy >= diaria) Conseguido else gris, FontWeight.SemiBold)
            else Dato("hoy ${Formato.cifra(c.hoy)}")
            if (!conc) Dato("capítulo ${Formato.cifra(c.capitulo)} · obra ${Formato.cifra(c.obra)}")
        }
    }
}

/** La ficha de un nombre, en pequeño (al pasar el ratón por encima en el texto). */
@Composable
private fun PreviaFicha(e: Escritos, f: Doc) {
    val datos = camposDe(f).filter { it.first.isNotBlank() && it.second.isNotBlank() }.take(3)
    val rels = e.relaciones(f.pid).filter { it.s("a") == f.id || it.s("b") == f.id }.take(4).mapNotNull { r -> e.get(if (r.s("a") == f.id) r.s("b") else r.s("a"))?.let { r to it } }
    val c = colorEscrito(f.s("color"))
    Column(Modifier.width(340.dp).clip(esquina(6.dp)).background(Crudo).border(1.dp, Line, esquina(6.dp)).drawBehind { drawRect(c, Offset.Zero, Size(size.width, 5.dp.toPx())) }
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(singularFicha(f.s("tipo")).uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(f.s("nombre"), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 25.sp)
        if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (datos.isNotEmpty()) Column(Modifier.padding(vertical = 4.dp)) { datos.forEach { (k, v) -> DatoLectura(k, if (v.length > 90) v.take(88) + "…" else v) } }
        rels.forEach { (r, o) -> Text("${r.s("titulo").ifBlank { "unión" }.uppercase()} · ${o.s("nombre")}", color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp) }
        Text("Doble clic para abrir la ficha", color = Muted, fontFamily = Body, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Los sinónimos de una palabra, junto a ella: un clic la cambia. */
@Composable
private fun GloboSinonimos(palabra: String, grupos: List<List<String>>, onElige: (String) -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.widthIn(max = 460.dp).heightIn(max = 380.dp).clip(esquina(6.dp)).background(Crudo).border(1.dp, Line, esquina(6.dp)).verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SINÓNIMOS DE «${palabra.uppercase()}»", color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        grupos.take(12).forEachIndexed { i, g ->
            if (i > 0) Filete(1.dp, Line)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                g.forEach { s -> EnlaceSerif(s, tam = 16) { onElige(s) } }
            }
        }
        Text("Del diccionario libre de sinónimos RLA-ES. Esc para cerrar.", color = Muted, fontFamily = Body, fontSize = 10.5.sp)
    }
}

/** Los atajos de teclado del editor (y de la obra), como se escriben en este sistema. */
@Composable
fun ListaAtajos() {
    val l = listOf(
        "Ctrl+B" to "Negrita", "Ctrl+I" to "Cursiva", "Ctrl+Z" to "Deshacer", (if (Atajo.mac) "Ctrl+Mayús+Z" else "Ctrl+Y") to "Rehacer",
        "Ctrl+S" to "Guardar una versión (historial)", "Ctrl+F" to "Buscar y reemplazar", "Ctrl+G" to "Siguiente coincidencia", "Ctrl+Mayús+S" to "Sinónimos de la palabra",
        "Ctrl+Mayús+F" to "Concentración (Esc para salir)", "Ctrl+Mayús+M" to "Máquina de escribir", "Ctrl+Mayús+P" to "Panel de la escena",
        "Ctrl++ / Ctrl+−" to "Letra más grande o más pequeña", "Ctrl+0" to "Letra normal", "Ctrl+1 … Ctrl+8" to "Las pestañas de la obra",
    )
    l.forEach { (k, que) -> MagFact(que, Atajo.texto(k)) }
    MagFact("Pantalla completa", if (Atajo.mac) "⌃⌘F" else "F11")
    MagFact("Abrir la ficha de un nombre subrayado", "Doble clic")
    Spacer(Modifier.height(8.dp))
    Text("Al escribir (con la tipografía automática): -- es una raya, ... son puntos suspensivos, las comillas \" son « ».", color = Muted, fontFamily = Body, fontSize = 12.sp)
}

/** Los atajos de teclado, en una ventana (la «?» de la barra). */
@Composable
private fun DialogoAtajos(onCerrar: () -> Unit) {
    Dialogo("Atajos de teclado", onCerrar, ancho = 620.dp, botones = { BotonAcento("Cerrar", alto = 44.dp, modifier = Modifier.width(120.dp)) { onCerrar() } }) { ListaAtajos() }
}

/** Buscar en toda la obra (escenas, fichas y notas, sin mirar acentos ni mayúsculas): un clic abre lo encontrado. */
@Composable
private fun DialogoBuscarObra(e: Escritos, obra: Doc, inicial: String, onCerrar: () -> Unit, abrir: (String, String) -> Unit, verDoc: (String) -> Unit) {
    var q by remember { mutableStateOf(inicial) }
    val res = remember(q, e.version) { if (q.trim().length < 2) emptyList() else e.busca(obra.id, q) }
    val f = remember { FocusRequester() }
    Hoja(onCerrar, ancho = 760.dp) {
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar en «${obra.s("titulo")}»", Modifier.weight(1f), color = Charcoal, fontFamily = Display, fontSize = 30.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                EnlaceRevista("Cerrar ✕") { onCerrar() }
            }
            Filete(2.dp)
            CampoRevista(q, { q = it }, "Una palabra o una frase", foco = f)
            Text(if (q.trim().length < 2) "" else if (res.isEmpty()) "Nada con «$q»." else "${res.size} ${if (res.size == 1) "resultado" else "resultados"}", color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = 36.dp).padding(bottom = 26.dp)) {
            res.forEach { h ->
                Column(Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand).clickableSinFoco { if (h.doc.t == Escritos.T_ESCENA) abrir(h.doc.id, q.trim()) else verDoc(h.doc.id) }.padding(vertical = 8.dp)) {
                    Text(h.donde.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1)
                    Text(h.extracto, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 3)
                }
                Filete(1.dp, Line)
            }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { f.requestFocus() } }
}

/** Las palabras escritas hoy en esta escena (para enseñarlo mientras escribes). */
fun palabras(t: String) = Escritos.palabras(t)
