package com.gonzalo.mostaza

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
    Skin.cargar(c); TemaAtrio.cargarGuardado(c)
    Biblioteca.cargar(c); Addons.cargar(c); Tmdb.cargar(c)
    SincroP.iniciar(c)
    // El motor de torrents (no arranca nada hasta que se abre un torrent)
    FuenteMotor.conecta(c)
}

/** Dónde estás: la portada y sus secciones (se llega a ellas desde el menú de la portada, como en las hermanas). */
enum class Destino { Portada, Viendo, Pendiente, Visto, Abandonado, Explorar, Buscar, Addons, Ajustes }

private val entraHoja = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(220))
private val saleHoja = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it / 14 } + fadeOut(tween(160))

/** La app: portada de revista, sus secciones, la ficha por encima y, encima de todo, las fuentes de lo que vas a ver. */
@Composable
fun MostazaApp(fichaPedida: String? = null, addonPedido: String? = null, seccionPedida: String? = null, alAtender: () -> Unit = {}) {
    var destino by rememberSaveable { mutableIntStateOf(0) }
    var ficha by remember { mutableStateOf<MetaBreve?>(null) }
    var instalar by remember { mutableStateOf<String?>(null) }
    val d = Destino.entries[destino]
    LaunchedEffect(fichaPedida, addonPedido, seccionPedida) {
        // (una sección que pide un widget: la de debajo; si además pide una ficha, la ficha va por encima)
        seccionPedida?.let { s -> Destino.entries.firstOrNull { it.name == s }?.let { destino = it.ordinal; ficha = null } }
        fichaPedida?.let { r -> Biblioteca.porRef(r)?.let { ficha = it.breve() } }
        addonPedido?.let { instalar = it; destino = Destino.Addons.ordinal }
        if (fichaPedida != null || addonPedido != null || seccionPedida != null) alAtender()
    }
    fun ir(x: Destino) { destino = x.ordinal }
    AvisoActualizacion()
    BackHandler(enabled = ficha != null) { ficha = null }
    BackHandler(enabled = ficha == null && d != Destino.Portada) { destino = 0 }
    Box(Modifier.fillMaxSize().background(Paper)) {
        AnimatedContent(d, transitionSpec = {
            val ida = targetState != Destino.Portada
            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { if (ida) it / 24 else -it / 24 }) togetherWith fadeOut(tween(140))
        }, label = "pagina") { x ->
            when (x) {
                Destino.Portada -> PantPortada(alAbrir = { ficha = it }, alIr = ::ir)
                Destino.Explorar -> PantExplorar(alVolver = { destino = 0 }, alAbrir = { ficha = it })
                Destino.Buscar -> PantBuscar(alVolver = { destino = 0 }, alAbrir = { ficha = it })
                Destino.Addons -> PantAddons(alVolver = { destino = 0 }, instalar = instalar, alInstalado = { instalar = null })
                Destino.Ajustes -> PantEstilo(alVolver = { destino = 0 }, alAddons = { ir(Destino.Addons) })
                else -> PantSecciones(x, alVolver = { destino = 0 }, alAbrir = { ficha = it }, alIr = ::ir)
            }
        }
        AnimatedVisibility(ficha != null, enter = entraHoja, exit = saleHoja) {
            ficha?.let { b -> PantFicha(b, alCerrar = { ficha = null }, alAbrir = { ficha = it }) }
        }
    }
}
