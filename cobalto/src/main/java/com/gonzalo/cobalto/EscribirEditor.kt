@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.cobalto

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ES = Locale("es", "ES")

/** Lo que el editor lleva de una escena: su id y lo último escrito (para guardarlo al salir, aunque ya se esté en otra). */
private class EscenaViva(val id: String, var texto: String, var titulo: String)

/**
 * El editor de una escena: una página de libro donde escribir con el formato a la vista, con concentración a pantalla
 * completa (y, si quieres, todo apagado salvo el párrafo que escribes), máquina de escribir, deshacer, buscar y reemplazar
 * en la escena o en toda la obra, las fichas que nombras subrayadas (con su vista previa), la letra, el tamaño, el
 * interlineado y el ancho a tu gusto, atajos con un teclado físico, y un panel con la escena, las fichas, las notas, los
 * borradores y el historial. Las cuentas (palabras, sesión, meta, lo de hoy) siempre a la vista. Se guarda solo (y sube con
 * tu cuenta en cuanto hay red; sin conexión se queda en el dispositivo).
 */
@Composable
fun EscribirEditor(eid: String, onBack: () -> Unit, abrirEscena: (String) -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val escena = e.get(eid)
    if (escena == null) { LaunchedEffect(Unit) { onBack() }; return }
    val pid = escena.pid
    val obra = e.get(pid)
    val pagina = remember { PaginaEscribir.de(ctx) }
    var sprint by remember { mutableStateOf(false) }
    var sinonimos by remember { mutableStateOf<Pair<IntRange, List<List<String>>>?>(null) }
    var avisoEd by remember { mutableStateOf("") }
    LaunchedEffect(avisoEd) { if (avisoEd.isNotEmpty()) { kotlinx.coroutines.delay(2600); avisoEd = "" } }
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Sinonimos.carga(ctx) } }
    var foco by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var buscar by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var hojaPagina by remember { mutableStateOf(false) }
    var atajos by remember { mutableStateOf(false) }

    var tf by remember(eid) { mutableStateOf(TextFieldValue(escena.s("texto"), TextRange(escena.s("texto").length))) }
    val historial = remember(eid) { HistorialTexto(tf) }
    var guardado by remember(eid) { mutableStateOf(escena.s("texto")) }
    val inicio = remember(eid) { Escritos.palabras(escena.s("texto")) }
    var titulo by remember(eid) { mutableStateOf(escena.s("titulo")) }
    // (lo de esta escena, para guardarlo al irse: NO vale un rememberUpdatedState del texto, porque al cambiar de escena
    // dentro del editor ya tendría el texto de la nueva cuando se guarda la vieja, y la pisaría)
    val viva = remember(eid) { EscenaViva(eid, escena.s("texto"), escena.s("titulo")) }
    SideEffect { viva.texto = tf.text; viva.titulo = titulo }

    fun guardaYa(x: EscenaViva = viva) {
        // (la de ahora, con lo último del teclado aunque aún no se haya vuelto a pintar)
        if (x === viva) { x.texto = tf.text; x.titulo = titulo }
        val d = e.get(x.id) ?: return
        if (d.s("texto") != x.texto) e.escribe(x.id, x.texto)
        if (d.s("titulo") != x.titulo) e.edita(x.id) { put("titulo", x.titulo) }
        if (x === viva) guardado = x.texto
    }
    DisposableEffect(eid) {
        EscribirHub.editores++
        onDispose {
            EscribirHub.editores = (EscribirHub.editores - 1).coerceAtLeast(0)
            guardaYa(viva)
            e.instantanea(viva.id, viva.texto)
            Escritos.pronto(ctx)
        }
    }
    // Si Android pausa la app (otra app, la pantalla apagada…), lo último escrito se guarda ya: no espera a la calma
    @Suppress("DEPRECATION") val ciclo = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(ciclo, eid) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) { guardaYa(viva); e.instantanea(viva.id, viva.texto) }
        }
        ciclo.lifecycle.addObserver(o)
        onDispose { ciclo.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(eid) { e.instantanea(eid, escena.s("texto"), hueco = 3_600_000L) }
    // Se guarda a los 0,6 s de dejar de escribir; una instantánea (historial) cada 5 min
    LaunchedEffect(tf.text) { if (tf.text != guardado) { kotlinx.coroutines.delay(600); e.escribe(eid, tf.text); guardado = tf.text } }
    LaunchedEffect(eid) { while (true) { kotlinx.coroutines.delay(300_000); e.instantanea(viva.id, viva.texto) } }
    LaunchedEffect(titulo) { if (titulo != escena.s("titulo")) { kotlinx.coroutines.delay(500); e.edita(eid) { put("titulo", titulo) } } }
    // Si otro dispositivo cambia esta escena: si aquí no hay nada sin guardar, se ve al momento; si lo hay, se juntan los dos
    // a tres bandas (desde lo último guardado aquí) y, si chocan en el mismo trozo, lo de fuera se guarda como borrador
    LaunchedEffect(ver) {
        val r = e.get(eid)?.s("texto") ?: return@LaunchedEffect
        if (r == guardado) return@LaunchedEffect
        if (tf.text == guardado) {
            tf = TextFieldValue(r, TextRange(minOf(tf.selection.start, r.length)))
            historial.apunta(tf)
        } else {
            val m = EscribirMezcla.texto(guardado, tf.text, r, prefiereNuestro = true)
            if (m.choques > 0) e.nuevo(Escritos.T_BORRADOR, pid) { put("eid", eid); put("nombre", "Lo que llegó de otro dispositivo (" + fechaHora(System.currentTimeMillis()) + ")"); put("texto", r); put("conflicto", 1) }
            val comun = tf.text.commonPrefixWith(m.texto).length
            val c = tf.selection.start.let { if (it <= comun) it else it + (m.texto.length - tf.text.length) }.coerceIn(0, m.texto.length)
            tf = TextFieldValue(m.texto, TextRange(c))
            historial.apunta(tf)
        }
        guardado = r
        avisoEd = "Ha cambiado en otro dispositivo: ya lo ves al día."
    }
    // Lo que pide «Buscar en la obra» al abrir esta escena
    var irA by remember { mutableStateOf<Pair<String, Int>?>(null) }

    fun cambia(v: TextFieldValue) { tf = v; historial.apunta(v) }
    fun pon(texto: String) { cambia(TextFieldValue(texto, TextRange(texto.length))) }
    // Lo que llega del teclado: si es un solo carácter, la tipografía automática (— « » …), que se deshace aparte
    fun teclea(v: TextFieldValue) {
        val antes = tf
        val c = v.selection.start
        if (pagina.tipografia && v.selection.collapsed && c >= 1 && v.text.length == antes.text.length + 1 && v.composition.let { it == null || it.end <= c - 3 } &&
            v.text.regionMatches(0, antes.text, 0, c - 1) && v.text.regionMatches(c, antes.text, c - 1, v.text.length - c)) {
            Tipografia.aplica(v.text, c, v.text[c - 1])?.let { (t, k) ->
                historial.apunta(v); historial.corte()
                val r = TextFieldValue(t, TextRange(k))
                tf = r; historial.apunta(r); historial.corte()
                return
            }
        }
        cambia(v)
    }
    fun abreSinonimos() {
        val r = Sinonimos.palabraEn(tf.text, tf.selection.start, tf.selection.end)
        if (r == null) { avisoEd = "Pon el cursor en una palabra"; return }
        val g = Sinonimos.busca(tf.text.substring(r.first, r.last + 1))
        if (g.isEmpty()) avisoEd = "Sin sinónimos para «${tf.text.substring(r.first, r.last + 1)}»" else sinonimos = r to g
    }
    // Ir a otra escena desde el editor: lo de esta se guarda antes
    fun irAEscena(id: String) { guardaYa(); abrirEscena(id) }
    val orden = remember(ver, pid) { e.escenasDe(pid) }
    val i = orden.indexOfFirst { it.id == eid }
    val anterior = orden.getOrNull(i - 1)
    val siguiente = orden.getOrNull(i + 1)
    val capitulo = remember(ver, eid) { e.get(escena.s("cid")) }
    fun escenaNueva() {
        val cid = escena.s("cid")
        val hermanas = e.escenas(cid)
        val tras = hermanas.getOrNull(hermanas.indexOfFirst { it.id == eid } + 1)
        val n = e.nuevo(Escritos.T_ESCENA, pid) { put("cid", cid); put("titulo", "Escena ${hermanas.size + 1}") }
        e.mueveEscena(n.id, cid, eid, tras?.id)
        irAEscena(n.id)
    }

    // ---- Buscar y reemplazar
    var consulta by remember { mutableStateOf("") }
    var reemplazo by remember { mutableStateOf("") }
    var mayus by remember { mutableStateOf(false) }
    var entera by remember { mutableStateOf(false) }
    var conCambio by remember { mutableStateOf(false) }
    var enObra by remember { mutableStateOf(false) }
    var confirmaObra by remember { mutableStateOf(false) }
    val rx = remember(consulta, mayus, entera) { regexBusqueda(consulta, mayus, entera) }
    val coincidencias = remember(rx, tf.text) { rx?.findAll(tf.text)?.map { it.range }?.toList().orEmpty() }
    val enLaObra = remember(rx, ver, buscar) { if (buscar && rx != null) buscaEnObra(e, pid, rx).sumOf { it.veces.size } else 0 }
    var actual by remember(consulta, eid) { mutableIntStateOf(-1) }
    val foco1 = remember { FocusRequester() }
    val focoBusca = remember { FocusRequester() }
    var textoConFoco by remember { mutableStateOf(false) }
    fun salta(k: Int) {
        if (coincidencias.isEmpty()) return
        val j = ((k % coincidencias.size) + coincidencias.size) % coincidencias.size
        actual = j
        tf = tf.copy(selection = TextRange(coincidencias[j].first, coincidencias[j].last + 1))
        runCatching { foco1.requestFocus() }
    }
    fun abreBusca(cambiar: Boolean = false) {
        if (!tf.selection.collapsed) consulta = tf.text.substring(minOf(tf.selection.start, tf.selection.end), maxOf(tf.selection.start, tf.selection.end)).take(80)
        buscar = true; if (cambiar) conCambio = true
    }
    fun cierraBusca() { buscar = false; consulta = ""; confirmaObra = false }
    LaunchedEffect(buscar) { if (buscar) { kotlinx.coroutines.delay(60); runCatching { focoBusca.requestFocus() } } }
    LaunchedEffect(eid) {
        val b = EscribirHub.buscarAlAbrir
        if (b != null && b.first == eid) { EscribirHub.buscarAlAbrir = null; consulta = b.second; buscar = true; irA = eid to 0 }
    }
    LaunchedEffect(irA, coincidencias) {
        val (id, k) = irA ?: return@LaunchedEffect
        if (id != eid || coincidencias.isEmpty()) return@LaunchedEffect
        irA = null; salta(k.coerceIn(0, coincidencias.lastIndex))
    }

    val fichasObra = remember(ver, pid) { e.fichas(pid) }
    val nombres = remember(fichasObra) {
        val ns = fichasObra.flatMap { f -> listOf(f.s("nombre")) + f.s("alias").split(',') }.map { it.trim() }.filter { it.length >= 2 }.distinctBy { it.lowercase() }.sortedByDescending { it.length }
        if (ns.isEmpty()) null else Regex("(?<![\\p{L}\\p{N}])(" + ns.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
    // La ficha cuyo nombre (o alias) toca el cursor: se busca solo alrededor del cursor, para no recorrer la escena entera
    val fichaAqui = remember(tf.selection, tf.text, nombres, fichasObra) {
        val rxN = nombres ?: return@remember null
        val k = tf.selection.start.coerceIn(0, tf.text.length)
        val desde = (k - 80).coerceAtLeast(0)
        val trozo = tf.text.substring(desde, (k + 80).coerceAtMost(tf.text.length))
        val m = rxN.findAll(trozo).firstOrNull { k - desde >= it.range.first && k - desde <= it.range.last + 1 } ?: return@remember null
        fichasObra.firstOrNull { f -> (listOf(f.s("nombre")) + f.s("alias").split(',')).any { it.trim().equals(m.value, true) } }
    }
    var previaCerrada by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fichaAqui?.id) { if (fichaAqui == null) previaCerrada = null }
    val lectura = remember { AbreEscribir() }
    val familia = pagina.letra.familia
    val tam = pagina.tam
    val aspecto = AspectoTexto(Charcoal, Muted.copy(alpha = 0.6f), ColGeneral, Display, tam)
    val formato = remember(aspecto.tinta, aspecto.tenue, aspecto.acento, tam, nombres, rx) { FormatoMarkdown(aspecto, nombres, rx) }
    val vivo = if (foco && pagina.atenuar) EdicionTexto.parrafo(tf.text, tf.selection.start) else null
    val apagado = Charcoal.copy(alpha = 0.26f)
    val transformacion = remember(formato, pagina.sangria, tam, vivo, apagado) {
        FormatoPagina(formato, sangria = if (pagina.sangria) (tam * 1.6f).sp else androidx.compose.ui.unit.TextUnit.Unspecified, vivo = vivo, apagado = apagado)
    }
    val palabras = remember(tf.text) { Escritos.palabras(tf.text) }
    val selPalabras = if (tf.selection.collapsed) 0 else Escritos.palabras(tf.text.substring(minOf(tf.selection.start, tf.selection.end), maxOf(tf.selection.start, tf.selection.end)))
    val hoy = e.escritas(Escritos.hoy(), pid)
    val diaria = obra?.i("diaria") ?: 0
    val cuentas = CuentasEscena(palabras, selPalabras, palabras - inicio, escena.i("meta"), hoy, diaria)

    PantallaCompleta(foco)
    BackHandler {
        when {
            panel -> panel = false
            buscar -> cierraBusca()
            foco -> foco = false
            else -> onBack()
        }
    }

    // ---- Atajos con un teclado físico (ver ATAJOS_EDITOR)
    fun tecla(ev: KeyEvent): Boolean {
        if (ev.type != KeyEventType.KeyDown) return false
        val ctrl = ev.isCtrlPressed || ev.isMetaPressed
        when {
            ev.key == Key.Escape -> { when { buscar -> cierraBusca(); panel -> panel = false; foco -> foco = false; else -> return false }; return true }
            ev.key == Key.F11 || (ctrl && ev.isShiftPressed && ev.key == Key.F) -> { foco = !foco; return true }
            ev.key == Key.F3 -> { if (!buscar) abreBusca() else salta(if (ev.isShiftPressed) actual - 1 else actual + 1); return true }
            ev.key == Key.F1 -> { atajos = true; return true }
            ev.isAltPressed && !ctrl && (ev.key == Key.DirectionUp || ev.key == Key.DirectionDown) && textoConFoco -> {
                val v = EdicionTexto.mueveParrafo(tf, if (ev.key == Key.DirectionUp) -1 else 1); if (v.text != tf.text) cambia(v); return true
            }
        }
        if (!ctrl) return false
        when (ev.key) {
            Key.F -> abreBusca()
            Key.H -> abreBusca(cambiar = true)
            Key.G -> { if (!buscar) abreBusca() else salta(if (ev.isShiftPressed) actual - 1 else actual + 1) }
            Key.S -> { guardaYa(); e.instantanea(eid, tf.text, fuerza = true); avisoEd = "Versión guardada" }
            Key.T -> { pagina.maquina = !pagina.maquina; pagina.guarda(); avisoEd = if (pagina.maquina) "Máquina de escribir" else "Sin máquina de escribir" }
            Key.P -> panel = !panel
            Key.K -> abreSinonimos()
            Key.Slash -> atajos = true
            Key.Plus, Key.Equals, Key.NumPadAdd -> pagina.letraMas()
            Key.Minus, Key.NumPadSubtract -> pagina.letraMenos()
            Key.PageUp -> anterior?.let { irAEscena(it.id) } ?: return false
            Key.PageDown -> siguiente?.let { irAEscena(it.id) } ?: return false
            else -> {
                // Lo que cambia el texto, solo con el cursor en la página
                if (!textoConFoco) return false
                when (ev.key) {
                    Key.B -> cambia(EdicionTexto.envuelve(tf, "**"))
                    Key.I -> cambia(EdicionTexto.envuelve(tf, "*"))
                    Key.Z -> (if (ev.isShiftPressed) historial.rehace() else historial.deshace())?.let { tf = it }
                    Key.Y -> historial.rehace()?.let { tf = it }
                    Key.One -> cambia(EdicionTexto.prefijoLinea(tf, "# "))
                    Key.Two -> cambia(EdicionTexto.prefijoLinea(tf, "## "))
                    Key.Three -> cambia(EdicionTexto.prefijoLinea(tf, "### "))
                    Key.C -> if (ev.isShiftPressed) cambia(EdicionTexto.prefijoLinea(tf, "> ")) else return false
                    Key.E -> cambia(EdicionTexto.comillas(tf))
                    Key.D -> cambia(EdicionTexto.inserta(tf, "—"))
                    Key.Enter, Key.NumPadEnter -> cambia(EdicionTexto.salto(tf))
                    else -> return false
                }
            }
        }
        return true
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper).onPreviewKeyEvent { tecla(it) }) {
        val ancha = maxWidth >= 900.dp
        val tableta = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding().navigationBarsPadding().imePadding()) {
                if (!foco) {
                    CabeceraEditor(obra, capitulo?.let { numeroCapitulo(e, it) } ?: 0, titulo, { titulo = it.replace("\n", "") }, tableta, onBack, panel, { panel = !panel }, { foco = true }) {
                        Box {
                            IconPill(Icons.Rounded.MoreVert, "Más") { menu = true }
                            DropdownMenu(menu, { menu = false }, containerColor = Paper) {
                                DropdownMenuItem({ Text("Buscar y cambiar") }, { menu = false; abreBusca(cambiar = true) })
                                DropdownMenuItem({ Text("La página: letra, tamaño, interlineado…") }, { menu = false; hojaPagina = true })
                                DropdownMenuItem({ Text(if (SprintEscribir.activo()) "Terminar el sprint" else "Sprint de escritura…") }, { menu = false; if (SprintEscribir.activo()) SprintEscribir.termina(ctx, e, ya = true) else sprint = true })
                                DropdownMenuItem({ Text("Sinónimos de la palabra") }, { menu = false; abreSinonimos() })
                                DropdownMenuItem({ Text(if (pagina.maquina) "Máquina de escribir: sí" else "Máquina de escribir: no") }, { menu = false; pagina.maquina = !pagina.maquina; pagina.guarda() })
                                DropdownMenuItem({ Text("Guardar una versión ahora") }, { menu = false; guardaYa(); e.instantanea(eid, tf.text, fuerza = true); avisoEd = "Versión guardada" })
                                DropdownMenuItem({ Text("Escena nueva a continuación") }, { menu = false; escenaNueva() })
                                DropdownMenuItem({ Text("Atajos de teclado") }, { menu = false; atajos = true })
                                DropdownMenuItem({ Text("Compartir el texto") }, {
                                    menu = false
                                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, tf.text), "Compartir"))
                                })
                            }
                        }
                    }
                    // Hoy: lo escrito frente a la meta del día
                    if (diaria > 0) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Bar((hoy.toFloat() / diaria).coerceIn(0f, 1f), if (hoy >= diaria) VerdeHecho else ColGeneral, height = 3.dp)
                    }
                    AnimatedVisibility(buscar, enter = fadeIn() + androidx.compose.animation.expandVertically(), exit = fadeOut() + androidx.compose.animation.shrinkVertically()) {
                        BarraBuscar(
                            consulta, { consulta = it; confirmaObra = false }, reemplazo, { reemplazo = it }, mayus, { mayus = it }, entera, { entera = it }, conCambio, { conCambio = it },
                            coincidencias.size, actual, enLaObra, focoBusca,
                            onSig = { salta(actual + 1) }, onAnt = { salta(actual - 1) },
                            onEsta = {
                                if (actual in coincidencias.indices) {
                                    val r = coincidencias[actual]
                                    cambia(TextFieldValue(tf.text.replaceRange(r.first, r.last + 1, reemplazo), TextRange(r.first + reemplazo.length)))
                                    irA = eid to actual
                                }
                            },
                            onEscena = { if (rx != null && coincidencias.isNotEmpty()) { val n = coincidencias.size; cambia(TextFieldValue(rx.replace(tf.text, Regex.escapeReplacement(reemplazo)), TextRange(tf.selection.start.coerceAtMost(tf.text.length)))); avisoEd = "$n ${if (n == 1) "cambio" else "cambios"} en la escena" } },
                            confirmaObra = confirmaObra,
                            onObra = {
                                if (!confirmaObra) confirmaObra = true
                                else if (rx != null) {
                                    guardaYa()
                                    val n = reemplazaEnObra(e, pid, rx, reemplazo)
                                    e.get(eid)?.s("texto")?.let { r -> if (r != tf.text) { cambia(TextFieldValue(r, TextRange(tf.selection.start.coerceAtMost(r.length)))); guardado = r } }
                                    confirmaObra = false
                                    avisoEd = "$n ${if (n == 1) "cambio" else "cambios"} en toda la obra (antes se guardó una versión de cada escena)"
                                }
                            },
                            onVerObra = { enObra = true },
                            onCierra = { cierraBusca() }
                        )
                    }
                }

                // ---- La página
                val scroll = rememberScrollState()
                var vista by remember { mutableIntStateOf(0) }
                var diseno by remember { mutableStateOf<TextLayoutResult?>(null) }
                val densidad = LocalDensity.current
                val margenSup = 18.dp
                val arriba = if (pagina.maquina) (vista / densidad.density * 0.36f).dp else margenSup
                // Máquina de escribir: la línea donde escribes se queda a un 40 % de la altura. Sin ella, el cursor nunca se
                // queda escondido debajo de las barras (ni al escribir ni al saltar a lo buscado)
                // (solo con el cursor en la página: al abrir una escena se empieza arriba, como siempre)
                LaunchedEffect(tf.selection, diseno, pagina.maquina, foco, vista, textoConFoco) {
                    val d = diseno ?: return@LaunchedEffect
                    if (!textoConFoco || vista == 0 || tf.selection.end > d.layoutInput.text.length) return@LaunchedEffect
                    val r = d.getCursorRect(tf.selection.end.coerceIn(0, d.layoutInput.text.length))
                    val sup = with(densidad) { arriba.toPx() }
                    val destino = if (pagina.maquina) (r.top + sup - vista * 0.4f).toInt()
                    else {
                        val margen = with(densidad) { 36.dp.toPx() }
                        when {
                            r.top + sup - margen < scroll.value -> (r.top + sup - margen).toInt()
                            r.bottom + sup + margen > scroll.value + vista -> (r.bottom + sup + margen - vista).toInt()
                            else -> return@LaunchedEffect
                        }
                    }.coerceIn(0, scroll.maxValue)
                    if (kotlin.math.abs(destino - scroll.value) < r.height * 2.5f) scroll.scrollTo(destino) else scroll.animateScrollTo(destino)
                }
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { vista = it.height }) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = if (tableta) pagina.anchoMax else 700.dp).fillMaxWidth().padding(horizontal = if (foco) 28.dp else 22.dp).padding(top = arriba)) {
                            BasicTextField(
                                tf, { teclea(it) },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = (vista / densidad.density * 0.6f).dp).focusRequester(foco1)
                                    .onFocusChanged { textoConFoco = it.isFocused }.testTag("pagina")
                                    .semantics { contentDescription = "Texto de la escena" },
                                textStyle = TextStyle(fontFamily = familia, fontSize = tam.sp, lineHeight = (tam * pagina.interlineado).sp, color = Charcoal),
                                cursorBrush = SolidColor(ColGeneral),
                                visualTransformation = transformacion,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                onTextLayout = { diseno = it },
                                decorationBox = { inner -> Box { if (tf.text.isEmpty()) Text("Empieza aquí…", color = Muted, fontFamily = familia, fontSize = tam.sp, fontStyle = FontStyle.Italic); inner() } }
                            )
                            if (!foco) FinDeEscena(anterior, siguiente, onIr = { irAEscena(it) }, onNueva = { escenaNueva() })
                            Spacer(Modifier.height(if (pagina.maquina) (vista / densidad.density * 0.55f).dp else 120.dp))
                        }
                    }
                    if (foco) Text("SALIR", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).press(haptic = false) { foco = false }.padding(6.dp))
                    if (foco && SprintEscribir.activo(pid)) Box(Modifier.align(Alignment.TopStart).padding(14.dp).padding(6.dp)) { TiraSprint(e) { SprintEscribir.termina(ctx, e, ya = true) } }
                    // Los avisos, un momento, abajo
                    AvisoEditor(avisoEd, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 20.dp, end = 20.dp))
                }
                // La ficha del nombre donde está el cursor: su vista previa, y «Leer» la ficha
                if (!foco) PreviaFicha(e, fichaAqui?.takeIf { it.id != previaCerrada }, onLeer = { lectura.ver = it.id }, onCerrar = { previaCerrada = it.id })
                if (!foco) {
                    CuentasEditor(cuentas, discreto = false, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    if (SprintEscribir.activo(pid)) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TiraSprint(e) { SprintEscribir.termina(ctx, e, ya = true) }
                    }
                    BarraFormato(
                        tableta = tableta, maquina = pagina.maquina,
                        puedeDeshacer = historial.puedeDeshacer, puedeRehacer = historial.puedeRehacer,
                        onDeshacer = { historial.deshace()?.let { tf = it } }, onRehacer = { historial.rehace()?.let { tf = it } },
                        onNegrita = { cambia(EdicionTexto.envuelve(tf, "**")) }, onCursiva = { cambia(EdicionTexto.envuelve(tf, "*")) },
                        onTitulo = { cambia(EdicionTexto.prefijoLinea(tf, "## ")) }, onCita = { cambia(EdicionTexto.prefijoLinea(tf, "> ")) },
                        onSalto = { cambia(EdicionTexto.salto(tf)) }, onComillas = { cambia(EdicionTexto.comillas(tf)) },
                        onRaya = { cambia(EdicionTexto.inserta(tf, "—")) }, onBuscar = { if (buscar) cierraBusca() else abreBusca() }, onSinonimos = { abreSinonimos() },
                        onPagina = { hojaPagina = true }, onMaquina = { pagina.maquina = !pagina.maquina; pagina.guarda() },
                        onSprint = { if (SprintEscribir.activo()) SprintEscribir.termina(ctx, e, ya = true) else sprint = true }, onFoco = { foco = true }
                    )
                } else if (pagina.cuentasFoco) {
                    CuentasEditor(cuentas, discreto = true, Modifier.padding(horizontal = 28.dp, vertical = 10.dp).widthIn(max = pagina.anchoMax).align(Alignment.CenterHorizontally))
                }
            }
            if (ancha && panel && !foco) Column(Modifier.width(360.dp).fillMaxHeight().background(Crudo).statusBarsPadding().navigationBarsPadding()) {
                PanelEscena(e, escena, ver, onCierra = { panel = false }, inserta = { x -> cambia(EdicionTexto.inserta(tf, x)) }, pon = { pon(it) }, textoActual = { tf.text }, leer = { lectura.ver = it }, abrirEscena = { irAEscena(it) })
            }
        }
        if (sprint) SprintDialogo(e, pid) { sprint = false }
        if (hojaPagina) HojaPagina(pagina, tableta) { hojaPagina = false }
        if (atajos) AtajosDialogo { atajos = false }
        if (enObra && rx != null) ResultadosObra(e, pid, consulta, rx, eid, onIr = { id, k -> enObra = false; if (id == eid) salta(k) else { irA = id to k; irAEscena(id) } }, onCerrar = { enObra = false })
        VentanasEscribir(e, pid, lectura, abrirEscena = { irAEscena(it) })
        sinonimos?.let { (r, grupos) ->
            val palabra = tf.text.substring(r.first, (r.last + 1).coerceAtMost(tf.text.length))
            ModalBottomSheet({ sinonimos = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Crudo, shape = shp(28.dp)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sinónimos de «$palabra»", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                    grupos.take(12).forEach { g ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            g.forEach { s -> SoftChip(s, false) {
                                val fin = (r.last + 1).coerceAtMost(tf.text.length)
                                if (tf.text.substring(r.first, fin) == palabra) cambia(TextFieldValue(tf.text.replaceRange(r.first, fin, s), TextRange(r.first + s.length)))
                                sinonimos = null
                            } }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    }
                    Text("Del diccionario libre de sinónimos RLA-ES.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (!ancha && panel && !foco) ModalBottomSheet({ panel = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Crudo, shape = shp(28.dp)) {
            Column(Modifier.fillMaxHeight(0.92f)) {
                PanelEscena(e, escena, ver, onCierra = { panel = false }, inserta = { x -> cambia(EdicionTexto.inserta(tf, x)); panel = false }, pon = { pon(it); panel = false }, textoActual = { tf.text },
                    leer = { lectura.ver = it }, abrirEscena = { panel = false; irAEscena(it) })
            }
        }
    }
}

