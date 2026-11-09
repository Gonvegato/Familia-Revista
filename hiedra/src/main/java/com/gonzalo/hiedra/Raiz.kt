package com.gonzalo.hiedra

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Catalogo.cargar(c); Skin.cargar(c); Ajustes.cargar(c); NubeP.cargar(c); Biblioteca.cargar(c); Sagas.cargar(c); Inicio.cargar(c)
    TemaAtrio.cargarGuardado(c)
    Cuenta.cargar(c); SincroP.iniciar(c)
}

/** La ficha y la saga entran desde un poco más abajo, frenando al llegar, y se van más deprisa de lo que llegan. */
private val entraHoja = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(220))
private val saleHoja = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it / 14 } + fadeOut(tween(160))

/** Dónde estás: la portada y sus secciones (páginas de [PantPrincipal], en el orden de [PAGINAS]), Ajustes y Tu PC (encima). */
enum class Destino { Portada, Jugando, Pendiente, Pasado, Abandonado, Sagas, Sistemas, Ajustes, PC }

/** La sección pedida desde fuera (un aviso, Jonsu…): `go=jugando`, `go=pendientes`, `go=sistemas`… (el nombre de la pestaña o del destino). */
fun destinoDe(go: String?): Destino? {
    val g = Catalogo.norm(go ?: return null)
    return Destino.entries.firstOrNull { Catalogo.norm(it.name) == g } ?: when (g) {
        "pendientes" -> Destino.Pendiente; "pasados", "terminados" -> Destino.Pasado; "abandonados" -> Destino.Abandonado; "tu pc" -> Destino.PC; else -> null
    }
}

