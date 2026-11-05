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

/** Dónde estás: la portada, los menús (páginas por las que te mueves) y los ajustes. */
enum class Destino { Portada, Escribiendo, EnPausa, Terminadas, Archivo, Ajustes }

fun Destino.estado(): EstadoObra? = when (this) {
    Destino.Escribiendo -> EstadoObra.ESCRIBIENDO; Destino.EnPausa -> EstadoObra.PAUSA
    Destino.Terminadas -> EstadoObra.TERMINADA; Destino.Archivo -> EstadoObra.ARCHIVO; else -> null
}

/** Lo que pide un widget (o un aviso) al abrir la app: una obra, y dentro de ella una escena o un sprint. */
class Peticion(val obra: String, val escena: String? = null, val sprint: Boolean = false)

/**
 * La app: la portada de revista, sus menús, los ajustes y, por encima, el estudio de una obra (el de Atrio: manuscrito, fichas, árbol,
 * notas, línea, cifras y guía) o lo que te comparte tu pareja.
 */
@Composable
fun CobaltoApp(pedida: Peticion? = null, alAbrirPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    var destino by rememberSaveable { mutableIntStateOf(0) }
    var obraId by rememberSaveable { mutableStateOf<String?>(null) }
    var escenaInicial by rememberSaveable { mutableStateOf<String?>(null) }
    var sprintInicial by rememberSaveable { mutableStateOf(false) }
    var pareja by rememberSaveable { mutableStateOf(false) }
    var nueva by rememberSaveable { mutableStateOf(false) }
    val d = Destino.entries[destino]
    fun abrir(id: String, escena: String? = null, sprint: Boolean = false) {
        if (e.get(id) == null) return
        AjustesCobalto.ponUltimaObra(ctx, id)
        escenaInicial = escena; sprintInicial = sprint; obraId = id
    }
    // Un widget pide abrir una obra (o una escena, o un sprint)
    LaunchedEffect(pedida) { if (pedida != null) { abrir(pedida.obra, pedida.escena, pedida.sprint); alAbrirPedido() } }
    // Una obra borrada desde dentro: se vuelve a donde estabas
    LaunchedEffect(EscribirHub.obraBorrada) { if (EscribirHub.obraBorrada.isNotEmpty() && EscribirHub.obraBorrada == obraId) obraId = null }
    var buscar by rememberSaveable { mutableStateOf(false) }
    fun ir(x: Destino) { destino = x.ordinal }

    AvisoActualizacion()
    BackHandler(enabled = obraId == null && !pareja && d != Destino.Portada) { destino = 0 }
    BackHandler(enabled = pareja && obraId == null) { pareja = false }

    Box(Modifier.fillMaxSize().background(Paper)) {
        when (d) {
            Destino.Portada -> PantInicio(alAbrir = { abrir(it) }, alSeguir = { o, s -> abrir(o, s) }, alSprint = { abrir(it, sprint = true) }, alNueva = { nueva = true }, alIr = { ir(it) }, alPareja = { pareja = true }, alBuscar = { buscar = true; ir(Destino.Escribiendo) })
            Destino.Ajustes -> PantEstilo(alVolver = { destino = 0 })
            else -> PantSecciones(d, alVolver = { destino = 0; buscar = false }, alAbrir = { abrir(it) }, alNueva = { nueva = true }, buscando = buscar)
        }
        AnimatedVisibility(pareja, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            Box(Modifier.fillMaxSize().background(Paper)) { LecturaPareja(onBack = { pareja = false }) }
        }
        AnimatedVisibility(obraId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            obraId?.let { id -> Box(Modifier.fillMaxSize().background(Paper)) { Estudio(id, escenaInicial, sprintInicial, onCerrar = { obraId = null; escenaInicial = null; sprintInicial = false }) } }
        }
    }
    if (nueva) ObraDialogo(e, null, onCerrar = { nueva = false }, onCreada = { abrir(it.id) })
}

/**
 * El estudio de una obra, tal como está en Atrio: la obra con sus pestañas y, por encima, la página de una parte, la de un capítulo
 * y el editor de una escena. Al volver del editor se va a la página de su capítulo (como en el PC).
 */
@Composable
fun Estudio(obraId: String, escenaInicial: String?, sprintInicial: Boolean, onCerrar: () -> Unit) {
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