/** Un aviso corto del editor («Versión guardada»…), en una píldora abajo, que entra y se va suave. */
@Composable
private fun AvisoEditor(aviso: String, modifier: Modifier) {
    var ultimo by remember { mutableStateOf("") }
    if (aviso.isNotEmpty()) ultimo = aviso
    AnimatedVisibility(aviso.isNotEmpty(), modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Text(ultimo, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(pillShape()).background(Charcoal).padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

/** El verde de lo cumplido (la meta del día, la de la escena). */
val VerdeHecho = androidx.compose.ui.graphics.Color(0xFF5B8C5A)

/** Arriba del editor: volver al capítulo, la obra y el capítulo en pequeño, el título de la escena (se edita ahí mismo) y los botones. */
@Composable
private fun CabeceraEditor(
    obra: Doc?, numCap: Int, titulo: String, onTitulo: (String) -> Unit, tableta: Boolean, onBack: () -> Unit,
    panel: Boolean, onPanel: () -> Unit, onFoco: () -> Unit, mas: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        VolverRevista(onBack, if (numCap > 0) "Cap. $numCap" else "Volver")
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(obra?.s("titulo").orEmpty().uppercase(), color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val t = if (tableta) 24.sp else 20.sp
            BasicTextField(
                titulo, onTitulo, singleLine = true,
                textStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = t, color = Charcoal),
                cursorBrush = SolidColor(ColGeneral),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.semantics { contentDescription = "Título de la escena" },
                decorationBox = { inner -> Box { if (titulo.isEmpty()) Text("Sin título", color = Muted, fontFamily = Display, fontSize = t, fontStyle = FontStyle.Italic); inner() } }
            )
        }
        IconPill(Icons.Rounded.ViewSidebar, "Panel de la escena", active = panel, onClick = onPanel)
        Spacer(Modifier.width(4.dp))
        IconPill(Icons.Rounded.CenterFocusStrong, "Concentración", onClick = onFoco)
        Spacer(Modifier.width(4.dp))
        mas()
    }
}

/** Al final del texto: la escena de antes, la de después y una nueva a continuación (sin salir del editor). */
@Composable
private fun FinDeEscena(anterior: Doc?, siguiente: Doc?, onIr: (String) -> Unit, onNueva: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp)) {
        Text("* * *", color = Muted.copy(alpha = 0.6f), fontFamily = Display, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(bottom = 18.dp))
        Filete(1.dp, Line)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
            if (anterior != null) Column(Modifier.weight(1f).press(haptic = false) { onIr(anterior.id) }.padding(vertical = 10.dp)) {
                Text("← ESCENA ANTERIOR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                Text(anterior.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 16.sp, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            if (siguiente != null) Column(Modifier.weight(1f).press(haptic = false) { onIr(siguiente.id) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.End) {
                Text("SIGUIENTE →", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                Text(siguiente.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 16.sp, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Spacer(Modifier.weight(1f))
        }
        EnlaceRevista("+ Escena nueva a continuación", acento = siguiente == null, onClick = onNueva)
    }
}

@Composable
private fun BarraFormato(
    tableta: Boolean, maquina: Boolean,
    puedeDeshacer: Boolean, puedeRehacer: Boolean, onDeshacer: () -> Unit, onRehacer: () -> Unit,
    onNegrita: () -> Unit, onCursiva: () -> Unit, onTitulo: () -> Unit, onCita: () -> Unit,
    onSalto: () -> Unit, onComillas: () -> Unit, onRaya: () -> Unit, onBuscar: () -> Unit, onSinonimos: () -> Unit,
    onPagina: () -> Unit, onMaquina: () -> Unit, onSprint: () -> Unit, onFoco: () -> Unit
) {
    @Composable fun Tecla(icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean = true, activa: Boolean = false, f: () -> Unit) =
        Box(Modifier.size(44.dp).clip(CircleShape).then(if (on) Modifier.press(haptic = false, onClick = f) else Modifier).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = if (!on) Line else if (activa) Acento else Charcoal, modifier = Modifier.size(22.dp))
        }
    @Composable fun Letra(t: String, desc: String, f: () -> Unit) =
        Box(Modifier.size(44.dp).clip(CircleShape).press(haptic = false, onClick = f).semantics(mergeDescendants = true) { contentDescription = desc }, contentAlignment = Alignment.Center) {
            Text(t, color = Charcoal, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Display, modifier = Modifier.clearAndSetSemantics { })
        }
    @Composable fun Raya() = Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(Line))
    Row(Modifier.fillMaxWidth().background(Crudo).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Tecla(Icons.AutoMirrored.Rounded.Undo, "Deshacer", puedeDeshacer, f = onDeshacer)
            Tecla(Icons.AutoMirrored.Rounded.Redo, "Rehacer", puedeRehacer, f = onRehacer)
            Raya()
            Tecla(Icons.Rounded.FormatBold, "Negrita", f = onNegrita)
            Tecla(Icons.Rounded.FormatItalic, "Cursiva", f = onCursiva)
            Tecla(Icons.Rounded.Title, "Título", f = onTitulo)
            Tecla(Icons.Rounded.FormatQuote, "Cita", f = onCita)
            Tecla(Icons.Rounded.HorizontalRule, "Salto de escena", f = onSalto)
            Letra("«»", "Comillas", onComillas)
            Letra("—", "Raya", onRaya)
            Letra("≈", "Sinónimos", onSinonimos)
            Raya()
            Tecla(Icons.Rounded.Search, "Buscar", f = onBuscar)
            Tecla(Icons.Rounded.FormatSize, "La página", f = onPagina)
            if (!tableta) {
                Tecla(Icons.Rounded.VerticalAlignCenter, "Máquina de escribir", activa = maquina, f = onMaquina)
                Tecla(Icons.Rounded.Timer, "Sprint", f = onSprint)
            }
        }
        // En la tableta, lo de la escritura a la derecha y con su nombre
        if (tableta) Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
            FiltroRevista("Máquina", maquina, onClick = onMaquina)
            FiltroRevista(if (SprintEscribir.activo()) "Terminar sprint" else "Sprint", false, onClick = onSprint)
            FiltroRevista("Concentración", false, onClick = onFoco)
        }
    }
}

