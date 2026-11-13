package com.gonzalo.marfil

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gonzalo.familia.CapaAvisos

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Cuenta.cargar(c)
    Skin.cargar(c); Biblioteca.cargar(c); Sagas.cargar(c)
    TemaAtrio.cargarGuardado(c)
    SincroP.iniciar(c)
    // Las páginas de los libros que aún no las tienen (del archivo o de Open Library), sin molestar
    Paginas.enSegundoPlano(c)
}

/** Dónde estás: la portada, las secciones (páginas por las que te mueves) y los ajustes. */
enum class Destino { Portada, Leyendo, Pendientes, Leidos, Abandonados, Autores, Sagas, Escritos, Ajustes }

/** La app: portada de revista, sus secciones, la ficha de un libro o una saga por encima y el selector de archivos para añadir. */
@Composable
fun MarfilApp(libroPedido: String? = null, alAbrirPedido: () -> Unit = {}, irPedido: String? = null, alIrPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var destino by rememberSaveable { mutableIntStateOf(0) }
    // (un libro pedido por un widget se pone ya en la primera pantalla: sin ver antes la portada ni una animación de entrada)
    val pedidoAlArrancar = remember { libroPedido?.takeIf { Biblioteca.por(it) != null } }
    var fichaId by rememberSaveable { mutableStateOf(pedidoAlArrancar) }
    var fichaAlInstante by remember { mutableStateOf(pedidoAlArrancar != null) }
    var sagaId by rememberSaveable { mutableStateOf<String?>(null) }
    var autor by rememberSaveable { mutableStateOf<String?>(null) }
    val d = Destino.entries[destino]
    // Un widget (o un aviso) pide abrir un libro
    LaunchedEffect(libroPedido) {
        if (libroPedido != null && Biblioteca.por(libroPedido) != null && fichaId != libroPedido) { fichaAlInstante = true; fichaId = libroPedido; destino = 0 }
        if (libroPedido != null) alAbrirPedido()
    }
    // Otra app (Azabache) pide una sección: «escritos», «leyendo»…
    var escritoPedido by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(irPedido) {
        if (irPedido != null) {
            val seccion = irPedido.substringBefore(':'); escritoPedido = irPedido.substringAfter(':', "").ifBlank { null }
            Destino.entries.firstOrNull { it.name.equals(seccion, true) }?.let { fichaId = null; sagaId = null; autor = null; destino = it.ordinal }
            alIrPedido()
        }
    }

    // Añadir libros: se eligen uno o varios archivos (EPUB, PDF, MOBI…); se copian a la app y se leen sus datos
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ambito.launch {
            val hechos = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { Metadatos.anadir(ctx, it) }.getOrNull() } }
            Toast.makeText(ctx, if (hechos.isEmpty()) "No he podido añadir ese archivo (vale EPUB, PDF, MOBI, AZW3, FB2, CBZ y TXT)" else if (hechos.size == 1) "Añadido: ${hechos[0].titulo}" else "Añadidos ${hechos.size} libros", Toast.LENGTH_LONG).show()
        }
    }
    fun anadir() = elegir.launch(arrayOf("application/epub+zip", "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook", "application/x-fictionbook+xml", "application/vnd.comicbook+zip", "text/plain", "application/octet-stream"))
    fun leer(l: Libro) { Lector.abrir(ctx, l) }

    AvisoActualizacion()
    // Desde cualquier página o desde Ajustes, Atrás vuelve a la portada (y desde la portada, sale)
    BackHandler(enabled = fichaId == null && sagaId == null && autor == null && d != Destino.Portada) { destino = 0 }
    // (asomarse a un libro desenfoca lo de detrás)
    val desenfoque by androidx.compose.animation.core.animateDpAsState(if (Asomo.libro != null && !Asomo.saliendo) 10.dp else 0.dp,
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 420f), label = "asomo")

    Box(Modifier.fillMaxSize().background(Paper)) {
        // La portada y las secciones son páginas de una sola pantalla (se pasan con las pestañas o con el dedo); Ajustes va aparte
        Box(Modifier.fillMaxSize().then(if (desenfoque > 0.dp) Modifier.blur(desenfoque) else Modifier)) {
            if (d == Destino.Ajustes) PantEstilo(alVolver = { destino = 0 })
            else PantPrincipal(pagina = destino, alPagina = { destino = it }, alAbrir = { fichaId = it.id }, alLeer = ::leer, alAbrirSaga = { sagaId = it.id }, alAnadir = ::anadir,
                alAjustes = { destino = Destino.Ajustes.ordinal }, escritoPedido = escritoPedido, alEscritoAbierto = { escritoPedido = null }, alAbrirAutor = { autor = it })
            // La página de una saga o de un autor: nace de su fila, como la ficha
            CapaPagina(sagaId?.let { "saga:$it" } ?: autor?.let { "autor:$it" }, alCerrar = { sagaId = null; autor = null }) { k ->
                if (k.startsWith("saga:")) PantSaga(k.removePrefix("saga:"), alCerrar = { sagaId = null }, alAbrir = { fichaId = it.id })
                else PantAutor(k.removePrefix("autor:"), alCerrar = { autor = null }, alAbrir = { fichaId = it.id })
            }
        }
        CapaAsomo(alAbrir = { fichaId = it.id }, alLeer = ::leer)
        CapaFicha(fichaId, alCerrar = { fichaId = null }, alLeer = ::leer, alInstante = fichaAlInstante, alUsarInstante = { fichaAlInstante = false })
        CapaAvisos(Charcoal, Paper, Acento, Body)
    }
}

