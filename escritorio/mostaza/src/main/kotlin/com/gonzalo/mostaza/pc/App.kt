package com.gonzalo.mostaza.pc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.familia.pc.LaFamilia
import com.gonzalo.familia.pc.Sitio
import com.gonzalo.familia.pc.revista.*
import kotlinx.coroutines.launch

/** Dónde estás en Mostaza. */
sealed class Pantalla {
    data object Portada : Pantalla()
    data class Seccion(val estado: Estado) : Pantalla()
    data object Explorar : Pantalla()
    data object Buscar : Pantalla()
    data class Ficha(val breve: MetaBreve, val volver: Pantalla) : Pantalla()
    data class Fuentes(val breve: MetaBreve, val meta: Meta?, val video: Video?, val volver: Pantalla) : Pantalla()
    data object Addons : Pantalla()
    data class Ajustes(val pestana: Int = 0) : Pantalla()
}

/** Lo que necesitan las pantallas para moverse y avisar. [abre]: la ficha de algo, volviendo luego a donde estabas. */
class Nav(val ir: (Pantalla) -> Unit, val volver: () -> Unit, val aviso: (String) -> Unit, val actual: () -> Pantalla) {
    fun abre(b: MetaBreve) = ir(Pantalla.Ficha(b, actual()))
}

/**
 * Mostaza del ordenador: la columna de la izquierda (Portada, tu biblioteca, el catálogo, tus addons y Ajustes, como el menú de Mostaza del
 * móvil) y la página. Con el teclado: las flechas mueven el foco, Intro pulsa, Esc vuelve, Ctrl+F (⌘F en el Mac) busca, Ctrl+1…8 cambian de
 * sección; el mando hace lo mismo ([Mando]). Abajo, mientras mpv tiene algo puesto, la banda del reproductor.
 */
@Composable
fun AppMostaza(inicial: Pantalla = Pantalla.Portada) {
    LaFamilia.cambios
    remember { CineKDrive.engancha() }
    var pantalla by remember { mutableStateOf(inicial) }
    val historia = remember { mutableStateListOf<Pantalla>() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun ir(p: Pantalla) { if (p != pantalla) { historia.add(pantalla); if (historia.size > 40) historia.removeAt(0) }; pantalla = p }
    fun volver() {
        val p = pantalla
        pantalla = when {
            p is Pantalla.Ficha -> p.volver
            p is Pantalla.Fuentes -> p.volver
            historia.isNotEmpty() -> historia.removeAt(historia.size - 1)
            else -> Pantalla.Portada
        }
    }
    val nav = remember { Nav({ ir(it) }, { volver() }, { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } }, { pantalla }) }
    val titulos = Biblioteca.titulos()
    val destinos = buildList<Pair<Entrada, Pantalla>> {
        add(Entrada("Portada", grupo = "Mostaza") to Pantalla.Portada)
        listOf(Estado.VIENDO, Estado.PENDIENTE, Estado.VISTO, Estado.ABANDONADO).forEach { e -> add(Entrada(e.nombre, titulos.count { it.estado == e }, grupo = "Tu biblioteca") to Pantalla.Seccion(e)) }
        add(Entrada("Explorar", grupo = "El catálogo") to Pantalla.Explorar); add(Entrada("Buscar", grupo = "El catálogo") to Pantalla.Buscar)
        add(Entrada("Addons", grupo = "Más") to Pantalla.Addons); add(Entrada("Ajustes", grupo = "Más") to Pantalla.Ajustes())
    }
    val sel = destinos.indexOfFirst { (_, d) ->
        when (val p = pantalla) { is Pantalla.Ficha -> false; is Pantalla.Fuentes -> false; is Pantalla.Ajustes -> d is Pantalla.Ajustes; else -> d == p }
    }.let { if (it < 0) -1 else it }
    val foco = LocalFocusManager.current
    val raiz = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { raiz.requestFocus() } }
    Column(Modifier.fillMaxSize().focusRequester(raiz).focusable().onKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown) return@onKeyEvent false
        val mod = if (Sitio.sistema == Sitio.Sistema.MAC) k.isMetaPressed else k.isCtrlPressed
        when {
            // (las flechas que no usa nadie, por ejemplo un campo de texto, mueven el foco: como la cruceta del mando)
            k.key == Key.DirectionLeft -> foco.moveFocus(FocusDirection.Left)
            k.key == Key.DirectionRight -> foco.moveFocus(FocusDirection.Right)
            k.key == Key.DirectionUp -> foco.moveFocus(FocusDirection.Up)
            k.key == Key.DirectionDown -> foco.moveFocus(FocusDirection.Down)
            k.key == Key.Escape || (k.key == Key.Backspace && k.isAltPressed) -> { volver(); true }
            mod && k.key == Key.F -> { ir(Pantalla.Buscar); true }
            mod && k.key in listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight) -> {
                val i = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight).indexOf(k.key)
                destinos.getOrNull(i)?.let { ir(it.second) }; true
            }
            // (las secciones con los gatillos del mando o con Ctrl+Re Pág / Av Pág)
            mod && k.key == Key.PageUp -> { destinos.getOrNull((if (sel < 0) 0 else sel) - 1)?.let { ir(it.second) }; true }
            mod && k.key == Key.PageDown -> { destinos.getOrNull((if (sel < 0) 0 else sel) + 1)?.let { ir(it.second) }; true }
            k.key == Key.MediaPlayPause -> { Reproductor.alterna(); true }
            else -> false
        }
    }) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Lateral("Mostaza", destinos.map { it.first }, sel) { i -> ir(destinos[i].second) }
            Box(Modifier.width(1.dp).fillMaxHeight()) { Filete(1.dp, Line, Modifier.fillMaxHeight()) }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Como en el móvil: abrir una ficha la sube un poco desde abajo; entre secciones, un fundido rápido
                AnimatedContent(pantalla, transitionSpec = {
                    if (targetState is Pantalla.Ficha || targetState is Pantalla.Fuentes) (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 16 }) togetherWith fadeOut(tween(160))
                    else fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                }, label = "pantalla") { p ->
                    when (p) {
                        Pantalla.Portada -> PantPortada(nav)
                        is Pantalla.Seccion -> PantSeccion(p.estado, nav)
                        Pantalla.Explorar -> PantExplorar(nav)
                        Pantalla.Buscar -> PantBuscar(nav)
                        is Pantalla.Ficha -> PantFicha(p.breve, nav)
                        is Pantalla.Fuentes -> PantFuentes(p.breve, p.meta, p.video, nav)
                        Pantalla.Addons -> PantAddons(nav)
                        is Pantalla.Ajustes -> PantAjustes(nav, p.pestana)
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        BandaReproductor(nav)
    }
}

