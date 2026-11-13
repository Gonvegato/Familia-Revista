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
    Futbol.cargar(c)
    kotlin.concurrent.thread(name = "futbol-limpia") { Futbol.limpia() }
    SincroP.iniciar(c)
}

/** Dónde estás: la portada y sus secciones (se llega a ellas desde el menú de la portada, como en las hermanas). */
enum class Destino(val nombre: String) {
    Portada("Partidos"), Directo("Directo"), Guia("Guía"), Peliculas("Películas"), Series("Series"), Buscar("Buscar"), Listas("Listas"), Ajustes("Ajustes"), Favoritos("Favoritos"),
    /** «Tus equipos». */
    Futbol("Tus equipos"), AjustesFutbol("Ajustes del fútbol"), Tele("Tele"), Competiciones("Competiciones"),
    /** Los partidos del día (la portada de antes de la 0.8.0). */
    Hoy("Hoy");
    /** Lo de la tele (IPTV): al volver, a «Tele». */
    val deLaTele: Boolean get() = this in setOf(Directo, Guia, Peliculas, Series, Buscar, Listas, Favoritos)
}

/** Una ficha abierta por encima de la página (una película o una serie de una lista, o un partido). */
sealed class Ficha {
    class DePelicula(val lista: Lista, val pelicula: Pelicula) : Ficha()
    class DeSerie(val lista: Lista, val serie: Serie) : Ficha()
    class DePartido(val partido: com.gonzalo.iptv.futbol.Partido) : Ficha()
    /** La página de una competición (sus partidos por día, la clasificación, los goleadores). */
    class DeCompeticion(val liga: String) : Ficha()
    /** La página de un equipo (próximos, últimos, plantilla, su clasificación). */
    class DeEquipo(val equipo: com.gonzalo.iptv.futbol.Equipo, val liga: String) : Ficha()
    /** «IMÁGENES.» de un equipo: su escudo y su fondo. */
    class ImagenesDeEquipo(val equipo: com.gonzalo.iptv.futbol.Equipo) : Ficha()
}

private val entraHoja = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(220))
private val saleHoja = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it / 14 } + fadeOut(tween(160))

/** Lo que pide un widget al abrir la app (una página). Cada pedido es otro objeto: el mismo dos veces, dos veces. */
class Pedido(val destino: Destino?)

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

/**
 * La app: la portada son los partidos del día; el menú lleva a «Competiciones», «Tus equipos» y «Tele» (lo de antes: directo, favoritos,
 * guía, listas). Por encima, las fichas (un partido, una competición, un equipo, una película…), unas sobre otras: atrás cierra la de arriba.
 */