/**
 * La ficha por encima de las secciones. Nace de la fila que tocaste (o, si se abrió desde fuera, aparece en su sitio) y la tapa vuela desde
 * la fila hasta su hueco; al cerrar hace lo contrario. Todo con un muelle y se puede dar la vuelta a medio camino. Con el gesto de atrás de
 * Android, la ficha encoge con el dedo (vista previa): si sueltas, se cierra; si te arrepientes, vuelve.
 */
@Composable
private fun CapaFicha(fichaId: String?, alCerrar: () -> Unit, alLeer: (Libro) -> Unit, alInstante: Boolean = false, alUsarInstante: () -> Unit = {}) {
    // (pedida desde fuera al arrancar: ya puesta desde el primer fotograma)
    var visible by remember { mutableStateOf<String?>(if (alInstante) fichaId else null) }
    var origen by remember { mutableStateOf<com.gonzalo.familia.Origen.Datos?>(null) }
    var aperturas by remember { mutableIntStateOf(0) }
    val t = remember { androidx.compose.animation.core.Animatable(if (alInstante && fichaId != null) 1f else 0f) }
    val atras = remember { androidx.compose.animation.core.Animatable(0f) }
    var desdeIzquierda by remember { mutableStateOf(true) }
    val vista = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(fichaId) {
        if (fichaId != null && alInstante) {
            // Pedida por un widget: sin animación de entrada (al abrir la app ya se ve la ficha)
            origen = null; visible = fichaId; aperturas++; atras.snapTo(0f); t.snapTo(1f); com.gonzalo.familia.Vuelo.t = 1f
            Capas.ficha = true; alUsarInstante(); return@LaunchedEffect
        }
        if (fichaId != null) {
            if (visible != fichaId) { origen = com.gonzalo.familia.Origen.toma(fichaId); visible = fichaId; aperturas++; t.snapTo(0f); atras.snapTo(0f) }
            Capas.ficha = true
            if (origen?.tapa != null) com.gonzalo.familia.Vuelo.clave = fichaId
            t.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 230f)) { com.gonzalo.familia.Vuelo.t = value }
            com.gonzalo.familia.Vuelo.clave = null
        } else if (visible != null) {
            com.gonzalo.familia.Vibra.tic(vista)
            // La tapa sale de donde está ahora (aunque hayas bajado en la ficha o la hayas encogido con el gesto de atrás)
            com.gonzalo.familia.Vuelo.destino = com.gonzalo.familia.Vuelo.destinoAhora()
            if (origen?.tapa != null) com.gonzalo.familia.Vuelo.clave = visible
            // Lo que encogió el gesto de atrás se deshace a la vez: así la ficha aterriza justo sobre su fila (sin salto al final)
            launch { atras.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 300f)) }
            t.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 300f)) { com.gonzalo.familia.Vuelo.t = value }
            com.gonzalo.familia.Vuelo.clave = null; com.gonzalo.familia.Vuelo.destino = null
            visible = null; atras.snapTo(0f); Capas.ficha = false
        }
    }
    // El gesto de atrás con vista previa (Android 14+; antes, un toque de atrás normal)
    androidx.activity.compose.PredictiveBackHandler(enabled = fichaId != null) { progreso ->
        try {
            progreso.collect { e -> desdeIzquierda = e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT; atras.snapTo(e.progress) }
            alCerrar()
        } catch (c: kotlinx.coroutines.CancellationException) {
            atras.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 420f)); throw c
        }
    }
    val id = visible ?: return
    val o = origen
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer {
        val tt = t.value
        val p = atras.value
        val radioAtras = with(densidad) { 26.dp.toPx() } * (p * 4f).coerceAtMost(1f)
        clip = true
        shape = com.gonzalo.familia.FormaEntre(o?.fila, tt, radioAtras)
        val s = (1f - 0.1f * p) * (if (o == null) 0.96f + 0.04f * tt else 1f)
        scaleX = s; scaleY = s
        translationX = (if (desdeIzquierda) 1f else -1f) * p * with(densidad) { 24.dp.toPx() }
        // El papel aparece al empezar y, al cerrar, el último tramo (ya del tamaño de la fila) se funde con ella: nada de golpe
        alpha = if (o == null) tt.coerceIn(0f, 1f) else com.gonzalo.familia.fundidoBorde(tt)
    }) {
        // El papel de la ficha crece y encoge entero; lo de dentro aparece pronto al abrir y se va pronto al cerrar (sin transparencias
        // a medio camino encima de la lista)
        Box(Modifier.fillMaxSize().background(Paper))
        Box(Modifier.fillMaxSize().graphicsLayer {
            val tt = t.value
            alpha = if (fichaId == null) ((tt - 0.45f) / 0.55f).coerceIn(0f, 1f) else (tt * 2.5f).coerceIn(0f, 1f)
        }) { PantFicha(id, alCerrar = alCerrar, alLeer = alLeer, apertura = aperturas) }
    }
    // La tapa que vuela entre la fila y la ficha
    val desde = o?.tapa
    val hasta = com.gonzalo.familia.Vuelo.destino
    val libro = Biblioteca.por(id)
    if (desde != null && hasta != null && libro != null && com.gonzalo.familia.Vuelo.clave == id) {
        with(densidad) {
            Box(Modifier.size(hasta.width.toDp(), hasta.height.toDp()).graphicsLayer {
                val r = com.gonzalo.familia.lerpRect(desde, hasta, t.value)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                translationX = r.left; translationY = r.top
                scaleX = r.width / hasta.width; scaleY = r.height / hasta.height
                shadowElevation = 18.dp.toPx() * t.value
                alpha = com.gonzalo.familia.Vuelo.alfaCopia()
            }) { PortadaLibro(libro, Modifier.fillMaxSize(), radio = 0, conAutor = false) }
        }
    }
}
