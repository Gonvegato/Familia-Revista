package com.gonzalo.hiedra.pc

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.nucleo.Json
import com.gonzalo.familia.nucleo.comoMapa
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.Body
import com.gonzalo.familia.pc.revista.Display
import com.gonzalo.familia.pc.revista.FitTitle
import com.gonzalo.familia.pc.revista.Skin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Lo de la sala que es de este ordenador: si se ve oscura, si Hiedra se aparta al jugar y si se abre ya en la sala. */
object AjustesSala {
    private fun archivo() = File(Pc.dirPruebas ?: Sitio.de("hiedra"), "sala.json")
    private val m: Map<String, Any?> get() = runCatching { Json.lee(archivo().readText()).comoMapa() }.getOrDefault(emptyMap())
    private fun pon(k: String, v: Any) { runCatching { archivo().writeText(Json.escribe(m + (k to v))) } }

    /** Oscura (los colores de tu Hiedra en su versión de noche) o como la app. */
    var oscura by mutableStateOf(m["oscura"] as? Boolean ?: true)
        private set
    fun ponOscura(v: Boolean) { oscura = v; pon("oscura", v) }
    /** Al jugar, Hiedra se minimiza (y vuelve delante al cerrar el juego). */
    var apartar by mutableStateOf(m["apartar"] as? Boolean ?: true)
        private set
    fun ponApartar(v: Boolean) { apartar = v; pon("apartar", v) }
    /** Al abrir Hiedra, ya en la sala (además de con `--sala`). */
    var alAbrir by mutableStateOf(m["alAbrir"] as? Boolean ?: false)
        private set
    fun ponAlAbrir(v: Boolean) { alAbrir = v; pon("alAbrir", v) }
}

enum class Seccion(val nombre: String) { INICIO("Inicio"), BIBLIOTECA("Biblioteca"), PC("En este PC") }

/** Una pregunta antes de hacer algo que no tiene vuelta (apagar, quitar un juego…). */
class Confirmacion(val titulo: String, val texto: String, val si: String, val alSi: () -> Unit)

/** El estado de la sala: lo que sobrevive a ir y volver entre sus pantallas (las secciones, las páginas abiertas, por dónde ibas). */
class EstadoSala {
    var seccion by mutableStateOf(Seccion.INICIO)
    /** Las páginas de juego abiertas (sus claves), la de arriba al final. */
    val pila = mutableStateListOf<String>()
    var opciones by mutableStateOf<String?>(null)
    var buscando by mutableStateOf(false)
    var consulta by mutableStateOf("")
    var menu by mutableStateOf(false)
    var confirmar by mutableStateOf<Confirmacion?>(null)
    /** El juego del cartel de arriba (el último enfocado en una fila). */
    var destacada by mutableStateOf<String?>(null)
    var aviso by mutableStateOf<Pair<String, Long>?>(null)
    var filtro by mutableStateOf(0)
    private val filas = HashMap<String, LazyListState>()
    private val paginas = HashMap<String, ScrollState>()
    val rejilla = LazyGridState()
    fun fila(id: String) = filas.getOrPut(id) { LazyListState() }
    fun pagina(id: String) = paginas.getOrPut(id) { ScrollState(0) }
    /** La vista de ahora (para recordar lo enfocado en cada una). */
    val vista: String get() = pila.lastOrNull()?.let { "juego:$it" } ?: "sec:${seccion.name}"
    fun avisa(t: String) { aviso = t to System.currentTimeMillis() }
}

/** (La sala que se ve ahora, para probarla de verdad desde fuera: ProbarSala.) */
@Volatile var focoDePrueba: FocoSala? = null

val FILTROS = listOf("Todo", "Jugando", "Pendientes", "Pasados", "Favoritos", "En este PC")

/** Calcula la biblioteca en segundo plano cada vez que cambia la cuenta, lo de este ordenador o lo que se juega. */
@Composable
fun rememberBiblioteca(): Biblioteca {
    val cambios = LaFamilia.cambios
    val pc = Pc.version
    val jugando = Pc.partidasVersion
    var b by remember { mutableStateOf(Biblioteca(emptyList(), null)) }
    LaunchedEffect(cambios, pc, jugando) {
        b = withContext(Dispatchers.Default) { runCatching { Biblioteca.de(Juegos.todos(), Pc.yaMirado().orEmpty(), Juegos.dispositivo(), Pc.ahora) }.getOrDefault(b) }
    }
    return b
}

/** Jugar a algo desde la sala (o desde la ventana): entra en Hiedra («Jugando»), se abre y, en la sala, Hiedra se aparta. */
fun jugar(f: Ficha, enSala: Boolean, aviso: (String) -> Unit) {
    val l = f.local ?: run { aviso("${f.titulo} no está en este ordenador."); return }
    Thread({
        val id = runCatching { Juegos.mete(l, jugando = true) }.getOrNull().orEmpty()
        val m = Pc.juega(l, id)
        SwingUtilitiesLater { aviso(m) }
        if (id.isNotBlank()) runCatching { portadaDe(l, id) }
        if (enSala && AjustesSala.apartar && Pc.ahora?.clave == l.clave) { Thread.sleep(1500); VentanaHiedra.apartate() }
    }, "hiedra-jugar").apply { isDaemon = true; start() }
}

private fun SwingUtilitiesLater(que: () -> Unit) = javax.swing.SwingUtilities.invokeLater(que)

