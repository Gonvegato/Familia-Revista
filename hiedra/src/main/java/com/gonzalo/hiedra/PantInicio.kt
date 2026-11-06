package com.gonzalo.hiedra

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Ordena los juegos: los últimos que jugaste primero y, si no, los últimos que añadiste. */
fun recientes(l: List<Juego>) = l.sortedWith(compareByDescending<Juego> { it.ultima }.thenByDescending { it.anadido })

const val ATRIO = "com.gonzalo.gamerate"

/** Una frase como «Juegos · GBA · 3 h 20 min». */
fun metaDe(j: Juego): String = listOf(
    when (j.tipo) { TipoLanzador.ROM -> Catalogo.sistema(j.sistema)?.nombre ?: j.sistema; TipoLanzador.APP -> "Android"; TipoLanzador.WEB -> "Web"; TipoLanzador.PC -> "PC"; null -> j.plataforma.ifBlank { "Ficha" } },
    if (j.msJugados >= 60_000) tiempoDe(j.msJugados) else ""
).filter { it.isNotBlank() }.joinToString(" · ")

/** Lo de debajo del nombre en una portada: «GBA · 3 h». */
fun metaCorta(j: Juego): String {
    val h = if (j.estado == Estado.PASADO) "pasado" else if (j.msJugados >= 60_000) tiempoDe(j.msJugados) else ""
    return listOf(etiquetaDe(j), if (j.enOtroDispositivo) "en otro móvil" else h).filter { it.isNotBlank() }.joinToString(" · ")
}

/*
 * El mando (o el teclado): todo lo que se puede tocar en la portada se puede enfocar. La cruceta mueve el foco entre estantes y portadas (eso lo
 * hace Compose solo); A o Enter es «jugar» (lo mismo que un toque) y X o Y abre la ficha (lo mismo que mantener pulsado). Lo enfocado se ve: un
 * filete de acento debajo y un poco más grande.
 */
@Composable
fun Modifier.conMando(src: MutableInteractionSource, alA: () -> Unit, alX: () -> Unit): Modifier = this.onKeyEvent { e ->
    if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
    when (e.key) {
        Key.ButtonA -> { alA(); true }
        Key.ButtonX, Key.ButtonY, Key.Menu -> { alX(); true }
        else -> false
    }
}

@Composable
private fun Modifier.enfocado(src: MutableInteractionSource): Modifier {
    val foco by src.collectIsFocusedAsState()
    val escala by animateFloatAsState(if (foco) 1.05f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "foco")
    // Lo enfocado crece un poco (con un rebote corto) y lleva el marco del acento, como todo lo demás que se toca con el mando
    return this.graphicsLayer { scaleX = escala; scaleY = escala }.marcoFoco(src, fuera = 6.dp, radio = 8.dp)
}

/**
 * La portada de un juego en un estante: la imagen con el marco de la revista y, debajo, el nombre y de dónde es. Toque = jugar; mantener = ficha.
 * Siempre mide lo mismo (el nombre ocupa dos líneas aunque quepa en una): así el estante no crece ni encoge al deslizarlo y lo de debajo no se mueve.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Loseta(j: Juego, ancho: Dp, alJugar: () -> Unit, alAbrir: () -> Unit, modifier: Modifier = Modifier, kicker: String = "") {
    val hf = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    Column(modifier.width(ancho).enfocado(src).conMando(src, alJugar, alAbrir).focusable(interactionSource = src)
        .combinedClickable(interactionSource = src, indication = null, onClick = alJugar, onLongClick = { hf.performHapticFeedback(HapticFeedbackType.LongPress); alAbrir() })
        .testTag("loseta-" + j.id).padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp))) {
            PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = if (ancho >= 140.dp) 44 else 30)
        }
        if (kicker.isNotBlank()) Text(kicker.uppercase(), color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = if (ancho >= 140.dp) 15.sp else 13.sp, lineHeight = if (ancho >= 140.dp) 17.sp else 15.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (kicker.isBlank()) 6.dp else 1.dp))
        Text(metaCorta(j).uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una saga en un estante: sus portadas en abanico y su nombre. Toque = su página. */