/** La app: la portada y sus secciones como páginas, la ficha de un juego o una saga por encima, Ajustes, Tu PC y la hoja de «añadir». */
@Composable
fun HiedraApp(fichaPedida: String? = null, jugarPedido: Boolean = false, alAbrirPedida: () -> Unit = {}, irPedido: String? = null, alIrPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    /** La página de [PAGINAS] que se ve. */
    var pagina by rememberSaveable { mutableIntStateOf(0) }
    /** Lo que va encima de las páginas: Ajustes o Tu PC (null = nada). */
    var encima by rememberSaveable { mutableStateOf<Destino?>(null) }
    var pestanaAjustes by rememberSaveable { mutableIntStateOf(0) }
    var buscar by rememberSaveable { mutableStateOf(false) }
    var fichaId by rememberSaveable { mutableStateOf<String?>(null) }
    var sagaId by rememberSaveable { mutableStateOf<String?>(null) }
    var anadir by rememberSaveable { mutableStateOf(false) }
    var tipoInicial by remember { mutableStateOf<TipoLanzador?>(null) }
    /** La ficha sin lanzador a la que se le va a vincular lo que se añada ahora. */
    var vincularA by remember { mutableStateOf<String?>(null) }
    /** Un juego del PC al que se va a jugar: se elige si en el PC o en streaming. */
    var jugarPc by remember { mutableStateOf<Juego?>(null) }
    /** Un juego cuya ROM está en otro móvil: se baja de tu kDrive antes de jugar. */
    var bajarRom by remember { mutableStateOf<Juego?>(null) }
    /** Una captura abierta a pantalla completa. */
    var captura by remember { mutableStateOf<Shot?>(null) }
    fun ir(x: Destino) {
        val p = paginaDe(x)
        if (p >= 0) { encima = null; pagina = p } else encima = x
    }
    // Un widget pide abrir la ficha de un juego
    LaunchedEffect(fichaPedida) {
        val j = fichaPedida?.let { Biblioteca.por(it) }
        if (j != null) { if (jugarPedido && j.enPc) jugarPc = j else { fichaId = fichaPedida; encima = null; if (jugarPedido && j.enOtroDispositivo) bajarRom = j } }
        if (fichaPedida != null) alAbrirPedida()
    }
    // Un aviso u otra app pide una sección
    LaunchedEffect(irPedido) { if (irPedido != null) { destinoDe(irPedido)?.let { fichaId = null; sagaId = null; ir(it) }; alIrPedido() } }
    // Una captura recién llegada del PC se abre
    LaunchedEffect(Suite.arrived) { Suite.arrived?.let { captura = it; Suite.arrived = null } }
    LaunchedEffect(Suite.notice) { Suite.notice?.let { Lanzar.aviso(ctx, it); Suite.notice = null } }

    fun jugar(j: Juego) {
        when {
            j.enPc -> jugarPc = j
            // La ROM está en otro de tus móviles: se baja de tu kDrive y se juega (el jugador de Bastet la baja él solo)
            j.enOtroDispositivo && !Lanzar.esInterno(Lanzar.elegir(ctx, j)) -> bajarRom = j
            else -> Lanzar.lanzar(ctx, j)?.let { Lanzar.aviso(ctx, it) }
        }
    }
    fun abrirAnadir(t: TipoLanzador?, para: String? = null) { tipoInicial = t; vincularA = para; anadir = true }

    AvisoActualizacion()
    // Atrás: cierra lo de encima (la ficha, la saga, Ajustes o Tu PC); desde cualquier página vuelve a la portada; desde la portada, sale
    BackHandler(enabled = fichaId == null && sagaId == null && encima == null && pagina != 0) { pagina = 0 }
    BackHandler(enabled = fichaId == null && sagaId == null && encima != null) { encima = if (encima == Destino.Sistemas) Destino.Ajustes else null }
    BackHandler(enabled = sagaId != null && fichaId == null) { sagaId = null }
    BackHandler(enabled = fichaId != null) { fichaId = null }

    Box(Modifier.fillMaxSize().background(Paper)) {
        // La portada y las secciones son páginas de una sola pantalla (se pasan con las pestañas o con el dedo); Ajustes y Tu PC van encima
        when (encima) {
            Destino.Ajustes -> PantEstilo(alVolver = { encima = null }, alSistemas = { encima = null; pagina = paginaDe(Destino.Sistemas) }, pestanaInicial = pestanaAjustes)
            Destino.PC -> PantPc(alVolver = { encima = null }, alAbrir = { fichaId = it.id }, alCaptura = { captura = it })
            else -> PantPrincipal(pagina = pagina, alPagina = { pagina = it }, alAbrir = { fichaId = it.id }, alJugar = ::jugar, alAbrirSaga = { sagaId = it.id },
                alAnadir = { abrirAnadir(it) }, alAjustes = { pestanaAjustes = it; encima = Destino.Ajustes }, alIr = { ir(it) }, buscandoInicial = buscar, alBuscado = { buscar = false })
        }
        AnimatedVisibility(sagaId != null, enter = entraHoja, exit = saleHoja) {
            sagaId?.let { id -> PantSaga(id, alCerrar = { sagaId = null }, alAbrir = { fichaId = it.id }, alJugar = ::jugar) }
        }
        AnimatedVisibility(fichaId != null, enter = entraHoja, exit = saleHoja) {
            fichaId?.let { id -> Ficha(id, alCerrar = { fichaId = null }, alJugar = ::jugar, alVincular = { abrirAnadir(null, it.id) }, alIrA = { fichaId = it }, alAbrirSaga = { sagaId = it.id }) }
        }
        AnimatedVisibility(captura != null, enter = fadeIn(), exit = fadeOut()) {
            captura?.let { s -> VisorCaptura(s, alCerrar = { captura = null }, alBorrar = { Suite.deleteLocal(s); captura = null }) }
        }
    }
    BackHandler(enabled = captura != null) { captura = null }
    jugarPc?.let { j -> DialogoJugarPc(j) { jugarPc = null } }
    bajarRom?.let { j -> DialogoBajarRom(j, jugar = true) { bajarRom = null } }
    if (anadir) HojaAnadir(tipoInicial, vincularA = vincularA, alVincular = { fichaId = it.id }) { anadir = false; vincularA = null }
}
