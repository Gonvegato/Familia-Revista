package com.gonzalo.mau.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzalo.mau.Animador
import com.gonzalo.mau.BAJADA_ESCONDER
import com.gonzalo.mau.Borde
import com.gonzalo.mau.Cerebro
import com.gonzalo.mau.DURA_DORMIRSE
import com.gonzalo.mau.FUNDIDO_DORMIR
import com.gonzalo.mau.Frases
import com.gonzalo.mau.Pose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val TINTA = Color(0xFF050404)
private val CREMA = Color(0xFFF8F0D4)
private val PAPEL = Color(0xFFFFFDF5)
private val ESCONDERSE = CubicBezierEasing(.5f, 0f, .9f, .4f)
private val SUBIR = CubicBezierEasing(.2f, .9f, .25f, 1.08f)

/**
 * Mau encima de la app: se pone sobre todo lo demás (lo que no es Mau deja pasar los toques). Una por ventana o actividad, con su
 * [clave] (la de [Mau.empuja] / [Mau.base]).
 *
 *  - Tocarlo: el menú (las apps de la suite y lo que se le puede pedir). Dos toques: lo acaricias. Arrastrarlo: lo mueves.
 *  - Escondido: solo las orejas, en el borde que quieras (se arrastran); tocarlas lo trae de vuelta.
 *
 * [activa]: si esta ventana es la que se ve (en Android, la actividad de delante): solo la activa hace pasar el tiempo de Mau.
 * [margenFijo]: lo que ocupa abajo la app siempre (una barra de pestañas), para no taparlo; las pantallas piden más con
 * [Mau.ponMargen] (MauMargenAbajo). [atras]: el gesto de atrás de Android (cierra
 * el menú); en el ordenador, nada (cierra Esc o pulsar fuera).
 */
