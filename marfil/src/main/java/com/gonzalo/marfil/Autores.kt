package com.gonzalo.marfil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gonzalo.familia.FormaEntre
import com.gonzalo.familia.Origen
import com.gonzalo.familia.Vibra
import com.gonzalo.familia.entrada
import com.gonzalo.familia.pulsable
import kotlinx.coroutines.launch

/** Qué está abierto por encima de las secciones (para que Atrás cierre siempre lo de más arriba). */
object Capas {
    var ficha by mutableStateOf(false)
    var pagina by mutableStateOf(false)
    var asomo by mutableStateOf(false)
}

/** Los libros de un autor (con el mismo nombre, sin mirar tildes ni mayúsculas). */
fun librosDeAutor(nombre: String, todos: List<Libro> = Biblioteca.propios()): List<Libro> = norm(nombre).let { n -> todos.filter { norm(it.autor) == n } }

/**
 * Una página que nace de la fila que tocaste (la de un autor o la de una saga) y vuelve a ella al cerrar; con el gesto de atrás de Android
 * encoge con el dedo. [clave]: lo que está abierto (null: nada); [contenido] la pinta.
 */
@Composable
fun CapaPagina(clave: String?, alCerrar: () -> Unit, contenido: @Composable (String) -> Unit) {
    var visible by remember { mutableStateOf<String?>(null) }
    var origen by remember { mutableStateOf<Origen.Datos?>(null) }
    val t = remember { Animatable(0f) }
    val atras = remember { Animatable(0f) }
    var desdeIzquierda by remember { mutableStateOf(true) }
    val vista = LocalView.current
    LaunchedEffect(clave) {
        if (clave != null) {
            if (visible != clave) { origen = Origen.toma(clave); visible = clave; t.snapTo(0f); atras.snapTo(0f) }
            Capas.pagina = true
            t.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 240f))
        } else if (visible != null) {
            Vibra.tic(vista)
            t.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f))
            visible = null; atras.snapTo(0f); Capas.pagina = false
        }
    }
    androidx.activity.compose.PredictiveBackHandler(enabled = clave != null && !Capas.ficha && !Capas.asomo) { progreso ->
        try {
            progreso.collect { e -> desdeIzquierda = e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT; atras.snapTo(e.progress) }
            alCerrar()
        } catch (c: kotlinx.coroutines.CancellationException) {
            atras.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 420f)); throw c
        }
    }
    val k = visible ?: return
    val o = origen
    val densidad = LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer {
        val tt = t.value; val p = atras.value
        clip = true
        shape = FormaEntre(o?.fila, tt, with(densidad) { 26.dp.toPx() } * (p * 4f).coerceAtMost(1f))
        val s = (1f - 0.1f * p) * (if (o == null) 0.96f + 0.04f * tt else 1f)
        scaleX = s; scaleY = s
        translationX = (if (desdeIzquierda) 1f else -1f) * p * with(densidad) { 24.dp.toPx() }
        // Al cerrar, el último tramo (cuando el papel ya tiene el tamaño de la fila) se funde con la fila en vez de quitarse de golpe
        alpha = if (o == null) tt.coerceIn(0f, 1f) else if (clave == null) (tt / 0.28f).coerceIn(0f, 1f) else 1f
    }) {
        Box(Modifier.fillMaxSize().background(Paper))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (clave == null) ((t.value - 0.45f) / 0.55f).coerceIn(0f, 1f) else (t.value * 2.5f).coerceIn(0f, 1f) }) { contenido(k) }
    }
}

/**
 * La página de un autor: arriba, en tinta, su nombre en grande, cuántos libros tienes de él y cuántos has leído, y sus tapas en abanico;
 * debajo, sus libros por estante (Leyendo, Pendientes, Leídos, Abandonados). Cada libro abre su ficha encima.
 */
@Composable
fun PantAutor(nombre: String, alCerrar: () -> Unit, alAbrir: (Libro) -> Unit) {
    val libros = librosDeAutor(nombre)
    val leidos = libros.count { it.estado == Estado.LEIDO }
    val leyendo = libros.count { it.estado == Estado.LEYENDO }
    val ambito = rememberCoroutineScope()
    val clave = remember { "autor:$nombre:" + System.nanoTime() }
    LazyColumn(Modifier.fillMaxSize().background(Paper), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Charcoal).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
                EnlaceRevista("← Volver", color = Paper.copy(alpha = 0.85f)) { alCerrar() }
                Text("AUTOR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp).entrada("$clave:a", 0))
                FitTitle(nombre, 46.sp, 48.sp, color = Paper, family = Grotesca, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, maxLines = 3, minSize = 24f,
                    modifier = Modifier.padding(top = 6.dp).entrada("$clave:n", 1))
                val datos = listOfNotNull(
                    "${libros.size} ${if (libros.size == 1) "libro" else "libros"} en tu estante",
                    "$leidos ${if (leidos == 1) "leído" else "leídos"}",
                    if (leyendo > 0) "$leyendo leyendo" else null,
                ).joinToString(" · ").uppercase()
                Text(datos, color = Paper.copy(alpha = 0.7f), fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 10.dp).entrada("$clave:d", 2))
                if (libros.isNotEmpty()) Abanico(libros.sortedByDescending { it.ultima }.take(5), Modifier.padding(top = 24.dp))
            }
        }
        var n = 0
        listOf(Estado.LEYENDO to "Leyendo", Estado.PENDIENTE to "Pendientes", Estado.LEIDO to "Leídos", Estado.ABANDONADO to "Abandonados").forEach { (e, titulo) ->
            val de = libros.filter { it.estado == e }.sortedWith(compareByDescending<Libro> { it.ultima }.thenBy { norm(it.titulo) })
            if (de.isEmpty()) return@forEach
            item(key = "g:$e") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp)) {
                    Text(titulo.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    Text("${de.size}", color = Muted, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
            itemsIndexed(de, key = { _, l -> "l:" + l.id }) { i, l ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Filete(2.dp)
                    FilaLibro(l, alAbrir, orden = (++n).coerceAtMost(9), extra = Sagas.de(l.clave)?.let { "Saga ${it.nombre} · ${it.claves.indexOf(l.clave) + 1}" }.orEmpty())
                }
            }
        }
    }
}

