package com.gonzalo.hiedra

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * El mando (o el teclado): todo lo que se puede tocar en la portada se puede enfocar. La cruceta mueve el foco (eso lo hace Compose solo);
 * A o Enter es «jugar» (lo mismo que un toque en una portada) y X o Y abre la ficha (lo mismo que mantener pulsado). Lo enfocado se ve:
 * crece un poco y lleva el marco del acento.
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
    return this.graphicsLayer { scaleX = escala; scaleY = escala }.marcoFoco(src, fuera = 6.dp, radio = 0.dp)
}

/** Lo que se puede tocar en la portada con el dedo y con el mando: toque = [alToque] (A = [alA], de serie lo mismo); mantener (o X/Y) = [alMantener]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.tocableMando(alToque: () -> Unit, alMantener: () -> Unit, alA: () -> Unit = alToque): Modifier {
    val src = remember { MutableInteractionSource() }
    val hf = LocalHapticFeedback.current
    return this.enfocado(src).conMando(src, alA, alMantener).focusable(interactionSource = src)
        .combinedClickable(interactionSource = src, indication = null, onClick = alToque, onLongClick = { hf.performHapticFeedback(HapticFeedbackType.LongPress); alMantener() })
}

/** Qué hace el botón grande de debajo del cartel con este juego (y qué dice). */
fun textoJugar(j: Juego, enDirecto: Boolean = false): String = when {
    enDirecto -> "Jugar en el PC"
    j.sinLanzador -> "Ver la ficha"
    j.enOtroDispositivo -> "Bajar y jugar"
    j.enPc && !j.abrible -> "Jugar en el PC"
    j.estado == Estado.PASADO -> "Volver a jugar"
    j.msJugados >= 60_000 || j.estado == Estado.JUGANDO -> "Seguir jugando"
    else -> "Empezar a jugar"
}

