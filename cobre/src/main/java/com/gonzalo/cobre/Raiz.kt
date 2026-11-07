package com.gonzalo.cobre

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
import androidx.compose.ui.platform.LocalContext
import com.gonzalo.iptv.Lista
import com.gonzalo.iptv.Pelicula
import com.gonzalo.iptv.Serie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Cuenta.cargar(c)
    Skin.cargar(c); TemaAtrio.cargarGuardado(c)
    Listas.cargar(c)
    Tele.cargar(c)
    SincroP.iniciar(c)
}

/** Dónde estás: la portada y sus secciones (se llega a ellas desde el menú de la portada, como en las hermanas). */
enum class Destino(val nombre: String) { Portada("Portada"), Directo("Directo"), Guia("Guía"), Peliculas("Películas"), Series("Series"), Buscar("Buscar"), Listas("Listas"), Ajustes("Ajustes"), Favoritos("Favoritos") }

/** Una ficha abierta por encima de la página (una película o una serie de una lista). */
sealed class Ficha {
    class DePelicula(val lista: Lista, val pelicula: Pelicula) : Ficha()
    class DeSerie(val lista: Lista, val serie: Serie) : Ficha()
}

private val entraHoja = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(220))
private val saleHoja = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it / 14 } + fadeOut(tween(160))

/** La lista con la que se está (la última que elegiste, si sigue; si no, la primera que se puede usar aquí). */
fun listaActual(c: Context): Lista? {
    val l = Listas.usables()
    return l.firstOrNull { it.id == AjustesDirecto.lista(c) } ?: l.firstOrNull()
}

/** Lo de una lista leído del disco (fuera del hilo principal), y otra vez cuando cambia. */
@Composable
fun rememberCargada(l: Lista?): Cargada? {
    val v = Tele.version
    val r by produceState(l?.let { Tele.enMemoria(it.id) }, l, v) { value = if (l == null) null else withContext(Dispatchers.IO) { runCatching { Tele.cargada(l) }.getOrNull() } }
    return r
}

/** Lo de varias listas leído del disco (fuera del hilo principal). */
@Composable
fun rememberCargadas(l: List<Lista>): Map<String, Cargada?> {
    val v = Tele.version
    val r by produceState(l.associate { it.id to Tele.enMemoria(it.id) }, l, v) { value = withContext(Dispatchers.IO) { l.associate { it.id to runCatching { Tele.cargada(it) }.getOrNull() } } }
    return r
}

/** La app: portada de revista, sus secciones y, por encima, la ficha de una película o de una serie. */
@Composable
fun CobreApp(inicial: Destino = Destino.Portada) {
    val ctx = LocalContext.current
    var destino by rememberSaveable { mutableIntStateOf(inicial.ordinal) }
    var ficha by remember { mutableStateOf<Ficha?>(null) }
    val d = Destino.entries[destino]
    fun ir(x: Destino) { destino = x.ordinal }
    AvisoActualizacion()
    BackHandler(enabled = ficha != null) { ficha = null }
    BackHandler(enabled = ficha == null && d != Destino.Portada) { destino = 0 }
    Box(Modifier.fillMaxSize().background(Paper)) {
        AnimatedContent(d, transitionSpec = {
            val ida = targetState != Destino.Portada
            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { if (ida) it / 24 else -it / 24 }) togetherWith fadeOut(tween(140))
        }, label = "pagina") { x ->
            val volver = { destino = 0 }
            when (x) {
                Destino.Portada -> PantPortada(alIr = ::ir, alFicha = { ficha = it })
                Destino.Directo -> PantDirecto(alVolver = volver, alIr = ::ir)
                Destino.Favoritos -> PantFavoritos(alVolver = volver, alIr = ::ir)
                Destino.Guia -> PantGuia(alVolver = volver, alIr = ::ir)
                Destino.Peliculas -> PantVideoclub(false, alVolver = volver, alIr = ::ir, alFicha = { ficha = it })
                Destino.Series -> PantVideoclub(true, alVolver = volver, alIr = ::ir, alFicha = { ficha = it })
                Destino.Buscar -> PantBuscar(alVolver = volver, alFicha = { ficha = it })
                Destino.Listas -> PantListas(alVolver = volver)
                Destino.Ajustes -> PantAjustes(alVolver = volver, alListas = { ir(Destino.Listas) })
            }
        }
        AnimatedVisibility(ficha != null, enter = entraHoja, exit = saleHoja) {
            when (val f = ficha) {
                is Ficha.DePelicula -> PantFichaPelicula(f.lista, f.pelicula, alCerrar = { ficha = null })
                is Ficha.DeSerie -> PantFichaSerie(f.lista, f.serie, alCerrar = { ficha = null })
                null -> {}
            }
        }
    }
}