/** Las tapas en abanico, que se abren con un muelle al entrar. */
@Composable
private fun Abanico(libros: List<Libro>, modifier: Modifier = Modifier) {
    val m = (libros.size - 1) / 2f
    val abierto = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(140); abierto.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f)) }
    BoxWithConstraints(modifier.fillMaxWidth().height(150.dp)) {
        val paso = minOf(58.dp, (maxWidth - 90.dp) / (libros.size.coerceAtLeast(2) - 1).coerceAtLeast(1))
        libros.forEachIndexed { i, l ->
            val d = i - m
            PortadaLibro(l, Modifier.align(Alignment.TopCenter).zIndex(10f - kotlin.math.abs(d)).width(88.dp).height(132.dp).graphicsLayer {
                val a = abierto.value
                translationX = d * paso.toPx() * a
                translationY = kotlin.math.abs(d) * 8.dp.toPx() * a + (1f - a.coerceIn(0f, 1f)) * 24.dp.toPx()
                rotationZ = d * 7f * a
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.1f)
                shadowElevation = 14.dp.toPx()
                alpha = a.coerceIn(0f, 1f)
            }, radio = 0, conTitulo = false)
        }
    }
}

/** Mantener pulsado un libro: qué libro y dónde estaba su fila. */
object Asomo {
    var libro by mutableStateOf<String?>(null)
    var donde by mutableStateOf<Rect?>(null)
    fun abre(id: String, fila: androidx.compose.ui.layout.LayoutCoordinates?) { donde = fila?.takeIf { it.isAttached }?.boundsInRoot(); libro = id; Capas.asomo = true }
}

/**
 * Asomarse a un libro (al mantenerlo pulsado): el fondo se desenfoca y oscurece, el libro se adelanta un poco desde su sitio y debajo salen
 * acciones rápidas, sin abrir la ficha. Tocar fuera lo devuelve a su sitio.
 */
@Composable
fun CapaAsomo(alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit) {
    val id = Asomo.libro ?: return
    val l = Biblioteca.por(id) ?: run { Asomo.libro = null; Capas.asomo = false; return }
    val r = Asomo.donde
    val t = remember(id) { Animatable(0f) }
    val ambito = rememberCoroutineScope()
    val vista = LocalView.current
    var cerrando by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { t.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f)) }
    fun cierra(despues: () -> Unit = {}) {
        if (cerrando) return; cerrando = true
        ambito.launch { t.animateTo(0f, tween(200)); Asomo.libro = null; Capas.asomo = false; despues() }
    }
    androidx.activity.compose.BackHandler { cierra() }
    val densidad = LocalDensity.current
    val arriba = with(densidad) { ((r?.top ?: 300f).toDp() - 30.dp).coerceIn(90.dp, 420.dp) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = t.value.coerceIn(0f, 1f) }.background(Charcoal.copy(alpha = 0.45f))
            .pulsable(hundido = 1f, vibra = null) { cierra() })
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).offset(y = arriba)) {
            Box(Modifier.fillMaxWidth().graphicsLayer {
                val desde = r?.top ?: 0f
                translationY = (desde - with(densidad) { arriba.toPx() }) * (1f - t.value)
                val e = 1f + 0.04f * t.value; scaleX = e; scaleY = e
                shadowElevation = 24.dp.toPx() * t.value.coerceIn(0f, 1f)
            }.background(Paper).padding(horizontal = 14.dp)) {
                FilaLibro(l, { cierra { Origen.apunta(l.id, null, null); alAbrir(l) } })
            }
            Column(Modifier.fillMaxWidth().padding(top = 14.dp).graphicsLayer {
                val v = t.value.coerceIn(0f, 1f); alpha = v; translationY = (1f - v) * -16.dp.toPx()
            }.background(Charcoal)) {
                @Composable fun Accion(texto: String, signo: String, f: () -> Unit) =
                    Row(Modifier.fillMaxWidth().pulsable(hundido = 0.98f, vibra = Vibra::tic) { f() }.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(texto.uppercase(), color = Paper, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                        Text(signo, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                Accion("Abrir la ficha", "→") { cierra { alAbrir(l) } }
                if (!l.enOtroDispositivo) Accion(if (l.fraccion > 0.005f && l.estado != Estado.LEIDO) "Seguir leyendo" else "Empezar a leer", "↗") { cierra { alLeer(l) } }
                if (l.estado != Estado.LEIDO && !l.esEscrito) Accion("Marcar como leído", "✓") {
                    val antes = l
                    cierra { Biblioteca.poner(l.copy(estado = Estado.LEIDO)); Vibra.hecho(vista); com.gonzalo.familia.Avisos.di("Movido a Leídos") { Biblioteca.poner(antes) } }
                }
            }
        }
    }
}