@Composable
fun MauCapa(
    clave: Any,
    activa: Boolean = true,
    margenFijo: Dp = 0.dp,
    atras: @Composable (activo: Boolean, alVolver: () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (!Mau.iniciado) return
    val casa = Mau.casa
    val c = Mau.cerebro
    @Suppress("UNUSED_VARIABLE") val v = Mau.version
    val lugar = Mau.lugarDe(clave)
    val acento = Mau.acento
    // (una pantalla de esta ventana pide que no salga: un juego a pantalla completa, la sala de la tele)
    if (Mau.fueraDe(clave)) return
    // Lo que ocupa abajo la app: lo fijo de la ventana o lo que pida ahora una pantalla (la banda del reproductor)
    val margenAbajo = maxOf(margenFijo, Mau.margenDe(clave).dp)
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf(false) }
    var ajustes by remember { mutableStateOf(false) }
    // 0: sentado en su sitio; 1: hundido en el borde (escondido). Entre medias: escondiéndose o volviendo.
    val bajada = remember { Animatable(if (c.escondido) 1f else 0f) }
    var enTransicion by remember { mutableStateOf(false) }
    // (al esconderse, las orejas ya están en su sitio: no hay que asomarse)
    var sinAsomar by remember { mutableStateOf(false) }
    val anima = activa && c.mueve && !casa.sinAnimaciones

    // El tiempo de Mau (dormirse, el bocadillo, el sprint) solo pasa en la ventana que se ve
    var segundo by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activa) {
        if (!activa) return@LaunchedEffect
        while (true) {
            c.tick(Mau.ahora(), Mau.lugarDe(clave))
            segundo = Mau.ahora() / 1000
            delay(250)
        }
    }

    // Llegó desde otra app: sube desde el borde
    LaunchedEffect(activa, Mau.llega) {
        if (!activa || !Mau.llega) return@LaunchedEffect
        Mau.llega = false
        if (c.escondido) return@LaunchedEffect
        enTransicion = true
        bajada.snapTo(1f)
        if (anima) bajada.animateTo(0f, tween(520, easing = SUBIR)) else bajada.snapTo(0f)
        enTransicion = false
        c.di(c.elige(Frases.llegar), Mau.ahora(), espontaneo = false)
    }

    fun esconder() {
        menu = false; ajustes = false
        scope.launch {
            enTransicion = true
            if (anima) bajada.animateTo(1f, tween(320, easing = ESCONDERSE)) else bajada.snapTo(1f)
            c.ponBorde(Borde.ABAJO, c.sitio)
            sinAsomar = true
            c.esconde()
            enTransicion = false
        }
    }
    fun volver() {
        scope.launch {
            enTransicion = true
            c.vuelve(Mau.ahora())
            bajada.snapTo(1f)
            if (anima) bajada.animateTo(0f, tween(520, easing = SUBIR)) else bajada.snapTo(0f)
            enTransicion = false
            sinAsomar = false
            c.di(c.elige(Frases.volver), Mau.ahora(), espontaneo = false)
        }
    }
    fun acariciar() { menu = false; c.acaricia(Mau.ahora()); casa.ronronea() }

    atras(menu || ajustes) { if (ajustes) ajustes = false else menu = false }

    val anchoMau = Mau.anchoDp.dp
    val altoMau = anchoMau * 1.1f
    val altoOrejas = anchoMau * 0.4f

    Box(modifier.fillMaxSize()) {
        // Con el menú abierto: la pantalla se oscurece (en el móvil) y pulsar fuera lo cierra
        if (menu || ajustes) Box(
            Modifier.fillMaxSize()
                .background(if (casa.ordenador && !ajustes) Color.Transparent else TINTA.copy(alpha = .66f))
                .clickable(remember { MutableInteractionSource() }, null) { menu = false; ajustes = false }
                .semantics { contentDescription = "Cerrar el menú de Mau" }
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val W = maxWidth; val H = maxHeight
            val escondidoQuieto = c.escondido && !enTransicion

            if (!escondidoQuieto) {
                // ---- Mau sentado (o escondiéndose, o volviendo) ----
                var arrastre by remember { mutableFloatStateOf(Float.NaN) }
                val sitio = if (arrastre.isNaN()) c.sitio else arrastre
                val libre = (W - anchoMau).coerceAtLeast(0.dp)
                val x = libre * sitio
                val y = H - margenAbajo - altoMau
                val pose = if (enTransicion) Pose.SENTADO else c.pose(lugar, Mau.ahora())
                FiguraViva(
                    pose, acento, anima, dormirse = { c.dormirse(Mau.ahora()) }, bajada = { bajada.value },
                    modifier = Modifier.offset(x, y).size(anchoMau, altoMau)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Mau. Toca para abrir el menú de apps; dos toques para acariciarlo."
                            onClick("Abrir el menú de Mau") { menu = !menu; true }
                        }
                        .pointerInput(enTransicion) {
                            if (enTransicion) return@pointerInput
                            detectTapGestures(
                                onTap = { casa.tic(); c.toque(Mau.ahora()); menu = !menu; ajustes = false },
                                onDoubleTap = { acariciar() }
                            )
                        }
                        .pointerInput(enTransicion, libre) {
                            if (enTransicion) return@pointerInput
                            val ancho = libre.toPx().coerceAtLeast(1f)
                            detectDragGestures(
                                onDragStart = { arrastre = c.sitio; menu = false },
                                onDragEnd = { if (!arrastre.isNaN()) c.ponSitio(arrastre); arrastre = Float.NaN },
                                onDragCancel = { arrastre = Float.NaN }
                            ) { cambio, d -> cambio.consume(); arrastre = ((if (arrastre.isNaN()) c.sitio else arrastre) + d.x / ancho).coerceIn(0f, 1f) }
                        }
                )
                // El reloj del sprint
                c.sprintHasta?.let { hasta ->
                    val queda = ((hasta - segundo * 1000) / 1000).coerceAtLeast(0)
                    Pastilla(
                        "%d:%02d".format(queda / 60, queda % 60),
                        Modifier.offset(x + anchoMau * .5f - 26.dp, y - 18.dp)
                    )
                }
                // Lo que dice
                if (!menu && !ajustes) c.bocadillo?.let { b -> Globo(b.texto, b.accion?.texto, sitio > .5f, W, libre * sitio, margenAbajo + altoMau + 6.dp, { c.callaBocadillo() }) { c.callaBocadillo(); b.accion?.hacer?.invoke() } }
            } else {
                // ---- Escondido: solo las orejas, en su borde ----
                var arr by remember { mutableStateOf<Offset?>(null) }
                val b = c.borde
                val lado = b == Borde.IZQUIERDA || b == Borde.DERECHA
                val bw = if (lado) altoOrejas else anchoMau
                val bh = if (lado) anchoMau else altoOrejas
                val base = when (b) {
                    Borde.ABAJO -> Offset(((W - bw) * c.sitioBorde).value, (H - margenAbajo - bh).value)
                    Borde.ARRIBA -> Offset(((W - bw) * c.sitioBorde).value, 0f)
                    Borde.IZQUIERDA -> Offset(0f, ((H - bh) * c.sitioBorde).value)
                    Borde.DERECHA -> Offset((W - bw).value, ((H - bh) * c.sitioBorde).value)
                }
                val p = arr ?: base
                FiguraViva(
                    Pose.ESCONDIDO, acento, anima, giro = when (b) { Borde.ABAJO -> 0f; Borde.ARRIBA -> 180f; Borde.IZQUIERDA -> 90f; Borde.DERECHA -> -90f },
                    asomar = !sinAsomar,
                    modifier = Modifier.offset(p.x.dp, p.y.dp).size(bw, bh)
                        .semantics { role = Role.Button; contentDescription = "Mau está escondido. Toca para que vuelva."; onClick("Que vuelva Mau") { volver(); true } }
                        .pointerInput(Unit) { detectTapGestures(onTap = { casa.tic(); volver() }) }
                        .pointerInput(W, H) {
                            detectDragGestures(
                                onDragStart = { arr = base },
                                onDragEnd = {
                                    val q = arr ?: return@detectDragGestures
                                    arr = null
                                    // al borde más cercano
                                    val cx = q.x + bw.value / 2; val cy = q.y + bh.value / 2
                                    val dist = mapOf(Borde.IZQUIERDA to cx, Borde.DERECHA to W.value - cx, Borde.ARRIBA to cy, Borde.ABAJO to H.value - cy)
                                    val nb = dist.minBy { it.value }.key
                                    val f = if (nb == Borde.IZQUIERDA || nb == Borde.DERECHA) ((cy - anchoMau.value / 2) / (H.value - anchoMau.value).coerceAtLeast(1f))
                                            else ((cx - anchoMau.value / 2) / (W.value - anchoMau.value).coerceAtLeast(1f))
                                    sinAsomar = false
                                    c.ponBorde(nb, f.coerceIn(0f, 1f))
                                },
                                onDragCancel = { arr = null }
                            ) { cambio, d -> cambio.consume(); val q = arr ?: base; arr = Offset((q.x + d.x / density).coerceIn(0f, W.value - bw.value), (q.y + d.y / density).coerceIn(0f, H.value - bh.value)) }
                        }
                )
                c.bocadillo?.let { bo -> Globo(bo.texto, bo.accion?.texto, c.sitioBorde > .5f, W, ((W - anchoMau) * c.sitioBorde), margenAbajo + altoOrejas + 6.dp, { c.callaBocadillo() }) { c.callaBocadillo(); bo.accion?.hacer?.invoke() } }
            }

            if (menu && !c.escondido) {
                val libre = (W - anchoMau).coerceAtLeast(0.dp)
                val centro = Offset((libre * c.sitio + anchoMau / 2).value, (H - margenAbajo - altoMau / 2).value)
                val opciones = opcionesMenu(c, { esconder() }, { acariciar() }, { menu = false; ajustes = true }) { menu = false }
                if (casa.ordenador) MenuTarjeta(opciones, c.sitio > .5f, W, libre * c.sitio, margenAbajo + altoMau + 8.dp) { menu = false }
                else MenuArco(opciones, centro, W, H) { menu = false }
            }
        }
        if (ajustes) AjustesMau(c, acento) { ajustes = false }
    }
}