/** Una casilla de la búsqueda (sin marco: una línea debajo, como en la revista). */
@Composable
private fun CampoBusqueda(valor: String, onCambia: (String) -> Unit, pista: String, modifier: Modifier = Modifier, focus: FocusRequester? = null, onIntro: () -> Unit = {}) {
    BasicTextField(
        valor, onCambia, singleLine = true,
        textStyle = TextStyle(fontFamily = Body, fontSize = 16.sp, color = Charcoal),
        cursorBrush = SolidColor(ColGeneral),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onIntro() }),
        modifier = modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier).semantics { contentDescription = pista },
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 8.dp)) { if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Body, fontSize = 16.sp); inner() }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (valor.isEmpty()) Line else Charcoal))
            }
        }
    )
}

@Composable
private fun BarraBuscar(
    consulta: String, onConsulta: (String) -> Unit, reemplazo: String, onReemplazo: (String) -> Unit, mayus: Boolean, onMayus: (Boolean) -> Unit,
    entera: Boolean, onEntera: (Boolean) -> Unit, conCambio: Boolean, onConCambio: (Boolean) -> Unit,
    total: Int, actual: Int, enLaObra: Int, focus: FocusRequester, onSig: () -> Unit, onAnt: () -> Unit, onEsta: () -> Unit, onEscena: () -> Unit,
    confirmaObra: Boolean, onObra: () -> Unit, onVerObra: () -> Unit, onCierra: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Crudo).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CampoBusqueda(consulta, onConsulta, "Buscar en la escena", Modifier.weight(1f), focus, onIntro = onSig)
            Text(if (consulta.isEmpty()) "" else if (total == 0) "0" else (if (actual >= 0) "${actual + 1}" else "–") + "/$total", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            IconPill(Icons.Rounded.KeyboardArrowUp, "Anterior", onClick = onAnt)
            IconPill(Icons.Rounded.KeyboardArrowDown, "Siguiente", onClick = onSig)
            IconPill(Icons.Rounded.Close, "Cerrar la búsqueda", onClick = onCierra)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FiltroRevista("Aa", mayus) { onMayus(!mayus) }
            FiltroRevista("Palabra entera", entera) { onEntera(!entera) }
            FiltroRevista("Cambiar", conCambio) { onConCambio(!conCambio) }
            if (consulta.isNotEmpty()) FiltroRevista(if (enLaObra == 0) "En la obra: 0" else "En la obra: $enLaObra →", false) { if (enLaObra > 0) onVerObra() }
        }
        if (conCambio) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoBusqueda(reemplazo, onReemplazo, "Cambiar por", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                EnlaceRevista("Este", onClick = onEsta)
                EnlaceRevista("Todos en la escena", onClick = onEscena)
                EnlaceRevista(if (confirmaObra) "¿Seguro? Cambiar $enLaObra en la obra" else "Todos en la obra", acento = confirmaObra, onClick = onObra)
            }
        }
    }
}