/**
 * La sala: Hiedra para la tele, a pantalla completa y con el mando (también con el teclado y el ratón). Arriba, las secciones (LB y RB) y
 * la hora; en Inicio, el juego enfocado en grande y debajo las filas (Seguir jugando, Favoritos, cada tienda y cada consola de este
 * ordenador…); en Biblioteca, todo en portadas; cada juego tiene su página con «Jugar». X busca, Y da las opciones, ☰ el menú y ⧉ vuelve a la ventana.
 * Se ve igual en cualquier pantalla: se compone sobre 1080 líneas y se escala.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaSala(est: EstadoSala, foco: FocoSala = remember { FocoSala() }, alSalir: () -> Unit) {
    val tinta = TintaSala(if (AjustesSala.oscura) Skin.tonosPara(true) else Skin.tones)
    val bib = rememberBiblioteca()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(tinta.fondo)) {
        val k = (constraints.maxHeight / 1080f).coerceIn(0.3f, 6f)
        CompositionLocalProvider(LocalDensity provides Density(k, 1f), LocalTinta provides tinta, LocalFoco provides foco) {
            val fm = LocalFocusManager.current
            val ctl = remember { ControlSala(est, foco) }
            SideEffect { focoDePrueba = foco }
            ctl.fm = fm; ctl.bib = bib; ctl.alSalir = alSalir; ctl.scope = scope
            DisposableEffect(Unit) {
                // En la sala se ve el marco desde el principio (con el ratón se va)
                if (Entrada.con == ConQue.RATON) Entrada.con = if (Mando.mandos.isNotEmpty()) ConQue.MANDO else ConQue.TECLADO
                val h: (Accion) -> Boolean = { a -> ctl.hace(a) }
                val d: (Float) -> Unit = { px -> ctl.desplaza(px) }
                Entrada.manejador = h
                Mando.alDesplazar = d
                val disp = java.awt.KeyEventDispatcher { e -> if (VentanaHiedra.ventana?.isFocused == false && !Mando.ventanaActiva()) false else Entrada.teclaAwt(e, est.buscando) { c -> ctl.escribe(c) } }
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(disp)
                onDispose {
                    if (Entrada.manejador === h) Entrada.manejador = null
                    if (Mando.alDesplazar === d) Mando.alDesplazar = null
                    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(disp)
                }
            }
            // Al mover el ratón, el marco se va (y vuelve al tocar el mando o una tecla)
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    var ultimo: androidx.compose.ui.geometry.Offset? = null
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val p = e.changes.firstOrNull()?.position
                        if (e.type == PointerEventType.Move && p != null && ultimo != null && (p - ultimo!!).getDistance() > 6f) Entrada.con = ConQue.RATON
                        if (e.type == PointerEventType.Press) Entrada.con = ConQue.RATON
                        ultimo = p
                    }
                }
            }) {
                Column(Modifier.fillMaxSize()) {
                    BarraSala(est, bib)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val abierta = est.pila.lastOrNull()
                        AnimatedContent(abierta to est.seccion, transitionSpec = {
                            (fadeIn(tween(240)) + scaleIn(tween(320), initialScale = 0.985f)) togetherWith fadeOut(tween(160))
                        }, label = "vista") { (clave, sec) ->
                            CompositionLocalProvider(LocalCapa provides "base") {
                                if (clave != null) PaginaJuego(bib.por(clave), est, ctl)
                                else when (sec) {
                                    Seccion.INICIO -> Escaparate(bib.inicio(), bib, est, ctl, "INICIO")
                                    Seccion.BIBLIOTECA -> Rejilla(bib, est, ctl)
                                    Seccion.PC -> Escaparate(bib.porOrigen(), bib, est, ctl, "PC")
                                }
                            }
                        }
                    }
                    PistasSala(est, foco)
                }
                // Lo que se abre encima
                if (est.opciones != null) CompositionLocalProvider(LocalCapa provides "opciones") { PanelOpciones(bib.por(est.opciones!!), est, ctl) }
                if (est.menu) CompositionLocalProvider(LocalCapa provides "menu") { PanelMenu(est, ctl) }
                if (est.buscando) CompositionLocalProvider(LocalCapa provides "buscar") { PanelBuscar(bib, est, ctl) }
                est.confirmar?.let { c -> CompositionLocalProvider(LocalCapa provides "confirmar") { PanelConfirmar(c, est, ctl) } }
                AvisoSala(est)
                MarcoFoco(foco, tinta.acento)
            }
        }
    }
    // Al entrar (y en cuanto llega la biblioteca, que se calcula aparte), el foco a donde estaba
    LaunchedEffect(bib.fichas.isEmpty()) { delay(120); if (foco.actual == null && foco.pedido == null) foco.pedido = foco.ultimo[est.vista] ?: primeroDe(est, bib) }
}

/** Lo primero que se enfoca en una vista. */
private fun primeroDe(est: EstadoSala, bib: Biblioteca): String? = when {
    est.pila.isNotEmpty() -> "jugar"
    est.seccion == Seccion.BIBLIOTECA -> "f:0"
    else -> (if (est.seccion == Seccion.INICIO) bib.inicio() else bib.porOrigen()).firstOrNull()?.let { f -> f.fichas.firstOrNull()?.let { "${f.id}/${it.clave}" } }
}

/** Lo que hace cada acción del mando (o del teclado) en la sala. */
class ControlSala(val est: EstadoSala, val foco: FocoSala) {
    var fm: FocusManager? = null
    var bib: Biblioteca = Biblioteca(emptyList(), null)
    var alSalir: () -> Unit = {}
    var scope: kotlinx.coroutines.CoroutineScope? = null

    private fun capa(): String = when { est.confirmar != null -> "confirmar"; est.buscando -> "buscar"; est.menu -> "menu"; est.opciones != null -> "opciones"; else -> "base" }