/** Lo que se puede pedir en el menú: las apps y las acciones. */
private class Opciones(val apps: List<Pair<AppSuite, () -> Unit>>, val acciones: List<Pair<String, () -> Unit>>)

private fun opcionesMenu(c: Cerebro, esconder: () -> Unit, acariciar: () -> Unit, ajustes: () -> Unit, cierra: () -> Unit): Opciones {
    val casa = Mau.casa
    val apps = APPS_SUITE.map<AppSuite, Pair<AppSuite, () -> Unit>> { a ->
        a to {
            casa.tic()
            cierra()
            when {
                a.app == casa.app -> {}
                !casa.instalada(a.app) -> c.di("${a.nombre} no está en este ${if (casa.ordenador) "ordenador" else "móvil"}.", Mau.ahora(), espontaneo = false)
                else -> { if (!casa.abre(a.app)) c.di("No he podido abrir ${a.nombre}.", Mau.ahora(), espontaneo = false) }
            }
            Unit
        }
    }
    val ahora = Mau.ahora()
    val acciones = buildList<Pair<String, () -> Unit>> {
        add("Escóndete · solo orejas" to esconder)
        add("Acariciar" to acariciar)
        if (c.callado(ahora)) add("Que vuelva a hablar" to { cierra(); c.habla(Mau.ahora()); c.di(Frases.hablarOtraVez, Mau.ahora(), espontaneo = false) })
        else add("Silencio 1 hora" to { cierra(); c.calla(Mau.ahora()); c.di(Frases.callar, Mau.ahora(), espontaneo = false) })
        if (c.sprintHasta != null) add("Parar el sprint" to { cierra(); c.cortaSprint() })
        Mau.acciones.filter { runCatching { it.visible() }.getOrDefault(false) }.forEach { a -> add(a.texto to { cierra(); a.hacer() }) }
        add("Ajustes de Mau" to ajustes)
    }
    return Opciones(apps, acciones)
}

