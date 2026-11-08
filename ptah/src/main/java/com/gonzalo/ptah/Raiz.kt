package com.gonzalo.ptah

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Cuenta.cargar(c)
    Skin.cargar(c); TemaAtrio.cargarGuardado(c)
    SincroP.iniciar(c)
}

/** Dónde estás: la portada, elegir el tipo de uno nuevo, el editor y los ajustes. */
sealed class Pagina {
    data object Portada : Pagina()
    data object Nuevo : Pagina()
    data class Editor(val d: Diseno, val nuevo: Boolean) : Pagina()
    data object Ajustes : Pagina()
}

/** Lo que pide un widget al abrir la app (editar un diseño). Cada pedido es otro objeto: el mismo dos veces, dos veces. */
class Pedido(val editar: String?)

/**
 * La app. Con [eligiendo] (al poner un widget en la pantalla de inicio, desde [ConfigWidgetActivity]) tocar un diseño lo elige para ese
 * widget ([alElegir]) en vez de abrirlo; lo nuevo que hagas, al guardarlo, también.
 */
@Composable
fun PtahApp(pedido: Pedido? = null, eligiendo: Boolean = false, alElegir: (Diseno) -> Unit = {}) {
    val ctx = LocalContext.current
    var pila by remember { mutableStateOf(listOf<Pagina>(Pagina.Portada)) }
    val arriba = pila.last()
    fun ir(p: Pagina) { pila = pila + p }
    fun volver() { if (pila.size > 1) pila = pila.dropLast(1) }
    LaunchedEffect(pedido) { pedido?.editar?.let { id -> Disenos.de(ctx, id)?.let { pila = listOf(Pagina.Portada, Pagina.Editor(it, false)) } } }
    if (!eligiendo) AvisoActualizacion()
    BackHandler(enabled = pila.size > 1) { volver() }
    Box(Modifier.fillMaxSize().background(Paper)) {
        AnimatedContent(arriba, transitionSpec = {
            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 24 }) togetherWith fadeOut(tween(140))
        }, contentKey = { p -> when (p) { is Pagina.Editor -> "e" + p.d.id; else -> p.toString() } }, label = "pagina") { p ->
            when (p) {
                Pagina.Portada -> PantPortada(
                    eligiendo = eligiendo,
                    alAbrir = { d -> if (eligiendo) alElegir(d) else ir(Pagina.Editor(d, false)) },
                    alPlantilla = { pl -> val n = Plantillas.copia(pl); if (eligiendo) { Disenos.guarda(ctx, n); alElegir(n) } else ir(Pagina.Editor(n, true)) },
                    alNuevo = { ir(Pagina.Nuevo) },
                    alAjustes = { ir(Pagina.Ajustes) },
                )
                Pagina.Nuevo -> PantNuevo(alVolver = ::volver, alElegir = { t -> pila = pila.dropLast(1) + Pagina.Editor(t.nuevo(), true) })
                is Pagina.Editor -> PantEditor(p.d, p.nuevo, eligiendo = eligiendo, alVolver = ::volver, alGuardar = { d -> if (eligiendo) alElegir(d) else volver() })
                Pagina.Ajustes -> PantAjustes(alVolver = ::volver)
            }
        }
    }
}