/**
 * La banda del reproductor, abajo: lo que mpv tiene puesto (con su ventana aparte), por dónde va, lo que pasa («Probando otra…», «Ha entrado
 * en Viendo») y lo que se puede hacer desde aquí: pausa, saltar, el audio, los subtítulos, otra fuente, el episodio siguiente y cerrar.
 */
@Composable
fun BandaReproductor(nav: Nav) {
    val e = Reproductor.estado
    val aviso = e.aviso
    if (!e.activo && aviso == null) return
    var eligiendo by remember { mutableStateOf(0) }   // 0 nada, 1 audio, 2 subtítulos, 3 fuente
    Column(Modifier.fillMaxWidth().background(Sand)) {
        Filete(2.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (e.activo) {
                    Text((if (e.cargando) "CARGANDO · " else if (e.reproduciendo) "VIENDO · " else "EN PAUSA · ") + e.fuente.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(e.titulo, e.subtitulo).filter { it.isNotBlank() }.joinToString(" · "), color = Charcoal, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.dur > 0) { Barra(e.pos.toFloat() / e.dur, Modifier.padding(top = 4.dp).widthIn(max = 520.dp)); Text("${tiempo(e.pos)} de ${tiempo(e.dur)}", color = Muted, fontFamily = Body, fontSize = 11.sp) }
                }
                (e.mensaje ?: e.corte ?: e.siguiente?.let { s -> "Siguiente: $s" + if (e.cuentaAtras >= 0) " en ${e.cuentaAtras} s" else "" })?.let { Text(it, color = Charcoal, fontFamily = Body, fontSize = 13.sp) }
                aviso?.let { Text(it, color = Acento, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.activo) {
                    EnlaceRevista("−${AjustesReproductor.salto()} s") { Reproductor.salta(-AjustesReproductor.salto()) }
                    EnlaceRevista(if (e.reproduciendo) "Pausa" else "Seguir", acento = true) { Reproductor.alterna() }
                    EnlaceRevista("+${AjustesReproductor.salto()} s") { Reproductor.salta(AjustesReproductor.salto()) }
                    if (PistasDeMpv.audios(e.pistas).size > 1) EnlaceRevista("Audio") { eligiendo = if (eligiendo == 1) 0 else 1 }
                    if (PistasDeMpv.subtitulos(e.pistas).isNotEmpty()) EnlaceRevista("Subtítulos") { eligiendo = if (eligiendo == 2) 0 else 2 }
                    if (e.candidatas.size > 1) EnlaceRevista("Fuente") { eligiendo = if (eligiendo == 3) 0 else 3 }
                    if (e.corte != null) EnlaceRevista("Probar otra", acento = true) { Reproductor.siguienteFuente() }
                    if (e.siguiente != null) { EnlaceRevista("Ya", acento = true) { Reproductor.siguienteEpisodioYa() }; EnlaceRevista("No, gracias") { Reproductor.cancelaSiguiente() } }
                    EnlaceRevista("Cerrar") { Reproductor.para() }
                } else EnlaceRevista("Entendido") { Reproductor.quitaAviso() }
            }
        }
        if (e.activo && eligiendo != 0) {
            Filete(1.dp, Line)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when (eligiendo) {
                    1 -> PistasDeMpv.audios(e.pistas).forEach { p -> FiltroRevista(p.etiqueta, p.elegida) { Reproductor.ponAudio(p.id); eligiendo = 0 } }
                    2 -> {
                        val subs = PistasDeMpv.subtitulos(e.pistas)
                        FiltroRevista("Sin subtítulos", subs.none { it.elegida }) { Reproductor.ponSubtitulos(null); eligiendo = 0 }
                        subs.forEach { p -> FiltroRevista(p.etiqueta, p.elegida) { Reproductor.ponSubtitulos(p.id); eligiendo = 0 } }
                    }
                    3 -> e.candidatas.take(30).forEach { c -> FiltroRevista((c.analisis.resumen().ifBlank { c.fuente.nombre.lineSequence().firstOrNull().orEmpty() } + " · " + c.addon).take(70), c === e.elegida) {
                        Reproductor.cambiaFuente(c); eligiendo = 0 } }
                }
            }
        }
    }
}
