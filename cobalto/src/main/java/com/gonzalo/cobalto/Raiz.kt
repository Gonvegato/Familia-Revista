package com.gonzalo.cobalto

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Skin.cargar(c)
    TemaAtrio.cargarGuardado(c)
    Cuenta.cargar(c); SincroP.iniciar(c)
    Escritos.get(c)
}

/** Dónde estás: la portada y las páginas de obras (por las que te mueves con el dedo); los ajustes van aparte. */
enum class Destino { Portada, Escribiendo, EnPausa, Terminadas, Archivo, Ajustes }

fun Destino.estado(): EstadoObra? = when (this) {
    Destino.Escribiendo -> EstadoObra.ESCRIBIENDO; Destino.EnPausa -> EstadoObra.PAUSA
    Destino.Terminadas -> EstadoObra.TERMINADA; Destino.Archivo -> EstadoObra.ARCHIVO; else -> null
}

/** Lo que pide un widget (o un aviso) al abrir la app: una obra, y dentro de ella una escena o un sprint. */
class Peticion(val obra: String, val escena: String? = null, val sprint: Boolean = false)

/**
 * La app: una pantalla con la portada y las páginas de obras ([PantPrincipal]); por encima, la ficha de una obra («doble página»), el estudio
 * (el de Atrio: manuscrito, fichas, árbol, notas, línea, cifras y guía), lo que te comparte tu pareja y los ajustes. Atrás: del estudio a la
 * ficha (si venías de ella), de la ficha a la página, y de cualquier página a la portada.
 */
@Composable
fun CobaltoApp(pedida: Peticion? = null, alAbrirPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    var pagina by rememberSaveable { mutableIntStateOf(0) }
    var fichaId by rememberSaveable { mutableStateOf<String?>(null) }
    var obraId by rememberSaveable { mutableStateOf<String?>(null) }
    var escenaInicial by rememberSaveable { mutableStateOf<String?>(null) }
    var capInicial by rememberSaveable { mutableStateOf<String?>(null) }
    var sprintInicial by rememberSaveable { mutableStateOf(false) }
    var pareja by rememberSaveable { mutableStateOf(false) }
    var nueva by rememberSaveable { mutableStateOf(false) }
    var ajustes by rememberSaveable { mutableStateOf<Int?>(null) }
    /** Abre el estudio de una obra (en una escena, un capítulo, una pestaña o con un sprint). */
    fun estudio(id: String, escena: String? = null, sprint: Boolean = false, cap: String? = null, pestana: Int? = null) {
        if (e.get(id) == null) return
        AjustesCobalto.ponUltimaObra(ctx, id)
        if (pestana != null) EscribirHub.pestanaAlEntrar = id to pestana
        escenaInicial = escena; capInicial = cap; sprintInicial = sprint; obraId = id
    }
    fun ficha(id: String) { if (e.get(id) != null) fichaId = id }
    // Un widget pide abrir una obra (o una escena, o un sprint): va derecho al estudio
    LaunchedEffect(pedida) { if (pedida != null) { estudio(pedida.obra, pedida.escena, pedida.sprint); alAbrirPedido() } }
    // Una obra borrada desde dentro: se vuelve a donde estabas
    LaunchedEffect(EscribirHub.obraBorrada) {
        val b = EscribirHub.obraBorrada
        if (b.isNotEmpty()) { if (b == obraId) obraId = null; if (b == fichaId) fichaId = null }
    }

    AvisoActualizacion()
    BackHandler(enabled = obraId == null && fichaId == null && !pareja && ajustes == null && pagina != 0) { pagina = 0 }
    BackHandler(enabled = obraId == null && fichaId == null && pareja) { pareja = false }
    BackHandler(enabled = obraId == null && ajustes != null) { ajustes = null }
    BackHandler(enabled = obraId == null && fichaId != null && ajustes == null) { fichaId = null }

    Box(Modifier.fillMaxSize().background(Paper)) {
        PantPrincipal(pagina, alPagina = { pagina = it }, alAbrir = { ficha(it) }, alSeguir = { o, s -> estudio(o, s) }, alNueva = { nueva = true },
            alAjustes = { ajustes = 0 }, alPareja = { pareja = true }, alMeta = { ajustes = 1 }, alSprint = { estudio(it, sprint = true) })
        AnimatedVisibility(fichaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            fichaId?.let { id ->
                Box(Modifier.fillMaxSize().background(Paper)) {
                    PantFicha(id, alCerrar = { fichaId = null }, alEstudio = { s, c, p -> estudio(id, s, cap = c, pestana = p) }, alSprint = { estudio(id, sprint = true) })
                }
            }
        }
        AnimatedVisibility(ajustes != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            Box(Modifier.fillMaxSize().background(Paper)) { PantEstilo(alVolver = { ajustes = null }, pestanaInicial = ajustes ?: 0) }
        }
        AnimatedVisibility(pareja, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            Box(Modifier.fillMaxSize().background(Paper)) { LecturaPareja(onBack = { pareja = false }) }
        }
        AnimatedVisibility(obraId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            obraId?.let { id -> Box(Modifier.fillMaxSize().background(Paper)) { Estudio(id, escenaInicial, sprintInicial, capInicial = capInicial, onCerrar = { obraId = null; escenaInicial = null; capInicial = null; sprintInicial = false }) } }
        }
    }
    if (nueva) ObraDialogo(e, null, onCerrar = { nueva = false }, onCreada = { ficha(it.id) })
}