/**
 * La portada de Bastet, la primera página (la cabecera y las pestañas son de [PantPrincipal]), como la de Seshat: las portadas mandan.
 *  - El cartel «AHORA JUEGAS»: lo último a lo que juegas (o el que elegiste en «Editar portada», o lo que el PC ejecuta en directo), su
 *    portada grande con paspartú sobre su fondo o su color; debajo, «SEGUIR JUGANDO →» (dentro de Bastet, con el emulador, en el PC…).
 *  - Tu PC (si lo hay y no está jugando), el reto del año (juegos terminados), la semana en barras que se tocan y los días seguidos.
 *  - «También juegas» y «Pendientes» (lado a lado si hay pocos), los demás estantes que tengas puestos y el mosaico de lo pasado este año.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaginaPortada(alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAnadir: (TipoLanzador?) -> Unit, alIr: (Destino) -> Unit, alAbrirSaga: (Saga) -> Unit = {}, alAjustes: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    val atrio = remember { runCatching { ctx.packageManager.getLaunchIntentForPackage(ATRIO) }.getOrNull() }
    var editando by rememberSaveable { mutableStateOf(false) }
    val juegos = Biblioteca.juegos.toList()
    val cfg = Inicio.config
    val pc = Suite.target()
    val enDirecto = pc?.game?.let { g -> Biblioteca.porClave("pc:" + g.key) }
    val ahora = enDirecto ?: Inicio.destacado(juegos)
    fun jugarOAbrir(j: Juego) = if (j.sinLanzador) alAbrir(j) else alJugar(j)
    val activa = { e: Estante -> cfg.filas.firstOrNull { it.estante == e }?.activa != false }
    val otros = if (activa(Estante.JUGANDO)) juegos.filter { it.estado == Estado.JUGANDO && it.id != ahora?.id }.sortedByDescending { it.ultima } else emptyList()
    // Pendientes, en el orden que tengas elegido en su página; de cada saga, el siguiente que te queda
    val pendientes = remember(juegos, EspejoCuenta.version) {
        Sagas.agrupar(ordenar(OrdenGuardado.de(ctx, Estado.PENDIENTE), juegos.filter { it.estado == Estado.PENDIENTE && it.id != ahora?.id })).map { f ->
            when (f) { is FilaLista.Suelto -> f.juego to null; is FilaLista.DeSaga -> f.miembros.first() to f }
        }.take(12)
    }
    val nPendientes = juegos.count { it.estado == Estado.PENDIENTE }
    val semana = remember(juegos.map { it.id to it.msJugados to it.ultima }) { datosJuego(juegos) }
    val meta = remember(juegos, EspejoCuenta.version) { RetoJuegos.meta(ctx) }
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val pasadosAno = terminadosEn(juegos, ano)
    val tablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 960.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
        if (editando) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) { EditorInicio(juegos) { editando = false } }
        else if (juegos.isEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("Bastet es la puerta a todos tus juegos: los de consola (muchos los juega ella misma, y los demás con los emuladores que ya tienes), los de Android, los de navegador y los de tu PC.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            BotonC("ROMs y emuladores", fondo = Acento, letra = SobreAcento) { alAnadir(TipoLanzador.ROM) }
            Spacer(Modifier.height(8.dp))
            MagLink("Juegos de Android", "Los que ya tienes instalados") { alAnadir(TipoLanzador.APP) }
            Filete(1.dp, Line)
            MagLink("Juegos de navegador", "PokéRogue y otros, a pantalla completa") { alAnadir(TipoLanzador.WEB) }
            Filete(1.dp, Line)
            MagLink("Tu PC", "Abre Atrio en el PC con tu cuenta y sus juegos aparecen aquí") { alIr(Destino.PC) }
        } else {
            // ---- El cartel: lo que juegas ahora, de protagonista; debajo, el botón a todo el ancho
            if (ahora != null) {
                val kicker = when {
                    enDirecto != null -> "En directo · ${pc?.name.orEmpty()}"
                    cfg.destacado.isNotBlank() && ahora.id == cfg.destacado -> "Elegido"
                    ahora.estado == Estado.JUGANDO -> "Ahora juegas"
                    else -> "Lo último"
                } + (if (ahora.msJugados >= 60_000) " · " + horasCortas(ahora.msJugados) else "")
                val derecha = if (enDirecto != null && pc?.game != null) "desde hace ${desdeHace(pc.game.start)}" else if (ahora.ultima > 0) haceCuanto(ahora.ultima) else ""
                Cartel(ahora, kicker, derecha, directo = enDirecto != null, alJugar = { jugarOAbrir(ahora) }, alAbrir = { alAbrir(ahora) })
                if (ahora.sinLanzador) BotonC(textoJugar(ahora), fondo = Charcoal, letra = Paper) { alAbrir(ahora) }
                else BotonC(textoJugar(ahora, enDirecto != null), Modifier.testTag("seguir"), fondo = Acento, letra = SobreAcento) { alJugar(ahora) }
            }
            // ---- Tu PC (si lo hay y no está ejecutando nada: si lo está, es el cartel)
            if (enDirecto == null && pc != null) Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) { TarjetaPc(alIr = { alIr(Destino.PC) }, alAbrir = alAbrir) }

            // ---- El reto del año y la semana, justo debajo del botón
            BloqueReto(meta, pasadosAno.size, ano, alIr = { alIr(Destino.Pasado) }, alPoner = { alAjustes(1) })
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalAlignment = Alignment.Bottom) {
                Text("Esta semana", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${minutosCorto(semana.semana.sumOf { it.second })} · HOY ${minutosCorto(semana.hoyMin)}", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1)
            }
            SemanaTocable(semana, Modifier.padding(horizontal = 20.dp))
            if (semana.racha > 0) Text("${semana.racha} ${if (semana.racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS"} JUGANDO", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black,
                fontSize = 10.5.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))

            // ---- También juegas y Pendientes: con pocos (tres portadas entre los dos, como mucho), lado a lado; si no, cada uno en su fila
            val conPendientes = activa(Estante.PENDIENTE)
            val juntos = otros.isNotEmpty() && conPendientes && pendientes.isNotEmpty() && otros.size + pendientes.size <= 3
            if (juntos) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                    val n = otros.size + pendientes.size
                    val ancho = ((maxWidth.value - 18f - 10f * (n - 2)) / n).coerceIn(72f, 120f).toInt()
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column {
                            EncabezadoCorto("También juegas", "${otros.size} →") { alIr(Destino.Jugando) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { otros.forEach { j -> TapaConHoras(j, ancho, { jugarOAbrir(j) }, { alAbrir(j) }) } }
                        }
                        Column {
                            EncabezadoCorto("Pendientes", "$nPendientes →") { alIr(Destino.Pendiente) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pendientes.forEach { (j, saga) -> TapaPendiente(j, saga, ancho, { jugarOAbrir(j) }, { alAbrir(j) }) } }
                        }
                    }
                }
            } else {
                if (otros.isNotEmpty()) {
                    TituloEstante("También juegas", "${otros.size} →") { alIr(Destino.Jugando) }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        otros.take(12).forEach { j -> TapaConHoras(j, if (tablet) 150 else 120, { jugarOAbrir(j) }, { alAbrir(j) }) }
                    }
                }
                if (conPendientes) {
                    TituloEstante("Pendientes", if (nPendientes == 0) null else "$nPendientes →") { alIr(Destino.Pendiente) }
                    if (pendientes.isEmpty()) VacioEstante("No tienes nada pendiente. Lo que añadas sin empezar saldrá aquí.")
                    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pendientes.forEach { (j, saga) -> TapaPendiente(j, saga, if (tablet) 130 else 104, { jugarOAbrir(j) }, { alAbrir(j) }) }
                    }
                }
            }

            // ---- Los demás estantes que tengas puestos (en «Editar portada»), en su orden y su tamaño
            cfg.filas.filter { it.activa && it.estante !in setOf(Estante.JUGANDO, Estante.PENDIENTE) }.forEach { f ->
                val ancho = if (f.grande) (if (tablet) 150 else 120) else (if (tablet) 130 else 104)
                when (f.estante) {
                    Estante.SAGAS -> {
                        val sagas = Sagas.lista.toList().map { it to Sagas.juegos(it, juegos) }.filter { it.second.isNotEmpty() }.sortedByDescending { (_, l) -> l.maxOf { it.ultima } }.take(12)
                        if (sagas.isNotEmpty()) {
                            TituloEstante(f.estante.nombre, "${Sagas.lista.size} →") { alIr(Destino.Sagas) }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                sagas.forEach { (s, l) -> TapaSaga(s, l, ancho) { alAbrirSaga(s) } }
                            }
                        }
                    }
                    Estante.PC -> {
                        val l = Inicio.juegosDe(Estante.PC, juegos, ahora)
                        if (l.isNotEmpty() || Suite.pcs().isNotEmpty()) {
                            TituloEstante(f.estante.nombre, "Ver →") { alIr(Destino.PC) }
                            if (l.isEmpty()) VacioEstante("Los juegos del PC a los que juegues saldrán aquí. La biblioteca entera, en «Tu PC».")
                            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                l.forEach { j -> TapaConHoras(j, ancho, { alJugar(j) }, { alAbrir(j) }) }
                            }
                        }
                    }
                    else -> {
                        val l = Inicio.juegosDe(f.estante, juegos, ahora)
                        val destino = if (f.estante == Estante.PASADO) Destino.Pasado else null
                        if (l.isNotEmpty()) {
                            TituloEstante(f.estante.nombre, if (destino != null) "Ver →" else null) { destino?.let(alIr) }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                l.forEach { j -> TapaConHoras(j, ancho, { jugarOAbrir(j) }, { alAbrir(j) }) }
                            }
                        } else if (f.estante == Estante.FIJADOS) {
                            TituloEstante(f.estante.nombre, null) {}
                            VacioEstante("Elige tus juegos con «Editar portada», aquí abajo.")
                        }
                    }
                }
            }

            // ---- Tu año: el mosaico de lo pasado (el reto, con su barra, ya va arriba)
            TituloEstante("Tu $ano · ${pasadosAno.size} ${if (pasadosAno.size == 1) "pasado" else "pasados"}", "Pasados →") { alIr(Destino.Pasado) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                if (pasadosAno.isEmpty()) Text("Aún no has terminado ninguno este año. Cuando pases uno (márcalo «Pasado» en su ficha), su portada saldrá aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp)
                else MosaicoPasados(pasadosAno, alAbrir) { alIr(Destino.Pasado) }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            if (juegos.isNotEmpty() && !editando) {
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista("Editar portada →", acento = true) { editando = true }
                    Spacer(Modifier.weight(1f))
                }
                Text("Un toque en una portada juega · mantenla pulsada para ver la ficha · con mando: A juega, X abre la ficha", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (atrio != null) {
                Spacer(Modifier.height(10.dp))
                Filete(1.dp, Line)
                MagLink("Abrir Jonsu", "Valora lo que acabas de jugar") { ctx.startActivity(atrio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Text("Bastet · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
            // La versión instalada, debajo (para no tener que entrar en Ajustes a mirarla)
            Text("versión $VERSION", color = Muted.copy(alpha = 0.7f), fontFamily = Body, fontSize = 10.sp, letterSpacing = 0.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
    }
}

/** El título de un estante de la portada: el nombre en grotesca y, a la derecha y en el acento, «N →» (lleva a su página). */
@Composable
private fun TituloEstante(titulo: String, enlace: String?, alIr: () -> Unit) {
    Row(Modifier.fillMaxWidth().tocable(enabled = enlace != null, onClick = alIr).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 24.sp, maxLines = 2, modifier = Modifier.weight(1f))
        if (enlace != null) Text(enlace.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp, bottom = 3.dp))
    }
}

