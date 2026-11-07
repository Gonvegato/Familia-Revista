package com.gonzalo.mostaza

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La sala: Mostaza para la tele (o para una tablet con un mando, si lo pides en Ajustes). Es la misma Mostaza (el mismo APK, la misma
 * biblioteca, los mismos addons, el mismo elector y el mismo reproductor); solo cambia la cara: a pantalla completa, con portadas grandes
 * y todo con la cruceta del mando a distancia, como la sala de Hiedra en el ordenador (`--sala`).
 */
object Sala {
    private fun p(c: Context) = c.getSharedPreferences("mostaza_sala", Context.MODE_PRIVATE)

    /** ¿Es una tele? (Android TV o Google TV: el modo de pantalla de tele o, si el fabricante no lo pone, el «leanback» del sistema) */
    fun esTele(c: Context): Boolean {
        val conf = c.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (conf == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val um = c.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (um?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return runCatching { c.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }.getOrDefault(false)
    }

    /**
     * La sala en una tablet (o un móvil) con un mando: es de este dispositivo (no viaja con la cuenta: en el móvil de siempre no la
     * quieres). Se lee al arrancar y, al cambiarla en Ajustes, la app cambia al momento.
     */
    var enTablet by mutableStateOf(false)
        private set
    fun cargar(c: Context) { enTablet = p(c).getBoolean("tablet", false) }
    fun ponEnTablet(c: Context, v: Boolean) { enTablet = v; p(c).edit().putBoolean("tablet", v).apply() }

    /** ¿Se abre la sala? En la tele, siempre; en lo demás, si la has pedido. */
    fun activa(c: Context): Boolean = esTele(c) || enTablet
}

/** Las secciones de la sala (arriba, como pestañas). */
enum class SeccionSala(val nombre: String) { INICIO("Inicio"), BIBLIOTECA("Biblioteca"), EXPLORAR("Explorar"), BUSCAR("Buscar") }

/** Una pregunta antes de hacer algo que no tiene vuelta (quitar algo de tu biblioteca). */
class Confirmacion(val titulo: String, val texto: String, val si: String, val alSi: () -> Unit)

/** Lo que se pidió ver en la lista de fuentes: de qué título y, en una serie, de qué episodio. */
class PideFuentes(val breve: MetaBreve, val meta: Meta?, val video: Video?)

/** El estado de la sala: lo que sobrevive a ir y volver entre sus vistas (la sección, las fichas abiertas, por dónde ibas). */
class EstadoSala {
    var seccion by mutableStateOf(SeccionSala.INICIO)
    /** Las fichas abiertas (la de «Más como esta» encima de la de antes), la de arriba al final. */
    val pila = mutableStateListOf<MetaBreve>()
    var fuentes by mutableStateOf<PideFuentes?>(null)
    var menu by mutableStateOf(false)
    var confirmar by mutableStateOf<Confirmacion?>(null)
    /** Una página de las de siempre (Ajustes, Addons), a pantalla completa. */
    var pagina by mutableStateOf<Destino?>(null)
    /** Un addon que instalar (el botón «Install» de su página). */
    var instalar by mutableStateOf<String?>(null)
    /** Lo del cartel de arriba (lo último enfocado en una fila). */
    var destacada by mutableStateOf<MetaBreve?>(null)
    var aviso by mutableStateOf<Pair<String, Long>?>(null)
    /** La sección de la biblioteca que se ve. */
    var estadoBib by mutableStateOf(Estado.VIENDO)
    var consulta by mutableStateOf("")
    /** Lo primero que se enfoca en cada vista (lo dice la propia vista al pintarse). */
    val primero = HashMap<String, String>()
    private val filas = HashMap<String, LazyListState>()
    private val paginas = HashMap<String, ScrollState>()
    fun fila(id: String) = filas.getOrPut(id) { LazyListState() }
    fun pagina(id: String) = paginas.getOrPut(id) { ScrollState(0) }
    /** La vista de ahora (para recordar lo enfocado en cada una). */
    val vista: String get() = pila.lastOrNull()?.let { "ficha:${it.tipo}:${it.id}" } ?: "sec:${seccion.name}"
    fun avisa(t: String) { aviso = t to System.currentTimeMillis() }
}

/** Lo que hace cada cosa de la sala: abrir y cerrar capas, ir y volver, y adónde va el foco. */
class ControlSala(val est: EstadoSala, val foco: FocoSala) {
    fun capa(): String = when { est.confirmar != null -> "confirmar"; est.menu -> "menu"; est.fuentes != null -> "fuentes"; est.pila.isNotEmpty() -> "ficha"; else -> "base" }
    private fun claveDe(c: String) = if (c == "base" || c == "ficha") est.vista else c

    fun recuerda() { foco.actual?.let { foco.ultimo[claveDe(capa())] = it } }
    /** Lo primero de la vista de ahora (o lo último que tuvo el foco en ella). */
    fun pideInicial() { foco.pedido = foco.ultimo[claveDe(capa())] ?: est.primero[claveDe(capa())] ?: "tab:${est.seccion.name}" }

    private fun abreCapa(c: String, primero: String) { recuerda(); foco.abre(c); foco.pedido = primero }
    /** Cierra una capa y vuelve a lo que estaba enfocado debajo. */
    fun cierra(c: String) {
        when (c) { "fuentes" -> est.fuentes = null; "menu" -> est.menu = false; "confirmar" -> est.confirmar = null }
        foco.cierra(c)
        pideInicial()
    }

    fun abreFicha(b: MetaBreve) {
        recuerda()
        est.pila += b
        foco.abre("ficha")
        foco.pedido = foco.ultimo[est.vista] ?: "f:reproducir"
    }
    fun abreFuentes(p: PideFuentes) { est.fuentes = p; abreCapa("fuentes", "fu:0") }
    fun abreMenu() { if (capa() == "base" || capa() == "ficha") { est.menu = true; abreCapa("menu", "m:0") } }
    fun confirma(c: Confirmacion) { est.confirmar = c; abreCapa("confirmar", "no") }

    fun atras(): Boolean {
        when (val c = capa()) {
            "ficha" -> {
                recuerda()
                est.pila.removeAt(est.pila.lastIndex)
                if (est.pila.isEmpty()) foco.cierra("ficha")
                foco.salta = true
                pideInicial()
            }
            "base" -> {
                if (est.seccion == SeccionSala.INICIO) return false
                seccion(SeccionSala.INICIO)
            }
            else -> cierra(c)
        }
        return true
    }

    fun seccion(s: SeccionSala) {
        if (capa() != "base") return
        recuerda()
        est.seccion = s
        foco.salta = true
        foco.pedido = foco.ultimo[est.vista] ?: "tab:${s.name}"
    }
    fun seccionAl(paso: Int) { val l = SeccionSala.entries; seccion(l[(l.indexOf(est.seccion) + paso + l.size) % l.size]) }
}

/**
 * La sala: arriba, el nombre, las secciones (Inicio · Biblioteca · Explorar · Buscar), el menú y la hora; en Inicio, lo enfocado en
 * grande y debajo las filas (Seguir viendo, Pendientes, En tu kDrive y el catálogo); cada película o serie tiene su ficha, con
 * «Reproducir». Abajo, qué hace cada tecla. Se ve igual en cualquier tele: se compone sobre 1080 líneas y se escala.
 *
 * Con el mando a distancia: la cruceta mueve, OK pulsa, ATRÁS vuelve (de la ficha a donde estabas; de una sección a Inicio; en Inicio,
 * sale de Mostaza), MENÚ abre el menú, la tecla de buscar va a Buscar; con un mando de juegos, además, L1 y R1 cambian de sección.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SalaApp(fichaPedida: String? = null, addonPedido: String? = null, seccionPedida: String? = null, alAtender: () -> Unit = {},
            est: EstadoSala = remember { EstadoSala() }, foco: FocoSala = remember { FocoSala() }) {
    val ctx = LocalContext.current
    val ctl = remember(est, foco) { ControlSala(est, foco) }
    val tele = remember { Sala.esTele(ctx) }
    LaunchedEffect(fichaPedida, addonPedido, seccionPedida) {
        // (una sección que pide un widget: la biblioteca, en esa sección; si además pide una ficha, la ficha va por encima)
        seccionPedida?.let { s -> Estado.entries.firstOrNull { it.name.equals(s, true) }?.let { e ->
            est.estadoBib = e; if (ctl.capa() == "base") ctl.seccion(SeccionSala.BIBLIOTECA)
        } }
        fichaPedida?.let { r -> Biblioteca.porRef(r)?.let { ctl.abreFicha(it.breve()) } }
        addonPedido?.let { est.instalar = it; est.pagina = Destino.Addons }
        if (fichaPedida != null || addonPedido != null || seccionPedida != null) alAtender()
    }
    AvisoActualizacion()
    // Las páginas de siempre (Ajustes, Addons), a pantalla completa y con su letra de siempre; con márgenes de tele si es una tele
    est.pagina?.let { pg ->
        BackHandler { est.pagina = null; ctl.pideInicial() }
        Box(Modifier.fillMaxSize().background(Paper).then(if (tele) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier)) {
            when (pg) {
                Destino.Addons -> PantAddons(alVolver = { est.pagina = null; ctl.pideInicial() }, instalar = est.instalar, alInstalado = { est.instalar = null })
                else -> PantEstilo(alVolver = { est.pagina = null; ctl.pideInicial() }, alAddons = { est.pagina = Destino.Addons })
            }
        }
        return
    }
    BackHandler(enabled = ctl.capa() != "base" || est.seccion != SeccionSala.INICIO) { ctl.atras() }
    val tinta = TintaSala(Skin.tonosPara(true))
    val teclado = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    SideEffect { foco.visible = tele || teclado }
    BoxWithConstraints(Modifier.fillMaxSize().background(tinta.fondo)) {
        val k = minOf(constraints.maxHeight / 1080f, constraints.maxWidth / 1920f).coerceIn(0.3f, 6f)
        CompositionLocalProvider(LocalDensity provides Density(k, 1f), LocalTinta provides tinta, LocalFoco provides foco) {
            Box(Modifier.fillMaxSize().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Menu, Key.ButtonStart -> { if (est.menu) ctl.cierra("menu") else ctl.abreMenu(); true }
                    Key.Search, Key.ButtonX -> { if (ctl.capa() == "base") { ctl.seccion(SeccionSala.BUSCAR); foco.pedido = "q:campo" }; true }
                    Key.ButtonL1 -> { ctl.seccionAl(-1); true }
                    Key.ButtonR1 -> { ctl.seccionAl(1); true }
                    else -> false
                }
            }) {
                Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalCapa provides "base") { BarraSala(est, ctl) }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(est.seccion, transitionSpec = { (fadeIn(tween(240)) + scaleIn(tween(320), initialScale = 0.985f)) togetherWith fadeOut(tween(160)) }, label = "vista") { s ->
                            CompositionLocalProvider(LocalCapa provides "base") {
                                when (s) {
                                    SeccionSala.INICIO -> InicioSala(est, ctl)
                                    SeccionSala.BIBLIOTECA -> BibliotecaSala(est, ctl)
                                    SeccionSala.EXPLORAR -> ExplorarSala(est, ctl)
                                    SeccionSala.BUSCAR -> BuscarSala(est, ctl)
                                }
                            }
                        }
                        // La ficha (la de arriba de la pila), encima de la sección: lo de debajo sigue ahí para volver donde estabas
                        est.pila.lastOrNull()?.let { b ->
                            CompositionLocalProvider(LocalCapa provides "ficha") {
                                key(b.tipo + b.id) { FichaSala(b, est, ctl) }
                            }
                        }
                    }
                    PistasSala(est, ctl)
                }
                est.fuentes?.let { f -> CompositionLocalProvider(LocalCapa provides "fuentes") { PanelFuentes(f, est, ctl) } }
                if (est.menu) CompositionLocalProvider(LocalCapa provides "menu") { PanelMenu(est, ctl, tele) }
                est.confirmar?.let { c -> CompositionLocalProvider(LocalCapa provides "confirmar") { PanelConfirmar(c, ctl) } }
                AvisoSala(est)
                MarcoFoco(foco, tinta.acento)
            }
        }
    }
    // Al entrar (y al volver de una página), el foco a donde estaba; y si lo enfocado desaparece (lo quitaste, terminó de cargar la
    // ficha…), a lo primero de la vista: en la tele nunca se queda nada sin foco
    val actual = foco.actual
    LaunchedEffect(actual) { if (actual == null) { delay(350); if (foco.actual == null && foco.pedido == null) ctl.pideInicial() } }
    // Si lo pedido ya no está (se fue de la fila), a lo primero
    val pedido = foco.pedido
    LaunchedEffect(pedido) {
        if (pedido == null) return@LaunchedEffect
        delay(600)
        if (foco.pedido == pedido) {
            val vista = if (ctl.capa() == "base" || ctl.capa() == "ficha") est.vista else ctl.capa()
            foco.ultimo.remove(vista)
            foco.pedido = est.primero[vista]?.takeIf { it != pedido } ?: if (ctl.capa() == "base") "tab:${est.seccion.name}" else null
        }
    }
}

// ---------------------------------------------------------------- arriba y abajo

@Composable
private fun BarraSala(est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    var hora by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { delay(1000L * (60 - (System.currentTimeMillis() / 1000L % 60)).coerceAtLeast(1)); hora = Date() } }
    Column(Modifier.fillMaxWidth().padding(top = MARGEN_V - 20.dp)) {
        Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = MARGEN_H).focusGroup(), verticalAlignment = Alignment.CenterVertically) {
            Text("HATHOR", color = tinta.tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 3.sp, maxLines = 1)
            Spacer(Modifier.width(48.dp))
            SeccionSala.entries.forEach { s ->
                val on = s == est.seccion
                val ancho by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f), label = "pestaña")
                Column(Modifier.padding(horizontal = 6.dp).enfocable("tab:${s.name}", radio = 8.dp, crece = 1.05f) { ctl.seccion(s) }.padding(horizontal = 14.dp, vertical = 8.dp)
                    .width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.nombre.uppercase(), color = if (on) tinta.tinta else tinta.gris, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 2.4.sp, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(4.dp).fillMaxWidth(ancho).background(tinta.acento))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.enfocable("tab:menu", radio = 8.dp, crece = 1.05f) { ctl.abreMenu() }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("☰", color = tinta.tinta, fontSize = 24.sp, fontFamily = Body)
                Spacer(Modifier.width(10.dp))
                Text("MENÚ", color = tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.width(30.dp))
            Text(SimpleDateFormat("HH:mm", Locale("es", "ES")).format(hora), color = tinta.tinta, fontFamily = Display, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = MARGEN_H).height(2.dp).background(tinta.tinta.copy(alpha = 0.85f)))
    }
}

@Composable
private fun PistasSala(est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    Column(Modifier.fillMaxWidth().padding(bottom = MARGEN_V - 24.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = MARGEN_H).height(1.dp).background(tinta.linea))
        Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = MARGEN_H), verticalAlignment = Alignment.CenterVertically) {
            val usuario = Cuenta.usuario
            Text(if (Cuenta.vinculada && usuario.isNotBlank()) usuario else "Sin cuenta: entra en Menú → Ajustes → Cuenta", color = tinta.tinta, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 700.dp))
            Spacer(Modifier.weight(1f))
            val pistas = when (ctl.capa()) {
                "confirmar" -> listOf("OK" to "Elegir", "Atrás" to "Cancelar")
                "menu", "fuentes" -> listOf("OK" to "Elegir", "Atrás" to "Cerrar")
                "ficha" -> listOf("OK" to "Elegir", "Atrás" to "Volver", "☰" to "Menú")
                else -> listOf("OK" to "Abrir", "Atrás" to if (est.seccion == SeccionSala.INICIO) "Salir" else "Inicio", "☰" to "Menú")
            }
            pistas.forEachIndexed { i, (a, t) -> if (i > 0) Spacer(Modifier.width(34.dp)); PistaSala(a, t) }
        }
    }
}

@Composable
private fun AvisoSala(est: EstadoSala) {
    val tinta = LocalTinta.current
    val a = est.aviso
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(a) { if (a != null) { visible = true; delay(4200); visible = false } }
    Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible && a != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            Text(a?.first.orEmpty(), color = tinta.sobreAcento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 27.sp,
                modifier = Modifier.widthIn(max = 1200.dp).clip(formaSala(14.dp)).background(tinta.acento).padding(horizontal = 30.dp, vertical = 16.dp))
        }
    }
}

// ---------------------------------------------------------------- las filas de pósteres

/** Una fila de la sala: su id (para recordar el foco), su título y lo que trae, con lo que se dice debajo de cada póster. */
class FilaSala(
    val id: String, val titulo: String, val cosas: List<MetaBreve>, val sub: (MetaBreve) -> String = { it.ano },
    val progreso: (MetaBreve) -> Float? = { null }, val imagen: (MetaBreve) -> String = { it.poster },
)

/** El id del foco de un póster en una fila. */
fun idCarta(fila: String, b: MetaBreve) = "$fila/${b.tipo}:${b.id}"

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun FilaPostersSala(f: FilaSala, est: EstadoSala, ctl: ControlSala) {
    val tinta = LocalTinta.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.padding(horizontal = MARGEN_H), verticalAlignment = Alignment.Bottom) {
            AnteSala(f.titulo, tam = 18.sp)
            Spacer(Modifier.width(16.dp))
            Text(f.cosas.size.toString(), color = tinta.gris, fontFamily = Display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(300f) }) {
            // (con la cruceta, de una fila no se sale por los lados: al final de la fila, la cruceta se queda en el último). Sin focusRestorer:
            // en esta versión de Compose suelta dos veces el póster que guarda al quitarse la fila, y se cae; ya se vuelve a lo último con FocoSala
            LazyRow(Modifier.fillMaxWidth().focusGroup().focusProperties {
                exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) androidx.compose.ui.focus.FocusRequester.Cancel else androidx.compose.ui.focus.FocusRequester.Default }
            }, state = est.fila("${est.vista}/${f.id}"), contentPadding = PaddingValues(start = MARGEN_H, end = MARGEN_H, top = 26.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                itemsIndexed(f.cosas, key = { _, x -> x.tipo + ":" + x.id }) { _, x -> CartaSala(x, idCarta(f.id, x), est, ctl, f.sub(x), f.progreso(x), f.imagen(x)) }
            }
        }
    }
}