/** Mau animado (o quieto, sin [anima]). [bajada]: lo hundido en el borde (0..1). [giro]: para las orejas en un borde lateral. */
@Composable
private fun FiguraViva(
    pose: Pose, acento: Color, anima: Boolean, modifier: Modifier,
    dormirse: () -> Long? = { null }, bajada: () -> Float = { 0f }, giro: Float = 0f, asomar: Boolean = true
) {
    val animador = remember { Animador() }
    val reloj = remember { mutableLongStateOf(0L) }
    var desde by remember { mutableStateOf(pose to System.currentTimeMillis()) }
    if (desde.first != pose) desde = pose to (System.currentTimeMillis() - if (pose == Pose.ESCONDIDO && !asomar) 1_000 else 0)
    // (a 30 cuadros por segundo basta para un gato y gasta la mitad de batería)
    LaunchedEffect(anima) { if (anima) while (true) withFrameMillis { if (it - reloj.longValue >= 32) reloj.longValue = it } }
    Canvas(modifier.clipToBounds()) {
        reloj.longValue
        val t = System.currentTimeMillis() - desde.second
        val dorm = dormirse()
        val movs = if (anima) animador.mueve(pose, t, dorm) else emptyMap()
        val lado = giro == 90f || giro == -90f
        val w = if (lado) size.height else size.width
        val h = if (lado) size.width else size.height
        withTransform({
            rotate(giro, Offset(size.width / 2, size.height / 2))
            translate((size.width - w) / 2, (size.height - h) / 2)
        }) {
            val dy = bajada() * BAJADA_ESCONDER
            if (dorm != null && dorm > DURA_DORMIRSE) {
                // se funde con «durmiendo»
                val f = ((dorm - DURA_DORMIRSE).toFloat() / FUNDIDO_DORMIR).coerceIn(0f, 1f)
                pintaMauAncho(w, Pose.SENTADO, movs, acento, 1f - f, dy)
                pintaMauAncho(w, Pose.DURMIENDO, emptyMap(), acento, f, dy)
            } else pintaMauAncho(w, pose, movs, acento, 1f, dy)
        }
    }
}