@Composable
fun CobreApp(inicial: Destino = Destino.Portada, pedido: Pedido? = null) {
    // Las páginas (Portada · Hoy · Competiciones · Tus equipos · IPTV) van juntas en [PantPrincipal]; lo demás, encima
    var encima by rememberSaveable { mutableIntStateOf(if (paginaDe(inicial) >= 0) -1 else inicial.ordinal) }
    var pagina by rememberSaveable { mutableIntStateOf(paginaDe(inicial).coerceAtLeast(0)) }
    var fichas by remember { mutableStateOf(listOf<Ficha>()) }
    val d: Destino? = encima.takeIf { it >= 0 }?.let { Destino.entries[it] }
    fun ir(x: Destino) { fichas = emptyList(); val p = paginaDe(x); if (p >= 0) { encima = -1; pagina = p } else encima = x.ordinal }
    val abre: (Ficha) -> Unit = { fichas = fichas + it }
    val cierra: () -> Unit = { fichas = fichas.dropLast(1) }
    // (lo que pide un widget o un aviso: abrir una página)
    LaunchedEffect(pedido) { pedido?.destino?.let { ir(it) } }
    AvisoActualizacion()
    // Atrás: lo de la tele vuelve a la página IPTV; lo demás, a la página en la que estabas; en una página, a la portada
    BackHandler(enabled = fichas.isEmpty() && d != null) { if (d!!.deLaTele) pagina = paginaDe(Destino.Tele); encima = -1 }
    BackHandler(enabled = fichas.isEmpty() && d == null && pagina != 0) { pagina = 0 }
    Box(Modifier.fillMaxSize().background(Paper)) {
        AnimatedContent(d, transitionSpec = {
            val ida = targetState != null
            (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { if (ida) it / 24 else -it / 24 }) togetherWith fadeOut(tween(140))
        }, label = "pagina") { x ->
            val volver = { encima = -1 }
            val aTele = { pagina = paginaDe(Destino.Tele); encima = -1 }
            when (x) {
                null -> PantPrincipal(pagina = pagina, alPagina = { pagina = it }, alIr = ::ir, alFicha = abre)
                Destino.Directo -> PantDirecto(alVolver = aTele, alIr = ::ir)
                Destino.Favoritos -> PantFavoritos(alVolver = aTele, alIr = ::ir)
                Destino.Guia -> PantGuia(alVolver = aTele, alIr = ::ir)
                Destino.Peliculas -> PantVideoclub(false, alVolver = aTele, alIr = ::ir, alFicha = abre)
                Destino.Series -> PantVideoclub(true, alVolver = aTele, alIr = ::ir, alFicha = abre)
                Destino.Buscar -> PantBuscar(alVolver = aTele, alFicha = abre)
                Destino.Listas -> PantListas(alVolver = aTele)
                Destino.Ajustes -> PantAjustes(alVolver = volver, alListas = { ir(Destino.Listas) })
                Destino.AjustesFutbol -> PantAjustes(alVolver = volver, alListas = { ir(Destino.Listas) }, inicial = PESTANA_FUTBOL)
                // (las páginas no llegan aquí: van en PantPrincipal)
                else -> PantPrincipal(pagina = pagina, alPagina = { pagina = it }, alIr = ::ir, alFicha = abre)
            }
        }
        // Las fichas, unas encima de otras: cada una nace de la fila que tocaste (en la página o en la ficha de debajo) y se cierra con el
        // gesto de atrás (con vista previa); debajo se sigue viendo la anterior
        val niveles = remember { HashMap<Int, Ficha>() }
        fichas.forEachIndexed { i, f -> niveles[i] = f }
        for (i in 0 until maxOf(6, fichas.size + 1)) androidx.compose.runtime.key(i) {
            com.gonzalo.familia.CapaQueNace(fichas.getOrNull(i)?.let { claveDe(it) }, alCerrar = { fichas = fichas.take(i) }, fondo = Paper, atrasActivo = i == fichas.size - 1) { _, _ ->
                when (val f = niveles[i]) {
                    is Ficha.DePelicula -> PantFichaPelicula(f.lista, f.pelicula, alCerrar = cierra)
                    is Ficha.DeSerie -> PantFichaSerie(f.lista, f.serie, alCerrar = cierra)
                    is Ficha.DePartido -> PantFichaPartido(f.partido, alCerrar = cierra, alConfigurar = { ir(Destino.AjustesFutbol) }, alFicha = abre)
                    is Ficha.DeCompeticion -> PantCompeticion(f.liga, alCerrar = cierra, alFicha = abre)
                    is Ficha.DeEquipo -> PantEquipo(f.equipo, f.liga, alCerrar = cierra, alFicha = abre, alImagenes = { e -> abre(Ficha.ImagenesDeEquipo(e)) })
                    is Ficha.ImagenesDeEquipo -> ContenidoImagenesEquipo(f.equipo, alCerrar = cierra)
                    null -> {}
                }
            }
        }
    }
}

/** La clave de una ficha: con ella sabe de qué fila nace (la fila de un partido, un equipo o una competición se apunta con la misma). */
fun claveDe(f: Ficha): String = when (f) {
    is Ficha.DePartido -> "p:" + f.partido.id
    is Ficha.DeEquipo -> "e:" + f.equipo.id
    is Ficha.DeCompeticion -> "c:" + f.liga
    is Ficha.ImagenesDeEquipo -> "img:" + f.equipo.id
    is Ficha.DePelicula -> "peli:" + System.identityHashCode(f)
    is Ficha.DeSerie -> "serie:" + System.identityHashCode(f)
}
