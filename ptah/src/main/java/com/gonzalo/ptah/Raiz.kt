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

/** Dónde estás: la portada, la ficha de un diseño, elegir el tipo de uno nuevo, el editor y los ajustes. */
sealed class Pagina {
    data object Portada : Pagina()
    data class Ficha(val id: String) : Pagina()
    data object Nuevo : Pagina()
    data class Editor(val d: Diseno, val nuevo: Boolean) : Pagina()
    data object Ajustes : Pagina()
}

/** Lo que pide un widget al abrir la app (editar un diseño). Cada pedido es otro objeto: el mismo dos veces, dos veces. */
class Pedido(val editar: String?)

/**
 * La app. Con [eligiendo] (al poner un widget en la pantalla de inicio, desde [ConfigWidgetActivity]) tocar uno de tus diseños lo elige
 * para ese widget ([alElegir]); una plantilla, o uno nuevo, se hace tuyo en el editor y al guardarlo va a ese widget.
 * Sin [eligiendo]: tocar un diseño abre su ficha (ponerlo en la pantalla de inicio, editarlo, copiarlo, borrarlo); al guardar uno se vuelve
 * a la portada con él resaltado y con «Ponerlo en la pantalla de inicio» a mano.
 */
@Composable
fun PtahApp(pedido: Pedido? = null, eligiendo: Boolean = false, alElegir: (Diseno) -> Unit = {}, inicial: List<Pagina> = listOf(Pagina.Portada), guardadoInicial: String? = null) {
    val ctx = LocalContext.current
    var pila by remember { mutableStateOf(inicial) }
    /** El diseño que se acaba de guardar (la portada lo resalta y ofrece ponerlo en la pantalla). */
    var guardado by remember { mutableStateOf(guardadoInicial) }
    val arriba = pila.last()
    fun ir(p: Pagina) { pila = pila + p }
    fun volver() { if (pila.size > 1) pila = pila.dropLast(1) }
    LaunchedEffect(pedido) { pedido?.editar?.let { id -> Disenos.de(ctx, id)?.let { pila = listOf(Pagina.Portada, Pagina.Ficha(it.id)) } } }
    if (!eligiendo) AvisoActualizacion()
    BackHandler(enabled = pila.size > 1) { volver() }
    Box(Modifier.fillMaxSize().background(Paper)) {
        AnimatedContent(arriba, transitionSpec = {
            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 24 }) togetherWith fadeOut(tween(140))
        }, contentKey = { p -> when (p) { is Pagina.Editor -> "e" + p.d.id; is Pagina.Ficha -> "f" + p.id; else -> p.toString() } }, label = "pagina") { p ->
            when (p) {
                Pagina.Portada -> PantPortada(
                    eligiendo = eligiendo,
                    guardado = guardado,
                    alCerrarGuardado = { guardado = null },
                    alAbrir = { d -> if (eligiendo) alElegir(d) else ir(Pagina.Ficha(d.id)) },
                    alPlantilla = { pl -> ir(Pagina.Editor(Plantillas.copia(pl), true)) },
                    alNuevo = { ir(Pagina.Nuevo) },
                    alAjustes = { ir(Pagina.Ajustes) },
                )
                is Pagina.Ficha -> PantFicha(p.id, alVolver = ::volver, alEditar = { d -> ir(Pagina.Editor(d, false)) },
                    alCopiar = { d -> val c = Disenos.duplica(ctx, d); pila = listOf(Pagina.Portada); guardado = c.id })
                Pagina.Nuevo -> PantNuevo(alVolver = ::volver, alElegir = { t -> pila = pila.dropLast(1) + Pagina.Editor(t.nuevo(), true) })
                is Pagina.Editor -> PantEditor(p.d, p.nuevo, eligiendo = eligiendo, alVolver = ::volver, alGuardar = { d ->
                    if (eligiendo) alElegir(d) else { pila = listOf(Pagina.Portada); guardado = d.id }
                })
                Pagina.Ajustes -> PantAjustes(alVolver = ::volver)
            }
        }
    }
}