@Composable
private fun Pastilla(texto: String, modifier: Modifier) {
    Box(modifier.width(52.dp).background(TINTA, RoundedCornerShape(999.dp)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
        BasicText(texto, style = TextStyle(color = CREMA, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold))
    }
}

/** El bocadillo: encima de Mau, del lado de la pantalla donde está. [x]: dónde empieza Mau; [abajo]: hasta dónde llega por abajo. */
@Composable
private fun Globo(texto: String, accion: String?, aLaDerecha: Boolean, W: Dp, x: Dp, abajo: Dp, cierra: () -> Unit, hace: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = if (aLaDerecha) Alignment.BottomEnd else Alignment.BottomStart) {
        val margen = if (aLaDerecha) (W - x - 84.dp).coerceIn(8.dp, W) else x.coerceIn(8.dp, W)
        Box(Modifier.padding(start = if (aLaDerecha) 0.dp else margen, end = if (aLaDerecha) margen.coerceAtMost(W / 3) else 0.dp, bottom = abajo).widthIn(max = 250.dp)) {
            // la sombra dura, como en la revista
            Box(Modifier.matchParentSizeSombra())
            Column(
                Modifier.background(PAPEL, RoundedCornerShape(14.dp)).border(2.dp, TINTA, RoundedCornerShape(14.dp))
                    .clickable { cierra() }.padding(horizontal = 14.dp, vertical = 11.dp)
                    .semantics { contentDescription = "Mau dice: $texto" },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(texto, style = TextStyle(color = TINTA, fontSize = 14.sp, lineHeight = 19.sp))
                if (accion != null) Box(
                    Modifier.background(TINTA, RoundedCornerShape(999.dp)).clickable { hace() }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { BasicText(accion.uppercase(), style = TextStyle(color = CREMA, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)) }
            }
        }
    }
}

/** (sin sombra de verdad: la tarjeta lleva el borde de tinta y basta) */
private fun Modifier.matchParentSizeSombra(): Modifier = this

/** El menú en el móvil: las apps en arco alrededor de Mau y lo demás arriba. */
@Composable
private fun MenuArco(o: Opciones, centro: Offset, W: Dp, H: Dp, cierra: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = (H * .12f).coerceAtLeast(24.dp)).widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("¿A dónde vamos?", style = TextStyle(color = CREMA, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold))
            BasicText("Toca una app: Mau se va contigo.", style = TextStyle(color = Color(0xFFE9E0C4), fontSize = 14.sp, lineHeight = 19.sp))
            Spacer(Modifier.height(6.dp))
            o.acciones.forEachIndexed { i, (t, f) ->
                Box(
                    Modifier.background(if (i == 0) CREMA else Color.Transparent, RoundedCornerShape(999.dp))
                        .border(2.dp, CREMA, RoundedCornerShape(999.dp)).clickable { f() }
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) { BasicText(t.uppercase(), style = TextStyle(color = if (i == 0) TINTA else CREMA, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)) }
            }
        }
        // Las apps, en un cuarto de círculo por encima de Mau (hacia el lado donde hay sitio)
        val haciaIzq = centro.x > W.value / 2
        // (con 6 apps en 90°, a 250 dp quedan a ~78 dp unas de otras: no se pisan con su letrero)
        val r = minOf(250f, W.value * .68f, centro.y - 70f).coerceAtLeast(150f)
        val n = o.apps.size
        o.apps.forEachIndexed { i, (a, ir) ->
            val ang = Math.toRadians((if (haciaIzq) 270.0 - i * 90.0 / (n - 1) else 270.0 + i * 90.0 / (n - 1)))
            val cx = (centro.x + r * cos(ang)).toFloat().coerceIn(32f, W.value - 32f)
            val cy = (centro.y + r * sin(ang)).toFloat().coerceIn(32f, H.value - 32f)
            Ficha(a, a.app == Mau.casa.app, Mau.casa.instalada(a.app), Modifier.offset { IntOffset(((cx - 29) * density).roundToInt(), ((cy - 29) * density).roundToInt()) }, ir)
        }
    }
}

/** El icono de una app en el menú (como su icono: la letra, el punto de color y para qué es). */
@Composable
private fun Ficha(a: AppSuite, aqui: Boolean, esta: Boolean, modifier: Modifier, ir: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.size(58.dp).background(a.fondo.copy(alpha = if (esta) 1f else .45f), RoundedCornerShape(15.dp))
                .then(if (aqui) Modifier.border(3.dp, a.punto, RoundedCornerShape(15.dp)) else Modifier)
                .clickable(onClickLabel = if (aqui) "${a.nombre}: ya estás aquí" else "Ir a ${a.nombre}") { ir() }
                .semantics { contentDescription = if (aqui) "${a.nombre}: ya estás aquí" else if (esta) "Ir a ${a.nombre}" else "${a.nombre} no está instalada" }
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row {
                    BasicText(a.letra, style = TextStyle(color = a.tinta, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold))
                    BasicText(".", style = TextStyle(color = a.punto, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold))
                }
            }
            Box(Modifier.fillMaxWidth().height(13.dp).background(a.punto, RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp)), contentAlignment = Alignment.Center) {
                BasicText(a.que, style = TextStyle(color = a.sobrePunto, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp))
            }
        }
        if (aqui) BasicText("AQUÍ", style = TextStyle(color = Color(0xFFE0A92A), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp), modifier = Modifier.padding(top = 3.dp))
    }
}