// ---------------------------------------------------------------- El panel de la escena

@Composable
private fun PanelEscena(
    e: Escritos, escena: Doc, ver: Int, onCierra: () -> Unit, inserta: (String) -> Unit, pon: (String) -> Unit, textoActual: () -> String, leer: (String) -> Unit, abrirEscena: (String) -> Unit
) {
    var pestana by remember { mutableIntStateOf(0) }
    val pid = escena.pid
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("La escena", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal, modifier = Modifier.weight(1f))
            EnlaceRevista("Cerrar", Modifier.padding(horizontal = 10.dp), onClick = onCierra)
        }
        FiltrosRevista(listOf("Escena", "Fichas", "Notas", "Borradores", "Historial"), pestana, Modifier.padding(horizontal = 18.dp)) { pestana = it }
        Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Line))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (pestana) {
                0 -> PestanaEscena(e, escena)
                1 -> PestanaFichas(e, pid, textoActual(), inserta, leer)
                2 -> PestanaNotas(e, pid, inserta, leer)
                3 -> PestanaBorradores(e, escena, textoActual(), pon)
                else -> PestanaHistorial(e, escena, textoActual(), pon)
            }
        }
    }
}

@Composable
private fun PestanaEscena(e: Escritos, escena: Doc) {
    val fichas = e.fichas(escena.pid)
    var resumen by remember(escena.id) { mutableStateOf(escena.s("resumen")) }
    var cuando by remember(escena.id) { mutableStateOf(escena.s("cuando")) }
    var etq by remember(escena.id) { mutableStateOf(escena.lista("etq").joinToString(", ")) }
    var meta by remember(escena.id) { mutableStateOf(escena.i("meta").takeIf { it > 0 }?.toString().orEmpty()) }
    LaunchedEffect(resumen, cuando, etq, meta) {
        kotlinx.coroutines.delay(500)
        val etiquetas = etq.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (resumen != escena.s("resumen") || cuando != escena.s("cuando") || etiquetas != escena.lista("etq") || (meta.toIntOrNull() ?: 0) != escena.i("meta"))
            e.edita(escena.id) { put("resumen", resumen); put("cuando", cuando); put("etq", org.json.JSONArray(etiquetas)); put("meta", meta.toIntOrNull() ?: 0) }
    }
    EtiquetaEscribir("Estado")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ESTADOS_ESCENA.forEachIndexed { i, n -> SoftChip(n, escena.i("estado") == i) { e.edita(escena.id) { put("estado", i) } } }
    }
    CampoEscribir(resumen, { resumen = it }, "Resumen de la escena", lineas = 4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorEscribir("Punto de vista", escena.s("pov"), listOf("" to "—") + fichas.filter { it.s("tipo") == "personaje" }.map { it.id to it.s("nombre") }, { v -> e.edita(escena.id) { if (v.isBlank()) remove("pov") else put("pov", v) } }, Modifier.weight(1f))
        SelectorEscribir("Lugar", escena.s("lugar"), listOf("" to "—") + fichas.filter { it.s("tipo") == "lugar" }.map { it.id to it.s("nombre") }, { v -> e.edita(escena.id) { if (v.isBlank()) remove("lugar") else put("lugar", v) } }, Modifier.weight(1f))
    }
    CampoEscribir(cuando, { cuando = it }, "Cuándo pasa (para la línea temporal)")
    CampoEscribir(etq, { etq = it }, "Etiquetas (separadas por comas)")
    CampoEscribir(meta, { meta = it.filter(Char::isDigit).take(6) }, "Meta de palabras de esta escena", numero = true)
}