@Composable
private fun VacioEstante(texto: String) {
    Text(texto, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 20.dp))
}

/** El título pequeño de un estante cuando van dos lado a lado. */
@Composable
private fun EncabezadoCorto(titulo: String, enlace: String, alIr: () -> Unit) {
    Row(Modifier.tocable(onClick = alIr).padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
        Text("  $enlace", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** Una tapa de juego en la portada (2:3, recta), que se toca y se enfoca con el mando. */
@Composable
private fun Tapa(j: Juego, ancho: Int, alJugar: () -> Unit, alAbrir: () -> Unit) {
    Box(Modifier.width(ancho.dp).height((ancho * 1.5f).dp).tocableMando(alJugar, alAbrir).testTag("loseta-" + j.id)) {
        PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = (ancho * 0.4f).toInt())
    }
}

/** Una tapa de «También juegas»: su barra de la historia (si se sabe lo que dura) y sus horas. */
@Composable
private fun TapaConHoras(j: Juego, ancho: Int, alJugar: () -> Unit, alAbrir: () -> Unit) {
    Column(Modifier.width(ancho.dp)) {
        Tapa(j, ancho, alJugar, alAbrir)
        j.avance?.let { av -> Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(av.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) } }
        Text(if (j.estado == Estado.PASADO && j.finalizado > 0) "PASADO" else horasCortas(j.msJugados), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Una tapa de «Pendientes»: lo que suele durar o, de una saga, cuántos más quedan. */
@Composable
private fun TapaPendiente(j: Juego, saga: FilaLista.DeSaga?, ancho: Int, alJugar: () -> Unit, alAbrir: () -> Unit) {
    Column(Modifier.width(ancho.dp)) {
        Tapa(j, ancho, alJugar, alAbrir)
        val pie = when { saga != null && saga.miembros.size > 1 -> "+${saga.miembros.size - 1} DE LA SAGA"; j.durHistoria > 0 -> "~" + tiempoDe(j.durHistoria * 60_000L).uppercase(); else -> "" }
        if (pie.isNotBlank()) Text(pie, color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.4.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Una saga en la portada: su abanico de portadas y su nombre. */
@Composable
private fun TapaSaga(s: Saga, juegos: List<Juego>, ancho: Int, alAbrir: () -> Unit) {
    Column(Modifier.tocableMando(alAbrir, alAbrir)) {
        AbanicoSaga(juegos, ancho = (ancho * 0.8f).dp)
        Text(s.nombre, color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((ancho * 1.1f).dp).padding(top = 6.dp))
        Text(resumenSaga(juegos).uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((ancho * 1.1f).dp))
    }
}

/** El mosaico de lo pasado este año: 4 por fila, como mucho 3 filas; si hay más, la última casilla dice «+N» y lleva a Pasados. */
@Composable
private fun MosaicoPasados(juegos: List<Juego>, alAbrir: (Juego) -> Unit, alVerTodos: () -> Unit) {
    val col = 4
    val max = col * 3
    val sobran = (juegos.size - max).coerceAtLeast(0)
    val vistos = if (sobran > 0) juegos.take(max - 1) else juegos
    val casillas: List<Juego?> = vistos + if (sobran > 0) listOf(null) else emptyList()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        casillas.chunked(col).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { j ->
                    Box(Modifier.weight(1f).aspectRatio(2f / 3f)) {
                        if (j != null) Box(Modifier.fillMaxSize().tocableMando({ alAbrir(j) }, { alAbrir(j) })) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 28) }
                        else Box(Modifier.fillMaxSize().background(Charcoal).tocable(onClick = alVerTodos), contentAlignment = Alignment.Center) {
                            Text("+${sobran + 1}", color = Paper, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
                        }
                    }
                }
                // (huecos vacíos al final de la última fila: la rejilla no se descuadra)
                repeat(col - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * El cartel de la portada: el juego de ahora, como un cartel. Su portada grande (con paspartú) sobre su fondo, si le pusiste uno (la
 * portada, arriba a la izquierda), o sobre su color (el medio de la imagen o el de su tela), oscurecido abajo; encima, abajo, el antetítulo
 * («AHORA JUEGAS · 12 H 30»), el título en grotesca, la barra de la historia (si se sabe lo que dura) y de dónde es · cuándo jugaste.
 * Un toque (o X con el mando) abre la ficha; A juega. (Sin «mantener pulsado»: al deslizar despacio sobre el cartel, la cabecera se
 * recoge y el dedo no se mueve respecto a él, y se tomaba por una pulsación larga que abría la ficha.)
 */
@Composable
private fun Cartel(j: Juego, kicker: String, derecha: String, directo: Boolean, alJugar: () -> Unit, alAbrir: () -> Unit) {
    val base = remember(j.portada, j.titulo) { colorDePortada(j.portada) ?: telaDe(j.titulo) }
    val claro = lerpColor(base, Color.White, 0.12f)
    val oscuro = lerpColor(base, Color.Black, 0.72f)
    val acentoClaro = acentoLegible(AcentoBloque, Color(0xFF050404), Color.White, 4.5f)
    // La portada (con su paspartú) va entera en el hueco de arriba y acaba antes del texto: el oscurecido solo queda detrás del texto
    Box(Modifier.fillMaxWidth().height(ALTO_CARTEL).background(Brush.verticalGradient(listOf(claro, oscuro))).conMando(remember { MutableInteractionSource() }, alJugar, alAbrir).tocable(onClick = alAbrir).testTag("cartel")) {
        val conFondo = j.fondo.isNotBlank()
        if (conFondo) {
            // Con fondo elegido: el fondo ocupa el cartel (recortado) y la portada va encima, grande, arriba a la izquierda
            ImagenEncuadrada(j.fondo, j.encFondo, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color(0xCC050404), 1f to Color(0xF2050404))))
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = if (conFondo) 20.dp else 16.dp, bottom = 14.dp, start = 20.dp, end = 20.dp),
                contentAlignment = if (conFondo) Alignment.TopStart else Alignment.TopCenter) {
                val tope = if (conFondo) 225.dp else 330.dp
                PortadaSobreFondo(j, Modifier.heightIn(max = tope).fillMaxHeight().aspectRatio(2f / 3f, matchHeightConstraintsFirst = true), sombra = 22.dp)
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (directo) { PuntoDirecto(); Spacer(Modifier.width(8.dp)) }
                    Text(kicker.uppercase(), color = acentoClaro, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(j.titulo.uppercase(), color = Color.White, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
                j.avance?.let { av -> Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.22f))) { Box(Modifier.fillMaxWidth(av).fillMaxHeight().background(acentoClaro)) } }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(plataformaDe(j).uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val der = listOfNotNull(j.avance?.let { "${(it * 100).toInt()} % de la historia" }, derecha.ifBlank { null }).joinToString(" · ")
                    if (der.isNotBlank()) Text(der.uppercase(), color = Color.White.copy(alpha = 0.85f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

/** El reto del año: «Reto 2026 · 5 / 20», su barra y cómo vas. Sin reto, una invitación a ponértelo (en Ajustes → Juegos). */
@Composable
private fun BloqueReto(meta: Int, hechos: Int, ano: Int, alIr: () -> Unit, alPoner: () -> Unit) {
    Column(Modifier.fillMaxWidth().tocable(onClick = if (meta > 0) alIr else alPoner).testTag("reto").padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Reto $ano", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (meta > 0) {
                Text("$hechos", color = Charcoal, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(" / $meta", color = Muted, fontFamily = Grotesca, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        if (meta > 0) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(9.dp).background(Line)) {
                Box(Modifier.fillMaxWidth((hechos.toFloat() / meta).coerceIn(0f, 1f)).fillMaxHeight().background(Acento))
            }
            Text(ritmoReto(meta, hechos).texto(), color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.5.sp, letterSpacing = 1.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp))
        } else {
            Text("$hechos ${if (hechos == 1) "juego terminado" else "juegos terminados"} este año. Ponte un reto y verás cómo vas.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic,
                fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
            Text("PONERTE UN RETO →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** «Editar» la portada: el destacado, los estantes (a la vista, orden, tamaño) y los juegos de «Mis elegidos». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorInicio(juegos: List<Juego>, alListo: () -> Unit) {
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
