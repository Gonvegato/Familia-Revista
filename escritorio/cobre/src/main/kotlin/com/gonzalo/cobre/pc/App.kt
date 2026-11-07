package com.gonzalo.cobre.pc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie
import kotlinx.coroutines.launch

/** Dónde estás en Cobre. */
sealed class Pantalla {
    data object Directo : Pantalla()
    data object Guia : Pantalla()
    data object Peliculas : Pantalla()
    data object Series : Pantalla()
    data object Buscar : Pantalla()
    /** [editar]: abre ya el formulario de esa lista (en las capturas, una nueva). */
    data class Ajustes(val pestana: Int = 0, val editar: Lista? = null) : Pantalla()
    data class FichaPelicula(val lista: Lista, val pelicula: Pelicula, val volver: Pantalla) : Pantalla()
    data class FichaSerie(val lista: Lista, val serie: Serie, val volver: Pantalla) : Pantalla()
}

/** Lo que necesitan las pantallas para moverse y avisar. */
class Nav(val ir: (Pantalla) -> Unit, val volver: () -> Unit, val aviso: (String) -> Unit, val actual: () -> Pantalla) {
    /** Pon algo en mpv; si no se puede (sin mpv…), se dice. */
    fun ver(e: Encargo) { Reproductor.pon(e)?.let { aviso(it) } }
}

/**
 * Cobre del ordenador: la columna de la izquierda (Directo y Guía, el videoclub —Películas y Series, con Xtream—, Buscar y Ajustes, como el
 * menú de Cobre del móvil) y la página. Con el teclado: las flechas mueven el foco, Intro pulsa, Esc vuelve, Ctrl+F (⌘F en el Mac) busca,
 * Ctrl+1…6 cambian de sección y, con un canal puesto, Re Pág / Av Pág cambian de canal; el mando hace lo mismo ([Mando]). Abajo, mientras
 * mpv tiene algo puesto, la banda del reproductor (con la lista de canales).
 */