/** Un póster en una fila o en una rejilla: con su título debajo; al enfocarlo, crece y pasa al cartel de arriba. */
@Composable
fun CartaSala(b: MetaBreve, id: String, est: EstadoSala, ctl: ControlSala, sub: String = b.ano, progreso: Float? = null, imagen: String = b.poster, ancho: androidx.compose.ui.unit.Dp = 190.dp) {
    val tinta = LocalTinta.current
    val foco = LocalFoco.current
    val enfocada = foco.actual == id
    Column(Modifier.width(ancho)) {
        Box(Modifier.enfocable(id, radio = 10.dp, crece = 1.08f, alEnfocar = { est.destacada = b }) { ctl.abreFicha(b) }) {
            PosterSala(imagen, b.nombre, Modifier.fillMaxWidth(), sombra = if (enfocada) 22.dp else 6.dp, progreso = progreso)
        }
        Spacer(Modifier.height(22.dp))
        Text(b.nombre, color = if (enfocada) tinta.tinta else tinta.tinta.copy(alpha = 0.86f), fontFamily = Display, fontSize = 19.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sub.uppercase(), color = tinta.gris, fontFamily = Body, fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- Inicio: el cartel y las filas

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InicioSala(est: EstadoSala, ctl: ControlSala) {
    val d = rememberPortada()
    val filas = buildList {
        if (d.viendo.isNotEmpty()) add(FilaSala("viendo", "Seguir viendo", d.viendo.map { it.breve() },
            sub = { b -> d.viendo.firstOrNull { it.idStremio == b.id }?.let { seguirTexto(it) }.orEmpty() },
            progreso = { b -> d.viendo.firstOrNull { it.idStremio == b.id }?.let { Biblioteca.ultimo(it.ref)?.takeIf { p -> !p.visto }?.fraccion } },
            imagen = { b -> d.viendo.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster }))
        if (d.pendientes.isNotEmpty()) add(FilaSala("pendientes", "Pendientes", d.pendientes.take(30).map { it.breve() },
            sub = { b -> tipoNombre(b.tipo) + if (b.ano.isNotBlank()) " · ${b.ano}" else "" }, imagen = { b -> d.pendientes.firstOrNull { it.idStremio == b.id }?.imagen ?: b.poster }))
        if (d.enKDrive.isNotEmpty()) add(FilaSala("kdrive", "En tu kDrive", d.enKDrive, sub = { tipoNombre(it.tipo) + if (it.ano.isNotBlank()) " · ${it.ano}" else "" }))
        if (d.conTmdb) d.filasTmdb.orEmpty().forEach { f -> add(FilaSala("tmdb:" + f.clave, f.titulo, f.cosas.take(30))) }
        else d.filas.orEmpty().forEach { f -> add(FilaSala("cat:" + f.clave, f.titulo, f.cosas.take(30))) }
    }
    val primera = filas.firstOrNull()
    SideEffect { primera?.let { f -> f.cosas.firstOrNull()?.let { est.primero["sec:INICIO"] = idCarta(f.id, it) } } }
    val dest = est.destacada?.takeIf { x -> filas.any { f -> f.cosas.any { it.id == x.id } } } ?: d.destacado ?: primera?.cosas?.firstOrNull()
    val t = dest?.let { Biblioteca.por(it.id) }
    Box(Modifier.fillMaxSize()) {
        FondoSala(dest?.fondo?.ifBlank { null } ?: t?.fondo.orEmpty(), t?.imagen?.ifBlank { null } ?: dest?.poster.orEmpty(), dest?.let { it.tipo + it.id }, Modifier.fillMaxWidth().fillMaxHeight(0.75f))
        Column(Modifier.fillMaxSize()) {
            Cartel(dest, Modifier.fillMaxWidth().height(330.dp))
            val cargando = (d.conTmdb && d.filasTmdb == null) || (!d.conTmdb && d.filas == null)
            if (filas.isEmpty()) Column(Modifier.padding(horizontal = MARGEN_H, vertical = 30.dp)) {
                NotaSala(when {
                    cargando -> "Mirando el catálogo…"
                    d.conTmdb -> "TMDB no responde ahora mismo (¿sin conexión?, ¿la clave ya no vale?). Míralo en Menú → Ajustes → Catálogo."
                    else -> "Tus addons no dan ningún catálogo ahora mismo (sin conexión, o no tienen). Añade addons en Menú → Addons; para tener el catálogo de una plataforma, pon tu clave de TMDB en Menú → Ajustes → Catálogo."
                }, Modifier.widthIn(max = 1300.dp), tam = 28.sp)
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides remember { TraerAVista(80f, inicio = 60f) }) {
                Column(Modifier.fillMaxSize().verticalScroll(est.pagina(est.vista)).padding(bottom = 40.dp)) {
                    filas.forEach { f -> FilaPostersSala(f, est, ctl) }
                    if (!cargando && filas.isNotEmpty() && !d.conTmdb) NotaSala("Para que Hathor tenga su propio catálogo, como una plataforma, pon tu clave de TMDB (gratis) en Menú → Ajustes → Catálogo.",
                        Modifier.padding(horizontal = MARGEN_H, vertical = 24.dp).widthIn(max = 1300.dp), tam = 20.sp)
                }
            }
        }
    }
}

/** El cartel de arriba: lo enfocado, en grande. */
@Composable
private fun Cartel(b: MetaBreve?, modifier: Modifier) {
    val tinta = LocalTinta.current
    Box(modifier.padding(horizontal = MARGEN_H), contentAlignment = Alignment.BottomStart) {
        AnimatedContent(b, transitionSpec = { (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { it / 24 }) togetherWith fadeOut(tween(180)) }, label = "cartel",
            contentKey = { it?.let { x -> x.tipo + x.id } }) { x ->
            if (x == null) return@AnimatedContent
            val t = Biblioteca.por(x.id)
            Column(Modifier.fillMaxWidth(0.62f).padding(bottom = 24.dp)) {
                Text(listOfNotNull(tipoNombre(x.tipo), x.ano.ifBlank { null }, t?.estado?.nombre?.let { "En $it" }).joinToString("  ·  ").uppercase(),
                    color = tinta.acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 3.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                FitTitle(x.nombre, 80.sp, 84.sp, color = tinta.tinta, letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minSize = 40f)
                Spacer(Modifier.height(8.dp))
                val linea = listOfNotNull(t?.takeIf { it.estado == Estado.VIENDO }?.let { seguirTexto(it) }?.ifBlank { null }, x.generos.take(3).joinToString(", ").ifBlank { null }, x.nota.ifBlank { null }?.let { "★ $it" })
                if (linea.isNotEmpty()) Text(linea.joinToString("  ·  "), color = tinta.tinta.copy(alpha = 0.8f), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (x.descripcion.isNotBlank()) Text(x.descripcion, color = tinta.tinta.copy(alpha = 0.72f), fontFamily = Body, fontSize = 18.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- lo que se abre encima

@Composable
private fun PanelMenu(est: EstadoSala, ctl: ControlSala, tele: Boolean) {
    val ctx = LocalContext.current
    Lateral("Menú", "Hathor · versión $VERSION", alCerrar = { ctl.cierra("menu") }) {
        OpcionSala("Ajustes", "m:0", "La apariencia, el catálogo (TMDB), el reproductor, las fuentes y tu cuenta") { ctl.cierra("menu"); est.pagina = Destino.Ajustes }
        OpcionSala("Addons", "m:1", "Los addons de Stremio que usas: catálogos, fuentes y subtítulos") { ctl.cierra("menu"); est.pagina = Destino.Addons }
        OpcionSala("Biblioteca", "m:2", "Viendo, Pendiente, Visto y Abandonado") { ctl.cierra("menu"); est.pila.clear(); ctl.foco.cierra("ficha"); ctl.seccion(SeccionSala.BIBLIOTECA) }
        OpcionSala("Buscar", "m:3") { ctl.cierra("menu"); est.pila.clear(); ctl.foco.cierra("ficha"); ctl.seccion(SeccionSala.BUSCAR) }
        if (!tele) OpcionSala("Salir de la sala", "m:4", "Volver a la Hathor de siempre (en Ajustes → Avanzado se vuelve a la sala)") { ctl.cierra("menu"); Sala.ponEnTablet(ctx, false) }
    }
}

@Composable
private fun PanelConfirmar(c: Confirmacion, ctl: ControlSala) {
    val tinta = LocalTinta.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(900.dp).clip(formaSala(18.dp)).background(tinta.fondo).padding(56.dp)) {
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