/**
 * El estudio de una obra, tal como está en Atrio: la obra con sus pestañas y, por encima, la página de una parte, la de un capítulo
 * y el editor de una escena. Al volver del editor se va a la página de su capítulo (como en el PC).
 */
@Composable
fun Estudio(obraId: String, escenaInicial: String?, sprintInicial: Boolean, capInicial: String? = null, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    var parteId by rememberSaveable(obraId) { mutableStateOf<String?>(null) }
    var capId by rememberSaveable(obraId) { mutableStateOf<String?>(null) }
    var escenaId by rememberSaveable(obraId) { mutableStateOf<String?>(null) }
    var sprint by rememberSaveable(obraId) { mutableStateOf(sprintInicial) }
    if (e.get(obraId) == null) { onCerrar(); return }
    /** Abrir una escena: al volver del editor se va a la página de su capítulo. */
    fun abreEscena(id: String) {
        val s = e.get(id) ?: return
        val c = e.get(s.s("cid"))
        capId = c?.id; parteId = c?.s("aid")?.takeIf { e.get(it) != null }; escenaId = id
    }
    LaunchedEffect(escenaInicial) { if (escenaInicial != null) abreEscena(escenaInicial) }
    // Desde la ficha: un capítulo (y su parte, si la tiene)
    LaunchedEffect(capInicial) { if (capInicial != null && escenaInicial == null) { val c = e.get(capInicial); if (c != null) { capId = c.id; parteId = c.s("aid").takeIf { e.get(it) != null } } } }
    // Desde la portada: «Seguir con esta escena» (lo deja EscribirHub, como en Atrio)
    val pide = EscribirHub.abrir
    LaunchedEffect(pide) { if (pide != null && System.currentTimeMillis() - pide.second < 4000 && e.get(pide.first)?.pid == obraId) { abreEscena(pide.first); EscribirHub.abrir = null } }
    // «Ver en el árbol» desde una parte o un capítulo: a la obra, pestaña Árbol
    val verArbol: (String) -> Unit = { id -> parteId = null; capId = null; EscribirHub.irArbol = id }
    BackHandler(enabled = escenaId == null) {
        when {
            capId != null -> capId = null
            parteId != null -> parteId = null
            else -> onCerrar()
        }
    }
    VigilaSprint(e, ctx)
    when {
        escenaId != null -> EscribirEditor(escenaId!!, onBack = { escenaId = null }, abrirEscena = { abreEscena(it) })
        capId != null -> CapituloScreen(e, capId!!, onBack = { capId = null }, abrir = { escenaId = it }, verArbol = verArbol)
        parteId != null -> ParteScreen(e, parteId!!, onBack = { parteId = null }, abrirCapitulo = { capId = it }, abrir = { abreEscena(it) }, verArbol = verArbol)
        else -> ObraScreen(e, obraId, onBack = onCerrar, abrir = { abreEscena(it) },
            abrirParte = { parteId = it }, abrirCapitulo = { id -> capId = id; parteId = e.get(id)?.s("aid")?.takeIf { e.get(it) != null } })
    }
    if (sprint && escenaId == null) SprintDialogo(e, obraId, onCerrar = { sprint = false })
}