@Composable
fun AppCobre(inicial: Pantalla = Pantalla.Directo) {
    LaFamilia.cambios
    var pantalla by remember { mutableStateOf(inicial) }
    val historia = remember { mutableStateListOf<Pantalla>() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun ir(p: Pantalla) { if (p != pantalla) { historia.add(pantalla); if (historia.size > 40) historia.removeAt(0) }; pantalla = p }
    fun volver() {
        val p = pantalla
        pantalla = when {
            p is Pantalla.FichaPelicula -> p.volver
            p is Pantalla.FichaSerie -> p.volver
            historia.isNotEmpty() -> historia.removeAt(historia.size - 1)
            else -> Pantalla.Directo
        }
    }
    val nav = remember { Nav({ ir(it) }, { volver() }, { m -> scope.launch { snack.showSnackbar(m, withDismissAction = true) } }, { pantalla }) }
    val hayVideoclub = Listas.usables().any { it.esXtream }
    val destinos = buildList<Pair<Entrada, Pantalla>> {
        add(Entrada("Directo", grupo = "La tele") to Pantalla.Directo); add(Entrada("Guía", grupo = "La tele") to Pantalla.Guia)
        add(Entrada("Películas", grupo = "El videoclub") to Pantalla.Peliculas); add(Entrada("Series", grupo = "El videoclub") to Pantalla.Series)
        add(Entrada("Buscar", grupo = "Más") to Pantalla.Buscar); add(Entrada("Ajustes", grupo = "Más") to Pantalla.Ajustes())
    }
    val sel = destinos.indexOfFirst { (_, d) ->
        when (val p = pantalla) {
            is Pantalla.FichaPelicula -> d == Pantalla.Peliculas; is Pantalla.FichaSerie -> d == Pantalla.Series
            is Pantalla.Ajustes -> d is Pantalla.Ajustes; else -> d == p
        }
    }
    val foco = LocalFocusManager.current
    val raiz = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { raiz.requestFocus() } }
    val teclasSeccion = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)
    Column(Modifier.fillMaxSize().focusRequester(raiz).focusable().onKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown) return@onKeyEvent false
        val mod = if (Sitio.sistema == Sitio.Sistema.MAC) k.isMetaPressed else k.isCtrlPressed
        val e = Reproductor.estado
        when {
            // (las flechas que no usa nadie, por ejemplo un campo de texto, mueven el foco: como la cruceta del mando)
            k.key == Key.DirectionLeft -> foco.moveFocus(FocusDirection.Left)
            k.key == Key.DirectionRight -> foco.moveFocus(FocusDirection.Right)
            k.key == Key.DirectionUp -> foco.moveFocus(FocusDirection.Up)
            k.key == Key.DirectionDown -> foco.moveFocus(FocusDirection.Down)
            k.key == Key.Escape && e.lista != null -> { Reproductor.cierraLista(); true }
            k.key == Key.Escape || (k.key == Key.Backspace && k.isAltPressed) -> { volver(); true }
            mod && k.key == Key.F -> { ir(Pantalla.Buscar); true }
            mod && k.key in teclasSeccion -> { destinos.getOrNull(teclasSeccion.indexOf(k.key))?.let { ir(it.second) }; true }
            // (las secciones con los gatillos del mando o con Ctrl+Re Pág / Av Pág)
            mod && k.key == Key.PageUp -> { destinos.getOrNull((if (sel < 0) 0 else sel) - 1)?.let { ir(it.second) }; true }
            mod && k.key == Key.PageDown -> { destinos.getOrNull((if (sel < 0) 0 else sel) + 1)?.let { ir(it.second) }; true }
            // Con un canal puesto: Re Pág / Av Pág (y los botones de canal) cambian de canal
            e.activo && e.directo && (k.key == Key.PageUp || k.key == Key.ChannelUp) -> { Reproductor.arriba(); true }
            e.activo && e.directo && (k.key == Key.PageDown || k.key == Key.ChannelDown) -> { Reproductor.abajo(); true }
            k.key == Key.MediaPlayPause -> { Reproductor.alterna(); true }
            else -> false
        }
    }) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Lateral("Cobre", destinos.map { it.first }, sel) { i -> ir(destinos[i].second) }
            Box(Modifier.width(1.dp).fillMaxHeight()) { Filete(1.dp, Line, Modifier.fillMaxHeight()) }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(pantalla, transitionSpec = {
                    if (targetState is Pantalla.FichaPelicula || targetState is Pantalla.FichaSerie) (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 16 }) togetherWith fadeOut(tween(160))
                    else fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                }, label = "pantalla") { p ->
                    when (p) {
                        Pantalla.Directo -> PantDirecto(nav)
                        Pantalla.Guia -> PantGuia(nav)
                        Pantalla.Peliculas -> PantPeliculas(nav, hayVideoclub)
                        Pantalla.Series -> PantSeries(nav, hayVideoclub)
                        Pantalla.Buscar -> PantBuscar(nav)
                        is Pantalla.Ajustes -> PantAjustes(nav, p.pestana, p.editar)
                        is Pantalla.FichaPelicula -> PantFichaPelicula(p.lista, p.pelicula, nav)
                        is Pantalla.FichaSerie -> PantFichaSerie(p.lista, p.serie, nav)
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        BandaReproductor()
    }
}

/**
 * La banda del reproductor, abajo: lo que mpv tiene puesto (con su ventana aparte). En directo, el canal con lo que echan ahora y después,
 * cambiar de canal, la lista de canales (con lo que echan en cada uno) y volver a conectar; en una película o un episodio, por dónde va, la
 * pausa, saltar y el episodio siguiente. Y el audio, los subtítulos y cerrar.
 */