@Composable
private fun LosetaSaga(s: Saga, juegos: List<Juego>, ancho: Dp, alAbrir: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val v = juegos.take(3)
    Column(Modifier.width(ancho).enfocado(src).conMando(src, alAbrir, alAbrir).focusable(interactionSource = src).clickable(interactionSource = src, indication = null, onClick = alAbrir).padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA)) {
            v.asReversed().forEachIndexed { i, j ->
                val k = v.size - 1 - i
                Box(Modifier.padding(start = (ancho * 0.16f) * k, top = (ancho * 0.08f) * (v.size - 1 - k)).fillMaxWidth(if (v.size == 1) 1f else 0.7f).aspectRatio(PROPORCION_PORTADA).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp))) {
                    PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 26)
                }
            }
        }
        Text("Saga".uppercase(), color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
        Text(s.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
        Text(resumenSaga(juegos).uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * El destacado: el juego de ahora (o el que fijaste), con su arte a sangre dentro del marco de la revista y, encima del papel que lo funde por
 * abajo, el antetítulo, el titular y las dos acciones. Si el PC está ejecutando algo, es eso, en directo.
 *
 * En la tablet ([ancho]) es una doble página: la caja del juego a la izquierda, en grande, y a la derecha el titular, lo jugado y «Jugar»
 * como botón; detrás, el mismo arte, muy suave, de fondo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Destacado(j: Juego, kicker: String, meta: String, alJugar: () -> Unit, alAbrir: () -> Unit, directo: Boolean, ancho: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val hf = LocalHapticFeedback.current
    val textoJugar = if (j.enPc) "Jugar en el PC" else if (j.sinLanzador) "Ficha" else "Jugar"
    val sistema = remember(j.id, j.sistema) { Catalogo.sistema(j.sistema)?.nombre }
    if (ancho) {
        Row(Modifier.fillMaxWidth().height(340.dp).testTag("destacado").clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp))) {
            Box(Modifier.fillMaxHeight().aspectRatio(PROPORCION_PORTADA).enfocado(src).conMando(src, alJugar, alAbrir).focusable(interactionSource = src)
                .combinedClickable(interactionSource = src, indication = null, onClick = alJugar, onLongClick = { hf.performHapticFeedback(HapticFeedbackType.LongPress); alAbrir() })) {
                PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 120)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // El mismo arte, muy suave, detrás del texto (como el fondo de una doble página)
                PortadaArco(j, Modifier.matchParentSize().graphicsLayer { alpha = 0.16f }, titulo = false, letra = false, relleno = true, tamLetra = 10)
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Paper.copy(alpha = 0.55f), 1f to Paper)))
                Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 26.dp), verticalArrangement = Arrangement.Bottom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (directo) { PuntoDirecto(); Spacer(Modifier.width(8.dp)) }
                        Text(kicker.uppercase(), color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    }
                    FitTitle(j.titulo, 58.sp, 60.sp, letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    j.avance?.let { av -> Box(Modifier.padding(top = 14.dp).widthIn(max = 360.dp)) { Barra(av) } }
                    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccionGrande(if (j.sinLanzador) null else androidx.compose.material.icons.Icons.Rounded.PlayArrow, textoJugar, Modifier.widthIn(min = 200.dp), alHacer = alJugar)
                        if (!j.sinLanzador) EnlaceRevista("Ficha →", onClick = alAbrir)
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().testTag("destacado").enfocado(src).conMando(src, alJugar, alAbrir).focusable(interactionSource = src)
        .combinedClickable(interactionSource = src, indication = null, onClick = alJugar, onLongClick = { hf.performHapticFeedback(HapticFeedbackType.LongPress); alAbrir() })) {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp))) {
            PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 120)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.30f to androidx.compose.ui.graphics.Color.Transparent, 0.78f to Paper.copy(alpha = 0.86f), 1f to Paper)))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (directo) { PuntoDirecto(); Spacer(Modifier.width(8.dp)) }
                    Text(kicker.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1)
                    if (sistema != null && !directo) Text("  ·  " + sistema.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FitTitle(j.titulo, 36.sp, 38.sp, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                j.avance?.let { av -> Box(Modifier.padding(top = 10.dp)) { Barra(av) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            EnlaceRevista("$textoJugar →", acento = true, onClick = alJugar)
            if (!j.sinLanzador) EnlaceRevista("Ficha →", onClick = alAbrir)
        }
    }
}

/** Un estante: su antetítulo, «Ver todo →» y la fila de portadas. */
@Composable
private fun EstanteFila(titulo: String, verTodo: (() -> Unit)?, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Filete(1.dp)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Antetitulo(titulo) }
            if (verTodo != null) Text("VER TODO →", color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.tocable(onClick = verTodo).padding(4.dp))
        }
        contenido()
    }
}