@Composable
private fun PestanaFichas(e: Escritos, pid: String, texto: String, inserta: (String) -> Unit, leer: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var abierta by remember { mutableStateOf<String?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val aqui = remember(texto) { e.fichasEn(pid, texto).map { it.id }.toSet() }
    val todas = e.fichas(pid).filter { q.isBlank() || it.s("nombre").contains(q, true) || it.s("alias").contains(q, true) }
    CampoEscribir(q, { q = it }, "Buscar una ficha", mayus = false)
    val lista = todas.sortedByDescending { it.id in aqui }
    if (lista.isEmpty()) Text(if (e.fichas(pid).isEmpty()) "Aún no hay fichas. Crea la de tu protagonista: quedará subrayada en el texto cada vez que la nombres." else "Nada con «$q».", color = Muted, fontSize = 13.sp)
    lista.forEach { f ->
        val on = abierta == f.id
        val color = colorEscrito(f.s("color"), ColGeneral)
        Column(Modifier.fillMaxWidth().animateContentSize().clip(shp(14.dp)).background(Paper).border(1.dp, Line, shp(14.dp))) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(color))
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth().press(haptic = false) { abierta = if (on) null else f.id }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(singularFicha(f.s("tipo")).uppercase() + if (f.id in aqui) " · SALE AQUÍ" else "", color = if (f.id in aqui) color else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text(f.s("nombre"), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Charcoal)
                    }
                    Text(if (on) "▴" else "▾", color = Muted, fontSize = 14.sp)
                }
                if (on) {
                    if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), fontFamily = Display, fontStyle = FontStyle.Italic, color = Charcoal, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    f.o.optJSONArray("campos")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { c -> if (c.optString("v").isNotBlank()) Text("${c.optString("k")}: ${c.optString("v")}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                        EnlaceRevista("Leer la ficha →", acento = true) { leer(f.id) }
                        EnlaceRevista("Poner el nombre") { inserta(f.s("nombre")) }
                    }
                }
            }
        }
    }
    BotonEscribir("Nueva ficha", suave = true) { nueva = true }
    if (nueva) FichaDialogo(e, pid, null, onCerrar = { nueva = false }, onCreada = { leer(it.id) })
}