    fun hace(a: Accion): Boolean {
        when (a) {
            Accion.ARRIBA, Accion.ABAJO, Accion.IZQUIERDA, Accion.DERECHA -> {
                val d = when (a) { Accion.ARRIBA -> FocusDirection.Up; Accion.ABAJO -> FocusDirection.Down; Accion.IZQUIERDA -> FocusDirection.Left; else -> FocusDirection.Right }
                if (foco.actual == null) { pideInicial(); return true }
                val ok = runCatching { fm?.moveFocus(d) == true }.getOrDefault(false)
                // Sin nada más abajo en una página larga, se desplaza
                if (!ok && (a == Accion.ABAJO || a == Accion.ARRIBA)) desplaza(if (a == Accion.ABAJO) 220f else -220f)
            }
            Accion.A -> if (!foco.pulsa()) pideInicial()
            Accion.B -> atras()
            Accion.X -> if (capa() == "base") abreBuscar() else if (capa() == "buscar") cierra("buscar")
            Accion.Y -> if (!foco.opcionesDe()) est.pila.lastOrNull()?.let { if (capa() == "base") abreOpciones(it) }
            Accion.LB, Accion.RB -> if (capa() == "base") seccion(if (a == Accion.LB) -1 else 1)
            Accion.LT, Accion.RT -> if (capa() == "base" && est.pila.isEmpty() && est.seccion == Seccion.BIBLIOTECA) saltaLetra(if (a == Accion.LT) -1 else 1)
            Accion.MENU -> if (est.menu) cierra("menu") else if (capa() == "base") { est.menu = true; abre("menu"); foco.pedido = "op:0" }
            Accion.VISTA -> alSalir()
        }
        return true
    }

    private fun recuerda() { foco.actual?.let { foco.ultimo[if (capa() == "base") est.vista else capa()] = it } }
    fun abre(capa: String) { recuerda(); foco.abre(capa); foco.pedido = null }
    /** Cierra una capa y vuelve a lo que estaba enfocado debajo. */
    fun cierra(capa: String) {
        when (capa) { "opciones" -> est.opciones = null; "menu" -> est.menu = false; "buscar" -> est.buscando = false; "confirmar" -> est.confirmar = null }
        foco.cierra(capa)
        foco.pedido = foco.ultimo[if (foco.arriba == "base") est.vista else foco.arriba]
    }
    fun pideInicial() { foco.pedido = foco.ultimo[est.vista] ?: primeroDe(est, bib) }

    fun atras() {
        when (val c = capa()) {
            "base" -> when {
                est.pila.isNotEmpty() -> { recuerda(); est.pila.removeAt(est.pila.lastIndex); foco.salta = true; foco.pedido = foco.ultimo[est.vista] ?: primeroDe(est, bib) }
                est.seccion != Seccion.INICIO -> { recuerda(); est.seccion = Seccion.INICIO; foco.salta = true; foco.pedido = foco.ultimo[est.vista] ?: primeroDe(est, bib) }
            }
            else -> cierra(c)
        }
    }

    fun abreFicha(clave: String) { recuerda(); est.pila += clave; foco.salta = true; foco.pedido = "jugar" }
    fun abreOpciones(clave: String) { est.opciones = clave; abre("opciones"); foco.pedido = "op:0" }
    fun abreBuscar() { est.consulta = ""; est.buscando = true; abre("buscar"); foco.pedido = "k:A" }
    fun confirma(c: Confirmacion) { est.confirmar = c; abre("confirmar"); foco.pedido = "no" }

    fun seccion(paso: Int) {
        recuerda()
        est.pila.clear()
        val l = Seccion.entries
        est.seccion = l[(l.indexOf(est.seccion) + paso + l.size) % l.size]
        foco.salta = true
        foco.pedido = foco.ultimo[est.vista] ?: primeroDe(est, bib)
    }

    fun escribe(c: Char) {
        if (!est.buscando) return
        est.consulta = if (c == '\b') est.consulta.dropLast(1) else (est.consulta + c).take(40)
    }

    /** El stick derecho (o una flecha sin nada más allá): desplaza la página de ahora. */
    fun desplaza(px: Float) {
        val s = scope ?: return
        s.launch {
            if (est.pila.isEmpty() && est.seccion == Seccion.BIBLIOTECA) est.rejilla.scrollBy(px)
            else est.pagina(est.vista).scrollBy(px)
        }
    }

    /** LT y RT en la biblioteca: a la primera portada de la letra anterior o de la siguiente. */
    fun saltaLetra(paso: Int) {
        val l = fichasRejilla(bib, est.filtro)
        if (l.isEmpty()) return
        val i = foco.actual?.removePrefix("f:")?.toIntOrNull() ?: 0
        val letra = l.getOrNull(i)?.letra ?: l[0].letra
        val destino = if (paso > 0) l.indexOfFirst { it.letra > letra } else { val ant = l.lastOrNull { it.letra < letra }?.letra; if (ant == null) 0 else l.indexOfFirst { it.letra == ant } }
        if (destino < 0) return
        scope?.launch { est.rejilla.animateScrollToItem(destino); foco.pedido = "f:$destino" }
    }
}

// ---------------------------------------------------------------- arriba y abajo

