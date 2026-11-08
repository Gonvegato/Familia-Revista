package com.gonzalo.shai

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Cuenta.cargar(c)
    // (en modo prueba, las carreras de prueba: ver Prueba.kt)
    if (!Prueba.activo) Carreras.almacen = AlmacenCuenta()
    Prueba.cargar(c)
    Gasto.dispositivo = Cuenta.dispositivo
    Skin.cargar(c); TemaAtrio.cargarGuardado(c)
    SincroP.iniciar(c)
}

/** Dónde estás: tus carreras, una carrera (en una de sus pestañas), meter un partido, empezar una carrera, los ajustes. */
sealed class Pagina {
    data object Portada : Pagina()
    data class Carrera(val id: String) : Pagina()
    data class Partido(val id: String) : Pagina()
    data object Nueva : Pagina()
    data class Ajustes(val pestana: Int = 0) : Pagina()
}

/** Cómo se guarda la pila de páginas (para seguir donde estabas si Android cierra la app por detrás). */
val PILA: androidx.compose.runtime.saveable.Saver<List<Pagina>, ArrayList<String>> = androidx.compose.runtime.saveable.Saver(
    save = { st -> ArrayList(st.map { p -> when (p) { Pagina.Portada -> "P"; is Pagina.Carrera -> "C" + p.id; is Pagina.Partido -> "M" + p.id; Pagina.Nueva -> "N"; is Pagina.Ajustes -> "A" + p.pestana } }) },
    restore = { l -> l.mapNotNull { t -> when {
        t == "P" -> Pagina.Portada; t == "N" -> Pagina.Nueva
        t.startsWith("C") -> Pagina.Carrera(t.drop(1)); t.startsWith("M") -> Pagina.Partido(t.drop(1))
        t.startsWith("A") -> Pagina.Ajustes(t.drop(1).toIntOrNull() ?: 0)
        else -> null
    } }.ifEmpty { listOf(Pagina.Portada) } },
)

/** Lo que pide un aviso al abrir la app (ir a una carrera). Cada pedido es otro objeto. */
class Pedido(val carrera: String?)

@Composable
fun ShaiApp(pedido: Pedido? = null, inicial: List<Pagina> = listOf(Pagina.Portada)) {
    var pila by rememberSaveable(stateSaver = PILA) { mutableStateOf(inicial) }
    val arriba = pila.last()
    fun ir(p: Pagina) { pila = pila + p }
    fun volver() { if (pila.size > 1) pila = pila.dropLast(1) }
    LaunchedEffect(pedido) { pedido?.carrera?.let { pila = listOf(Pagina.Portada, Pagina.Carrera(it)) } }
    AvisoActualizacion()
    BackHandler(enabled = pila.size > 1) { volver() }
    Box(Modifier.fillMaxSize().background(Paper)) {
        AnimatedContent(arriba, transitionSpec = {
            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 24 }) togetherWith fadeOut(tween(140))
        }, label = "pagina") { p ->
            when (p) {
                Pagina.Portada -> PantPortada(alAbrir = { ir(Pagina.Carrera(it)) }, alNueva = { ir(Pagina.Nueva) }, alAjustes = { ir(Pagina.Ajustes(it)) })
                is Pagina.Carrera -> PantCarrera(p.id, alVolver = ::volver, alPartido = { ir(Pagina.Partido(p.id)) }, alAjustes = { ir(Pagina.Ajustes(it)) })
                is Pagina.Partido -> PantPartido(p.id, alVolver = ::volver, alGuardado = { volver() })
                Pagina.Nueva -> PantNueva(alVolver = ::volver, alCreada = { id -> pila = listOf(Pagina.Portada, Pagina.Carrera(id)) })
                is Pagina.Ajustes -> PantAjustes(alVolver = ::volver, inicial = p.pestana)
            }
        }
    }
}