@Composable
fun BandaReproductor() {
    val e = Reproductor.estado
    val aviso = e.aviso
    if (!e.activo && aviso == null) return
    var eligiendo by remember { mutableStateOf(0) }   // 0 nada, 1 audio, 2 subtítulos
    Column(Modifier.fillMaxWidth().background(Sand)) {
        Filete(2.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.activo && e.directo) {
                Text("${e.numero}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.padding(end = 14.dp))
                Logo(e.logo, e.titulo, 40.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                if (e.activo) {
                    Text((if (e.cargando) "CARGANDO · " else if (e.reproduciendo) (if (e.directo) "EN DIRECTO · " else "VIENDO · ") else "EN PAUSA · ") + e.subtitulo.uppercase(),
                        color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.titulo + (if (e.tecleado.isNotBlank()) "  ·  canal ${e.tecleado}…" else ""), color = Charcoal, fontFamily = Display, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.directo) AhoraYDespues(e.ahora, e.despues, System.currentTimeMillis(), Modifier.padding(top = 2.dp).widthIn(max = 620.dp))
                    else if (e.dur > 0) { Barra(e.pos.toFloat() / e.dur, Modifier.padding(top = 4.dp).widthIn(max = 520.dp)); Text("${Formato.tiempo(e.pos)} de ${Formato.tiempo(e.dur)}", color = Muted, fontFamily = Body, fontSize = 11.sp) }
                }
                (e.mensaje ?: e.siguiente?.let { s -> "Siguiente: $s" + if (e.cuentaAtras >= 0) " en ${e.cuentaAtras} s" else "" })?.let { Text(it, color = Charcoal, fontFamily = Body, fontSize = 13.sp) }
                aviso?.let { Text(it, color = Acento, fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.activo) {
                    if (e.directo) {
                        EnlaceRevista("▲ Canal") { Reproductor.arriba() }
                        EnlaceRevista("▼ Canal") { Reproductor.abajo() }
                        EnlaceRevista("Canales", acento = e.lista == null) { if (e.lista == null) Reproductor.abreLista() else Reproductor.cierraLista() }
                        if (aviso != null || e.mensaje?.startsWith("Se ha cortado") == true) EnlaceRevista("Volver a conectar", acento = true) { Reproductor.reintentaYa() }
                    } else {
                        EnlaceRevista("−${AjustesReproductor.salto()} s") { Reproductor.salta(-AjustesReproductor.salto()) }
                        EnlaceRevista(if (e.reproduciendo) "Pausa" else "Seguir", acento = true) { Reproductor.alterna() }
                        EnlaceRevista("+${AjustesReproductor.salto()} s") { Reproductor.salta(AjustesReproductor.salto()) }
                    }
                    if (e.pistas.count { it.tipo == "audio" } > 1) EnlaceRevista("Audio") { eligiendo = if (eligiendo == 1) 0 else 1 }
                    if (e.pistas.any { it.tipo == "sub" }) EnlaceRevista("Subtítulos") { eligiendo = if (eligiendo == 2) 0 else 2 }
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
                    1 -> e.pistas.filter { it.tipo == "audio" }.forEach { p -> FiltroRevista(p.etiqueta, p.elegida) { Reproductor.ponAudio(p.id); eligiendo = 0 } }
                    2 -> {
                        val subs = e.pistas.filter { it.tipo == "sub" }
                        FiltroRevista("Sin subtítulos", subs.none { it.elegida }) { Reproductor.ponSubtitulos(null); eligiendo = 0 }
                        subs.forEach { p -> FiltroRevista(p.etiqueta, p.elegida) { Reproductor.ponSubtitulos(p.id); eligiendo = 0 } }
                    }
                }
            }
        }
        e.lista?.let { canales -> if (e.activo) ListaRapida(canales, e.enLista) }
    }
}

/** La lista de canales encima (aquí, en la banda; en la ventana de mpv sale la misma, con las flechas): el de ahora marcado y lo que echan. */
@Composable
private fun ListaRapida(canales: List<com.gonzalo.iptv.Canal>, actual: Int) {
    Tele.version
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = (actual - 3).coerceAtLeast(0))
    LaunchedEffect(actual) { runCatching { estado.animateScrollToItem((actual - 3).coerceAtLeast(0)) } }
    val cg = Tele.enMemoria(Reproductor.estado.listaId)
    val ahora = System.currentTimeMillis()
    Filete(1.dp, Line)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 24.dp, vertical = 6.dp), state = estado) {
        itemsIndexed(canales, key = { _, c -> c.clave }) { i, c ->
            val (a, d) = cg?.let { Tele.ahoraYDespuesRapido(it, c, ahora) } ?: (null to null)
            Row(Modifier.fillMaxWidth().pulsable { Reproductor.vaACanal(i) }.background(if (i == actual) Paper else Sand).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${if (c.numero > 0) c.numero else i + 1}", color = if (i == actual) Acento else Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 36.dp).padding(end = 8.dp))
                Logo(c.logo, c.nombre, 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(c.nombre, color = Charcoal, fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(240.dp))
                AhoraYDespues(a, d, ahora, Modifier.weight(1f))
            }
        }
    }
}