@Composable
private fun BarraSala(est: EstadoSala, bib: Biblioteca) {
    val tinta = LocalTinta.current
    var hora by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000L * (60 - LocalTime.now().second).coerceAtLeast(1)); hora = LocalTime.now() } }
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tinta.fondo, tinta.fondo.copy(alpha = 0.92f))))) {
        Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BASTET", color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 3.sp)
            Spacer(Modifier.width(56.dp))
            Glifo(Accion.LB, 30.dp)
            Spacer(Modifier.width(22.dp))
            Seccion.entries.forEach { s ->
                val on = s == est.seccion && est.pila.isEmpty()
                val ancho by animateFloatAsState(if (s == est.seccion) 1f else 0f, spring(dampingRatio = 0.7f), label = "pestaña")
                Column(Modifier.padding(horizontal = 18.dp).width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.nombre.uppercase(), color = if (s == est.seccion) tinta.tinta else tinta.gris, fontFamily = Body, fontWeight = FontWeight.Black,
                        fontSize = 19.sp, letterSpacing = 2.4.sp, modifier = Modifier.alpha(if (on || est.pila.isEmpty()) 1f else 0.75f))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(4.dp).fillMaxWidth(ancho).background(tinta.acento))
                }
            }
            Spacer(Modifier.width(22.dp))
            Glifo(Accion.RB, 30.dp)
            Spacer(Modifier.weight(1f))
            bib.ahora?.let { p -> EnMarcha(p) ; Spacer(Modifier.width(36.dp)) }
            Text(hora.format(DateTimeFormatter.ofPattern("HH:mm")), color = tinta.tinta, fontFamily = Display, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 72.dp).height(2.dp).background(tinta.tinta.copy(alpha = 0.85f)))
    }
}

/** «● Jugando · Hades», con el punto latiendo. */
@Composable
private fun EnMarcha(p: Pc.Partida) {
    val tinta = LocalTinta.current
    val t = rememberInfiniteTransition(label = "latido")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "punto")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(tinta.acento.copy(alpha = a)))
        Spacer(Modifier.width(10.dp))
        Text((if (p.deHiedra && !p.enMarcha) "Abriendo · " else "Jugando · ").uppercase() + p.nombre, color = tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Black,
            fontSize = 15.sp, letterSpacing = 1.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 520.dp))
    }
}

@Composable
private fun PistasSala(est: EstadoSala, foco: FocoSala) {
    val tinta = LocalTinta.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 72.dp).height(1.dp).background(tinta.linea))
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            val m = Mando.mandos.lastOrNull()
            Text(LaFamilia.usuario, color = tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text("  ·  " + when { m != null && Entrada.con != ConQue.TECLADO -> "Mando: " + m.nombre; else -> "Teclado" },
                color = tinta.gris, fontFamily = Body, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 520.dp))
            Spacer(Modifier.weight(1f))
            val pistas = buildList {
                when {
                    est.confirmar != null -> { add(Accion.A to "Elegir"); add(Accion.B to "Cancelar") }
                    est.buscando -> { add(Accion.A to "Escribir / abrir"); add(Accion.B to "Cerrar") }
                    est.menu || est.opciones != null -> { add(Accion.A to "Elegir"); add(Accion.B to "Cerrar") }
                    est.pila.isNotEmpty() -> { add(Accion.A to "Elegir"); add(Accion.B to "Volver"); add(Accion.Y to "Opciones"); add(Accion.X to "Buscar") }
                    else -> {
                        add(Accion.A to "Abrir"); if (foco.tieneOpciones()) add(Accion.Y to "Opciones"); add(Accion.X to "Buscar")
                        if (est.seccion == Seccion.BIBLIOTECA) add(Accion.RT to "Letra"); add(Accion.MENU to "Menú")
                    }
                }
            }
            pistas.forEachIndexed { i, (a, t) -> if (i > 0) Spacer(Modifier.width(34.dp)); Pista(a, t) }
        }
    }
}

@Composable
private fun AvisoSala(est: EstadoSala) {
    val tinta = LocalTinta.current
    val a = est.aviso
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(a) { if (a != null) { visible = true; delay(3600); visible = false } }
    Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible && a != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            Text(a?.first.orEmpty(), color = tinta.sobreAcento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.clip(formaSala(14.dp)).background(tinta.acento).padding(horizontal = 30.dp, vertical = 16.dp))
        }
    }
}

// ---------------------------------------------------------------- Inicio y En este PC: el cartel y las filas

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun Escaparate(filas: List<Hilera>, bib: Biblioteca, est: EstadoSala, ctl: ControlSala, cual: String) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val dest = est.destacada?.let { bib.por(it) } ?: filas.firstOrNull()?.fichas?.firstOrNull()
    Box(Modifier.fillMaxSize()) {
        FondoJuego(dest, Modifier.fillMaxWidth().fillMaxHeight(0.72f))
        Column(Modifier.fillMaxSize()) {
            Cartel(dest, bib, Modifier.fillMaxWidth().height(400.dp))
            if (filas.isEmpty()) {
                Column(Modifier.padding(horizontal = 72.dp, vertical = 40.dp)) {
                    Text(if (cual == "PC") "No he encontrado juegos en este ordenador" else "Todavía no hay nada aquí", color = tinta.tinta, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Bastet mira Steam, Heroic (Epic, GOG, Amazon), Lutris, tus carpetas de ROMs y tus programas. Las carpetas de ROMs y los emuladores se eligen en la ventana: Ajustes → Emulación.",
                        color = tinta.gris, fontFamily = Body, fontSize = 20.sp, lineHeight = 30.sp, modifier = Modifier.widthIn(max = 1100.dp))
                    Spacer(Modifier.height(24.dp))
                    BotonSala("Volver a mirar", "remirar", principal = true) { Pc.refresca(escanear = true) { est.avisa("Mirado de nuevo") } }
                }
            }
            val scroll = est.pagina(est.vista)
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(80f, inicio = 100f) }) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 60.dp)) {
                    filas.forEach { f -> FilaSala(f, est, ctl) }
                }
            }
        }
    }
}