/**
 * La portada de Hiedra: cabecera de revista, las barras de lo jugado, el menú de secciones, lo que el PC ejecuta en directo (si hay PC), el
 * destacado y los estantes que tengas. «Editar» elige el destacado, los estantes (orden, tamaño, a la vista) y los juegos de «Mis elegidos».
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PantInicio(alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAnadir: (TipoLanzador?) -> Unit, alIr: (Destino) -> Unit, alBuscar: () -> Unit, alAbrirSaga: (Saga) -> Unit = {}) {
    val ctx = LocalContext.current
    val hoy = remember { Calendar.getInstance() }
    val atrio = remember { runCatching { ctx.packageManager.getLaunchIntentForPackage(ATRIO) }.getOrNull() }
    var editando by rememberSaveable { mutableStateOf(false) }
    val juegos = Biblioteca.juegos.toList()
    val cfg = Inicio.config
    val pc = Suite.target()
    val enDirecto = pc?.game?.let { g -> Biblioteca.porClave("pc:" + g.key) }
    val destacado = enDirecto ?: Inicio.destacado(juegos)

    val secciones = listOf<Pair<String, () -> Unit>>(
        "Jugando" to { alIr(Destino.Jugando) }, "Pendientes" to { alIr(Destino.Pendiente) }, "Pasados" to { alIr(Destino.Pasado) },
        "Abandonados" to { alIr(Destino.Abandonado) }, "Sagas" to { alIr(Destino.Sagas) }, "Tu PC" to { alIr(Destino.PC) }
    )

    // La cabecera y el menú, exactamente como en la portada de Atrio (RevistaHome): mismos márgenes, mismo tamaño de cabecera, de filetes y de letra
    val desplazado = rememberScrollState()
    val umbral = with(androidx.compose.ui.platform.LocalDensity.current) { 300.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
    val anchoPantalla = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    // La tablet (la OPPO Pad Mini: 840 dp de pie, 1260 tumbada): más margen, el destacado en doble página y portadas más grandes
    val tablet = anchoPantalla >= 700
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(desplazado).statusBarsPadding().padding(horizontal = if (tablet) 44.dp else 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            EnlaceRevista("Buscar") { alBuscar() }
            EnlaceRevista("+ Añadir", Modifier.padding(start = 14.dp), acento = true) { alAnadir(null) }
            EnlaceRevista(if (editando) "Listo" else "Editar", Modifier.padding(start = 14.dp), acento = editando) { editando = !editando }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Settings, "Ajustes", tint = Charcoal, modifier = Modifier.padding(start = 6.dp).tocable { alIr(Destino.Ajustes) }.padding(8.dp).size(22.dp))
        }
        val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
        // La cabecera, a lo ancho de la página (en la tablet tumbada, enorme: como la de una revista de verdad)
        Cabecera(if (ancho >= 1000) 200.sp else if (ancho >= 700) 150.sp else 92.sp, Modifier.padding(top = 8.dp))
        FileteCabecera(3.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Nº ${hoy.get(Calendar.DAY_OF_YEAR)}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")).format(hoy.time).uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(1.dp)
        BarrasJuego(juegos)
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Filete(1.dp)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                secciones.forEach { (t, f) ->
                    Text(t.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1, modifier = Modifier.tocable(onClick = f).padding(vertical = 9.dp))
                }
            }
            Filete(1.dp)
        }
        Spacer(Modifier.height(18.dp))

        if (editando) EditorInicio(juegos) { editando = false }
        else if (juegos.isEmpty()) {
            Text("Hiedra es la puerta a todos tus juegos: los de consola (muchos los juega ella misma, y los demás con los emuladores que ya tienes), los de Android, los de navegador y los de tu PC.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(6.dp))
            MagLink("ROMs y emuladores", "GBA, SNES, Mega Drive, DS, PlayStation… en la propia Hiedra o con el emulador que elijas", accent = true) { alAnadir(TipoLanzador.ROM) }
            Filete(1.dp, Line)
            MagLink("Juegos de Android", "Los que ya tienes instalados") { alAnadir(TipoLanzador.APP) }
            Filete(1.dp, Line)
            MagLink("Juegos de navegador", "PokéRogue y otros, a pantalla completa") { alAnadir(TipoLanzador.WEB) }
            Filete(1.dp, Line)
            MagLink("Tu PC", "Abre Atrio en el PC con tu cuenta y sus juegos aparecen aquí") { alIr(Destino.PC) }
        } else {
            // El PC, si lo hay y no está ejecutando nada (si está jugando, es el destacado)
            if (enDirecto == null && pc != null) { TarjetaPc(alIr = { alIr(Destino.PC) }, alAbrir = alAbrir); Spacer(Modifier.height(4.dp)) }
            if (destacado != null) {
                val kicker = when {
                    enDirecto != null -> "En directo · ${pc?.name}"
                    cfg.destacado.isNotBlank() -> "Elegido"
                    destacado.estado == Estado.JUGANDO -> "Ahora"
                    else -> "Último"
                }
                val meta = if (enDirecto != null && pc?.game != null) "Jugando desde hace ${desdeHace(pc.game.start)}" + (if (destacado.msJugados >= 60_000) " · ${tiempoDe(destacado.msJugados)} en total" else "") else metaDe(destacado)
                Destacado(destacado, kicker, meta, alJugar = { if (destacado.sinLanzador) alAbrir(destacado) else alJugar(destacado) }, alAbrir = { alAbrir(destacado) }, directo = enDirecto != null, ancho = tablet)
            }
            cfg.filas.filter { it.activa }.forEach { f ->
                val ancho = if (tablet) (if (f.grande) 190.dp else 132.dp) else if (f.grande) 150.dp else 104.dp
                when (f.estante) {
                    Estante.SAGAS -> {
                        val sagas = Sagas.lista.toList().map { it to Sagas.juegos(it, juegos) }.filter { it.second.isNotEmpty() }.sortedByDescending { (_, l) -> l.maxOf { it.ultima } }.take(12)
                        if (sagas.isNotEmpty()) EstanteFila(f.estante.nombre, { alIr(Destino.Sagas) }) {
                            LazyRow(Modifier.sangrado(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(sagas, key = { it.first.id }) { (s, l) -> LosetaSaga(s, l, ancho) { alAbrirSaga(s) } } }
                        }
                    }
                    Estante.PC -> {
                        val l = Inicio.juegosDe(Estante.PC, juegos, destacado)
                        if (l.isNotEmpty() || Suite.pcs().isNotEmpty()) EstanteFila(f.estante.nombre, { alIr(Destino.PC) }) {
                            if (l.isEmpty()) Text("Los juegos del PC a los que juegues saldrán aquí. La biblioteca entera, en «Tu PC».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                            else LazyRow(Modifier.sangrado(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(l, key = { it.id }) { j -> Loseta(j, ancho, { alJugar(j) }, { alAbrir(j) }) } }
                        }
                    }
                    else -> {
                        val l = Inicio.juegosDe(f.estante, juegos, destacado)
                        val destino = when (f.estante) { Estante.JUGANDO -> Destino.Jugando; Estante.PENDIENTE -> Destino.Pendiente; Estante.PASADO -> Destino.Pasado; else -> null }
                        if (l.isNotEmpty()) EstanteFila(f.estante.nombre, destino?.let { d -> { alIr(d) } }) {
                            LazyRow(Modifier.sangrado(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(l, key = { it.id }) { j -> Loseta(j, ancho, { if (j.sinLanzador) alAbrir(j) else alJugar(j) }, { alAbrir(j) }) } }
                        } else if (f.estante == Estante.FIJADOS) EstanteFila(f.estante.nombre, null) {
                            Text("Elige tus juegos con «Editar».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Filete(1.dp)
            Text("Un toque juega · mantén pulsado para ver la ficha · con mando: A juega, X abre la ficha", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            if (atrio != null) {
                Spacer(Modifier.height(10.dp))
                Filete(1.dp, Line)
                MagLink("Abrir Azabache", "Valora lo que acabas de jugar") { ctx.startActivity(atrio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Text("Hiedra · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
            // La versión instalada, debajo (para no tener que entrar en Ajustes a mirarla)
            Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
    // Al bajar, la cabecera se pliega: arriba solo quedan el nombre y lo de siempre (Buscar · + Añadir…)
    // (derivedStateOf: la portada entera no se vuelve a componer a cada píxel que se desliza, solo cuando la barra aparece o se va)
    val plegada by remember(umbral) { derivedStateOf { desplazado.value > umbral } }
    BarraPlegada("Hiedra", plegada) { EnlaceRevista("Buscar") { alBuscar() }; EnlaceRevista("+ Añadir", Modifier.padding(start = 14.dp), acento = true) { alAnadir(null) } }
    }
}

/** «Editar» la portada: el destacado, los estantes (a la vista, orden, tamaño) y los juegos de «Mis elegidos». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorInicio(juegos: List<Juego>, alListo: () -> Unit) {
    val cfg = Inicio.config
    var elegirDestacado by remember { mutableStateOf(false) }
    var elegirFijados by remember { mutableStateOf(false) }
    fun mover(i: Int, d: Int) { val l = cfg.filas.toMutableList(); val j = i + d; if (j !in l.indices) return; l[i] = l[j].also { l[j] = l[i] }; Inicio.cambiar { it.copy(filas = l) } }
    Column(Modifier.fillMaxWidth()) {
        Antetitulo("Destacado", Modifier.padding(bottom = 4.dp))
        val fijo = cfg.destacado.takeIf { it.isNotBlank() }?.let { k -> juegos.firstOrNull { it.id == k } }
        Text(fijo?.titulo ?: "Automático: lo que estés jugando (o el PC, en directo)", color = Charcoal, fontFamily = Display, fontSize = 18.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EnlaceRevista("Elegir un juego →", acento = true) { elegirDestacado = true }
            if (fijo != null || cfg.destacado.isNotBlank()) EnlaceRevista("Automático →") { Inicio.cambiar { it.copy(destacado = "") } }
        }
        Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Estantes", Modifier.padding(bottom = 4.dp))
        Cuerpo("Cuáles se ven, en qué orden y con portadas grandes o pequeñas.", tam = 12)
        cfg.filas.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.estante.nombre, color = if (f.activa) Charcoal else Muted, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                    Text(if (f.grande) "PORTADAS GRANDES" else "PORTADAS PEQUEÑAS", color = Acento, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                        modifier = Modifier.tocable { Inicio.cambiar { c -> c.copy(filas = c.filas.mapIndexed { k, x -> if (k == i) x.copy(grande = !x.grande) else x }) } }.padding(vertical = 2.dp))
                }
                Text("▲", color = if (i > 0) Charcoal else Line, fontSize = 16.sp, modifier = Modifier.tocable(enabled = i > 0) { mover(i, -1) }.padding(8.dp))
                Text("▼", color = if (i < cfg.filas.size - 1) Charcoal else Line, fontSize = 16.sp, modifier = Modifier.tocable(enabled = i < cfg.filas.size - 1) { mover(i, 1) }.padding(8.dp))
                Interruptor(f.activa) { on -> Inicio.cambiar { c -> c.copy(filas = c.filas.mapIndexed { k, x -> if (k == i) x.copy(activa = on) else x }) } }
            }
            Filete(1.dp, Line)
        }
        Spacer(Modifier.height(14.dp)); Filete(2.dp); Spacer(Modifier.height(10.dp))
        Antetitulo("Mis elegidos", Modifier.padding(bottom = 4.dp))
        val fijados = cfg.fijados.mapNotNull { k -> juegos.firstOrNull { it.id == k }?.let { k to it } }
        if (fijados.isEmpty()) Cuerpo("Los juegos que quieras tener siempre en la portada, en el orden que quieras.", tam = 12)
        fijados.forEachIndexed { i, (k, j) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.width(26.dp))
                Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("▲", color = if (i > 0) Charcoal else Line, fontSize = 16.sp, modifier = Modifier.tocable(enabled = i > 0) { Inicio.cambiar { c -> val l = c.fijados.toMutableList(); l[i] = l[i - 1].also { l[i - 1] = l[i] }; c.copy(fijados = l) } }.padding(8.dp))
                Text("▼", color = if (i < fijados.size - 1) Charcoal else Line, fontSize = 16.sp, modifier = Modifier.tocable(enabled = i < fijados.size - 1) { Inicio.cambiar { c -> val l = c.fijados.toMutableList(); l[i] = l[i + 1].also { l[i + 1] = l[i] }; c.copy(fijados = l) } }.padding(8.dp))
                Text("✕", color = Acento, fontSize = 16.sp, modifier = Modifier.tocable { Inicio.cambiar { c -> c.copy(fijados = c.fijados - k) } }.padding(8.dp))
            }
            Filete(1.dp, Line)
        }
        EnlaceRevista("+ Añadir juegos →", acento = true) { elegirFijados = true }
        Spacer(Modifier.height(14.dp)); Filete(2.dp)
        MagLink("Listo", accent = true, onClick = alListo)
    }
    if (elegirDestacado) SelectorJuegos("Destacado", juegos, { elegirDestacado = false }, uno = true) { k -> elegirDestacado = false; k.firstOrNull()?.let { c -> Inicio.cambiar { it.copy(destacado = c) } } }
    if (elegirFijados) SelectorJuegos("Mis elegidos", juegos.filter { it.id !in cfg.fijados }, { elegirFijados = false }) { ks -> elegirFijados = false; Inicio.cambiar { it.copy(fijados = it.fijados + ks) } }
}