/** El menú en el ordenador: una tarjeta encima de Mau. */
@Composable
private fun MenuTarjeta(o: Opciones, aLaDerecha: Boolean, W: Dp, x: Dp, abajo: Dp, cierra: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = if (aLaDerecha) Alignment.BottomEnd else Alignment.BottomStart) {
        val margen = if (aLaDerecha) (W - x - 104.dp).coerceIn(12.dp, W) else x.coerceIn(12.dp, W)
        Column(
            Modifier.padding(start = if (aLaDerecha) 0.dp else margen, end = if (aLaDerecha) margen else 0.dp, bottom = abajo).width(260.dp)
                .background(PAPEL, RoundedCornerShape(14.dp)).border(2.dp, TINTA, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BasicText("MAU · IR A", style = TextStyle(color = Color(0xFF4A5163), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp), modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 6.dp))
            o.apps.forEach { (a, ir) ->
                val aqui = a.app == Mau.casa.app
                val esta = Mau.casa.instalada(a.app)
                Row(
                    Modifier.fillMaxWidth().background(if (aqui) Color(0xFFE8E3D3) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { ir() }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(26.dp).background(a.fondo.copy(alpha = if (esta) 1f else .45f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                        Row { BasicText(a.letra, style = TextStyle(color = a.tinta, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)); BasicText(".", style = TextStyle(color = a.punto, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)) }
                    }
                    BasicText(
                        "${a.nombre} · " + when { aqui -> "aquí"; !esta -> "no está"; else -> a.que.lowercase() },
                        style = TextStyle(color = if (esta) TINTA else TINTA.copy(alpha = .5f), fontSize = 14.sp, fontWeight = if (aqui) FontWeight.SemiBold else FontWeight.Normal)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.5.dp).background(Color(0xFFE2DCCB)))
            o.acciones.forEach { (t, f) ->
                BasicText(t, style = TextStyle(color = TINTA, fontSize = 14.sp), modifier = Modifier.fillMaxWidth().clickable { f() }.padding(6.dp))
            }
        }
    }
}

/** Los ajustes de Mau: si habla, si se mueve, si se duerme, su tamaño. */
@Composable
private fun AjustesMau(c: Cerebro, acento: Color, cierra: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().background(PAPEL, RoundedCornerShape(18.dp)).border(2.dp, TINTA, RoundedCornerShape(18.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MauQuieto(Pose.SENTADO, acento, Modifier.size(48.dp, 53.dp))
                Column {
                    BasicText("MAU", style = TextStyle(color = TINTA, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold))
                    BasicText("Tu gato de la suite", style = TextStyle(color = Color(0xFF5E5440), fontSize = 13.sp))
                }
            }
            Interruptor("Habla", "Te dice cosas de vez en cuando (pocas).", c.habla) { c.habla = it }
            Interruptor("Se mueve", "Respira, parpadea, mueve la cola.", c.mueve) { c.mueve = it }
            Interruptor("Se duerme", "Si no haces nada durante un rato.", c.duerme) { c.duerme = it }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Tamaño", style = TextStyle(color = TINTA, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pequeño", "Normal", "Grande").forEachIndexed { i, t ->
                        val si = c.tamano == i
                        Box(
                            Modifier.background(if (si) TINTA else Color.Transparent, RoundedCornerShape(999.dp)).border(2.dp, TINTA, RoundedCornerShape(999.dp))
                                .clickable { c.tamano = i }.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { BasicText(t, style = TextStyle(color = if (si) CREMA else TINTA, fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
            BasicText("Para moverlo, arrástralo. Escondido, arrastra sus orejas al borde que quieras.", style = TextStyle(color = Color(0xFF5E5440), fontSize = 12.sp, lineHeight = 16.sp))
            Box(
                Modifier.fillMaxWidth().background(TINTA, RoundedCornerShape(999.dp)).clickable { cierra() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { BasicText("LISTO", style = TextStyle(color = CREMA, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.Center)) }
        }
    }
}

@Composable
private fun Interruptor(titulo: String, explica: String, si: Boolean, cambia: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { cambia(!si) }.semantics { contentDescription = "$titulo: ${if (si) "sí" else "no"}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(titulo, style = TextStyle(color = TINTA, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            BasicText(explica, style = TextStyle(color = Color(0xFF5E5440), fontSize = 12.sp, lineHeight = 16.sp))
        }
        Box(Modifier.size(46.dp, 26.dp).background(if (si) TINTA else Color(0xFFD6C9A0), RoundedCornerShape(999.dp)).padding(3.dp), contentAlignment = if (si) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(20.dp).background(PAPEL, RoundedCornerShape(999.dp)))
        }
    }
}