/** El cartel de arriba: el juego enfocado, en grande. */
@Composable
private fun Cartel(f: Ficha?, bib: Biblioteca, modifier: Modifier) {
    val tinta = LocalTinta.current
    Box(modifier.padding(horizontal = 72.dp), contentAlignment = Alignment.BottomStart) {
        AnimatedContent(f, transitionSpec = { (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { it / 24 }) togetherWith fadeOut(tween(180)) }, label = "cartel",
            contentKey = { it?.clave }) { ff ->
            if (ff == null) return@AnimatedContent
            Column(Modifier.fillMaxWidth(0.62f).padding(bottom = 34.dp)) {
                val ahora = bib.ahora?.takeIf { p -> p.clave == ff.local?.clave || (ff.juego != null && p.juego == ff.juego.id) }
                Text(listOfNotNull(ff.etiqueta.ifBlank { null }, ff.estado?.nombre, if (!ff.jugable) "No está en este PC" else null, if (ahora != null) "Jugando ahora" else null)
                    .joinToString("  ·  ").uppercase(), color = tinta.acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 3.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                FitTitle(ff.titulo, 88.sp, 90.sp, color = tinta.tinta, letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minSize = 40f)
                Spacer(Modifier.height(10.dp))
                Text(meta(ff), color = tinta.tinta.copy(alpha = 0.78f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** La línea de debajo del título: horas, última vez, de dónde. */
fun meta(f: Ficha): String {
    val partes = ArrayList<String>()
    if (f.ms > 0) partes += horas(f.ms) + " jugadas"
    f.juego?.ultima?.takeIf { it > 0 }?.let { partes += "última vez " + hace(it) }
    if (partes.isEmpty()) partes += if (f.enCuenta) "Sin estrenar" else if (f.jugable) "Instalado en este PC · aún no está en tu biblioteca" else ""
    return partes.filter { it.isNotBlank() }.joinToString("  ·  ")
}

/** «hoy», «ayer», «hace 3 días», «el 4 oct». */
fun hace(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val dias = java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now())
    return when { dias <= 0 -> "hoy"; dias == 1L -> "ayer"; dias < 14 -> "hace $dias días"; else -> "el " + fecha(ms) }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FilaSala(f: Hilera, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 72.dp), verticalAlignment = Alignment.Bottom) {
            AnteSala(f.titulo, tam = 18.sp)
            Spacer(Modifier.width(16.dp))
            Text(f.fichas.size.toString(), color = tinta.gris, fontFamily = Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (f.sub.isNotBlank()) { Spacer(Modifier.width(16.dp)); Text(f.sub.uppercase(), color = tinta.gris, fontFamily = Body, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(300f) }) {
            LazyRow(Modifier.fillMaxWidth().focusRestorer().focusGroup().focusProperties {
                onExit = { if (requestedFocusDirection == FocusDirection.Left || requestedFocusDirection == FocusDirection.Right) cancelFocusChange() }
            }, state = est.fila("${est.vista}/${f.id}"), contentPadding = PaddingValues(start = 72.dp, end = 72.dp, top = 30.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                itemsIndexed(f.fichas, key = { _, x -> x.clave }) { _, x -> Carta(x, "${f.id}/${x.clave}", est, ctl) }
            }
        }
    }
}

/** Una portada en una fila o en la rejilla: con su título debajo; al enfocarla, crece y pasa al cartel. */
@Composable
private fun Carta(f: Ficha, id: String, est: EstadoSala, ctl: ControlSala, ancho: androidx.compose.ui.unit.Dp = 204.dp) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val enfocada = foco.actual == id
    Column(Modifier.width(ancho)) {
        Box(Modifier.enfocable(id, radio = 10.dp, crece = 1.08f, alOpciones = { ctl.abreOpciones(f.clave) }, alEnfocar = { est.destacada = f.clave }) { ctl.abreFicha(f.clave) }) {
            PortadaSala(f, Modifier.fillMaxWidth(), sombra = if (enfocada) 22.dp else 6.dp)
            if (est.pila.isEmpty() && ctl.bib.ahora?.let { p -> p.clave == f.local?.clave } == true)
                Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(formaSala(6.dp)).background(tinta.acento).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("JUGANDO", color = tinta.sobreAcento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp)
                }
            if (f.fav) Text("★", color = tinta.acento, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(f.titulo, color = if (enfocada) tinta.tinta else tinta.tinta.copy(alpha = 0.86f), fontFamily = Display, fontSize = 19.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(f.etiqueta, if (f.ms > 0) horas(f.ms) else "").filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = tinta.gris, fontFamily = Body,
            fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- Biblioteca: todo, en portadas

fun fichasRejilla(bib: Biblioteca, filtro: Int): List<Ficha> = when (filtro) {
    1 -> bib.jugando; 2 -> bib.pendientes.sortedBy { it.orden }; 3 -> bib.pasados; 4 -> bib.favoritos; 5 -> bib.aqui
    else -> bib.todas
}.let { l -> if (filtro == 1 || filtro == 3) l else l.sortedBy { it.orden } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Rejilla(bib: Biblioteca, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val l = fichasRejilla(bib, est.filtro)
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(120f) }) {
            LazyVerticalGrid(GridCells.Adaptive(204.dp), Modifier.fillMaxSize(), est.rejilla, contentPadding = PaddingValues(start = 72.dp, end = 150.dp, top = 30.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp), verticalArrangement = Arrangement.spacedBy(34.dp)) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Biblioteca", color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, letterSpacing = (-1).sp)
                            Spacer(Modifier.width(22.dp))
                            Text("${l.size} ${if (l.size == 1) "juego" else "juegos"}", color = tinta.gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            FILTROS.forEachIndexed { i, n ->
                                val on = i == est.filtro
                                Text(n.uppercase(), color = if (on) tinta.sobreAcento else tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.8.sp,
                                    modifier = Modifier.enfocable("filtro:$i", radio = 10.dp, crece = 1.04f) { est.filtro = i; foco.ultimo.remove(est.vista) }
                                        .clip(formaSala(10.dp)).background(if (on) tinta.acento else tinta.tarjeta.copy(alpha = 0.7f)).padding(horizontal = 22.dp, vertical = 12.dp))
                            }
                        }
                    }
                }
                itemsIndexed(l, key = { _, f -> f.clave }) { i, f -> Carta(f, "f:$i", est, ctl) }
            }
        }
        // La letra por la que vas (LT / RT saltan de letra)
        val actual = foco.actual?.removePrefix("f:")?.toIntOrNull()?.let { l.getOrNull(it) }?.letra
        if (actual != null) Column(Modifier.align(Alignment.CenterEnd).padding(end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Glifo(Accion.LT, 28.dp)
            Text(actual.toString(), color = tinta.acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, modifier = Modifier.padding(vertical = 6.dp))
            Glifo(Accion.RT, 28.dp)
        }
        if (l.isEmpty()) Text("Aquí no hay ningún juego", color = tinta.gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 34.sp, modifier = Modifier.align(Alignment.Center))
    }
}

// ---------------------------------------------------------------- la página de un juego

@Composable
private fun PaginaJuego(f: Ficha?, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    if (f == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Este juego ya no está", color = tinta.tinta, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 40.sp) }; return }
    val enMarcha = ctl.bib.ahora?.takeIf { p -> p.clave == f.local?.clave }
    Box(Modifier.fillMaxSize()) {
        FondoJuego(f, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().verticalScroll(est.pagina(est.vista)).padding(horizontal = 96.dp, vertical = 56.dp)) {
            PortadaSala(f, Modifier.width(380.dp), radio = 14.dp, sombra = 30.dp)
            Spacer(Modifier.width(72.dp))
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(f.etiqueta.ifBlank { null }, f.estado?.nombre).joinToString("  ·  ").uppercase(), color = tinta.acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(10.dp))
                FitTitle(f.titulo, 96.sp, 98.sp, color = tinta.tinta, letterSpacing = (-2).sp, maxLines = 3, overflow = TextOverflow.Ellipsis, minSize = 44f)
                Spacer(Modifier.height(12.dp))
                Text(meta(f), color = tinta.tinta.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp)
                Spacer(Modifier.height(36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        enMarcha != null -> BotonSala(if (enMarcha.enMarcha) "Jugando ahora" else "Abriendo…", "jugar", principal = true, icono = "●", activo = false) {}
                        f.jugable -> BotonSala("Jugar", "jugar", principal = true, icono = "▶", alto = 72.dp, modifier = Modifier.widthIn(min = 280.dp)) { jugar(f, true) { est.avisa(it) } }
                        else -> BotonSala("No está en este PC", "jugar", activo = false) {}
                    }
                    if (f.enCuenta) BotonSala(if (f.fav) "★ Favorito" else "☆ Favorito", "fav") { f.juego?.let { Juegos.cambia(it.id, mapOf("fav" to !f.fav)) } }
                    else BotonSala("+ A tu biblioteca", "anadir") { anadir(f) { est.avisa(it) } }
                    BotonSala("Opciones", "opciones") { ctl.abreOpciones(f.clave) }
                }
                Spacer(Modifier.height(44.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(tinta.tinta.copy(alpha = 0.8f)))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                    Column(Modifier.weight(1f)) {
                        AnteSala("Tus horas")
                        Spacer(Modifier.height(10.dp))
                        DatoSala("En total", horas(f.ms))
                        f.juego?.let { j ->
                            val dev = Juegos.dispositivo()
                            j.horas.sortedByDescending { it.entero("total") }.take(5).forEach { h ->
                                val fu = h.texto("fuente")
                                DatoSala(when { fu == "steam" -> "Steam"; fu.startsWith("pc:") -> "En el PC (Atrio)"; fu == dev -> "En este ordenador"; fu.startsWith("carmesi:") -> "Carmesí"; fu.startsWith("hiedra-linux-") || fu.startsWith("hiedra-mac-") -> "En otro ordenador"; else -> "En un móvil" }, horas(h.entero("total")))
                            }
                            if (j.veces > 0) DatoSala("Partidas", j.veces.toString())
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        AnteSala("Cómo se abre")
                        Spacer(Modifier.height(10.dp))
                        f.local?.let { l -> DatoSala("Aquí", comoSeAbre(l)) } ?: DatoSala("Aquí", "no está instalado")
                        f.juego?.let { j ->
                            if (j.fin > 0) DatoSala("Lo pasaste", fecha(j.fin))
                            if (j.anadido > 0) DatoSala("En tu biblioteca desde", fecha(j.anadido))
                            j.nota?.let { DatoSala("En Jonsu", it.toString()) }
                        }
                    }
                }
            }
        }
    }
}

/** Con qué se abre lo de aquí: «Steam», «Heroic (Epic)», «RetroArch · mGBA»… */
fun comoSeAbre(l: JuegoPc): String = when (l.fuente) {
    Fuente.STEAM -> "Steam"
    Fuente.HEROIC -> "Heroic (${l.etiqueta})"
    Fuente.LUTRIS -> "Lutris"
    Fuente.EMULACION -> Emulacion.elegida(l.sistema)?.nombre ?: "sin emulador (Ajustes → Emulación)"
    Fuente.PROGRAMA -> File(l.ruta).name
}

/** Añadir a tu biblioteca algo de este ordenador (en «Pendiente»; si lo habías borrado, vuelve el de antes). */
fun anadir(f: Ficha, aviso: (String) -> Unit) {
    val l = f.local ?: return
    Thread({
        val id = runCatching { Juegos.mete(l) }.getOrNull()
        SwingUtilitiesLater { aviso(if (id != null) "${f.titulo}, en tu biblioteca" else "No se ha podido añadir") }
        if (id != null) runCatching { portadaDe(l, id) }
    }, "hiedra-anadir").apply { isDaemon = true; start() }
}

// ---------------------------------------------------------------- lo que se abre encima

/** Un panel a la derecha, encima de todo (el fondo se oscurece). */
@Composable
private fun Lateral(titulo: String, ante: String, alCerrar: () -> Unit, contenido: @Composable ColumnScope.() -> Unit) {
    val tinta = LocalTinta.current
    var dentro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dentro = true }
    val x by animateFloatAsState(if (dentro) 0f else 1f, spring(dampingRatio = 0.86f, stiffness = 420f), label = "panel")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * (1f - x))).pointerInput(Unit) { detectTapGesturesFuera(alCerrar) }) {
        Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(620.dp).graphicsLayer { translationX = x * size.width * 1.05f }.background(tinta.fondo).padding(horizontal = 56.dp, vertical = 64.dp)
            .pointerInput(Unit) { detectTapGesturesFuera {} }) {
            Text(ante.uppercase(), color = tinta.acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(titulo, color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(tinta.tinta))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = contenido)
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesFuera(f: () -> Unit) = detectTapGestures(onTap = { f() })

/** Una opción de un panel: una línea grande que se enfoca. */
@Composable
private fun OpcionSala(texto: String, id: String, sub: String = "", marcada: Boolean = false, peligro: Boolean = false, alPulsar: () -> Unit) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val on = foco.actual == id
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).enfocable(id, radio = 10.dp, crece = 1.02f, alPulsar = alPulsar).clip(formaSala(10.dp))
        .background(if (on && foco.visible) tinta.tarjeta else Color.Transparent).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(26.dp).background(if (marcada) tinta.acento else Color.Transparent))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(texto, color = if (peligro) tinta.acento else tinta.tinta, fontFamily = Display, fontSize = 27.sp, fontWeight = if (marcada) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub.uppercase(), color = tinta.gris, fontFamily = Body, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (marcada) Text("✓", color = tinta.acento, fontSize = 26.sp, fontFamily = Body, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PanelOpciones(f: Ficha?, est: EstadoSala, ctl: ControlSala) {
    if (f == null) { LaunchedEffect(Unit) { ctl.cierra("opciones") }; return }
    Lateral(f.titulo, listOfNotNull(f.etiqueta.ifBlank { null }, "Opciones").joinToString(" · "), { ctl.cierra("opciones") }) {
        var i = 0
        fun id() = "op:${i++}"
        if (f.jugable) OpcionSala("Jugar", id(), comoSeAbre(f.local!!)) { ctl.cierra("opciones"); jugar(f, true) { est.avisa(it) } }
        if (est.pila.lastOrNull() != f.clave) OpcionSala("Ver su página", id()) { ctl.cierra("opciones"); ctl.abreFicha(f.clave) }
        val j = f.juego
        if (j != null) {
            Spacer(Modifier.height(12.dp)); AnteSala("Cómo va", tam = 14.sp); Spacer(Modifier.height(6.dp))
            Estado.entries.forEach { e -> OpcionSala(e.nombre, id(), marcada = j.estado == e) { Juegos.ponEstado(j, e); est.avisa("${f.titulo}: ${e.nombre.lowercase()}") } }
            Spacer(Modifier.height(12.dp))
            OpcionSala(if (j.fav) "Quitar de favoritos" else "Añadir a favoritos", id()) { Juegos.cambia(j.id, mapOf("fav" to !j.fav)) }
            OpcionSala("Quitar de Bastet", id(), "Se queda en este PC; en tu biblioteca no", peligro = true) {
                ctl.cierra("opciones")
                ctl.confirma(Confirmacion("¿Quitar «${f.titulo}»?", "Sale de tu biblioteca (en todos tus dispositivos). Si lo tienes instalado aquí, sigue en «En este PC»; si lo vuelves a jugar o a añadir, vuelve con sus horas y su nota.", "Quitar") {
                    Juegos.quita(j.id); est.pila.remove(f.clave); est.avisa("Quitado de tu biblioteca")
                })
            }
        } else if (f.jugable) OpcionSala("Añadir a tu biblioteca", id(), "Entra en Pendiente") { anadir(f) { est.avisa(it) }; ctl.cierra("opciones") }
    }
}

@Composable
private fun PanelMenu(est: EstadoSala, ctl: ControlSala) {
    Lateral("Menú", "Bastet · ${Sitio.nombreEquipo}", { ctl.cierra("menu") }) {
        OpcionSala("Volver a la ventana", "op:0", "⧉ o F11") { ctl.cierra("menu"); ctl.alSalir() }
        OpcionSala(if (Pc.mirando) "Mirando…" else "Volver a mirar este PC", "op:1", "Steam, Heroic, Lutris, tus ROMs y tus programas") { Pc.refresca(escanear = true) { est.avisa("Mirado de nuevo: ${Pc.yaMirado().orEmpty().size} juegos aquí") }; ctl.cierra("menu") }
        OpcionSala("Hacer una captura", "op:7", "De esta pantalla, a Imágenes/Hiedra" + if (AjustesTuPc.capturasAlMovil) " y al móvil" else "") {
            ctl.cierra("menu")
            // (cuando el menú ya se ha ido)
            Thread({
                Thread.sleep(700)
                val r = runCatching { Capturas.haz() }
                javax.swing.SwingUtilities.invokeLater { est.avisa(r.fold({ "Captura de ${it.w}×${it.h}" + if (it.subida) ", también en el móvil" else "" }, { it.message ?: "No se ha podido capturar" })) }
            }, "hiedra-captura").apply { isDaemon = true; start() }
        }
        OpcionSala(if (AjustesSala.oscura) "Sala clara" else "Sala oscura", "op:2", "Los colores de tu Bastet") { AjustesSala.ponOscura(!AjustesSala.oscura) }
        OpcionSala(if (AjustesSala.apartar) "Al jugar: Bastet se aparta" else "Al jugar: Bastet se queda", "op:3", "Cambiar") { AjustesSala.ponApartar(!AjustesSala.apartar) }
        Spacer(Modifier.height(14.dp)); AnteSala("El ordenador", tam = 14.sp); Spacer(Modifier.height(6.dp))
        OpcionSala("Suspender", "op:4") { ctl.cierra("menu"); ctl.confirma(Confirmacion("¿Suspender el ordenador?", "Lo que tengas abierto se queda como está.", "Suspender") { Energia.suspende() }) }
        OpcionSala("Apagar", "op:5", peligro = true) { ctl.cierra("menu"); ctl.confirma(Confirmacion("¿Apagar el ordenador?", "Guarda antes lo que estés jugando.", "Apagar") { Energia.apaga() }) }
        OpcionSala("Cerrar Bastet", "op:6") { ctl.cierra("menu"); VentanaHiedra.cerrar() }
    }
}

@Composable
private fun PanelConfirmar(c: Confirmacion, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(860.dp).clip(formaSala(18.dp)).background(tinta.fondo).padding(56.dp)) {
            Text(c.titulo, color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(c.texto, color = tinta.gris, fontFamily = Body, fontSize = 21.sp, lineHeight = 31.sp)
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BotonSala("Cancelar", "no") { ctl.cierra("confirmar") }
                BotonSala(c.si, "si", principal = true) { ctl.cierra("confirmar"); c.alSi() }
            }
        }
    }
}

/** Buscar con el mando: un teclado en pantalla (también se puede escribir con el teclado de verdad) y lo que va saliendo debajo. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PanelBuscar(bib: Biblioteca, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    val res = remember(est.consulta, bib) { bib.busca(est.consulta).take(30) }
    val filas = listOf("ABCDEFGHIJ", "KLMNÑOPQRS", "TUVWXYZ123", "4567890:'-")
    var caret by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(530); caret = !caret } }
    Box(Modifier.fillMaxSize().background(tinta.fondo)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 60.dp)) {
            AnteSala("Buscar en tu biblioteca y en este PC", tam = 16.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(est.consulta.ifEmpty { " " }, color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 76.sp, maxLines = 1)
                Box(Modifier.padding(bottom = 18.dp, start = 4.dp).width(5.dp).height(64.dp).background(if (caret) tinta.acento else Color.Transparent))
                Spacer(Modifier.weight(1f))
                if (est.consulta.isNotEmpty()) Text("${res.size} ${if (res.size == 1) "resultado" else "resultados"}", color = tinta.gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(tinta.tinta))
            Spacer(Modifier.height(26.dp))
            Column(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filas.forEach { f ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        f.forEach { c -> Tecla(c.toString(), "k:$c") { ctl.escribe(c.lowercaseChar()) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tecla("Espacio", "k:espacio", ancho = 236.dp) { ctl.escribe(' ') }
                    Tecla("⌫ Borrar", "k:borrar", ancho = 236.dp) { ctl.escribe('\b') }
                    Tecla("Vaciar", "k:vaciar", ancho = 236.dp) { est.consulta = "" }
                    Tecla("Cerrar", "k:cerrar", ancho = 236.dp) { ctl.cierra("buscar") }
                }
            }
            Spacer(Modifier.height(30.dp))
            if (res.isNotEmpty()) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(300f) }) {
                    LazyRow(Modifier.fillMaxWidth().focusRestorer().focusGroup(), contentPadding = PaddingValues(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        itemsIndexed(res, key = { _, f -> f.clave }) { _, f -> CartaBusqueda(f, "r:${f.clave}", est, ctl) }
                    }
                }
            } else if (est.consulta.isNotEmpty()) Text("Nada con «${est.consulta}»", color = tinta.gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 30.sp)
        }
    }
}

@Composable
private fun CartaBusqueda(f: Ficha, id: String, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    Column(Modifier.width(150.dp)) {
        Box(Modifier.enfocable(id, radio = 8.dp, crece = 1.08f, alOpciones = { ctl.abreOpciones(f.clave) }) { ctl.cierra("buscar"); ctl.abreFicha(f.clave) }) {
            PortadaSala(f, Modifier.fillMaxWidth(), radio = 8.dp, sombra = 8.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(f.titulo, color = tinta.tinta, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Tecla(t: String, id: String, ancho: androidx.compose.ui.unit.Dp = 104.dp, alPulsar: () -> Unit) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val on = foco.actual == id && foco.visible
    Box(Modifier.width(ancho).height(76.dp).enfocable(id, radio = 10.dp, crece = 1.1f, alPulsar = alPulsar).clip(formaSala(10.dp))
        .background(if (on) tinta.acento else tinta.tarjeta), contentAlignment = Alignment.Center) {
        Text(t, color = if (on) tinta.sobreAcento else tinta.tinta, fontFamily = if (t.length == 1) Display else Body, fontWeight = FontWeight.Bold, fontSize = if (t.length == 1) 34.sp else 18.sp, letterSpacing = if (t.length == 1) 0.sp else 1.sp)
    }
}

/** Suspender y apagar el ordenador (en Linux con systemd, que en tu sesión no pide contraseña; en el Mac, como lo hace el menú Apple). */
object Energia {
    fun suspende() = runCatching { Programas.abre(if (Sitio.sistema == Sitio.Sistema.MAC) listOf("pmset", "sleepnow") else listOf("systemctl", "suspend")) }
    fun apaga() = runCatching { Programas.abre(if (Sitio.sistema == Sitio.Sistema.MAC) listOf("osascript", "-e", "tell application \"System Events\" to shut down") else listOf("systemctl", "poweroff")) }
}