@Composable
private fun PestanaNotas(e: Escritos, pid: String, inserta: (String) -> Unit, leer: (String) -> Unit) {
    var abierta by remember { mutableStateOf<String?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val notas = e.notas(pid).sortedWith(compareByDescending<Doc> { it.b("fija") }.thenByDescending { it.s("tipo") == "plantilla" }.thenByDescending { it.u })
    if (notas.isEmpty()) Text("Notas, ideas, investigación y plantillas de la obra: aquí las tienes al lado mientras escribes.", color = Muted, fontSize = 13.sp)
    notas.forEach { n ->
        val on = abierta == n.id
        val color = colorEscrito(n.s("color"), ColGeneral)
        Column(Modifier.fillMaxWidth().animateContentSize().clip(shp(14.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.45f), shp(14.dp)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().press(haptic = false) { abierta = if (on) null else n.id }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(singularNota(n.s("tipo")).uppercase() + if (n.b("fija")) " · FIJADA" else "", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text(n.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (on) "▴" else "▾", color = Muted, fontSize = 14.sp)
            }
            if (on) {
                Text(n.s("texto"), color = Charcoal, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
                    EnlaceRevista("Leer →", acento = true) { leer(n.id) }
                    if (n.s("texto").isNotBlank()) EnlaceRevista(if (n.s("tipo") == "plantilla") "Insertar la plantilla" else "Insertar el texto") { inserta(n.s("texto")) }
                }
            }
        }
    }
    BotonEscribir("Nueva nota", suave = true) { nueva = true }
    if (nueva) NotaDialogo(e, pid, null, onCerrar = { nueva = false })
}

private fun fechaHora(t: Long) = SimpleDateFormat("d MMM · HH:mm", ES).format(Date(t)).replace(".", "")

@Composable
private fun PestanaBorradores(e: Escritos, escena: Doc, actual: String, pon: (String) -> Unit) {
    var comparar by remember { mutableStateOf<Doc?>(null) }
    val bs = e.borradores(escena.id).reversed()
    Text("Un borrador es otra versión de esta escena: pruébala sin perder la buena, compárala y mezcla lo mejor de cada una.", color = Muted, fontSize = 13.sp)
    BotonEscribir("Guardar el texto actual como borrador", suave = true) {
        e.nuevo(Escritos.T_BORRADOR, escena.pid) { put("eid", escena.id); put("nombre", "Borrador del " + fechaHora(System.currentTimeMillis())); put("texto", actual) }
    }
    bs.forEach { b ->
        Column(Modifier.fillMaxWidth().clip(shp(14.dp)).background(Paper).border(1.dp, if (b.b("conflicto")) ColGeneral else Line, shp(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(b.s("nombre").ifBlank { "Borrador" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Charcoal)
            Text("${cifraE(Escritos.palabras(b.s("texto")))} palabras" + if (b.b("conflicto")) " · se guardó al juntar dos cambios a la vez" else "", color = if (b.b("conflicto")) ColGeneral else Muted, fontSize = 12.sp)
            Text(b.s("texto").take(220).replace('\n', ' ') + if (b.s("texto").length > 220) "…" else "", color = Charcoal, fontFamily = Display, fontSize = 14.sp, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                EnlaceRevista("Comparar y mezclar", acento = true) { comparar = b }
                EnlaceRevista("Usar este") {
                    e.nuevo(Escritos.T_BORRADOR, escena.pid) { put("eid", escena.id); put("nombre", "Antes de usar «${b.s("nombre")}»"); put("texto", actual) }
                    pon(b.s("texto"))
                }
                EnlaceRevista("Borrar") { aLaPapelera(e, b) }
            }
        }
    }
    comparar?.let { b -> ComparaDialogo(actual, b.s("nombre"), b.s("texto"), onCerrar = { comparar = null }, onAplica = { t -> e.instantanea(escena.id, actual, fuerza = true); pon(t); comparar = null }) }
}

@Composable
private fun PestanaHistorial(e: Escritos, escena: Doc, actual: String, pon: (String) -> Unit) {
    var comparar by remember { mutableStateOf<Version?>(null) }
    val vs = remember(escena.u, actual.length / 50) { e.versiones(escena.id).reversed() }
    Text("Cada cierto tiempo (y al salir) se guarda una versión de la escena en este dispositivo: las últimas ${Escritos.VERSIONES}. Con Ctrl + S (o «Guardar una versión ahora») guardas una cuando quieras.", color = Muted, fontSize = 13.sp)
    if (vs.isEmpty()) Text("Todavía no hay versiones: se guardan solas mientras escribes.", color = Muted, fontSize = 13.sp)
    vs.forEach { v ->
        val dif = v.palabras - Escritos.palabras(actual)
        Column(Modifier.fillMaxWidth().clip(shp(14.dp)).background(Paper).border(1.dp, Line, shp(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fechaHora(v.u), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Charcoal, modifier = Modifier.weight(1f))
                Text("${cifraE(v.palabras)} palabras" + if (dif != 0) " (${if (dif > 0) "+" else ""}${cifraE(dif)})" else "", color = Muted, fontSize = 12.sp)
            }
            Text(v.texto.take(160).replace('\n', ' ') + if (v.texto.length > 160) "…" else "", color = Muted, fontFamily = Display, fontSize = 14.sp, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EnlaceRevista("Comparar", acento = true) { comparar = v }
                EnlaceRevista("Restaurar") { e.instantanea(escena.id, actual, fuerza = true); pon(v.texto) }
            }
        }
    }
    comparar?.let { v -> ComparaDialogo(actual, fechaHora(v.u), v.texto, onCerrar = { comparar = null }, onAplica = { t -> e.instantanea(escena.id, actual, fuerza = true); pon(t); comparar = null }) }
}

/** Un párrafo cambiado, pintado palabra a palabra: lo que solo está en este lado, resaltado ([tachar]: tachado, porque se va). */
private fun diferencias(trozos: List<TrozoDiff>, lado: Int, tachar: Boolean): AnnotatedString = buildAnnotatedString {
    trozos.forEach { t ->
        when (t.tipo) {
            TrozoDiff.IGUAL -> append(t.texto)
            lado -> withStyle(SpanStyle(background = ColGeneral.copy(alpha = 0.22f), textDecoration = if (tachar) TextDecoration.LineThrough else null)) { append(t.texto) }
        }
    }
}

/** Las diferencias entre el texto de ahora y otra versión, párrafo a párrafo (y dentro, palabra a palabra), para quedarte con lo que quieras de cada una. */
@Composable
fun ComparaDialogo(actual: String, nombre: String, otro: String, onCerrar: () -> Unit, onAplica: (String) -> Unit) {
    val partes = remember(actual, otro) { EscribirDiff.compara(actual, otro) }
    val cambios = partes.filterIsInstance<Parte.Cambio>()
    val elige = remember(partes) { mutableStateListOf<Boolean>().also { l -> repeat(cambios.size) { l.add(false) } } }
    HojaEscribir("Comparar con «$nombre»", onCerrar) {
        if (cambios.isEmpty()) Text("Son iguales.", color = Muted, fontSize = 15.sp)
        else Text("${cambios.size} ${if (cambios.size == 1) "diferencia" else "diferencias"}. Toca en cada una el texto con el que te quedas: lo que cambia va resaltado.", color = Muted, fontSize = 13.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val lado = maxWidth >= 620.dp
            var k = 0
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                partes.forEach { p ->
                    when (p) {
                        is Parte.Igual -> Text(p.parrafos.joinToString("\n\n").let { if (it.length > 180) it.take(90) + " … " + it.takeLast(70) else it }, color = Muted, fontSize = 13.sp, fontFamily = Display)
                        is Parte.Cambio -> {
                            val i = k++
                            val otroOn = elige.getOrElse(i) { false }
                            val trozos = remember(p) { EscribirDiff.porPalabras(p.aqui.joinToString("\n\n"), p.otro.joinToString("\n\n")) }
                            @Composable fun Caja(m: Modifier, on: Boolean, titulo: String, texto: AnnotatedString, vacio: Boolean, f: () -> Unit) =
                                Column(m.clip(shp(10.dp)).background(if (on) ColGeneral.copy(alpha = 0.10f) else Paper).border(if (on) 2.dp else 1.dp, if (on) ColGeneral else Line, shp(10.dp)).press(haptic = false, onClick = f).padding(12.dp)) {
                                    Text(if (on) "✓ $titulo" else titulo, color = if (on) ColGeneral else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                                    if (vacio) Text("(nada)", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                                    else Text(texto, color = Charcoal, fontFamily = Display, fontSize = 15.sp, lineHeight = 21.sp)
                                }
                            val a = diferencias(trozos, TrozoDiff.AQUI, tachar = true)
                            val b = diferencias(trozos, TrozoDiff.OTRO, tachar = false)
                            if (lado) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Caja(Modifier.weight(1f).fillMaxHeight(), !otroOn, "EL DE AHORA", a, p.aqui.isEmpty()) { if (i < elige.size) elige[i] = false }
                                Caja(Modifier.weight(1f).fillMaxHeight(), otroOn, "EL OTRO", b, p.otro.isEmpty()) { if (i < elige.size) elige[i] = true }
                            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Caja(Modifier.fillMaxWidth(), !otroOn, "EL DE AHORA", a, p.aqui.isEmpty()) { if (i < elige.size) elige[i] = false }
                                Caja(Modifier.fillMaxWidth(), otroOn, "EL OTRO", b, p.otro.isEmpty()) { if (i < elige.size) elige[i] = true }
                            }
                        }
                    }
                }
            }
        }
        if (cambios.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir("Aplicar la mezcla") { onAplica(EscribirDiff.mezcla(partes, elige.toList())) }
            BotonEscribir("Quedarme con el otro", suave = true) { onAplica(otro) }
        }
        Text("Antes de cambiar nada se guarda una versión de lo de ahora (en Historial).", color = Muted, fontSize = 12.sp)
    }
}
